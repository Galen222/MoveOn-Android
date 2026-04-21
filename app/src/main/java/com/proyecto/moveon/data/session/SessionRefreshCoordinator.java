package com.proyecto.moveon.data.session;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.proyecto.moveon.core.concurrency.MoveOnExecutors;
import com.proyecto.moveon.data.remote.retrofit.RetrofitProvider;
import com.proyecto.moveon.data.session.dto.LoginResponseDto;
import com.proyecto.moveon.data.session.dto.RefreshRequestDto;
import com.proyecto.moveon.utils.StringUtils;

import java.io.IOException;

import okhttp3.ResponseBody;
import retrofit2.Response;

/**
 * Coordinador único del refresh de sesión para toda la app.
 *
 * <p>Centraliza dos orígenes de renovación:</p>
 * <ul>
 *     <li>Refresh proactivo al volver a foreground.</li>
 *     <li>Refresh reactivo cuando OkHttp recibe un 401 de un endpoint protegido.</li>
 * </ul>
 *
 * <p>La meta es que, aunque haya varias peticiones compitiendo, solo exista un refresh
 * real en vuelo y el resto de flujos reutilicen el resultado más reciente.</p>
 *
 * <p>Esta versión añade una mejora importante de testabilidad: la lógica de coordinación
 * ya no depende rígidamente de Retrofit ni de {@link SecureSessionManager}. En producción
 * se siguen usando ambos, pero internamente se adaptan a interfaces pequeñas para poder
 * montar tests de concurrencia reales y baratos.</p>
 */
public final class SessionRefreshCoordinator {

    private static final long PROACTIVE_REFRESH_WINDOW_SECONDS = 90L;
    private static final long REFRESH_WAIT_TIMEOUT_MS = 5000L;

    private static volatile SessionRefreshCoordinator instance;

    private final SessionStore sessionStore;
    private final RefreshBackend refreshBackend;
    private final Object monitor = new Object();

    private boolean refreshInFlight = false;
    @NonNull
    private RefreshOutcome lastOutcome = RefreshOutcome.skipped();

    public interface Callback {
        void onComplete(@NonNull RefreshOutcome outcome);
    }

    /**
     * Vista mínima y estable del almacenamiento de sesión.
     *
     * <p>La implementación de producción delega en {@link SecureSessionManager}, pero los tests
     * pueden usar una implementación en memoria para simular carreras entre varios hilos.</p>
     */
    public interface SessionStore {
        boolean isAccessTokenExpiringWithinSeconds(long leewaySeconds);

        @NonNull
        StoredSession getStoredSession();

        void saveLoginSync(@Nullable String username,
                           @Nullable String accessToken,
                           @Nullable String refreshToken);

        void logout();
    }

    /**
     * Backend mínimo capaz de ejecutar {@code /token/refresh}.
     *
     * <p>Separarlo de Retrofit permite comprobar que dos 401 concurrentes terminan en una sola
     * llamada real de refresh y que la coordinación sigue siendo correcta bajo concurrencia.</p>
     */
    public interface RefreshBackend {
        @NonNull
        BackendRefreshResult refresh(@NonNull String refreshToken) throws IOException;
    }

    public enum Status {
        SUCCESS,
        SKIPPED,
        UNAUTHORIZED,
        TRANSIENT_ERROR
    }

    /**
     * Snapshot inmutable de sesión usado por el coordinador.
     */
    public static final class StoredSession {
        @Nullable private final String username;
        @Nullable private final String accessToken;
        @Nullable private final String refreshToken;
        @Nullable private final String userId;

        public StoredSession(@Nullable String username,
                             @Nullable String accessToken,
                             @Nullable String refreshToken,
                             @Nullable String userId) {
            this.username = username;
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.userId = userId;
        }

