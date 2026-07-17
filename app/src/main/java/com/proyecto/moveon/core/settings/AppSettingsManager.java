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
 * - ajustes locales de tracking y auth social
 */
public final class AppSettingsManager {

    public static final String PREFS = "AppSettings";

    private static final String KEY_THEME_MODE = "theme_mode";
    private static final String KEY_APP_LANGUAGE = "app_language";
    private static final String KEY_PACE_DISPLAY_MODE = "pace_display_mode";
    private static final String KEY_PENDING_UI_TRANSITION_SPLASH = "pending_ui_transition_splash";
    private static final String KEY_SHOW_AUTO_PAUSE_ALERTS = "show_auto_pause_alerts";
    private static final String KEY_AUTH_PROVIDER = "auth_provider";
    private static final String KEY_GOOGLE_SILENT_ENABLED = "google_silent_enabled";

    private static final String KEY_TRACKING_LOCATION_PERMISSION_REQUESTED =
            "tracking_location_permission_requested";
    private static final String KEY_TRACKING_ACTIVITY_PERMISSION_REQUESTED =
            "tracking_activity_permission_requested";
    private static final String KEY_TRACKING_BATTERY_EXEMPTION_REQUESTED =
            "tracking_battery_exemption_requested";
    private static final String KEY_TRACKING_NOTIFICATIONS_PERMISSION_REQUESTED =
            "tracking_notifications_permission_requested";



    public static final String PACE_DISPLAY_TOTAL = "total";
    public static final String PACE_DISPLAY_MOVING = "moving";

    /**
     * Evita instancias de una clase utilitaria basada en métodos estáticos.
     */
    private AppSettingsManager() {}

