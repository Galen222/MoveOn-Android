package com.proyecto.moveon.core.settings;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.proyecto.moveon.core.i18n.AppLanguageManager;
import com.proyecto.moveon.core.theme.ThemeManager;

/**
 * Preferencias globales de la aplicación.
 * Aquí viven ajustes que NO dependen de una cuenta concreta:
 * - modo de tema
 * - idioma de la app
 * - flags internos del flujo de permisos/requisitos del tracking
 */
public final class AppSettingsManager {

    public static final String PREFS = "AppSettings";

    private static final String KEY_THEME_MODE = "theme_mode";
    private static final String KEY_APP_LANGUAGE = "app_language";
    private static final String KEY_PACE_DISPLAY_MODE = "pace_display_mode";
    private static final String KEY_PENDING_UI_TRANSITION_SPLASH = "pending_ui_transition_splash";

    private static final String KEY_TRACKING_LOCATION_PERMISSION_REQUESTED =
            "tracking_location_permission_requested";
    private static final String KEY_TRACKING_ACTIVITY_PERMISSION_REQUESTED =
            "tracking_activity_permission_requested";
    private static final String KEY_TRACKING_NOTIFICATIONS_PERMISSION_REQUESTED =
            "tracking_notifications_permission_requested";



    public static final String PACE_DISPLAY_TOTAL = "total";
    public static final String PACE_DISPLAY_MOVING = "moving";

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

    public static boolean hasThemeMode(@NonNull Context context) {
        return prefs(context).contains(KEY_THEME_MODE);
    }

    // ── Idioma ──────────────────────────────────────────────────────────────

    @Nullable
    public static String getStoredAppLanguage(@NonNull Context context) {
        String value = prefs(context).getString(KEY_APP_LANGUAGE, null);
        if (AppLanguageManager.MODE_SPANISH.equals(value)) return AppLanguageManager.MODE_SPANISH;
        if (AppLanguageManager.MODE_ENGLISH.equals(value)) return AppLanguageManager.MODE_ENGLISH;
        return null;
    }

    public static void setAppLanguage(@NonNull Context context, @Nullable String mode) {
        SharedPreferences.Editor editor = prefs(context).edit();
        if (AppLanguageManager.MODE_SPANISH.equals(mode) || AppLanguageManager.MODE_ENGLISH.equals(mode)) {
            editor.putString(KEY_APP_LANGUAGE, mode);
        } else {
            editor.remove(KEY_APP_LANGUAGE);
        }
        editor.apply();
    }

    public static boolean hasAppLanguage(@NonNull Context context) {
        return getStoredAppLanguage(context) != null;
    }

    @Nullable
    public static String getRawAppLanguage(@NonNull Context context) {
        return prefs(context).getString(KEY_APP_LANGUAGE, null);
    }

    public static void clearAppLanguage(@NonNull Context context) {
        prefs(context).edit().remove(KEY_APP_LANGUAGE).apply();
    }

    // ── Ritmo medio ─────────────────────────────────────────────────────────

    @NonNull
    public static String getPaceDisplayMode(@NonNull Context context) {
        String value = prefs(context).getString(KEY_PACE_DISPLAY_MODE, PACE_DISPLAY_TOTAL);
        if (PACE_DISPLAY_MOVING.equals(value)) {
            return PACE_DISPLAY_MOVING;
        }
        return PACE_DISPLAY_TOTAL;
    }

    public static void setPaceDisplayMode(@NonNull Context context, @Nullable String mode) {
        String safeMode = PACE_DISPLAY_MOVING.equals(mode)
                ? PACE_DISPLAY_MOVING
                : PACE_DISPLAY_TOTAL;
        prefs(context).edit().putString(KEY_PACE_DISPLAY_MODE, safeMode).apply();
    }

    public static boolean isPaceDisplayMoving(@NonNull Context context) {
        return PACE_DISPLAY_MOVING.equals(getPaceDisplayMode(context));
    }


    public static void requestUiTransitionSplash(@NonNull Context context) {
        prefs(context).edit().putBoolean(KEY_PENDING_UI_TRANSITION_SPLASH, true).apply();
    }

    public static boolean isUiTransitionSplashRequested(@NonNull Context context) {
        return prefs(context).getBoolean(KEY_PENDING_UI_TRANSITION_SPLASH, false);
    }

    public static void clearUiTransitionSplashRequest(@NonNull Context context) {
        prefs(context).edit().remove(KEY_PENDING_UI_TRANSITION_SPLASH).apply();
    }


    // ── Tracking: flags internos del flujo de permisos ─────────────────────

    public static boolean wasTrackingLocationPermissionRequested(@NonNull Context context) {
        return prefs(context).getBoolean(KEY_TRACKING_LOCATION_PERMISSION_REQUESTED, false);
    }

    public static void setTrackingLocationPermissionRequested(@NonNull Context context, boolean requested) {
        prefs(context).edit().putBoolean(KEY_TRACKING_LOCATION_PERMISSION_REQUESTED, requested).apply();
    }

    public static boolean wasTrackingActivityPermissionRequested(@NonNull Context context) {
        return prefs(context).getBoolean(KEY_TRACKING_ACTIVITY_PERMISSION_REQUESTED, false);
    }

    public static void setTrackingActivityPermissionRequested(@NonNull Context context, boolean requested) {
        prefs(context).edit().putBoolean(KEY_TRACKING_ACTIVITY_PERMISSION_REQUESTED, requested).apply();
    }

    public static boolean wasTrackingNotificationsPermissionRequested(@NonNull Context context) {
        return prefs(context).getBoolean(KEY_TRACKING_NOTIFICATIONS_PERMISSION_REQUESTED, false);
    }

    public static void setTrackingNotificationsPermissionRequested(@NonNull Context context, boolean requested) {
        prefs(context).edit().putBoolean(KEY_TRACKING_NOTIFICATIONS_PERMISSION_REQUESTED, requested).apply();
    }

    public static void clearTrackingPermissionRequestFlags(@NonNull Context context) {
        prefs(context).edit()
                .remove(KEY_TRACKING_LOCATION_PERMISSION_REQUESTED)
                .remove(KEY_TRACKING_ACTIVITY_PERMISSION_REQUESTED)
                .remove(KEY_TRACKING_NOTIFICATIONS_PERMISSION_REQUESTED)
                .apply();
    }
}