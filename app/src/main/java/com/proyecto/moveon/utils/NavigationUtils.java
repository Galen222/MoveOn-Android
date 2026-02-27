package com.proyecto.moveon.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;

/**
 * Utilidades para centralizar la navegación y evitar duplicación de Intent/Flags.
 */
public final class NavigationUtils {

    private NavigationUtils() {
        // Utility class
    }

    /**
     * Navega a una actividad sin modificar el back stack.
     */
    public static void goToActivity(@NonNull Context context, @NonNull Class<?> target) {
        context.startActivity(new Intent(context, target));
    }

    /**
     * Navega a una actividad y finaliza la activity actual.
     */
    public static void goToActivityAndFinish(@NonNull Activity activity, @NonNull Class<?> target) {
        goToActivity(activity, target);
        activity.finish();
    }

    /**
     * Navega a una actividad limpiando todo el stack de pantallas (útil para Login/Logout).
     */
    public static void goToActivityAndClearTask(@NonNull Context context, @NonNull Class<?> target) {
        Intent i = new Intent(context, target);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(i);
        if (context instanceof Activity) {
            ((Activity) context).finish();
        }
    }
}