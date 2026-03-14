package com.proyecto.moveon.core.settings;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.proyecto.moveon.core.theme.ThemeManager;

/**
 * Preferencias globales de la aplicación.
 *
 * Aquí deben vivir ajustes que NO dependen de una cuenta concreta,
 * por ejemplo:
 * - modo de tema
 * - notificaciones activadas/desactivadas dentro de la app
 */
public final class AppSettingsManager {

    public static final String PREFS = "AppSettings";

    private static final String KEY_THEME_MODE = "theme_mode";
    private static final String KEY_NOTIFICATIONS_ENABLED = "notifications_enabled";

    private AppSettingsManager() {}

    @NonNull
    private static SharedPreferences prefs(@NonNull Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ── Tema ────────────────────────────────────────────────────────────────

    @NonNull
    public static String getThemeMode(@NonNull Context context) {
        String mode = prefs(context).getString(KEY_THEME_MODE, ThemeManager.MODE_SYSTEM);
        return mode != null ? mode : ThemeManager.MODE_SYSTEM;
    }

    public static void setThemeMode(@NonNull Context context, @NonNull String mode) {
        prefs(context).edit().putString(KEY_THEME_MODE, mode).apply();
    }

    public static boolean hasThemeMode(@NonNull Context context) {
        return prefs(context).contains(KEY_THEME_MODE);
    }

    // ── Notificaciones ──────────────────────────────────────────────────────

    public static boolean areNotificationsEnabled(@NonNull Context context) {
        return prefs(context).getBoolean(KEY_NOTIFICATIONS_ENABLED, false);
    }

    public static void setNotificationsEnabled(@NonNull Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled).apply();
    }

    public static boolean hasNotificationsPreference(@NonNull Context context) {
        return prefs(context).contains(KEY_NOTIFICATIONS_ENABLED);
    }

    // ── Utilidades opcionales ───────────────────────────────────────────────

    public static void clearNotificationsPreference(@NonNull Context context) {
        prefs(context).edit().remove(KEY_NOTIFICATIONS_ENABLED).apply();
    }

    public static void clearThemeMode(@NonNull Context context) {
        prefs(context).edit().remove(KEY_THEME_MODE).apply();
    }
}
