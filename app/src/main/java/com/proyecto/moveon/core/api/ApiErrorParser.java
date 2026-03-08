package com.proyecto.moveon.core.api;

import android.content.Context;
import androidx.annotation.NonNull;

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
     *   1. Primer mensaje específico del array "detail" (FastAPI/Pydantic) — MAYOR prioridad
     *   2. Mensajes de "errores_campos"
     *   3. Campo "mensaje" / "message" / "error" genérico del backend
     *   4. Fallback genérico por código HTTP
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
        boolean hasCustomMsg = false;
        Map<String, List<String>> fieldErrors = new HashMap<>();

        if (type == ApiErrorType.PAYLOAD_TOO_LARGE) {
            msg = context.getString(R.string.api_error_payload_too_large);
            hasCustomMsg = true;
        }

        if (StringUtils.hasText(raw)) {
            try {
                JsonElement root = JsonParser.parseString(raw);
                if (root != null && root.isJsonObject()) {
                    JsonObject obj = root.getAsJsonObject();

                    // 1. Mensaje genérico del backend — fallback, puede ser sobreescrito por detail.
                    if (obj.has("mensaje") && obj.get("mensaje").isJsonPrimitive()) {
                        String m = cleanBackendMsg(obj.get("mensaje").getAsString());
                        if (StringUtils.hasText(m)) { msg = m; hasCustomMsg = true; }
                    }
                    if (obj.has("message") && obj.get("message").isJsonPrimitive()) {
                        String m = cleanBackendMsg(obj.get("message").getAsString());
                        if (StringUtils.hasText(m) && !hasCustomMsg) { msg = m; hasCustomMsg = true; }
                    }
                    if (obj.has("error") && obj.get("error").isJsonPrimitive()) {
                        String m = cleanBackendMsg(obj.get("error").getAsString());
                        if (StringUtils.hasText(m) && !hasCustomMsg) { msg = m; hasCustomMsg = true; }
                    }

                    // 2. Errores por campos (formato personalizado del backend).
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
                                    if (it != null && it.isJsonPrimitive()) {
                                        String m = cleanBackendMsg(it.getAsString());
                                        if (StringUtils.hasText(m)) addFieldError(fieldErrors, key, m);
                                    }
                                }
                            }
                        }
                        if (!fieldErrors.isEmpty() && (code == 400 || code == 422)) {
                            type = ApiErrorType.VALIDATION;
                        }
                    }

                    // 3. Errores formato FastAPI/Pydantic ("detail").
                    //    El primer mensaje específico de campo SIEMPRE sobreescribe
                    //    el "mensaje" genérico ("Solicitud inválida", etc.) del paso 1.
                    if (obj.has("detail")) {
                        JsonElement detail = obj.get("detail");
                        if (detail != null && detail.isJsonPrimitive()) {
                            String d = cleanBackendMsg(detail.getAsString());
                            if (StringUtils.hasText(d)) { msg = d; hasCustomMsg = true; }

                        } else if (detail != null && detail.isJsonArray()) {
                            JsonArray arr = detail.getAsJsonArray();
                            if (code == 400 || code == 422) type = ApiErrorType.VALIDATION;

                            for (JsonElement it : arr) {
                                if (it == null || !it.isJsonObject()) continue;
                                JsonObject o = it.getAsJsonObject();

                                String col = o.has("columna") && o.get("columna").isJsonPrimitive()
                                        ? o.get("columna").getAsString() : null;
                                String m = o.has("mensaje") && o.get("mensaje").isJsonPrimitive()
                                        ? cleanBackendMsg(o.get("mensaje").getAsString()) : null;

                                if (!StringUtils.hasText(m) && o.has("msg") && o.get("msg").isJsonPrimitive()) {
                                    m = cleanBackendMsg(o.get("msg").getAsString());
                                }
                                if (!StringUtils.hasText(col) && o.has("loc") && o.get("loc").isJsonArray()) {
                                    col = lastLocAsFieldName(o.getAsJsonArray("loc"));
                                }
                                if (StringUtils.hasText(col) && StringUtils.hasText(m)) {
                                    addFieldError(fieldErrors, col, m);
                                }
                                // Primer mensaje específico sobreescribe siempre el genérico
                                if (StringUtils.hasText(m)) {
                                    msg = m;
                                    hasCustomMsg = true;
                                    break;
                                }
                            }

                            if (!hasCustomMsg && !arr.isEmpty()) {
                                msg = context.getString(R.string.api_error_validacion_invalida);
                                hasCustomMsg = true;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                type = ApiErrorType.PARSE;
                msg = context.getString(R.string.api_error_respuesta_invalida);
                hasCustomMsg = true;
            }
        }

        if (type == ApiErrorType.RATE_LIMIT && !hasCustomMsg) {
            String retryAfter = response.headers().get("Retry-After");
            if (StringUtils.hasText(retryAfter)) {
                msg = context.getString(R.string.api_error_rate_limit_retry, retryAfter);
            } else {
                msg = context.getString(R.string.api_error_rate_limit);
            }
        }

        return new ApiError(type, code, msg, fieldErrors, raw);
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
            ApiErrorType type;
            String msg;

            if (c == 429) {
                type = ApiErrorType.RATE_LIMIT;
                msg = StringUtils.hasText(retryAfter)
                        ? context.getString(R.string.api_error_rate_limit_retry, retryAfter)
                        : context.getString(R.string.api_error_rate_limit);
            } else if (c == 400 || c == 422) {
                type = ApiErrorType.UNKNOWN;
                msg = context.getString(R.string.api_error_refresh_invalido);
            } else {
                type = ApiErrorType.SERVER;
                msg = context.getString(R.string.api_error_inesperado);
            }
            return ApiError.typed(type, c, msg);
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
     * para que el toast al usuario sea más natural y limpio.
     * Ej.: "Error: El nombre no puede contener números" → "El nombre no puede contener números"
     */
    @NonNull
    private static String cleanBackendMsg(@NonNull String m) {
        String trimmed = m.trim();
        if (trimmed.startsWith("Error: ")) {
            return trimmed.substring(7).trim();
        }
        return trimmed;
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