package com.proyecto.moveon.core.settings;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.proyecto.moveon.core.i18n.AppLanguageManager;
import com.proyecto.moveon.core.theme.ThemeManager;

/**
 * Preferencias globales de la aplicación.
 *
 * Aquí deben vivir ajustes que NO dependen de una cuenta concreta,
 * por ejemplo:
 * - modo de tema
 * - idioma de la app
 * - notificaciones activadas/desactivadas dentro de la app
 */
public final class AppSettingsManager {

    public static final String PREFS = "AppSettings";

    private static final String KEY_THEME_MODE = "theme_mode";
    private static final String KEY_APP_LANGUAGE = "app_language";
    private static final String KEY_NOTIFICATIONS_ENABLED = "notifications_enabled";
    private static final String KEY_NOTIFICATIONS_PERMISSION_REQUESTED = "notifications_permission_requested";

    private AppSettingsManager() {}

    @NonNull
    private static SharedPreferences prefs(@NonNull Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ── Tema ────────────────────────────────────────────────────────────────

    @NonNull
    public static String getThemeMode(@NonNull Context context) {
        return prefs(context).getString(KEY_THEME_MODE, ThemeManager.MODE_SYSTEM);
    }

    public static void setThemeMode(@NonNull Context context, @NonNull String mode) {
        prefs(context).edit().putString(KEY_THEME_MODE, mode).apply();
    }

    @SuppressWarnings("unused")
    public static boolean hasThemeMode(@NonNull Context context) {
        return prefs(context).contains(KEY_THEME_MODE);
    }

    // ── Idioma ──────────────────────────────────────────────────────────────

    /**
     * Devuelve únicamente una selección manual válida ("es" / "en").
     * Si no hay preferencia guardada, o viene un valor legacy como "system",
     * devuelve null para que AppLanguageManager resuelva el idioma por defecto
     * a partir del idioma del sistema.
     */
    @Nullable
    public static String getStoredAppLanguage(@NonNull Context context) {
        String value = prefs(context).getString(KEY_APP_LANGUAGE, null);
        if (AppLanguageManager.MODE_SPANISH.equals(value)) return AppLanguageManager.MODE_SPANISH;
        if (AppLanguageManager.MODE_ENGLISH.equals(value)) return AppLanguageManager.MODE_ENGLISH;
        return null;
    }

    /**
     * Guarda una selección manual válida.
     * Cualquier valor distinto de "es" o "en" limpia la preferencia para
     * volver al comportamiento por defecto según el idioma del sistema.
     */
    public static void setAppLanguage(@NonNull Context context, @Nullable String mode) {
        SharedPreferences.Editor editor = prefs(context).edit();
        if (AppLanguageManager.MODE_SPANISH.equals(mode) || AppLanguageManager.MODE_ENGLISH.equals(mode)) {
            editor.putString(KEY_APP_LANGUAGE, mode);
        } else {
            editor.remove(KEY_APP_LANGUAGE);
        }
        editor.apply();
    }

    /**
     * Indica si el usuario ha elegido manualmente un idioma soportado.
     */
    public static boolean hasAppLanguage(@NonNull Context context) {
        return getStoredAppLanguage(context) != null;
    }

    /**
     * Permite detectar valores legacy todavía presentes en disco, por ejemplo "system".
     */
    @Nullable
    public static String getRawAppLanguage(@NonNull Context context) {
        return prefs(context).getString(KEY_APP_LANGUAGE, null);
    }

    @SuppressWarnings("unused")
    public static void clearAppLanguage(@NonNull Context context) {
        prefs(context).edit().remove(KEY_APP_LANGUAGE).apply();
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

    public static boolean wasNotificationsPermissionRequested(@NonNull Context context) {
        return prefs(context).getBoolean(KEY_NOTIFICATIONS_PERMISSION_REQUESTED, false);
    }

    public static void setNotificationsPermissionRequested(@NonNull Context context, boolean requested) {
        prefs(context).edit().putBoolean(KEY_NOTIFICATIONS_PERMISSION_REQUESTED, requested).apply();
    }

    // ── Utilidades opcionales ───────────────────────────────────────────────

    @SuppressWarnings("unused")
    public static void clearNotificationsPreference(@NonNull Context context) {
        prefs(context).edit()
                .remove(KEY_NOTIFICATIONS_ENABLED)
                .remove(KEY_NOTIFICATIONS_PERMISSION_REQUESTED)
                .apply();
    }

    @SuppressWarnings("unused")
    public static void clearThemeMode(@NonNull Context context) {
        prefs(context).edit().remove(KEY_THEME_MODE).apply();
    }
}
