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

    private PaceDisplayUtils() {
    }

    public static boolean shouldUseMovingPace(@NonNull Context context) {
        return AppSettingsManager.isPaceDisplayMoving(context);
    }

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

    private static boolean hasText(@Nullable String value) {
        return value != null && !value.trim().isEmpty();
    }
}
