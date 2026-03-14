package com.proyecto.moveon.core.api;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.proyecto.moveon.R;
import com.proyecto.moveon.utils.StringUtils;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.ResponseBody;
import retrofit2.Response;

public final class ApiErrorParser {

    private ApiErrorParser() {}

    /**
     * Parsea una respuesta de error de Retrofit (códigos HTTP 4xx o 5xx)
     * Prioridad de mensaje para el usuario:
     *   1. Primer mensaje específico de campo del array "detail" (preferiblemente por error_code)
     *   2. Mensajes de "errores_campos" (preferiblemente por error_code)
     *   3. Campo top-level "error_code" localizado en frontend
     *   4. Campo "mensaje" / "message" / "error" genérico del backend
     *   5. Fallback genérico por código HTTP
     */
    @NonNull
    public static ApiError fromHttp(@NonNull Context context, @NonNull Response<?> response) {
        int code = response.code();
        String raw = null;
        ResponseBody eb = response.errorBody();
        if (eb != null) {
            try {
                raw = eb.string();
            } catch (Exception ignored) {
            } finally {
                eb.close();
            }
        }

        ApiErrorType type = mapHttpToType(code);
        String msg = context.getString(R.string.api_error_http, code);
        String errorCode = null;
        boolean hasCustomMsg = false;
        Map<String, List<String>> fieldErrors = new HashMap<>();
        String retryAfter = response.headers().get("Retry-After");

        if (type == ApiErrorType.PAYLOAD_TOO_LARGE) {
            msg = context.getString(R.string.api_error_payload_too_large);
            hasCustomMsg = true;
        }

        if (StringUtils.hasText(raw)) {
            try {
                JsonElement root = JsonParser.parseString(raw);
                if (root != null && root.isJsonObject()) {
                    JsonObject obj = root.getAsJsonObject();

                    errorCode = getString(obj, "error_code");
                    String topLevelBackendMsg = firstNonEmpty(
                            getString(obj, "mensaje"),
                            getString(obj, "message"),
                            getString(obj, "error")
                    );

                    String topLevelResolved = resolveDisplayMessage(
                            context,
                            errorCode,
                            topLevelBackendMsg,
                            retryAfter,
                            code
                    );
                    if (StringUtils.hasText(topLevelResolved)) {
                        msg = topLevelResolved;
                        hasCustomMsg = true;
                    }

                    // 1. Errores por campos (formato personalizado del backend).
                    if (obj.has("errores_campos") && obj.get("errores_campos").isJsonObject()) {
                        JsonObject fe = obj.getAsJsonObject("errores_campos");
                        for (String key : fe.keySet()) {
                            JsonElement ve = fe.get(key);
                            if (ve == null) continue;
                            if (ve.isJsonPrimitive()) {
                                String m = cleanBackendMsg(ve.getAsString());
                                if (StringUtils.hasText(m)) addFieldError(fieldErrors, key, m);
                            } else if (ve.isJsonArray()) {
                                JsonArray arr = ve.getAsJsonArray();
                                for (JsonElement it : arr) {
                                    if (it == null) continue;
                                    if (it.isJsonPrimitive()) {
                                        String m = cleanBackendMsg(it.getAsString());
                                        if (StringUtils.hasText(m)) addFieldError(fieldErrors, key, m);
                                    } else if (it.isJsonObject()) {
                                        JsonObject o = it.getAsJsonObject();
                                        String m = resolveDisplayMessage(
                                                context,
                                                getString(o, "error_code"),
                                                firstNonEmpty(
                                                        getString(o, "mensaje"),
                                                        getString(o, "message"),
                                                        getString(o, "error")
                                                ),
                                                retryAfter,
                                                code
                                        );
                                        if (StringUtils.hasText(m)) addFieldError(fieldErrors, key, m);
                                    }
                                }
                            } else if (ve.isJsonObject()) {
                                JsonObject o = ve.getAsJsonObject();
                                String m = resolveDisplayMessage(
                                        context,
                                        getString(o, "error_code"),
                                        firstNonEmpty(
                                                getString(o, "mensaje"),
                                                getString(o, "message"),
                                                getString(o, "error")
                                        ),
                                        retryAfter,
                                        code
                                );
                                if (StringUtils.hasText(m)) addFieldError(fieldErrors, key, m);
                            }
                        }
                        if (!fieldErrors.isEmpty() && (code == 400 || code == 422)) {
                            type = ApiErrorType.VALIDATION;
                        }
                    }

                    // 2. Errores formato FastAPI/Pydantic ("detail").
                    //    El primer mensaje específico de campo SIEMPRE sobreescribe
                    //    el genérico del paso superior.
                    if (obj.has("detail")) {
                        JsonElement detail = obj.get("detail");
                        if (detail != null && detail.isJsonPrimitive()) {
                            String d = resolveDisplayMessage(
                                    context,
                                    errorCode,
                                    detail.getAsString(),
                                    retryAfter,
                                    code
                            );
                            if (StringUtils.hasText(d)) {
                                msg = d;
                                hasCustomMsg = true;
                            }

                        } else if (detail != null && detail.isJsonArray()) {
                            JsonArray arr = detail.getAsJsonArray();
                            if (code == 400 || code == 422) type = ApiErrorType.VALIDATION;

                            for (JsonElement it : arr) {
                                if (it == null || !it.isJsonObject()) continue;
                                JsonObject o = it.getAsJsonObject();

                                String col = getString(o, "columna");
                                String itemErrorCode = getString(o, "error_code");
                                String backendMsg = firstNonEmpty(
                                        getString(o, "mensaje"),
                                        getString(o, "msg"),
                                        getString(o, "message"),
                                        getString(o, "error")
                                );

                                if (!StringUtils.hasText(col) && o.has("loc") && o.get("loc").isJsonArray()) {
                                    col = lastLocAsFieldName(o.getAsJsonArray("loc"));
                                }

                                String m = resolveDisplayMessage(
                                        context,
                                        itemErrorCode,
                                        backendMsg,
                                        retryAfter,
                                        code
                                );

                                if (StringUtils.hasText(col) && StringUtils.hasText(m)) {
                                    addFieldError(fieldErrors, col, m);
                                }
                                // Primer mensaje específico sobreescribe siempre el genérico.
                                if (StringUtils.hasText(m)) {
                                    msg = m;
                                    if (StringUtils.hasText(itemErrorCode)) errorCode = itemErrorCode;
                                    hasCustomMsg = true;
                                    break;
                                }
                            }

                            if (!hasCustomMsg && !arr.isEmpty()) {
                                String validationMsg = resolveDisplayMessage(
                                        context,
                                        errorCode,
                                        null,
                                        retryAfter,
                                        code
                                );
                                msg = StringUtils.hasText(validationMsg)
                                        ? validationMsg
                                        : context.getString(R.string.api_error_validacion_invalida);
                                hasCustomMsg = true;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                type = ApiErrorType.PARSE;
                msg = context.getString(R.string.api_error_respuesta_invalida);
                errorCode = null;
                hasCustomMsg = true;
            }
        }

        if (type == ApiErrorType.RATE_LIMIT && !hasCustomMsg) {
            if (StringUtils.hasText(retryAfter)) {
                msg = context.getString(R.string.api_error_rate_limit_retry, retryAfter);
            } else {
                msg = context.getString(R.string.api_error_rate_limit);
            }
        }

        return new ApiError(type, code, msg, errorCode, fieldErrors, raw);
    }

    /**
     * Maneja excepciones de red o de sistema (Timeouts, No Internet, etc.)
     */
    @NonNull
    public static ApiError fromThrowable(@NonNull Context context, @NonNull Throwable t, boolean canceled) {
        if (canceled) {
            return ApiError.typed(ApiErrorType.CANCELED, context.getString(R.string.api_error_cancelado));
        }

        if (t instanceof com.proyecto.moveon.data.remote.retrofit.TokenAuthenticator.RefreshFailedException) {
            com.proyecto.moveon.data.remote.retrofit.TokenAuthenticator.RefreshFailedException ex =
                    (com.proyecto.moveon.data.remote.retrofit.TokenAuthenticator.RefreshFailedException) t;

            int c = ex.getCode();
            String retryAfter = ex.getRetryAfter();
            String backendErrorCode = ex.getErrorCode();
            String backendMessage = ex.getBackendMessage();
            ApiErrorType type;
            String msg;

            if (c == 429) {
                type = ApiErrorType.RATE_LIMIT;
                msg = resolveDisplayMessage(context, backendErrorCode, backendMessage, retryAfter, c);
                if (!StringUtils.hasText(msg)) {
                    msg = StringUtils.hasText(retryAfter)
                            ? context.getString(R.string.api_error_rate_limit_retry, retryAfter)
                            : context.getString(R.string.api_error_rate_limit);
                }
            } else if (c == 400 || c == 422) {
                type = ApiErrorType.UNKNOWN;
                msg = resolveDisplayMessage(context, backendErrorCode, backendMessage, retryAfter, c);
                if (!StringUtils.hasText(msg)) {
                    msg = context.getString(R.string.api_error_refresh_invalido);
                }
            } else {
                type = c >= 500 ? ApiErrorType.SERVER : ApiErrorType.UNKNOWN;
                msg = resolveDisplayMessage(context, backendErrorCode, backendMessage, retryAfter, c);
                if (!StringUtils.hasText(msg)) {
                    msg = context.getString(R.string.api_error_inesperado);
                }
            }
            return ApiError.typed(type, c, msg, backendErrorCode);
        }

        if (t instanceof SocketTimeoutException) {
            return ApiError.typed(ApiErrorType.TIMEOUT, context.getString(R.string.api_error_timeout));
        }

        if (t instanceof IOException) {
            return ApiError.typed(ApiErrorType.NETWORK, context.getString(R.string.api_error_conexion));
        }

        return ApiError.local(context.getString(R.string.api_error_inesperado));
    }

    /**
     * Elimina el prefijo "Error:" que añade el backend en sus mensajes de validación,
     * para que el texto al usuario sea más natural y limpio.
     */
    @NonNull
    private static String cleanBackendMsg(@NonNull String m) {
        String trimmed = m.trim();
        if (trimmed.startsWith("Error: ")) {
            return trimmed.substring(7).trim();
        }
        return trimmed;
    }

    @Nullable
    private static String resolveDisplayMessage(@NonNull Context context,
                                                @Nullable String errorCode,
                                                @Nullable String backendMessage,
                                                @Nullable String retryAfter,
                                                int httpCode) {
        String localized = BackendErrorLocalizer.localize(context, errorCode, retryAfter);
        if (StringUtils.hasText(localized)) return localized;

        if (StringUtils.hasText(backendMessage)) {
            return cleanBackendMsg(backendMessage);
        }

        if (httpCode == 429) {
            return StringUtils.hasText(retryAfter)
                    ? context.getString(R.string.api_error_rate_limit_retry, retryAfter)
                    : context.getString(R.string.api_error_rate_limit);
        }
        return null;
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

    private static ApiErrorType mapHttpToType(int code) {
        if (code == 401) return ApiErrorType.UNAUTHORIZED;
        if (code == 403) return ApiErrorType.FORBIDDEN;
        if (code == 404) return ApiErrorType.NOT_FOUND;
        if (code == 408) return ApiErrorType.TIMEOUT;
        if (code == 409) return ApiErrorType.CONFLICT;
        if (code == 413) return ApiErrorType.PAYLOAD_TOO_LARGE;
        if (code == 429) return ApiErrorType.RATE_LIMIT;
        if (code == 400 || code == 422) return ApiErrorType.VALIDATION;
        if (code >= 500) return ApiErrorType.SERVER;
        return ApiErrorType.UNKNOWN;
    }

    private static void addFieldError(Map<String, List<String>> map, String field, String msg) {
        if (!StringUtils.hasText(field) || !StringUtils.hasText(msg)) return;
        List<String> list = map.get(field);
        if (list == null) list = new ArrayList<>();
        list.add(msg);
        map.put(field, list);
    }

    private static String lastLocAsFieldName(JsonArray loc) {
        if (loc == null || loc.isEmpty()) return null;
        JsonElement last = loc.get(loc.size() - 1);
        if (last != null && last.isJsonPrimitive()) {
            String s = last.getAsString();
            return StringUtils.hasText(s) ? s : null;
        }
        return null;
    }
}
