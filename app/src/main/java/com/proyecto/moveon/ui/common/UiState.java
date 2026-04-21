package com.proyecto.moveon.ui.common;

import androidx.annotation.Nullable;

import com.proyecto.moveon.core.api.ApiError;
/**
 * Estado que modela la información necesaria para ui.
 */
public class UiState<T> {
    public final boolean loading;
    @Nullable public final T data;
    @Nullable public final ApiError error;

    /**
     * Constructor privado usado por las factorías {@link #loading()},
     * {@link #success(Object)} y {@link #error(ApiError)}. Fuerza a los
     * consumidores a usar los tres estados canónicos en lugar de construir
     * combinaciones inválidas (p. ej. loading con data y error a la vez).
     *
     * @param loading {@code true} si el ViewModel está esperando resultado.
     * @param data payload de éxito, o {@code null} si aún no hay datos o hay error.
     * @param error error ocurrido, o {@code null} si todo fue bien o aún no terminó.
     */
    private UiState(boolean loading, @Nullable T data, @Nullable ApiError error) {
        this.loading = loading;
        this.data = data;
        this.error = error;
    }

    /**
     * Estado "cargando" sin datos ni error, emitido por el ViewModel al
     * iniciar una operación para que la UI muestre el spinner.
     *
     * @param <T> tipo del payload que se entregará en el éxito final.
     * @return instancia reutilizable con {@code loading=true}.
     */
    public static <T> UiState<T> loading() { return new UiState<>(true, null, null); }
    /**
     * Estado de éxito con el payload recibido. La UI debe ocultar el
     * spinner, limpiar errores previos y pintar {@code data}.
     *
     * @param <T> tipo del payload.
     * @param data datos recibidos del repositorio/backend.
     * @return estado final de éxito con los datos.
     */
    public static <T> UiState<T> success(T data) { return new UiState<>(false, data, null); }
    /**
     * Estado de error con el {@link ApiError} producido. La UI oculta el
     * spinner y muestra el mensaje localizado correspondiente.
     *
     * @param <T> tipo del payload que no pudo recibirse.
     * @param err error ocurrido durante la operación.
     * @return estado final de error con el motivo.
     */
    public static <T> UiState<T> error(ApiError err) { return new UiState<>(false, null, err); }
}