        /**
         * Devuelve el nombre de usuario devuelto por backend junto a los tokens, si vino informado.
         */
        @Nullable public String getUsername() { return username; }
        /**
         * Devuelve el access token crudo retornado por backend.
         */
        @Nullable public String getAccessToken() { return accessToken; }
        /**
         * Devuelve el refresh token crudo retornado por backend.
         */
        @Nullable public String getRefreshToken() { return refreshToken; }
        /**
         * Devuelve el identificador interno del usuario asociado a la sesión.
         */
        @Nullable public String getUserId() { return userId; }

        /**
         * Indica si el snapshot conserva un refresh token con el que intentar la renovación.
         */
        public boolean hasRefreshToken() {
            return StringUtils.hasText(refreshToken);
        }
    }

    /**
     * Resultado normalizado del intento de refresh.
     */
    public static final class RefreshOutcome {
        @NonNull private final Status status;
        @Nullable private final String accessToken;
        @Nullable private final String refreshToken;
        private final int httpCode;
        @Nullable private final String retryAfter;
        @Nullable private final String errorCode;
        @Nullable private final String message;

        private RefreshOutcome(@NonNull Status status,
                               @Nullable String accessToken,
                               @Nullable String refreshToken,
                               int httpCode,
                               @Nullable String retryAfter,
                               @Nullable String errorCode,
                               @Nullable String message) {
            this.status = status;
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.httpCode = httpCode;
            this.retryAfter = retryAfter;
            this.errorCode = errorCode;
            this.message = message;
        }

        /**
         * Crea un resultado exitoso con el nuevo par de tokens publicado.
         */
        @NonNull
        public static RefreshOutcome success(@NonNull String accessToken,
                                             @NonNull String refreshToken) {
            return new RefreshOutcome(Status.SUCCESS, accessToken, refreshToken,
                    200, null, null, null);
        }

        /**
         * Crea un resultado que representa que no fue necesario renovar la sesión.
         */
        @NonNull
        public static RefreshOutcome skipped() {
            return new RefreshOutcome(Status.SKIPPED, null, null,
                    0, null, null, null);
        }

        /**
         * Crea un resultado terminal para respuestas que invalidan definitivamente la sesión actual.
         */
        @NonNull
        public static RefreshOutcome unauthorized(int httpCode,
                                                  @Nullable String errorCode,
                                                  @Nullable String message) {
            return new RefreshOutcome(Status.UNAUTHORIZED, null, null,
                    httpCode, null, errorCode, message);
        }

        /**
         * Crea un resultado recuperable para errores temporales de red o servidor.
         */
        @NonNull
        public static RefreshOutcome transientError(int httpCode,
                                                    @Nullable String retryAfter,
                                                    @Nullable String errorCode,
                                                    @Nullable String message) {
            return new RefreshOutcome(Status.TRANSIENT_ERROR, null, null,
                    httpCode, retryAfter, errorCode, message);
        }

        /**
         * Devuelve el estado resumido del intento de refresh.
         */
        @NonNull public Status getStatus() { return status; }
        @Nullable public String getAccessToken() { return accessToken; }
        @Nullable public String getRefreshToken() { return refreshToken; }
        /**
         * Devuelve el código HTTP de la respuesta cruda de refresh.
         */
        public int getHttpCode() { return httpCode; }
        /**
         * Devuelve el valor bruto de la cabecera de reintento para errores temporales.
         */
        @Nullable public String getRetryAfter() { return retryAfter; }
        /**
         * Devuelve el código semántico de error del backend de refresh.
         */
        @Nullable public String getErrorCode() { return errorCode; }
        /**
         * Devuelve el mensaje backend asociado al fallo de refresh, si existe.
         */
        @Nullable public String getMessage() { return message; }

