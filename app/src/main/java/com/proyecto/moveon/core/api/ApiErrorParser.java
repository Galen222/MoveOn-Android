
package com.proyecto.moveon.core.api;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.proyecto.moveon.R;
import com.proyecto.moveon.core.i18n.AppLanguageManager;
import com.proyecto.moveon.utils.StringUtils;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.ResponseBody;
import retrofit2.Response;

/**
 * Parser para el contrato de errores actual del backend:
 *
 * Prioridad para el mensaje visible:
 *   1. Primer error específico de "detail" (por su error_code)
 *   2. error_code top-level
 *   3. mensaje backend (solo como fallback de compatibilidad o diagnóstico)
 *   4. fallback HTTP genérico
 *
 * "detail" se sigue procesando porque aporta errores por campo y el primer error
 * específico útil para UX en formularios.
 */
public final class ApiErrorParser {

    private static final String TAG = "ApiErrorParser";

    /**
     * Evita instancias de un parser estático de errores HTTP y de red.
     */
    private ApiErrorParser() {}

    /**
     * Lee el {@code errorBody} con catches tipados y deja traza con {@code Log.w}
     * cuando falla la lectura.
     * El {@code IOException} cubre el caso habitual (conexión cortada, body ya
     * consumido); el {@code RuntimeException} cubre edge cases de OkHttp.
     */
    @NonNull
    public static ApiError fromHttp(@NonNull Context context, @NonNull Response<?> response) {
        context = AppLanguageManager.localizedContext(context);
        int code = response.code();
        String raw = null;
        ResponseBody eb = response.errorBody();
        if (eb != null) {
            try {
                raw = eb.string();
            } catch (IOException e) {
                Log.w(TAG, "No se pudo leer errorBody()", e);
            } catch (RuntimeException e) {
                Log.w(TAG, "Fallo inesperado leyendo errorBody()", e);
            } finally {
                eb.close();
            }
        }

        ApiErrorType type = mapHttpToType(code);
        String retryAfter = response.headers().get("Retry-After");

        String visibleMessage = defaultHttpMessage(context, type, code, retryAfter);
        String visibleErrorCode = null;
        Map<String, List<String>> fieldErrors = new HashMap<>();

        if (StringUtils.hasText(raw)) {
            try {
                JsonElement root = JsonParser.parseString(raw);
                if (root != null && root.isJsonObject()) {
                    JsonObject obj = root.getAsJsonObject();

                    String topLevelErrorCode = getString(obj, "error_code");
                    String topLevelBackendMessage = firstNonEmpty(
                            getString(obj, "mensaje"),
                            getString(obj, "message"),
                            getString(obj, "error")
                    );

                    DetailParseResult detailResult = parseDetail(
                            context,
                            obj.get("detail"),
                            retryAfter,
                            code
                    );
                    mergeFieldErrors(fieldErrors, detailResult.fieldErrors);

                    // errores_campos sigue siendo útil para pintar errores por campo,
                    // pero ya no manda sobre el mensaje general visible.
                    parseErroresCampos(
                            context,
                            obj.get("errores_campos"),
                            fieldErrors,
                            retryAfter,
                            code
                    );

                    if (StringUtils.hasText(detailResult.firstDisplayMessage)) {
                        visibleMessage = detailResult.firstDisplayMessage;
                        visibleErrorCode = detailResult.firstErrorCode;
                    } else {
                        String topLevelResolved = resolveDisplayMessage(
                                context,
                                topLevelErrorCode,
                                topLevelBackendMessage,
                                retryAfter,
                                code
                        );
                        if (StringUtils.hasText(topLevelResolved)) {
                            visibleMessage = topLevelResolved;
                            visibleErrorCode = topLevelErrorCode;
                        }
                    }
                }
            } catch (Exception e) {
                type = ApiErrorType.PARSE;
                visibleMessage = context.getString(R.string.api_error_respuesta_invalida);
                visibleErrorCode = null;
                fieldErrors.clear();
            }
        }

        return new ApiError(type, code, visibleMessage, visibleErrorCode, fieldErrors, raw);
    }

