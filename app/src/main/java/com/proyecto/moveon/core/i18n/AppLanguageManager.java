package com.proyecto.moveon.core.i18n;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import com.proyecto.moveon.core.settings.AppSettingsManager;

import java.util.Locale;

/**
 * Gestiona el idioma de la app.
 *
 * Reglas:
 * - Si el usuario elige manualmente español o inglés, esa selección manda.
 * - Si no hay selección manual guardada:
 *   - sistema en español => app en español
 *   - sistema en inglés => app en inglés
 *   - cualquier otro idioma del sistema => app en inglés
 *
 * No existe ya una opción visible de "idioma del sistema"; el idioma del
 * sistema solo se usa como valor por defecto inicial cuando no hay una
 * preferencia manual almacenada.
 */
public final class AppLanguageManager {

    private static final String LEGACY_MODE_SYSTEM = "system";

    public static final String MODE_SPANISH = "es";
    public static final String MODE_ENGLISH = "en";

    private AppLanguageManager() {}

    /**
     * Aplica el idioma guardado usando {@code setApplicationLocales}.
     * Usar SOLO en {@code Application.onCreate()} (arranque en frío, sin Activity visible).
     * NO usar durante cambios interactivos de idioma — causa pantalla negra en API 33+.
     */
    public static void applySavedLanguage(@NonNull Context context) {
        cleanupLegacyStoredMode(context);
        applyResolvedLanguage(context);
    }

    /**
     * Guarda el idioma Y llama a {@code setApplicationLocales}.
     * Usar SOLO cuando NO hay Activity visible (ej: Application.onCreate).
     */
    public static void saveAndApply(@NonNull Context context, @NonNull String mode) {
        String safeMode = sanitizeSelectableMode(mode);
        AppSettingsManager.setAppLanguage(context, safeMode);
        applyResolvedLanguage(context);
    }

    /**
     * Guarda el idioma SIN llamar a {@code setApplicationLocales}.
     * Diseñado para cambios interactivos: guardar → recreate() → attachBaseContext
     * aplica el nuevo idioma sin pasar por LocaleManager del sistema.
     */
    public static void saveOnly(@NonNull Context context, @NonNull String mode) {
        String safeMode = sanitizeSelectableMode(mode);
        AppSettingsManager.setAppLanguage(context, safeMode);
    }

    /**
     * Envuelve un contexto base con el idioma resuelto.
     * Llamar desde {@code Activity.attachBaseContext()} para aplicar el idioma
     * sin usar setApplicationLocales (evita la recreación del sistema en API 33+).
     */
    @NonNull
    public static Context wrapContext(@NonNull Context baseContext) {
        String tag = getResolvedLanguageTag(baseContext);
        Locale locale = Locale.forLanguageTag(tag);
        Locale currentLocale = baseContext.getResources().getConfiguration().getLocales().get(0);
        if (locale.equals(currentLocale)) {
            return baseContext;
        }
        Configuration config = new Configuration(baseContext.getResources().getConfiguration());
        config.setLocale(locale);
        config.setLayoutDirection(locale);
        return baseContext.createConfigurationContext(config);
    }


    /**
     * Devuelve un contexto listo para resolver recursos con el idioma activo de la app.
     *
     * <p>Este helper está pensado para componentes que no pasan por
     * {@code Activity.attachBaseContext()}, por ejemplo {@code Service},
     * workers o utilidades que generan bitmaps/textos fuera de una pantalla.</p>
     *
     * <p>Internamente reutiliza {@link #wrapContext(Context)} para aplicar el
     * idioma guardado sobre el contexto recibido en ese momento.</p>
     */
    @NonNull
    public static Context localizedContext(@NonNull Context context) {
        // No usamos directamente getApplicationContext() aquí porque también puede
        // interesar envolver contextos de Activity, Service o Dialog sin perder
        // el resto de la configuración visual ya aplicada por Android.
        return wrapContext(context);
    }

    /**
     * Devuelve el modo que debe reflejar la UI del selector (siempre "es" o "en").
     * Si el usuario no ha elegido nada aún, devuelve el idioma efectivo resuelto
     * a partir del idioma del sistema.
     */
    @NonNull
    public static String getSelectedMode(@NonNull Context context) {
        String stored = AppSettingsManager.getStoredAppLanguage(context);
        if (stored != null) {
            return stored;
        }
        return resolveSystemFallbackLanguage(context);
    }

    public static boolean hasManualSelection(@NonNull Context context) {
        return AppSettingsManager.hasAppLanguage(context);
    }

    @NonNull
    public static Locale getActiveLocale(@NonNull Context context) {
        return Locale.forLanguageTag(getResolvedLanguageTag(context));
    }

    @NonNull
    public static String getResolvedLanguageTag(@NonNull Context context) {
        return getSelectedMode(context);
    }

    private static void applyResolvedLanguage(@NonNull Context context) {
        applyLanguageTag(getResolvedLanguageTag(context));
    }

    private static void applyLanguageTag(@NonNull String languageTag) {
        LocaleListCompat targetLocales = LocaleListCompat.forLanguageTags(languageTag);
        LocaleListCompat currentLocales = AppCompatDelegate.getApplicationLocales();
        if (targetLocales.equals(currentLocales)) {
            return;
        }
        AppCompatDelegate.setApplicationLocales(targetLocales);
    }

    @NonNull
    public static String sanitizeSelectableMode(@Nullable String mode) {
        if (MODE_SPANISH.equals(mode)) return MODE_SPANISH;
        return MODE_ENGLISH;
    }

    private static void cleanupLegacyStoredMode(@NonNull Context context) {
        String raw = AppSettingsManager.getRawAppLanguage(context);
        if (LEGACY_MODE_SYSTEM.equals(raw)) {
            AppSettingsManager.clearAppLanguage(context);
        }
    }

    @NonNull
    private static String resolveSystemFallbackLanguage(@NonNull Context context) {
        Locale systemLocale = getSystemLocale(context);
        String language = systemLocale.getLanguage();
        return MODE_SPANISH.equalsIgnoreCase(language) ? MODE_SPANISH : MODE_ENGLISH;
    }

    @NonNull
    private static Locale getSystemLocale(@NonNull Context context) {
        Resources systemResources = Resources.getSystem();
        android.content.res.Configuration configuration = systemResources.getConfiguration();

        if (!configuration.getLocales().isEmpty()) {
            Locale locale = configuration.getLocales().get(0);
            if (locale != null) return locale;
        }

        Locale fallback = Locale.getDefault();
        return fallback != null ? fallback : Locale.ENGLISH;
    }
}