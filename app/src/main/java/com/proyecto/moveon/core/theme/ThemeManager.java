package com.proyecto.moveon.core.theme;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;

import com.proyecto.moveon.core.settings.AppSettingsManager;

/**
 * Responsable SOLO de la lógica del tema.
 * - Lee/escribe el modo a través de AppSettingsManager
 * - Aplica el modo visual con AppCompatDelegate
 */
public final class ThemeManager {

    /**
     * Evita instancias de una clase utilitaria basada en métodos estáticos.
     */
    private ThemeManager() {}

    public static final String MODE_SYSTEM = "system";
    public static final String MODE_LIGHT  = "light";
    public static final String MODE_DARK   = "dark";

    /**
     * Aplica en {@link AppCompatDelegate} el modo de tema persistido actualmente.
     *
     * @param context contexto usado para leer la preferencia guardada en {@link AppSettingsManager}.
     */
    public static void applySavedTheme(@NonNull Context context) {
        applyMode(AppSettingsManager.getThemeMode(context));
    }

    /**
     * Normaliza el modo solicitado, lo guarda y lo aplica inmediatamente a toda la app.
     *
     * @param context contexto usado para persistir la preferencia de tema.
     * @param mode modo solicitado por la UI.
     */
    public static void saveAndApply(@NonNull Context context, @NonNull String mode) {
        String safeMode = sanitizeMode(mode);
        AppSettingsManager.setThemeMode(context, safeMode);
        applyMode(safeMode);
    }

    /**
     * Lee el modo de tema efectivo guardado para la aplicación.
     *
     * @param context contexto usado para acceder a preferencias.
     * @return uno de {@link #MODE_SYSTEM}, {@link #MODE_LIGHT} o {@link #MODE_DARK}.
     */
    @NonNull
    public static String getSavedMode(@NonNull Context context) {
        return AppSettingsManager.getThemeMode(context);
    }

    /**
     * Traduce el modo lógico de la app al valor equivalente de {@link AppCompatDelegate}.
     *
     * @param mode modo lógico solicitado.
     */
    public static void applyMode(@NonNull String mode) {
        switch (sanitizeMode(mode)) {
            case MODE_DARK:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            case MODE_LIGHT:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }

    /**
     * Fuerza cualquier valor inesperado a un modo soportado por la app.
     *
     * @param mode valor recibido desde preferencias o desde la UI.
     * @return modo seguro compatible con el resto del gestor.
     */
    @NonNull
    private static String sanitizeMode(String mode) {
        if (MODE_DARK.equals(mode)) return MODE_DARK;
        if (MODE_LIGHT.equals(mode)) return MODE_LIGHT;
        return MODE_SYSTEM;
    }
}