        /**
         * Indica si el refresh terminó publicando un nuevo par de tokens.
         */
        public boolean isSuccess() { return status == Status.SUCCESS; }
        /**
         * Indica si el coordinador decidió no refrescar porque no era necesario.
         */
        public boolean isSkipped() { return status == Status.SKIPPED; }
        /**
         * Indica si la sesión quedó inválida y debe tratarse como no autorizada.
         */
        public boolean isUnauthorized() { return status == Status.UNAUTHORIZED; }
        /**
         * Indica si el fallo puede volver a intentarse más adelante.
         */
        public boolean isTransientError() { return status == Status.TRANSIENT_ERROR; }
    }

    /**
     * Resultado crudo que devuelve el backend de refresh antes de adaptarlo a {@link RefreshOutcome}.
     */
    public static final class BackendRefreshResult {
        private final boolean successful;
        private final int httpCode;
        @Nullable private final String accessToken;
        @Nullable private final String refreshToken;
        @Nullable private final String username;
        @Nullable private final String retryAfter;
        @Nullable private final String errorCode;
        @Nullable private final String backendMessage;

        private BackendRefreshResult(boolean successful,
                                     int httpCode,
                                     @Nullable String accessToken,
                                     @Nullable String refreshToken,
                                     @Nullable String username,
                                     @Nullable String retryAfter,
                                     @Nullable String errorCode,
                                     @Nullable String backendMessage) {
            this.successful = successful;
            this.httpCode = httpCode;
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.username = username;
            this.retryAfter = retryAfter;
            this.errorCode = errorCode;
            this.backendMessage = backendMessage;
        }

        /**
         * Construye el resultado crudo de una llamada de refresh aceptada por backend.
         */
        @NonNull
        public static BackendRefreshResult success(@NonNull String accessToken,
                                                   @NonNull String refreshToken,
                                                   @Nullable String username) {
            return new BackendRefreshResult(true, 200, accessToken, refreshToken,
                    username, null, null, null);
        }

        /**
         * Construye el resultado crudo de una respuesta de refresh fallida.
         */
        @NonNull
        public static BackendRefreshResult failure(int httpCode,
                                                   @Nullable String retryAfter,
                                                   @Nullable String errorCode,
                                                   @Nullable String backendMessage) {
            return new BackendRefreshResult(false, httpCode, null, null,
                    null, retryAfter, errorCode, backendMessage);
        }

        /**
         * Indica si la respuesta cruda de refresh fue aceptada por backend.
         */
        public boolean isSuccessful() { return successful; }
        public int getHttpCode() { return httpCode; }
        @Nullable public String getAccessToken() { return accessToken; }
        @Nullable public String getRefreshToken() { return refreshToken; }
        @Nullable public String getUsername() { return username; }
        @Nullable public String getRetryAfter() { return retryAfter; }
        @Nullable public String getErrorCode() { return errorCode; }
        /**
         * Devuelve el mensaje textual bruto asociado al fallo de refresh.
         */
        @Nullable public String getBackendMessage() { return backendMessage; }
    }

