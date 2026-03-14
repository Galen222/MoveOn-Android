package com.proyecto.moveon.data.remote.retrofit;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.proyecto.moveon.core.auth.GlobalAuthManager;
import com.proyecto.moveon.data.session.SecureSessionManager;
import com.proyecto.moveon.data.session.dto.LoginResponseDto;
import com.proyecto.moveon.data.session.dto.RefreshRequestDto;
import com.proyecto.moveon.utils.StringUtils;

import java.io.IOException;

import okhttp3.Authenticator;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;
import okhttp3.ResponseBody;

public class TokenAuthenticator implements Authenticator {

    private static final Object LOCK = new Object();
    private static final HttpUrl TARGET_URL = HttpUrl.get(com.proyecto.moveon.BuildConfig.BASE_URL);
    private static final String TARGET_HOST = TARGET_URL.host();
    private static final int TARGET_PORT = TARGET_URL.port();

    private final Context context;
    private final SecureSessionManager sessionManager;

    public TokenAuthenticator(Context context) {
        this.context = context.getApplicationContext();
        this.sessionManager = SecureSessionManager.getInstance(this.context);
    }

    @Nullable
    @Override
    public Request authenticate(@Nullable Route route, @NonNull Response response) throws IOException {
        // 1. SEGURIDAD: Solo actuamos si la petición es hacia nuestro servidor (validamos esquema, host y puerto)
        HttpUrl requestUrl = response.request().url();
        if (!requestUrl.scheme().equals(TARGET_URL.scheme()) ||
                !requestUrl.host().equals(TARGET_HOST) ||
                requestUrl.port() != TARGET_PORT) {
            return null;
        }

        // 2. ANTI-BUCLE: Si ya hemos intentado refrescar y la petición sigue fallando,
        // evitamos un bucle infinito limitando a 2 reintentos.
        if (responseCount(response) >= 2) {
            return null;
        }

        // 3. SINCRONIZACIÓN: Usamos un LOCK para que si hay 10 peticiones fallando por 401 a la vez,
        // solo el primer hilo haga la llamada de refresco y los demás esperen.
        synchronized (LOCK) {
            String currentToken = sessionManager.getAccessToken();
            String headerAuth = response.request().header("Authorization");

            // Si el token que tenemos en el manager ya es distinto al que causó el error,
            // significa que otro hilo ya refrescó el token con éxito.
            if (StringUtils.hasText(currentToken) && StringUtils.hasText(headerAuth)) {
                String expected = "Bearer " + currentToken;
                if (!headerAuth.equals(expected)) {
                    // Reintentamos inmediatamente con el nuevo token que ya existe en el manager
                    return response.request().newBuilder()
                            .header("Authorization", expected)
                            .build();
                }
            }

            // 4. VALIDACIÓN DE REFRESH TOKEN: Si no tenemos token de refresco, no podemos hacer nada
            String refreshToken = sessionManager.getRefreshToken();
            if (!StringUtils.hasText(refreshToken)) {
                logout(); // Limpieza local y aviso a la UI
                return null;
            }

            // 5. LLAMADA DE REFRESCO: Ejecución síncrona del endpoint /token/refresh
            retrofit2.Response<LoginResponseDto> refreshResp = RetrofitProvider.authApi(context)
                    .refresh(new RefreshRequestDto(refreshToken))
                    .execute();

            if (refreshResp.isSuccessful() && refreshResp.body() != null) {
                String newAccess = refreshResp.body().tokenAcceso;
                String newRefresh = refreshResp.body().refreshToken;

                // HARDENING: Si el servidor devuelve un 200, pero sin tokens válidos, la sesión está corrupta
                if (!StringUtils.hasText(newAccess) || !StringUtils.hasText(newRefresh)) {
                    logout();
                    return null;
                }

                // 6. ACTUALIZACIÓN: Guardamos los nuevos tokens en el almacenamiento seguro
                sessionManager.updateTokens(newAccess, newRefresh);

                // 7. REINTENTO: Devolvemos la petición original modificada con el nuevo token de acceso
                return response.request().newBuilder()
                        .header("Authorization", "Bearer " + newAccess)
                        .build();
            } else {
                String backendErrorCode = null;
                String backendMessage = null;
                ResponseBody errorBody = refreshResp.errorBody();
                if (errorBody != null) {
                    try {
                        String raw = errorBody.string();
                        if (StringUtils.hasText(raw)) {
                            JsonElement root = JsonParser.parseString(raw);
                            if (root != null && root.isJsonObject()) {
                                JsonObject obj = root.getAsJsonObject();
                                backendErrorCode = getString(obj, "error_code");
                                backendMessage = firstNonEmpty(
                                        getString(obj, "mensaje"),
                                        getString(obj, "message"),
                                        getString(obj, "error")
                                );
                                if (!StringUtils.hasText(backendMessage) && obj.has("detail") && obj.get("detail").isJsonPrimitive()) {
                                    backendMessage = getString(obj, "detail");
                                }
                            }
                        }
                    } catch (Exception ignored) {
                    } finally {
                        try {
                            errorBody.close();
                        } catch (Exception ignored) {
                        }
                    }
                }

                int code = refreshResp.code();

                // Si el servidor nos dice que el Refresh Token es inválido o ha expirado (401/403),
                // la sesión ha muerto definitivamente.
                if (code == 401 || code == 403) {
                    logout();
                    return null;
                }

                // 9. EXCEPCIÓN DE FALLO TEMPORAL: Para errores como 5xx o 429 (Rate Limit),
                // lanzamos la excepción que nuestro ApiErrorParser convertirá en un mensaje útil.
                throw new RefreshFailedException(
                        code,
                        refreshResp.headers().get("Retry-After"),
                        backendErrorCode,
                        backendMessage
                );
            }
        }
    }

    private void logout() {
        // Corta el bucle de reintentos 401, pero NO destruye el refresh token.
        // Así el logout explícito del usuario todavía puede intentar revocar
        // la sesión en backend.
        sessionManager.clearAccessTokenOnly();
        GlobalAuthManager.getInstance().notifySessionExpired();
    }

    private int responseCount(Response response) {
        int result = 1;
        while ((response = response.priorResponse()) != null) result++;
        return result;
    }

    @Nullable
    private static String getString(@NonNull JsonObject obj, @NonNull String key) {
        if (!obj.has(key) || obj.get(key) == null || !obj.get(key).isJsonPrimitive()) return null;
        String value = obj.get(key).getAsString();
        return StringUtils.hasText(value) ? value : null;
    }

    @Nullable
    private static String firstNonEmpty(@Nullable String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (StringUtils.hasText(value)) return value;
        }
        return null;
    }

    // ENCAPSULACIÓN: Campos privados y getters
    public static class RefreshFailedException extends IOException {
        private final int code;
        @Nullable private final String retryAfter;
        @Nullable private final String errorCode;
        @Nullable private final String backendMessage;

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

        public int getCode() { return code; }
        @Nullable public String getRetryAfter() { return retryAfter; }
        @Nullable public String getErrorCode() { return errorCode; }
        @Nullable public String getBackendMessage() { return backendMessage; }
    }
}