    /**
     * Traduce excepciones de red, refresh o cancelación a un {@link ApiError} consumible por la UI.
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
            if (c == 429) {
                type = ApiErrorType.RATE_LIMIT;
            } else if (c == 400 || c == 422) {
                type = ApiErrorType.UNKNOWN;
            } else {
                type = c >= 500 ? ApiErrorType.SERVER : ApiErrorType.UNKNOWN;
            }

            String msg = resolveDisplayMessage(context, backendErrorCode, backendMessage, retryAfter, c);
            if (!StringUtils.hasText(msg)) {
                msg = defaultHttpMessage(context, type, c, retryAfter);
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
     * Procesa el nodo {@code detail} del backend y extrae el primer mensaje visible junto con errores por campo.
     */
    @NonNull
    private static DetailParseResult parseDetail(@NonNull Context context,
                                                 @Nullable JsonElement detail,
                                                 @Nullable String retryAfter,
                                                 int httpCode) {
        DetailParseResult result = new DetailParseResult();
        if (detail == null) {
            return result;
        }

        if (detail.isJsonPrimitive()) {
            String primitiveMessage = resolveDisplayMessage(
                    context,
                    null,
                    detail.getAsString(),
                    retryAfter,
                    httpCode
            );
            result.firstDisplayMessage = primitiveMessage;
            return result;
        }

        if (!detail.isJsonArray()) {
            return result;
        }

        JsonArray arr = detail.getAsJsonArray();
        for (JsonElement it : arr) {
            if (it == null || !it.isJsonObject()) continue;
            JsonObject o = it.getAsJsonObject();

            String field = getString(o, "columna");
            if (!StringUtils.hasText(field) && o.has("loc") && o.get("loc").isJsonArray()) {
                field = lastLocAsFieldName(o.getAsJsonArray("loc"));
            }

            String itemErrorCode = getString(o, "error_code");
            String backendMessage = firstNonEmpty(
                    getString(o, "mensaje"),
                    getString(o, "msg"),
                    getString(o, "message"),
                    getString(o, "error")
            );

            String resolvedMessage = resolveDisplayMessage(
                    context,
                    itemErrorCode,
                    backendMessage,
                    retryAfter,
                    httpCode
            );

            if (StringUtils.hasText(field) && StringUtils.hasText(resolvedMessage)) {
                addFieldError(result.fieldErrors, field, resolvedMessage);
            }

            if (!StringUtils.hasText(result.firstDisplayMessage) && StringUtils.hasText(resolvedMessage)) {
                result.firstDisplayMessage = resolvedMessage;
                result.firstErrorCode = itemErrorCode;
            }
        }

        return result;
    }

    /**
     * Añade al mapa de errores los mensajes incluidos en el objeto legacy {@code errores_campos}.
     */
    private static void parseErroresCampos(@NonNull Context context,
                                           @Nullable JsonElement erroresCampos,
                                           @NonNull Map<String, List<String>> fieldErrors,
                                           @Nullable String retryAfter,
                                           int httpCode) {
        if (erroresCampos == null || !erroresCampos.isJsonObject()) {
            return;
        }

        JsonObject fe = erroresCampos.getAsJsonObject();
        for (String key : fe.keySet()) {
            JsonElement ve = fe.get(key);
            if (ve == null) continue;

            if (ve.isJsonPrimitive()) {
                String msg = resolveDisplayMessage(context, null, ve.getAsString(), retryAfter, httpCode);
                if (StringUtils.hasText(msg)) addFieldError(fieldErrors, key, msg);
                continue;
            }

            if (ve.isJsonArray()) {
                JsonArray arr = ve.getAsJsonArray();
                for (JsonElement item : arr) {
                    if (item == null) continue;
                    if (item.isJsonPrimitive()) {
                        String msg = resolveDisplayMessage(context, null, item.getAsString(), retryAfter, httpCode);
                        if (StringUtils.hasText(msg)) addFieldError(fieldErrors, key, msg);
                    } else if (item.isJsonObject()) {
                        JsonObject obj = item.getAsJsonObject();
                        String msg = resolveDisplayMessage(
                                context,
                                getString(obj, "error_code"),
                                firstNonEmpty(
                                        getString(obj, "mensaje"),
                                        getString(obj, "message"),
                                        getString(obj, "error")
                                ),
                                retryAfter,
                                httpCode
                        );
                        if (StringUtils.hasText(msg)) addFieldError(fieldErrors, key, msg);
                    }
                }
                continue;
            }

            if (ve.isJsonObject()) {
                JsonObject obj = ve.getAsJsonObject();
                String msg = resolveDisplayMessage(
                        context,
                        getString(obj, "error_code"),
                        firstNonEmpty(
                                getString(obj, "mensaje"),
                                getString(obj, "message"),
                                getString(obj, "error")
                        ),
                        retryAfter,
                        httpCode
                );
                if (StringUtils.hasText(msg)) addFieldError(fieldErrors, key, msg);
            }
        }
    }

    /**
     * Devuelve el mensaje genérico por HTTP cuando el backend no ofrece uno mejor para mostrar.
     */
    @NonNull
    private static String defaultHttpMessage(@NonNull Context context,
                                             @NonNull ApiErrorType type,
                                             int httpCode,
                                             @Nullable String retryAfter) {
        if (type == ApiErrorType.PAYLOAD_TOO_LARGE) {
            return context.getString(R.string.api_error_payload_too_large);
        }
        String localized = localizedHttpFallback(context, httpCode, retryAfter);
        if (StringUtils.hasText(localized)) {
            return localized;
        }
        return context.getString(R.string.api_error_http, httpCode);
    }

    /**
     * Resuelve el mensaje final combinando localización por código, mensaje backend útil y fallback HTTP.
     */
    @Nullable
    private static String resolveDisplayMessage(@NonNull Context context,
                                                @Nullable String errorCode,
                                                @Nullable String backendMessage,
                                                @Nullable String retryAfter,
                                                int httpCode) {
        String localized = BackendErrorLocalizer.localize(context, errorCode, retryAfter);
        if (StringUtils.hasText(localized)) return localized;

        String cleanedBackend = StringUtils.hasText(backendMessage)
                ? cleanBackendMsg(backendMessage)
                : null;

        if (StringUtils.hasText(cleanedBackend) && !isGenericFrameworkMessage(cleanedBackend)) {
            return cleanedBackend;
        }

        return localizedHttpFallback(context, httpCode, retryAfter);
    }

