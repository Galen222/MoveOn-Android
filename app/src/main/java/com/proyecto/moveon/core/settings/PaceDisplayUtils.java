package com.proyecto.moveon.core.settings;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.proyecto.moveon.domain.activity.ActividadItem;
import com.proyecto.moveon.ui.home.tracking.TrackingState;

/**
 * Helper centralizado para resolver qué ritmo medio debe mostrarse en la app
 * según la preferencia guardada por el usuario.
 */
public final class PaceDisplayUtils {

    /**
     * Constructor privado: clase de utilidades con sólo métodos estáticos,
     * no está pensada para instanciarse.
     */
    private PaceDisplayUtils() {
    }

    /**
     * Consulta la preferencia del usuario para decidir si la UI debe usar
     * el ritmo en movimiento (excluye paradas) o el ritmo total.
     *
     * @param context contexto desde el que se lee la preferencia persistida.
     * @return {@code true} si el usuario prefiere el ritmo en movimiento.
     */
    public static boolean shouldUseMovingPace(@NonNull Context context) {
        return AppSettingsManager.isPaceDisplayMoving(context);
    }

    /**
     * Devuelve el ritmo medio preferido por el usuario para una actividad
     * ya cerrada. Cae al ritmo total si el usuario prefiere el ritmo de
     * movimiento pero la actividad no tiene datos de movimiento (p. ej.
     * actividades antiguas o con GPS desactivado).
     *
     * @param context contexto para leer la preferencia.
     * @param item actividad de la que se quiere el ritmo medio a mostrar.
     * @return ritmo medio en segundos por kilómetro según la preferencia.
     */
    public static int getPreferredAveragePaceSeconds(
            @NonNull Context context,
            @NonNull ActividadItem item
    ) {
        if (shouldUseMovingPace(context) && item.ritmoMedioMovimientoSegKm > 0) {
            return item.ritmoMedioMovimientoSegKm;
        }
        return item.ritmoMedioTotalSegKm;
    }

    @Nullable
    /**
     * Versión para pantallas en vivo (tracking activo): devuelve el texto
     * formateado del ritmo preferido, haciendo fallback al ritmo total si
     * el de movimiento aún no está disponible al principio de la sesión.
     *
     * @param context contexto para leer la preferencia.
     * @param state estado de tracking del que se leen los ritmos ya formateados.
     * @return texto listo para mostrar, o {@code null} si no hay ningún ritmo disponible todavía.
     */
    public static String getPreferredAveragePaceText(
            @NonNull Context context,
            @NonNull TrackingState state
    ) {
        if (shouldUseMovingPace(context) && hasText(state.getAverageMovingPace())) {
            return state.getAverageMovingPace();
        }
        if (hasText(state.getAverageElapsedPace())) {
            return state.getAverageElapsedPace();
        }
        return state.getAverageMovingPace();
    }

    /**
     * Helper interno: {@code true} si la cadena no es nula y contiene
     * algún carácter no blanco. Se usa para distinguir ritmos sin calcular
     * ({@code null} o vacío) de ritmos reales.
     *
     * @param value cadena a comprobar.
     * @return {@code true} si el valor tiene contenido útil para mostrar.
     */
    private static boolean hasText(@Nullable String value) {
        return value != null && !value.trim().isEmpty();
    }
}