    /**
     * Obtiene el contenedor de preferencias globales asociado al contexto de aplicación.
     *
     * @param context contexto desde el que resolver el {@code applicationContext}.
     * @return {@link SharedPreferences} globales de la aplicación.
     */
    @NonNull
    private static SharedPreferences prefs(@NonNull Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ── Tema ────────────────────────────────────────────────────────────────

    /**
     * Lee el modo de tema persistido y devuelve el modo del sistema cuando aún no hay valor guardado.
     *
     * @param context contexto desde el que leer preferencias.
     * @return modo de tema almacenado o {@link ThemeManager#MODE_SYSTEM} como fallback.
     */
    @NonNull
    public static String getThemeMode(@NonNull Context context) {
        return prefs(context).getString(KEY_THEME_MODE, ThemeManager.MODE_SYSTEM);
    }

    /**
     * Guarda el modo de tema elegido por el usuario.
     *
     * @param context contexto desde el que persistir la preferencia.
     * @param mode modo de tema seleccionado.
     */
    public static void setThemeMode(@NonNull Context context, @NonNull String mode) {
        prefs(context).edit().putString(KEY_THEME_MODE, mode).apply();
    }

    /**
     * Indica si ya existe una preferencia explícita de tema.
     *
     * @param context contexto desde el que leer preferencias.
     * @return {@code true} cuando el usuario ya ha guardado un tema manual.
     */
    public static boolean hasThemeMode(@NonNull Context context) {
        return prefs(context).contains(KEY_THEME_MODE);
    }

    // ── Idioma ──────────────────────────────────────────────────────────────

    /**
     * Devuelve el idioma manual almacenado solo si pertenece al conjunto de idiomas soportados.
     *
     * @param context contexto desde el que leer preferencias.
     * @return idioma manual soportado o {@code null} si no existe o es inválido.
     */
    @Nullable
    public static String getStoredAppLanguage(@NonNull Context context) {
        String value = prefs(context).getString(KEY_APP_LANGUAGE, null);
        if (AppLanguageManager.MODE_SPANISH.equals(value)) return AppLanguageManager.MODE_SPANISH;
        if (AppLanguageManager.MODE_ENGLISH.equals(value)) return AppLanguageManager.MODE_ENGLISH;
        return null;
    }

    /**
     * Guarda el idioma de la app o elimina la preferencia cuando el valor recibido no es válido.
     *
     * @param context contexto desde el que persistir la preferencia.
     * @param mode modo de idioma solicitado.
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
     * Comprueba si hay un idioma manualmente seleccionado.
     *
     * @param context contexto desde el que leer preferencias.
     * @return {@code true} cuando existe un idioma manual persistido.
     */
    public static boolean hasAppLanguage(@NonNull Context context) {
        return getStoredAppLanguage(context) != null;
    }

    /**
     * Recupera el valor bruto persistido para el idioma sin validarlo ni normalizarlo.
     *
     * @param context contexto desde el que leer preferencias.
     * @return valor bruto almacenado o {@code null}.
     */
    @Nullable
    public static String getRawAppLanguage(@NonNull Context context) {
        return prefs(context).getString(KEY_APP_LANGUAGE, null);
    }

    /**
     * Elimina la preferencia de idioma para volver al comportamiento por defecto basado en el sistema.
     *
     * @param context contexto desde el que limpiar preferencias.
     */
    public static void clearAppLanguage(@NonNull Context context) {
        prefs(context).edit().remove(KEY_APP_LANGUAGE).apply();
    }

    // ── Ritmo medio ─────────────────────────────────────────────────────────

    /**
     * Devuelve el modo de ritmo configurado, normalizando cualquier valor inesperado al modo total.
     *
     * @param context contexto desde el que leer preferencias.
     * @return modo de ritmo seguro para la UI.
     */
    @NonNull
    public static String getPaceDisplayMode(@NonNull Context context) {
        String value = prefs(context).getString(KEY_PACE_DISPLAY_MODE, PACE_DISPLAY_TOTAL);
        if (PACE_DISPLAY_MOVING.equals(value)) {
            return PACE_DISPLAY_MOVING;
        }
        return PACE_DISPLAY_TOTAL;
    }

    /**
     * Persiste el modo de visualización del ritmo forzando un valor seguro entre las opciones admitidas.
     *
     * @param context contexto desde el que persistir la preferencia.
     * @param mode modo solicitado por la UI.
     */
    public static void setPaceDisplayMode(@NonNull Context context, @Nullable String mode) {
        String safeMode = PACE_DISPLAY_MOVING.equals(mode)
                ? PACE_DISPLAY_MOVING
                : PACE_DISPLAY_TOTAL;
        prefs(context).edit().putString(KEY_PACE_DISPLAY_MODE, safeMode).apply();
    }

    /**
     * Indica si la UI debe priorizar el ritmo medio en movimiento.
     *
     * @param context contexto desde el que leer preferencias.
     * @return {@code true} cuando debe priorizarse el ritmo en movimiento.
     */
    public static boolean isPaceDisplayMoving(@NonNull Context context) {
        return PACE_DISPLAY_MOVING.equals(getPaceDisplayMode(context));
    }

    // ── Tracking / auth social locales ─────────────────────────────────────

    /**
     * Lee la preferencia local que controla si los avisos de auto-pausa deben mostrarse por defecto.
     *
     * @param context contexto desde el que leer preferencias.
     * @return {@code true} cuando las alertas de auto-pausa están habilitadas por defecto.
     */
    public static boolean shouldShowAutoPauseAlertsByDefault(@NonNull Context context) {
        return prefs(context).getBoolean(KEY_SHOW_AUTO_PAUSE_ALERTS, true);
    }

    /**
     * Actualiza la preferencia local de visibilidad de las alertas de auto-pausa.
     *
     * @param context contexto desde el que persistir preferencias.
     * @param show nuevo valor para la preferencia.
     */
    public static void setShowAutoPauseAlertsByDefault(@NonNull Context context, boolean show) {
        prefs(context).edit().putBoolean(KEY_SHOW_AUTO_PAUSE_ALERTS, show).apply();
    }

    /**
     * Devuelve el proveedor con el que se autenticó la sesión recuperable actual, si existe.
     *
     * @param context contexto desde el que leer preferencias.
     * @return proveedor guardado o {@code null}.
     */
    @Nullable
    public static String getAuthProvider(@NonNull Context context) {
        return prefs(context).getString(KEY_AUTH_PROVIDER, null);
    }

    /**
     * Guarda el proveedor de autenticación recortando espacios o lo elimina si queda vacío.
     *
     * @param context contexto desde el que persistir preferencias.
     * @param authProvider proveedor a guardar o {@code null} para limpiarlo.
     */
    public static void setAuthProvider(@NonNull Context context, @Nullable String authProvider) {
        SharedPreferences.Editor editor = prefs(context).edit();
        if (authProvider == null || authProvider.trim().isEmpty()) {
            editor.remove(KEY_AUTH_PROVIDER);
        } else {
            editor.putString(KEY_AUTH_PROVIDER, authProvider.trim());
        }
        editor.apply();
    }

    /**
     * Indica si el reingreso silencioso con Google está habilitado localmente.
     *
     * @param context contexto desde el que leer preferencias.
     * @return {@code true} cuando el silent sign-in de Google está habilitado.
     */
    public static boolean isGoogleSilentSignInEnabled(@NonNull Context context) {
        return prefs(context).getBoolean(KEY_GOOGLE_SILENT_ENABLED, false);
    }

    /**
     * Activa o desactiva el intento de acceso silencioso con Google en próximos arranques.
     *
     * @param context contexto desde el que persistir preferencias.
     * @param enabled nuevo estado del silent sign-in.
     */
    public static void setGoogleSilentSignInEnabled(@NonNull Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_GOOGLE_SILENT_ENABLED, enabled).apply();
    }

