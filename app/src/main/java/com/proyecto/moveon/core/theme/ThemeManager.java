package com.proyecto.moveon.core.theme;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;

import com.proyecto.moveon.core.settings.AppSettingsManager;

/**
 * Responsable SOLO de la lógica del tema.
 *
 * - Lee/escribe el modo a través de AppSettingsManager
 * - Aplica el modo visual con AppCompatDelegate
 */
public final class ThemeManager {

    private ThemeManager() {}

    public static final String MODE_SYSTEM = "system";
    public static final String MODE_LIGHT  = "light";
    public static final String MODE_DARK   = "dark";

    public static void applySavedTheme(@NonNull Context context) {
        applyMode(AppSettingsManager.getThemeMode(context));
    }

    public static void saveAndApply(@NonNull Context context, @NonNull String mode) {
        String safeMode = sanitizeMode(mode);
        AppSettingsManager.setThemeMode(context, safeMode);
        applyMode(safeMode);
    }

    @NonNull
    public static String getSavedMode(@NonNull Context context) {
        return AppSettingsManager.getThemeMode(context);
    }

    public static boolean isDarkMode(@NonNull Context context) {
        return MODE_DARK.equals(getSavedMode(context));
    }

    public static boolean isLightMode(@NonNull Context context) {
        return MODE_LIGHT.equals(getSavedMode(context));
    }

    public static boolean isSystemMode(@NonNull Context context) {
        return MODE_SYSTEM.equals(getSavedMode(context));
    }

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

    @NonNull
    private static String sanitizeMode(String mode) {
        if (MODE_DARK.equals(mode)) return MODE_DARK;
        if (MODE_LIGHT.equals(mode)) return MODE_LIGHT;
        return MODE_SYSTEM;
    }
}
