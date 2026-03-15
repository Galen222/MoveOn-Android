package com.proyecto.moveon.core.i18n;

import android.content.Context;
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

    public static void applySavedLanguage(@NonNull Context context) {
        cleanupLegacyStoredMode(context);
        applyResolvedLanguage(context);
    }

    public static void saveAndApply(@NonNull Context context, @NonNull String mode) {
        String safeMode = sanitizeSelectableMode(mode);
        AppSettingsManager.setAppLanguage(context, safeMode);
        applyResolvedLanguage(context);
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