    /**
     * Limpia las pistas locales del login social y restablece el silent sign-in a desactivado.
     *
     * @param context contexto desde el que limpiar preferencias.
     */
    public static void clearSocialAuthState(@NonNull Context context) {
        prefs(context).edit()
                .remove(KEY_AUTH_PROVIDER)
                .putBoolean(KEY_GOOGLE_SILENT_ENABLED, false)
                .apply();
    }

    /**
     * Marca que la siguiente transición de UI debe mostrar la pantalla splash.
     *
     * @param context contexto desde el que persistir preferencias.
     */
    public static void requestUiTransitionSplash(@NonNull Context context) {
        prefs(context).edit().putBoolean(KEY_PENDING_UI_TRANSITION_SPLASH, true).apply();
    }

    /**
     * Comprueba si existe una solicitud pendiente para mostrar el splash de transición.
     *
     * @param context contexto desde el que leer preferencias.
     * @return {@code true} cuando el splash de transición sigue pendiente.
     */
    public static boolean isUiTransitionSplashRequested(@NonNull Context context) {
        return prefs(context).getBoolean(KEY_PENDING_UI_TRANSITION_SPLASH, false);
    }

    /**
     * Elimina la marca que fuerza el splash de transición.
     *
     * @param context contexto desde el que limpiar preferencias.
     */
    public static void clearUiTransitionSplashRequest(@NonNull Context context) {
        prefs(context).edit().remove(KEY_PENDING_UI_TRANSITION_SPLASH).apply();
    }


    // ── Tracking: flags internos del flujo de permisos ─────────────────────

    /**
     * Indica si ya se mostró al usuario la propuesta de exención de optimización de batería.
     *
     * <p>Se usa para que el diálogo de "no matar la app durante una actividad" aparezca una
     * sola vez al iniciar tracking y no se convierta en fricción recurrente.</p>
     *
     * @param context contexto desde el que leer la preferencia global.
     * @return {@code true} cuando la propuesta ya se enseñó alguna vez.
     */
    public static boolean wasTrackingBatteryExemptionRequested(@NonNull Context context) {
        return prefs(context).getBoolean(KEY_TRACKING_BATTERY_EXEMPTION_REQUESTED, false);
    }

