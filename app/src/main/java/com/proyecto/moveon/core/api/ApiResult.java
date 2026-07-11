package com.proyecto.moveon.core.api;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Clase responsable de api result.
 */
public final class ApiResult<T> {

    @Nullable public final T data;
    @Nullable public final ApiError error;

    /**
     * Constructor privado invocado sólo por las factorías {@link #success},
     * {@link #successVoid} y {@link #failure}. Limita las combinaciones
     * posibles para que un resultado con {@code data} y {@code error} no
     * nulos sea imposible de construir.
     *
     * @param data payload de éxito o {@code null} si es fallo.
     * @param error error ocurrido o {@code null} si es éxito.
     */
    private ApiResult(@Nullable T data, @Nullable ApiError error) {
        this.data = data;
        this.error = error;
    }

    /**
     * Atajo para comprobar si el resultado es de éxito: lo es siempre que
     * {@code error} sea {@code null}. Así los consumidores no tienen que
     * inspeccionar campos a mano.
     *
     * @return {@code true} si no hay error asociado; {@code false} en caso contrario.
     */
    public boolean isSuccess() { return error == null; }

    /**
     * Construye un resultado de éxito con el payload recibido.
     *
     * @param <T> tipo del dato devuelto.
     * @param data payload no nulo devuelto al llamador.
     * @return resultado de éxito envolviendo {@code data}.
     */
    @NonNull
    public static <T> ApiResult<T> success(@NonNull T data) {
        return new ApiResult<>(data, null);
    }

    /**
     * Variante para operaciones que completan sin payload (p. ej. DELETE
     * o PATCH fire-and-forget). Evita introducir {@code Void} a mano y
     * mantiene consistentes los callbacks.
     *
     * @return resultado de éxito sin dato.
     */
    @NonNull
    public static ApiResult<Void> successVoid() {
        return new ApiResult<>(null, null);
    }

    /**
     * Construye un resultado de fallo con el {@link ApiError} producido.
     *
     * @param <T> tipo del dato que se esperaba devolver.
     * @param error error que impidió completar la operación.
     * @return resultado de fallo sin {@code data}.
     */
    @NonNull
    public static <T> ApiResult<T> failure(@NonNull ApiError error) {
        return new ApiResult<>(null, error);
    }
}