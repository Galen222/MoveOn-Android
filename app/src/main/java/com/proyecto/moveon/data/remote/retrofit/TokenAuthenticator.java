package com.proyecto.moveon.data.remote.retrofit;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.proyecto.moveon.core.auth.GlobalAuthManager;
import com.proyecto.moveon.data.session.SessionRefreshCoordinator;
import com.proyecto.moveon.utils.StringUtils;

import java.io.IOException;

import okhttp3.Authenticator;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;

/**
 * {@link Authenticator} de OkHttp para endpoints protegidos.
 *
 * <p>Cuando un endpoint protegido devuelve 401, este autenticador delega la renovación
 * en {@link SessionRefreshCoordinator}. La clave es que el coordinador deduplica refreshes,
 * de forma que varias respuestas 401 casi simultáneas no terminan en múltiples llamadas a
 * {@code /token/refresh} con el mismo refresh token rotado.</p>
 */
public class TokenAuthenticator implements Authenticator {

    private static final HttpUrl TARGET_URL = HttpUrl.get(com.proyecto.moveon.BuildConfig.BASE_URL);
    private static final String TARGET_HOST = TARGET_URL.host();
    private static final int TARGET_PORT = TARGET_URL.port();

    private final SessionRefreshCoordinator sessionRefreshCoordinator;

    /**
     * Crea el autenticador obteniendo el coordinador global de refresh asociado al proceso.
     *
     * @param context cualquier contexto Android desde el que obtener el {@code applicationContext}.
     */
    public TokenAuthenticator(Context context) {
        this(SessionRefreshCoordinator.getInstance(context.getApplicationContext()));
    }

    /**
     * Constructor adicional para tests.
     *
     * <p>Permite inyectar un coordinador con backend y almacenamiento falsos para validar
     * escenarios de concurrencia de forma determinista.</p>
     */
    TokenAuthenticator(@NonNull SessionRefreshCoordinator sessionRefreshCoordinator) {
        this.sessionRefreshCoordinator = sessionRefreshCoordinator;
    }

    /**
     * Intenta reconstruir una petición protegida tras un 401 renovando antes el access token.
     *
     * @param route ruta de red propuesta por OkHttp, no utilizada directamente.
     * @param response respuesta 401 original que disparó la autenticación.
     * @return nueva {@link Request} con token renovado o {@code null} si la petición no debe reintentarse.
     * @throws IOException cuando el refresh falla de forma transitoria y OkHttp debe propagar el error.
     */
    @Nullable
    @Override
    public Request authenticate(@Nullable Route route, @NonNull Response response) throws IOException {
        HttpUrl requestUrl = response.request().url();
        if (!requestUrl.scheme().equals(TARGET_URL.scheme())
                || !requestUrl.host().equals(TARGET_HOST)
                || requestUrl.port() != TARGET_PORT) {
            return null;
        }

        if (responseCount(response) >= 2) {
            return null;
        }

        SessionRefreshCoordinator.RefreshOutcome outcome =
                sessionRefreshCoordinator.refreshBlocking(response.request().header("Authorization"), true);

        if (outcome.isSuccess() && StringUtils.hasText(outcome.getAccessToken())) {
            return response.request().newBuilder()
                    .header("Authorization", "Bearer " + outcome.getAccessToken())
                    .build();
        }

        if (outcome.isUnauthorized()) {
            GlobalAuthManager.getInstance().notifySessionExpired();
            return null;
        }

        throw new RefreshFailedException(
                outcome.getHttpCode(),
                outcome.getRetryAfter(),
                outcome.getErrorCode(),
                outcome.getMessage()
        );
    }

    /**
     * Cuenta cuántas respuestas encadenadas lleva la misma petición para evitar bucles infinitos de autenticación.
     *
     * @param response respuesta actual enlazada con sus {@code priorResponse}.
     * @return número total de respuestas acumuladas para la petición.
     */
    private int responseCount(Response response) {
        int result = 1;
        while ((response = response.priorResponse()) != null) result++;
        return result;
    }

    /**
     * Excepción checked que encapsula errores transitorios durante refresh.
     */
    public static class RefreshFailedException extends IOException {
        private final int code;
        @Nullable private final String retryAfter;
        @Nullable private final String errorCode;
        @Nullable private final String backendMessage;

        /**
         * Crea la excepción checked que describe por qué el refresh no pudo completarse.
         *
         * @param code código HTTP devuelto por el backend.
         * @param retryAfter valor opcional de cabecera para reintento.
         * @param errorCode código interno opcional devuelto por el backend.
         * @param backendMessage mensaje opcional devuelto por el backend.
         */
        public RefreshFailedException(int code,
                                      @Nullable String retryAfter,
                                      @Nullable String errorCode,
                                      @Nullable String backendMessage) {
            super("Refresh error: " + code + (StringUtils.hasText(errorCode) ? " / " + errorCode : ""));
            this.code = code;
            this.retryAfter = retryAfter;
            this.errorCode = errorCode;
            this.backendMessage = backendMessage;
        }

        /**
         * Devuelve el código HTTP asociado al fallo de refresh.
         *
         * @return código HTTP recibido del backend.
         */
        public int getCode() { return code; }
        /**
         * Devuelve el valor de {@code Retry-After} cuando el backend lo proporcionó.
         *
         * @return cabecera de reintento o {@code null}.
         */
        @Nullable public String getRetryAfter() { return retryAfter; }
        /**
         * Devuelve el código de error interno enviado por el backend, si existe.
         *
         * @return código interno o {@code null}.
         */
        @Nullable public String getErrorCode() { return errorCode; }
        /**
         * Devuelve el mensaje textual devuelto por el backend, si existe.
         *
         * @return mensaje del backend o {@code null}.
         */
        @Nullable public String getBackendMessage() { return backendMessage; }
    }
}