    /**
     * Persiste que la propuesta de exención de batería ya fue mostrada.
     *
     * @param context contexto desde el que guardar la preferencia.
     * @param requested valor a persistir para el flag de exención de batería.
     */
    public static void setTrackingBatteryExemptionRequested(@NonNull Context context, boolean requested) {
        prefs(context).edit().putBoolean(KEY_TRACKING_BATTERY_EXEMPTION_REQUESTED, requested).apply();
    }

    /**
     * Indica si la app ya lanzó alguna vez la petición del permiso de ubicación para tracking.
     *
     * @param context contexto desde el que leer la preferencia global.
     * @return {@code true} cuando el flujo de tracking ya intentó pedir ubicación al usuario.
     */
    public static boolean wasTrackingLocationPermissionRequested(@NonNull Context context) {
        return prefs(context).getBoolean(KEY_TRACKING_LOCATION_PERMISSION_REQUESTED, false);
    }

    /**
     * Persiste si el permiso de ubicación ya fue solicitado durante el flujo de tracking.
     *
     * @param context contexto desde el que guardar la preferencia.
     * @param requested valor a persistir para el flag de ubicación.
     */
    public static void setTrackingLocationPermissionRequested(@NonNull Context context, boolean requested) {
        prefs(context).edit().putBoolean(KEY_TRACKING_LOCATION_PERMISSION_REQUESTED, requested).apply();
    }

    /**
     * Indica si la app ya pidió el permiso de reconocimiento de actividad.
     *
     * @param context contexto desde el que leer la preferencia global.
     * @return {@code true} cuando ya se mostró o lanzó la petición de actividad física.
     */
    public static boolean wasTrackingActivityPermissionRequested(@NonNull Context context) {
        return prefs(context).getBoolean(KEY_TRACKING_ACTIVITY_PERMISSION_REQUESTED, false);
    }

    /**
     * Guarda si el permiso de reconocimiento de actividad ya se intentó solicitar.
     *
     * @param context contexto desde el que guardar la preferencia.
     * @param requested valor a persistir para el flag de actividad física.
     */
    public static void setTrackingActivityPermissionRequested(@NonNull Context context, boolean requested) {
        prefs(context).edit().putBoolean(KEY_TRACKING_ACTIVITY_PERMISSION_REQUESTED, requested).apply();
    }

    /**
     * Indica si el permiso runtime de notificaciones ya fue pedido en el flujo de tracking.
     *
     * @param context contexto desde el que leer la preferencia global.
     * @return {@code true} cuando la app ya intentó pedir notificaciones para el tracking.
     */
    public static boolean wasTrackingNotificationsPermissionRequested(@NonNull Context context) {
        return prefs(context).getBoolean(KEY_TRACKING_NOTIFICATIONS_PERMISSION_REQUESTED, false);
    }

    /**
     * Persiste si el permiso de notificaciones ya se solicitó al usuario.
     *
     * @param context contexto desde el que guardar la preferencia.
     * @param requested valor a persistir para el flag de notificaciones.
     */
    public static void setTrackingNotificationsPermissionRequested(@NonNull Context context, boolean requested) {
        prefs(context).edit().putBoolean(KEY_TRACKING_NOTIFICATIONS_PERMISSION_REQUESTED, requested).apply();
    }

    /**
     * Reinicia las marcas internas usadas para saber qué permisos de tracking ya se habían pedido.
     *
     * @param context contexto desde el que limpiar las preferencias del flujo de permisos.
     */
    public static void clearTrackingPermissionRequestFlags(@NonNull Context context) {
        prefs(context).edit()
                .remove(KEY_TRACKING_LOCATION_PERMISSION_REQUESTED)
                .remove(KEY_TRACKING_ACTIVITY_PERMISSION_REQUESTED)
                .remove(KEY_TRACKING_NOTIFICATIONS_PERMISSION_REQUESTED)
                .apply();
    }
}