    /**
     * Devuelve el coordinador singleton usado por toda la app para deduplicar refreshes concurrentes.
     */
    @NonNull
    public static SessionRefreshCoordinator getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (SessionRefreshCoordinator.class) {
                if (instance == null) {
                    Context appContext = context.getApplicationContext();
                    SecureSessionManager sessionManager = SecureSessionManager.getInstance(appContext);
                    instance = new SessionRefreshCoordinator(
                            new SecureSessionStore(sessionManager),
                            new RetrofitRefreshBackend(appContext)
                    );
                }
            }
        }
        return instance;
    }

    /**
     * Fábrica orientada a tests.
     *
     * <p>Permite inyectar un almacén en memoria y un backend falso para verificar que,
     * con dos 401 casi simultáneos, el coordinador hace un único refresh real.</p>
     */
    @NonNull
    public static SessionRefreshCoordinator createForTests(@NonNull SessionStore sessionStore,
                                                           @NonNull RefreshBackend refreshBackend) {
        return new SessionRefreshCoordinator(sessionStore, refreshBackend);
    }

    /**
     * Crea un coordinador con el almacén de sesión y el backend de refresh que deben cooperar.
     */
    private SessionRefreshCoordinator(@NonNull SessionStore sessionStore,
                                      @NonNull RefreshBackend refreshBackend) {
        this.sessionStore = sessionStore;
        this.refreshBackend = refreshBackend;
    }

    /**
     * Indica si conviene renovar antes de que el token caduque del todo.
     */
    public boolean shouldRefreshProactively() {
        return sessionStore.isAccessTokenExpiringWithinSeconds(PROACTIVE_REFRESH_WINDOW_SECONDS);
    }

    /**
     * Lanza un refresh en un hilo de trabajo.
     *
     * <p>Si otro refresh ya está en curso, este flujo quedará deduplicado
     * por {@link #refreshBlocking(String, boolean)}.</p>
     */
    public void ensureFreshSessionAsync(@NonNull Callback callback) {
        MoveOnExecutors.io().execute(() -> {
            RefreshOutcome outcome = refreshBlocking(null, false);
            callback.onComplete(outcome);
        });
    }

    /**
     * Ejecuta o reutiliza un refresh de sesión.
     *
     * @param failedAuthorizationHeader cabecera Authorization del request que falló con 401.
     *                                  Si ya existe una sesión más nueva en memoria, esta cabecera
     *                                  permite detectarlo y reutilizarla sin volver a llamar al backend.
     * @param forceRefresh              {@code true} cuando venimos de un 401 real y debemos intentar
     *                                  renovar aunque no estemos aún dentro de la ventana proactiva.
     */
    @NonNull
    public RefreshOutcome refreshBlocking(@Nullable String failedAuthorizationHeader,
                                          boolean forceRefresh) {
        synchronized (monitor) {
            RefreshOutcome immediate = buildImmediateOutcomeLocked(failedAuthorizationHeader, forceRefresh);
            if (immediate != null) {
                return immediate;
            }

            if (refreshInFlight) {
                waitForCurrentRefreshLocked();
                return buildOutcomeAfterWaitLocked(failedAuthorizationHeader, forceRefresh);
            }

            refreshInFlight = true;
        }

        RefreshOutcome outcome = RefreshOutcome.transientError(
                0,
                null,
                null,
                "Unexpected refresh termination"
        );
        try {
            outcome = executeRefreshNow();
        } finally {
            synchronized (monitor) {
                lastOutcome = outcome;
                refreshInFlight = false;
                monitor.notifyAll();
            }
        }

        synchronized (monitor) {
            return adaptOutcomeForCallerLocked(failedAuthorizationHeader, forceRefresh, outcome);
        }
    }

    /**
     * Decide si el caller puede obtener una respuesta inmediata sin lanzar un refresh real.
     */
    @Nullable
    private RefreshOutcome buildImmediateOutcomeLocked(@Nullable String failedAuthorizationHeader,
                                                       boolean forceRefresh) {
        RefreshOutcome reused = tryReuseStoredSessionLocked(failedAuthorizationHeader);
        if (reused != null) {
            return reused;
        }

        if (!forceRefresh && !shouldRefreshProactively()) {
            return RefreshOutcome.skipped();
        }

        StoredSession snapshot = sessionStore.getStoredSession();
        if (!snapshot.hasRefreshToken()) {
            sessionStore.logout();
            return RefreshOutcome.unauthorized(401, null, "No refresh token available");
        }

        return null;
    }

    /**
     * Calcula el resultado que debe recibir un flujo que se quedó esperando a un refresh ya en curso.
     */
    @NonNull
    private RefreshOutcome buildOutcomeAfterWaitLocked(@Nullable String failedAuthorizationHeader,
                                                       boolean forceRefresh) {
        RefreshOutcome reused = tryReuseStoredSessionLocked(failedAuthorizationHeader);
        if (reused != null) {
            return reused;
        }

        if (!forceRefresh && !shouldRefreshProactively()) {
            return RefreshOutcome.skipped();
        }

        if (lastOutcome.isSuccess()) {
            RefreshOutcome current = buildSuccessFromStoredSession();
            if (current != null) {
                return current;
            }
        }

        StoredSession snapshot = sessionStore.getStoredSession();
        if (!snapshot.hasRefreshToken()) {
            return RefreshOutcome.unauthorized(401, null, "No refresh token available");
        }

        return lastOutcome;
    }

    /**
     * Ajusta el resultado final al contexto del caller, reutilizando sesión nueva si ya quedó almacenada.
     */
    @NonNull
    private RefreshOutcome adaptOutcomeForCallerLocked(@Nullable String failedAuthorizationHeader,
                                                       boolean forceRefresh,
                                                       @NonNull RefreshOutcome outcome) {
        RefreshOutcome reused = tryReuseStoredSessionLocked(failedAuthorizationHeader);
        if (reused != null) {
            return reused;
        }

        if (outcome.isSuccess()) {
            RefreshOutcome current = buildSuccessFromStoredSession();
            if (current != null) {
                return current;
            }
        }

        if (!forceRefresh && !outcome.isUnauthorized() && !outcome.isTransientError()) {
            return RefreshOutcome.skipped();
        }

        return outcome;
    }

    /**
     * Si el request que ha fallado llevaba un access token antiguo, devolvemos la sesión
     * actualmente almacenada en vez de refrescar otra vez.
     */
    @Nullable
    private RefreshOutcome tryReuseStoredSessionLocked(@Nullable String failedAuthorizationHeader) {
        if (!StringUtils.hasText(failedAuthorizationHeader)) return null;

        StoredSession snapshot = sessionStore.getStoredSession();
        String currentAccess = snapshot.getAccessToken();
        String currentRefresh = snapshot.getRefreshToken();
        if (!StringUtils.hasText(currentAccess) || !StringUtils.hasText(currentRefresh)) return null;

        String expected = "Bearer " + currentAccess;
        if (!expected.equals(failedAuthorizationHeader)) {
            return RefreshOutcome.success(currentAccess, currentRefresh);
        }
        return null;
    }

    /**
     * Reconstruye un resultado exitoso a partir de la sesión ya persistida si ambos tokens están presentes.
     */
    @Nullable
    private RefreshOutcome buildSuccessFromStoredSession() {
        StoredSession snapshot = sessionStore.getStoredSession();
        String currentAccess = snapshot.getAccessToken();
        String currentRefresh = snapshot.getRefreshToken();
        if (!StringUtils.hasText(currentAccess) || !StringUtils.hasText(currentRefresh)) {
            return null;
        }
        return RefreshOutcome.success(currentAccess, currentRefresh);
    }

    /**
     * Espera a que termine el refresh en vuelo o fuerza la salida al alcanzar el timeout de coordinación.
     */
    private void waitForCurrentRefreshLocked() {
        long deadlineMs = System.currentTimeMillis() + REFRESH_WAIT_TIMEOUT_MS;
        while (refreshInFlight) {
            long remainingMs = deadlineMs - System.currentTimeMillis();
            if (remainingMs <= 0L) {
                refreshInFlight = false;
                monitor.notifyAll();
                break;
            }
            try {
                monitor.wait(remainingMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * Realiza o reutiliza la llamada HTTP real a {@code /token/refresh}.
     */
    @NonNull
    private RefreshOutcome executeRefreshNow() {
        StoredSession snapshot = sessionStore.getStoredSession();
        String refreshToken = snapshot.getRefreshToken();
        if (!StringUtils.hasText(refreshToken)) {
            sessionStore.logout();
            return RefreshOutcome.unauthorized(401, null, "No refresh token available");
        }

        try {
            BackendRefreshResult refreshResp = refreshBackend.refresh(refreshToken);

            if (refreshResp.isSuccessful()) {
                String newAccess = refreshResp.getAccessToken();
                String newRefresh = refreshResp.getRefreshToken();

                if (!StringUtils.hasText(newAccess) || !StringUtils.hasText(newRefresh)) {
                    sessionStore.logout();
                    return RefreshOutcome.unauthorized(401, null, "Refresh response without valid tokens");
                }

                String username = StringUtils.hasText(refreshResp.getUsername())
                        ? refreshResp.getUsername()
                        : StringUtils.textOf(snapshot.getUsername());

                // La publicación del nuevo par access/refresh debe ser síncrona
                // para que ninguna petición tardía relea el refresh antiguo y
                // provoque "reutilizacion_refresh_detectada".
                sessionStore.saveLoginSync(username, newAccess, newRefresh);
                return RefreshOutcome.success(newAccess, newRefresh);
            }

            int code = refreshResp.getHttpCode();
            if (code == 401 || code == 403) {
                sessionStore.logout();
                return RefreshOutcome.unauthorized(code,
                        refreshResp.getErrorCode(),
                        refreshResp.getBackendMessage());
            }

            if (code == 429 || code >= 500) {
                return RefreshOutcome.transientError(code,
                        refreshResp.getRetryAfter(),
                        refreshResp.getErrorCode(),
                        refreshResp.getBackendMessage());
            }

            return RefreshOutcome.unauthorized(code,
                    refreshResp.getErrorCode(),
                    refreshResp.getBackendMessage());
        } catch (IOException ioException) {
            return RefreshOutcome.transientError(0, null, null, ioException.getMessage());
        } catch (Exception e) {
            return RefreshOutcome.transientError(0, null, null, e.getMessage());
        }
    }

    /**
     * Adaptador de producción sobre {@link SecureSessionManager}.
     */
    private static final class SecureSessionStore implements SessionStore {
        private final SecureSessionManager sessionManager;

        /**
         * Crea el adaptador sobre el almacenamiento seguro de sesión usado en producción.
         */
        private SecureSessionStore(@NonNull SecureSessionManager sessionManager) {
            this.sessionManager = sessionManager;
        }

        @Override
        /**
         * Reenvía al almacenamiento seguro la comprobación de caducidad próxima del access token.
         *
         * @param leewaySeconds margen de seguridad en segundos usado para considerar el token como "a punto de expirar".
         * @return {@code true} si conviene refrescar antes de seguir usando el token actual.
         */
        public boolean isAccessTokenExpiringWithinSeconds(long leewaySeconds) {
            return sessionManager.isAccessTokenExpiringWithinSeconds(leewaySeconds);
        }

        @NonNull
        @Override
        /**
         * Obtiene un snapshot consistente de la sesión actual desde {@link SecureSessionManager}.
         *
         * @return sesión almacenada adaptada al formato ligero que usa el coordinador.
         */
        public StoredSession getStoredSession() {
            SecureSessionManager.SessionSnapshot snapshot = sessionManager.getSessionSnapshot();
            return new StoredSession(
                    snapshot.getUsername(),
                    snapshot.getAccessToken(),
                    snapshot.getRefreshToken(),
                    snapshot.getUserId()
            );
        }

        @Override
        public void saveLoginSync(@Nullable String username,
                                  @Nullable String accessToken,
                                  @Nullable String refreshToken) {
            sessionManager.saveLoginSync(username, accessToken, refreshToken);
        }

        @Override
        /**
         * Elimina la sesión persistida cuando el refresh concluye que ya no es recuperable.
         */
        public void logout() {
            sessionManager.logout();
        }
    }

    /**
     * Adaptador de producción sobre Retrofit.
     */
    private static final class RetrofitRefreshBackend implements RefreshBackend {
        private final Context appContext;

        /**
         * Crea el backend real de refresh reutilizando el contexto de aplicación.
         */
        private RetrofitRefreshBackend(@NonNull Context appContext) {
            this.appContext = appContext.getApplicationContext();
        }

        @NonNull
        @Override
        /**
         * Ejecuta la llamada real a {@code /token/refresh} y traduce la respuesta Retrofit al resultado interno del coordinador.
         *
         * @param refreshToken refresh token vigente con el que se intenta renovar la sesión.
         * @return resultado crudo del backend con éxito o metadatos de error ya clasificados.
         * @throws IOException si el transporte HTTP falla antes de obtener respuesta.
         */
        public BackendRefreshResult refresh(@NonNull String refreshToken) throws IOException {
            Response<LoginResponseDto> refreshResp = RetrofitProvider.authApi(appContext)
                    .refresh(new RefreshRequestDto(refreshToken))
                    .execute();

            if (refreshResp.isSuccessful() && refreshResp.body() != null) {
                LoginResponseDto body = refreshResp.body();
                return BackendRefreshResult.success(body.tokenAcceso, body.refreshToken, body.nombreUsuario);
            }

            ParsedRefreshError parsed = parseError(refreshResp);
            return BackendRefreshResult.failure(
                    refreshResp.code(),
                    parsed.retryAfter,
                    parsed.errorCode,
                    parsed.backendMessage
            );
        }

        /**
         * Extrae de la respuesta fallida los metadatos necesarios para clasificar el fallo de refresh.
         */
        @NonNull
        private ParsedRefreshError parseError(@NonNull Response<?> response) {
            String retryAfter = response.headers().get("Retry-After");
            ResponseBody errorBody = response.errorBody();
            if (errorBody == null) {
                return new ParsedRefreshError(retryAfter, null, null);
            }

            try {
                String raw = errorBody.string();
                if (!StringUtils.hasText(raw)) {
                    return new ParsedRefreshError(retryAfter, null, null);
                }

                JsonElement root = JsonParser.parseString(raw);
                if (root == null || !root.isJsonObject()) {
                    return new ParsedRefreshError(retryAfter, null, raw);
                }

                JsonObject obj = root.getAsJsonObject();
                String errorCode = getAsString(obj, "error_code");
                String message = firstNonEmpty(
                        getAsString(obj, "mensaje"),
                        getAsString(obj, "message"),
                        getAsString(obj, "error")
                );

                if (!StringUtils.hasText(message) && obj.has("detail")) {
                    JsonElement detail = obj.get("detail");
                    if (detail != null && detail.isJsonPrimitive()) {
                        message = detail.getAsString();
                    }
                }

                return new ParsedRefreshError(retryAfter, errorCode, message);
            } catch (Exception ignored) {
                return new ParsedRefreshError(retryAfter, null, null);
            } finally {
                errorBody.close();
            }
        }

        /**
         * Lee una propiedad string opcional del JSON de error y devuelve {@code null} si no es usable.
         */
        @Nullable
        private String getAsString(@NonNull JsonObject obj, @NonNull String key) {
            if (!obj.has(key) || obj.get(key) == null || !obj.get(key).isJsonPrimitive()) {
                return null;
            }
            String value = obj.get(key).getAsString();
            return StringUtils.hasText(value) ? value : null;
        }

        /**
         * Devuelve la primera cadena no vacía del listado recibido.
         */
        @Nullable
        private String firstNonEmpty(@Nullable String... values) {
            if (values == null) return null;
            for (String value : values) {
                if (StringUtils.hasText(value)) {
                    return value;
                }
            }
            return null;
        }
    }

    private static final class ParsedRefreshError {
        @Nullable final String retryAfter;
        @Nullable final String errorCode;
        @Nullable final String backendMessage;

        /**
         * Agrupa la información mínima extraída de un error de refresh.
         */
        private ParsedRefreshError(@Nullable String retryAfter,
                                   @Nullable String errorCode,
                                   @Nullable String backendMessage) {
            this.retryAfter = retryAfter;
            this.errorCode = errorCode;
            this.backendMessage = backendMessage;
        }
    }
}
