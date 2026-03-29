package com.proyecto.moveon.ui.profile;

import android.content.Context;

import androidx.annotation.NonNull;

import com.proyecto.moveon.R;
import com.proyecto.moveon.core.i18n.AppLanguageManager;
import com.proyecto.moveon.core.i18n.ProfileValueLocalizer;
import com.proyecto.moveon.domain.activity.ActividadItem;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.FormatStyle;
import java.util.Locale;

/**
 * Utilidades de formato para compartir rutas.
 */
public final class ShareRouteFormatter {

    private ShareRouteFormatter() {
        // Utility class
    }

    /**
     * Construye el texto resumen que acompaña a la imagen compartida.
     */
    @NonNull
    public static String buildShareText(@NonNull Context context, @NonNull ActividadItem item) {
        // Para el texto adjunto hemos simplificado el copy a una única frase fija,
        // tal como se pidió, sin concatenar métricas de la actividad.
        return context.getString(R.string.share_routes_share_text);
    }

    /**
     * Traduce el tipo canónico de actividad a su label visible en la UI.
     */
    @NonNull
    public static String displayType(@NonNull Context context, @NonNull ActividadItem item) {
        return ProfileValueLocalizer.displayActivityType(context, item.tipo);
    }


    /**
     * Devuelve solo el valor numérico de la distancia en kilómetros, sin unidad,
     * para poder maquetarlo por separado en composiciones visuales tipo Strava.
     */
    @NonNull
    public static String formatDistanceNumber(@NonNull Context context, int meters) {
        Locale locale = AppLanguageManager.getActiveLocale(context);
        return String.format(locale, "%.2f", meters / 1000f);
    }

    /**
     * Formatea la distancia en kilómetros con dos decimales.
     */
    @NonNull
    public static String formatDistance(@NonNull Context context, int meters) {
        Locale locale = AppLanguageManager.getActiveLocale(context);
        return String.format(locale, "%.2f km", meters / 1000f);
    }

    /**
     * Formatea la duración total en formato breve y legible.
     */
    @NonNull
    public static String formatDuration(@NonNull Context context, int seconds) {
        Locale locale = AppLanguageManager.getActiveLocale(context);

        int hours = seconds / 3600;
        int minutes = (seconds % 3600) / 60;
        int remainingSeconds = seconds % 60;

        if (hours > 0) {
            return String.format(locale, "%dh %02dm", hours, minutes);
        }
        if (minutes > 0) {
            return String.format(locale, "%dm %02ds", minutes, remainingSeconds);
        }
        return String.format(locale, "%ds", Math.max(1, remainingSeconds));
    }

    /**
     * Formatea el ritmo como min/km.
     */
    @NonNull
    public static String formatPace(@NonNull Context context, int secondsPerKm) {
        if (secondsPerKm <= 0) {
            return context.getString(R.string.share_routes_pace_empty);
        }

        int minutes = secondsPerKm / 60;
        int seconds = secondsPerKm % 60;
        return String.format(Locale.US, "%d'%02d\"", minutes, seconds);
    }


    /**
     * Variante explícita para valores opcionales de ritmo.
     *
     * <p>Cuando el ritmo no existe todavía, reutiliza el placeholder estándar de la tarjeta.</p>
     */
    @NonNull
    public static String formatOptionalPace(@NonNull Context context, int secondsPerKm) {
        return formatPace(context, secondsPerKm);
    }

    /**
     * Formatea el ritmo como min/km incluyendo explícitamente la unidad final.
     */
    @NonNull
    public static String formatPaceWithUnit(@NonNull Context context, int secondsPerKm) {
        String pace = formatPace(context, secondsPerKm);
        if (pace.equals(context.getString(R.string.share_routes_pace_empty))) {
            return pace;
        }
        return pace + "/km";
    }

    /**
     * Formatea la fecha ISO intentando soportar tanto {@link LocalDate} como {@link OffsetDateTime}.
     *
     * <p>Bugfix: cuando la entrada trae offset, primero se normaliza a la zona horaria local del
     * dispositivo. Así la fecha mostrada en la tarjeta compartida coincide con la usada por las
     * estadísticas y por el historial.</p>
     */
    @NonNull
    public static String formatDate(@NonNull Context context, @NonNull String isoDate) {
        Locale locale = AppLanguageManager.getActiveLocale(context);
        DateTimeFormatter formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                .withLocale(locale);

        try {
            return OffsetDateTime.parse(isoDate)
                    .atZoneSameInstant(ZoneId.systemDefault())
                    .toLocalDate()
                    .format(formatter);
        } catch (DateTimeParseException ignored) {
            // Fallback al formato solo-fecha si no venía zona horaria.
        }

        try {
            return LocalDate.parse(isoDate).format(formatter);
        } catch (DateTimeParseException ignored) {
            // Fallback final: mostrar texto bruto si no es parseable.
        }

        return isoDate.length() >= 10 ? isoDate.substring(0, 10) : isoDate;
    }
}