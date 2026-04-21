
package com.proyecto.moveon.core.api;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Clase responsable de api error.
 */
public final class ApiError {

    @NonNull private final ApiErrorType type;
    private final int httpCode;
    @NonNull private final String message;
    @Nullable private final String errorCode;

    // fieldErrors: {"email": ["Email inválido"], "password": ["..."] }
    @NonNull private final Map<String, List<String>> fieldErrors;

    @Nullable private final String raw;

    /**
     * Crea una instancia inmutable con la información ya normalizada del error.
     *
     * @param type tipo categórico del error para la capa cliente.
     * @param httpCode código HTTP asociado cuando el error proviene de red.
     * @param message mensaje principal listo para mostrar o propagar.
     * @param errorCode código de negocio devuelto por backend, si existe.
     * @param fieldErrors mapa de errores por campo en el formato usado por validaciones de formulario.
     * @param raw carga útil original del error cuando conviene conservarla para diagnóstico.
     */
    public ApiError(@NonNull ApiErrorType type,
                    int httpCode,
                    @NonNull String message,
                    @Nullable String errorCode,
                    @Nullable Map<String, List<String>> fieldErrors,
                    @Nullable String raw) {
        this.type = type;
        this.httpCode = httpCode;
        this.message = message;
        this.errorCode = errorCode;
        this.fieldErrors = fieldErrors != null ? fieldErrors : Collections.emptyMap();
        this.raw = raw;
    }

    /**
     * Devuelve la categoría del error.
     *
     * @return valor de {@link ApiErrorType} asociado a esta instancia.
     */
    @NonNull public ApiErrorType getType() { return type; }
    /**
     * Devuelve el código HTTP asociado al fallo.
     *
     * @return código HTTP o {@code 0} cuando el error no viene de una respuesta remota.
     */
    public int getHttpCode() { return httpCode; }
    /**
     * Devuelve el mensaje principal del error.
     *
     * @return texto principal listo para mostrar o registrar.
     */
    @NonNull public String getMessage() { return message; }
    /**
     * Devuelve el código de negocio del backend si existe.
     *
     * @return código de error remoto o {@code null}.
     */
    @Nullable public String getErrorCode() { return errorCode; }
    /**
     * Devuelve el detalle de errores por campo.
     *
     * @return mapa inmutable de claves de formulario a mensajes asociados.
     */
    @NonNull public Map<String, List<String>> getFieldErrors() { return fieldErrors; }
    /**
     * Devuelve la carga útil original del error.
     *
     * @return contenido raw del backend o {@code null} si no se conservó.
     */
    @Nullable public String getRaw() { return raw; }

    /**
     * Indica si el error contiene detalle por campos.
     *
     * @return {@code true} cuando {@link #getFieldErrors()} no está vacío.
     */
    public boolean hasFieldErrors() { return !fieldErrors.isEmpty(); }

    /**
     * Busca el primer mensaje no vacío disponible entre varias claves de campo.
     *
     * @param keys claves a consultar en orden de prioridad.
     * @return primer mensaje encontrado o {@code null} si ninguna clave tiene errores asociados.
     */
    @Nullable
    public String firstFieldMessage(@NonNull String... keys) {
        if (keys.length == 0) return null;
        for (String k : keys) {
            if (k == null) continue;
            List<String> msgs = fieldErrors.get(k);
            if (msgs != null && !msgs.isEmpty()) {
                String v = msgs.get(0);
                if (v != null && v.trim().length() > 0) return v;
            }
        }
        return null;
    }

    /**
     * Crea un error local sin metadatos HTTP ni payload remoto.
     *
     * @param message mensaje principal del error.
     * @return instancia marcada como {@link ApiErrorType#UNKNOWN}.
     */
    @NonNull
    public static ApiError local(@NonNull String message) {
        return new ApiError(ApiErrorType.UNKNOWN, 0, message, null, null, null);
    }

    /**
     * Crea un error tipado sin código HTTP.
     *
     * @param type clasificación del error.
     * @param message mensaje principal asociado.
     * @return nueva instancia de {@link ApiError}.
     */
    @NonNull
    public static ApiError typed(@NonNull ApiErrorType type, @NonNull String message) {
        return new ApiError(type, 0, message, null, null, null);
    }

    /**
     * Crea un error tipado incluyendo el código HTTP de la respuesta.
     *
     * @param type clasificación del error.
     * @param httpCode código HTTP recibido.
     * @param message mensaje principal asociado.
     * @return nueva instancia de {@link ApiError}.
     */
    @NonNull
    public static ApiError typed(@NonNull ApiErrorType type, int httpCode, @NonNull String message) {
        return new ApiError(type, httpCode, message, null, null, null);
    }

    /**
     * Crea un error tipado incluyendo código HTTP y código funcional del backend.
     *
     * @param type clasificación del error.
     * @param httpCode código HTTP recibido.
     * @param message mensaje principal asociado.
     * @param errorCode código de negocio devuelto por backend.
     * @return nueva instancia de {@link ApiError}.
     */
    @NonNull
    public static ApiError typed(@NonNull ApiErrorType type,
                                 int httpCode,
                                 @NonNull String message,
                                 @Nullable String errorCode) {
        return new ApiError(type, httpCode, message, errorCode, null, null);
    }

    /**
     * Devuelve una copia del error añadiendo un mensaje a un campo concreto.
     *
     * @param key nombre lógico del campo.
     * @param value mensaje a asociar al campo.
     * @return nueva instancia que conserva el resto de propiedades y acumula el nuevo mensaje.
     */
    @NonNull
    public ApiError withFieldError(@NonNull String key, @NonNull String value) {
        Map<String, List<String>> m = new HashMap<>(this.fieldErrors);
        List<String> list = m.get(key);
        if (list == null) list = new ArrayList<>();
        list.add(value);
        m.put(key, list);
        return new ApiError(this.type, this.httpCode, this.message, this.errorCode, m, this.raw);
    }
}