    /**
     * Mapea ciertos códigos HTTP a cadenas localizadas específicas cuando el backend no aporta detalle usable.
     */
    @Nullable
    private static String localizedHttpFallback(@NonNull Context context,
                                                int httpCode,
                                                @Nullable String retryAfter) {
        switch (httpCode) {
            case 401:
                return context.getString(R.string.api_error_unauthorized);
            case 403:
                return context.getString(R.string.api_error_forbidden);
            case 404:
                return context.getString(R.string.api_error_not_found);
            case 409:
                return context.getString(R.string.api_error_conflict);
            case 413:
                return context.getString(R.string.api_error_payload_too_large);
            case 429:
                return StringUtils.hasText(retryAfter)
                        ? context.getString(R.string.api_error_rate_limit_retry, retryAfter)
                        : context.getString(R.string.api_error_rate_limit);
            default:
                if (httpCode >= 500) {
                    return context.getString(R.string.api_error_server);
                }
                return null;
        }
    }

    /**
     * Limpia prefijos ruidosos del backend para dejar un texto más apto para mostrar al usuario.
     */
    @NonNull
    private static String cleanBackendMsg(@NonNull String m) {
        String trimmed = m.trim();
        if (trimmed.startsWith("Error: ")) {
            return trimmed.substring(7).trim();
        }
        return trimmed;
    }

    /**
     * Detecta mensajes genéricos del framework que no aportan contexto suficiente para mostrarlos tal cual.
     */
    private static boolean isGenericFrameworkMessage(@Nullable String message) {
        if (!StringUtils.hasText(message)) return true;

        String normalized = message.trim().toLowerCase(java.util.Locale.US);
        return normalized.equals("not found")
                || normalized.equals("not found.")
                || normalized.equals("not_found")
                || normalized.equals("forbidden")
                || normalized.equals("forbidden.")
                || normalized.equals("unauthorized")
                || normalized.equals("unauthorized.")
                || normalized.equals("internal server error")
                || normalized.equals("bad request")
                || normalized.equals("unprocessable entity")
                || normalized.equals("method not allowed")
                || normalized.equals("solicitud inválida")
                || normalized.equals("error en la solicitud");
    }

    /**
     * Lee una propiedad string opcional del JSON y devuelve {@code null} cuando está ausente o vacía.
     */
    @Nullable
    private static String getString(@NonNull JsonObject obj, @NonNull String key) {
        if (!obj.has(key) || obj.get(key) == null || !obj.get(key).isJsonPrimitive()) return null;
        String value = obj.get(key).getAsString();
        return StringUtils.hasText(value) ? value : null;
    }

    /**
     * Devuelve la primera cadena no vacía de la lista recibida.
     */
    @Nullable
    private static String firstNonEmpty(@Nullable String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (StringUtils.hasText(value)) return value;
        }
        return null;
    }

    /**
     * Añade un mensaje de error al campo indicado creando su lista acumulada si todavía no existe.
     */
    private static void addFieldError(@NonNull Map<String, List<String>> map,
                                      @NonNull String field,
                                      @NonNull String msg) {
        if (!StringUtils.hasText(field) || !StringUtils.hasText(msg)) return;
        List<String> list = map.get(field);
        if (list == null) list = new ArrayList<>();
        list.add(msg);
        map.put(field, list);
    }

    /**
     * Fusiona dos mapas de errores preservando todos los mensajes válidos del origen.
     */
    private static void mergeFieldErrors(@NonNull Map<String, List<String>> target,
                                         @NonNull Map<String, List<String>> source) {
        for (Map.Entry<String, List<String>> entry : source.entrySet()) {
            List<String> list = entry.getValue();
            if (list == null) continue;
            for (String msg : list) {
                if (StringUtils.hasText(msg)) {
                    addFieldError(target, entry.getKey(), msg);
                }
            }
        }
    }

    /**
     * Extrae el último elemento de {@code loc} como nombre de campo cuando el backend usa la convención de FastAPI/Pydantic.
     */
    @Nullable
    private static String lastLocAsFieldName(@Nullable JsonArray loc) {
        if (loc == null || loc.isEmpty()) return null;
        JsonElement last = loc.get(loc.size() - 1);
        if (last != null && last.isJsonPrimitive()) {
            String s = last.getAsString();
            return StringUtils.hasText(s) ? s : null;
        }
        return null;
    }

    /**
     * Clasifica un código HTTP en una categoría de error de dominio para simplificar la reacción de la app.
     */
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

    private static final class DetailParseResult {
        @Nullable String firstDisplayMessage;
        @Nullable String firstErrorCode;
        @NonNull final Map<String, List<String>> fieldErrors = new HashMap<>();
    }
}

