
package com.proyecto.moveon.core.i18n;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import com.proyecto.moveon.core.settings.AppSettingsManager;

import java.util.Locale;

/**
 * Punto único de resolución y aplicación del idioma de la aplicación.
 *
 * <p>Centraliza tanto la persistencia de la preferencia en {@link AppSettingsManager}
 * como la aplicación efectiva del locale vía {@link AppCompatDelegate} o
 * {@link #wrapContext(Context)}, según el momento del ciclo de vida.</p>
 *
 * <p>Reglas activas:</p>
 * <ul>
 *   <li>Si el usuario elige manualmente español o inglés, esa selección manda.</li>
 *   <li>Si no hay selección manual guardada, solo se soportan dos fallbacks: sistema en
 *   español => app en español; cualquier otro idioma => app en inglés.</li>
 * </ul>
 *
 * <p>No existe ya una opción visible de "idioma del sistema"; el idioma del sistema
 * solo se usa como valor por defecto inicial cuando no hay una preferencia manual almacenada.</p>
 */
public final class AppLanguageManager {

    private static final String LEGACY_MODE_SYSTEM = "system";

    public static final String MODE_SPANISH = "es";
    public static final String MODE_ENGLISH = "en";

    /**
     * Evita instancias de una clase utilitaria puramente estática.
     */
    private AppLanguageManager() {}

    /**
     * Aplica el idioma guardado usando {@code setApplicationLocales}.
     * Usar SOLO en {@code Application.onCreate()} (arranque en frío, sin Activity visible).
     * NO usar durante cambios interactivos de idioma — causa pantalla negra en API 33+.
     *
     * @param context contexto desde el que se resuelve el idioma persistido.
     */
    public static void applySavedLanguage(@NonNull Context context) {
        cleanupLegacyStoredMode(context);
        applyResolvedLanguage(context);
    }

    /**
     * Guarda el idioma y aplica inmediatamente la locale de proceso.
     *
     * @param context contexto desde el que persistir la preferencia.
     * @param mode modo solicitado por la UI.
     */
    public static void saveAndApply(@NonNull Context context, @NonNull String mode) {
        String safeMode = sanitizeSelectableMode(mode);
        AppSettingsManager.setAppLanguage(context, safeMode);
        applyResolvedLanguage(context);
    }

    /**
     * Guarda el idioma sin tocar todavía la locale global del proceso.
     *
     * @param context contexto desde el que persistir la preferencia.
     * @param mode modo solicitado por la UI.
     */
    public static void saveOnly(@NonNull Context context, @NonNull String mode) {
        String safeMode = sanitizeSelectableMode(mode);
        AppSettingsManager.setAppLanguage(context, safeMode);
    }

    /**
     * Envuelve un contexto base con el idioma resuelto.
     *
     * @param baseContext contexto base recibido por el componente Android.
     * @return contexto localizado con la {@link Locale} efectiva de la app.
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
     *
     * @param context contexto que debe envolverse.
     * @return contexto listo para resolver recursos localizados.
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
     *
     * @param context contexto desde el que leer la preferencia persistida.
     * @return modo visible en la UI del selector de idioma.
     */
    @NonNull
    public static String getSelectedMode(@NonNull Context context) {
        String stored = AppSettingsManager.getStoredAppLanguage(context);
        if (stored != null) {
            return stored;
        }
        return resolveSystemFallbackLanguage();
    }


    /**
     * Resuelve un recurso de texto usando el idioma activo de la app y opcionalmente aplica formato.
     *
     * @param context contexto desde el que resolver recursos.
     * @param resId identificador del recurso de texto.
     * @param args argumentos de formato opcionales.
     * @return cadena ya localizada con el idioma activo de la app.
     */
    @NonNull
    public static String getString(@NonNull Context context, @StringRes int resId, Object... args) {
        Context localized = localizedContext(context);
        if (args == null || args.length == 0) {
            return localized.getString(resId);
        }
        return localized.getString(resId, args);
    }

    /**
     * Indica si el usuario eligió manualmente un idioma distinto del fallback automático.
     *
     * @param context contexto desde el que leer la preferencia.
     * @return {@code true} si existe una selección manual persistida.
     */
    public static boolean hasManualSelection(@NonNull Context context) {
        return AppSettingsManager.hasAppLanguage(context);
    }

    /**
     * Devuelve la {@link Locale} efectiva con la que la app debe pintar recursos en este momento.
     *
     * @param context contexto desde el que resolver el idioma activo.
     * @return locale efectiva usada por la aplicación.
     */
    @NonNull
    public static Locale getActiveLocale(@NonNull Context context) {
        return Locale.forLanguageTag(getResolvedLanguageTag(context));
    }

    /**
     * Obtiene la etiqueta BCP-47 del idioma finalmente resuelto para la aplicación.
     *
     * @param context contexto desde el que resolver el idioma activo.
     * @return etiqueta BCP-47 de la locale efectiva.
     */
    @NonNull
    public static String getResolvedLanguageTag(@NonNull Context context) {
        return getSelectedMode(context);
    }

    /**
     * Aplica a nivel de proceso la etiqueta de idioma resultante para el contexto recibido.
     *
     * @param context contexto desde el que resolver el idioma activo.
     */
    private static void applyResolvedLanguage(@NonNull Context context) {
        applyLanguageTag(getResolvedLanguageTag(context));
    }

    /**
     * Publica en AppCompat la lista de locales objetivo solo cuando difiere de la actual.
     *
     * @param languageTag etiqueta BCP-47 que debe publicarse como locale activa.
     */
    private static void applyLanguageTag(@NonNull String languageTag) {
        LocaleListCompat targetLocales = LocaleListCompat.forLanguageTags(languageTag);
        LocaleListCompat currentLocales = AppCompatDelegate.getApplicationLocales();
        if (targetLocales.equals(currentLocales)) {
            return;
        }
        AppCompatDelegate.setApplicationLocales(targetLocales);
    }

    /**
     * Normaliza el modo recibido a uno de los idiomas seleccionables por la UI.
     *
     * @param mode valor bruto recibido desde preferencias o desde la UI.
     * @return uno de {@link #MODE_SPANISH} o {@link #MODE_ENGLISH}.
     */
    @NonNull
    public static String sanitizeSelectableMode(@Nullable String mode) {
        if (MODE_SPANISH.equals(mode)) return MODE_SPANISH;
        return MODE_ENGLISH;
    }

    /**
     * Elimina el antiguo valor "system" para migrar al modelo actual sin opción visible de sistema.
     *
     * @param context contexto desde el que leer y limpiar la preferencia antigua.
     */
    private static void cleanupLegacyStoredMode(@NonNull Context context) {
        String raw = AppSettingsManager.getRawAppLanguage(context);
        if (LEGACY_MODE_SYSTEM.equals(raw)) {
            AppSettingsManager.clearAppLanguage(context);
        }
    }

    /**
     * Calcula el idioma por defecto a partir del idioma del sistema limitándolo a los soportados por la app.
     *
     * @return modo de idioma soportado por la app.
     */
    @NonNull
    private static String resolveSystemFallbackLanguage() {
        Locale systemLocale = getSystemLocale();
        String language = systemLocale.getLanguage();
        return MODE_SPANISH.equalsIgnoreCase(language) ? MODE_SPANISH : MODE_ENGLISH;
    }

    /**
     * Recupera la locale principal del sistema con un fallback defensivo a {@link Locale#ENGLISH}.
     *
     * @return locale principal del sistema o un fallback seguro.
     */
    @NonNull
    private static Locale getSystemLocale() {
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
