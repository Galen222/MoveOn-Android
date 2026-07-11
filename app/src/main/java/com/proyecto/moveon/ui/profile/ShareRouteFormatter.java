package com.proyecto.moveon.ui.profile;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.proyecto.moveon.R;
import com.proyecto.moveon.core.i18n.AppLanguageManager;
import com.proyecto.moveon.core.i18n.ProfileValueLocalizer;
import com.proyecto.moveon.core.settings.PaceDisplayUtils;
import com.proyecto.moveon.domain.activity.ActividadItem;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.FormatStyle;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Utilidades de formato para construir el contenido textual del flujo de compartir rutas.
 *
 * <p>Centraliza el formato visible de métricas y fechas para que la tarjeta compartida y
 * el texto adjunto mantengan el mismo criterio de localización que el resto de la app.</p>
 */
public final class ShareRouteFormatter {

    /**
     * Constructor privado: clase de utilidades con sólo métodos estáticos,
     * no está pensada para instanciarse.
     */
    private ShareRouteFormatter() {
        // Utility class
    }

    /**
     * Construye el texto resumen que acompaña a la imagen compartida.
     *
     * @param context contexto usado para resolver recursos localizados.
     * @return texto corto que acompaña la imagen en el flujo de compartir.
     */
    @NonNull
    public static String buildShareText(@NonNull Context context) {
        // Para el texto adjunto hemos simplificado el copy a una única frase fija,
        // tal como se pidió, sin concatenar métricas de la actividad.
        return context.getString(R.string.share_routes_share_text);
    }

    /**
     * Traduce el tipo canónico de actividad a su label visible en la UI.
     *
     * @param context contexto desde el que resolver traducciones y recursos.
     * @param item actividad cuyo tipo canónico debe mostrarse al usuario.
     * @return etiqueta localizada del tipo de actividad.
     */
    @NonNull
    public static String displayType(@NonNull Context context, @NonNull ActividadItem item) {
        return ProfileValueLocalizer.displayActivityType(context, item.tipo);
    }


    /**
     * Devuelve solo el valor numérico de la distancia en kilómetros, sin unidad,
     * para poder maquetarlo por separado en composiciones visuales tipo Strava.
     *
     * @param context contexto desde el que se obtiene el {@link Locale} activo.
     * @param meters distancia original expresada en metros.
     * @return distancia en kilómetros con dos decimales y sin sufijo de unidad.
     */
    @NonNull
    public static String formatDistanceNumber(@NonNull Context context, int meters) {
        Locale locale = AppLanguageManager.getActiveLocale(context);
        return String.format(locale, "%.2f", meters / 1000f);
    }

    /**
     * Formatea la distancia en kilómetros con dos decimales.
     *
     * @param context contexto desde el que se obtiene el {@link Locale} activo.
     * @param meters distancia original expresada en metros.
     * @return texto localizado con la distancia en kilómetros.
     */
    @NonNull
    public static String formatDistance(@NonNull Context context, int meters) {
        Locale locale = AppLanguageManager.getActiveLocale(context);
        return String.format(locale, "%.2f km", meters / 1000f);
    }

    /**
     * Formatea la duración total en formato breve y legible.
     *
     * @param context contexto desde el que se obtiene el {@link Locale} activo.
     * @param seconds duración total de la actividad en segundos.
     * @return duración resumida adaptada a horas, minutos o segundos.
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
     *
     * @param context contexto usado para resolver el placeholder cuando no hay ritmo válido.
     * @param secondsPerKm ritmo expresado en segundos por kilómetro.
     * @return ritmo en formato {@code m'ss"} o el texto vacío configurado en recursos.
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
     *
     * @param context contexto usado para resolver el placeholder del ritmo.
     * @param secondsPerKm ritmo expresado en segundos por kilómetro.
     * @return mismo resultado que {@link #formatPace(Context, int)} para mantener una API más explícita.
     */
    @NonNull
    public static String formatOptionalPace(@NonNull Context context, int secondsPerKm) {
        return formatPace(context, secondsPerKm);
    }

    /**
     * Resuelve el ritmo medio exclusivamente para la tarjeta de compartir.
     *
     * <p>Primero respeta el dato persistido y la preferencia del usuario. Si la actividad
     * llega sin ritmo total —el caso que provocaba N/D en la tarjeta— lo reconstruye con
     * la duración y la distancia de esa actividad sin cambiar el cálculo de otras pantallas.</p>
     */
    public static int resolveShareAveragePaceSeconds(
            @NonNull Context context,
            @NonNull ActividadItem item
    ) {
        int storedPace = PaceDisplayUtils.getPreferredAveragePaceSeconds(context, item);
        if (storedPace > 0) {
            return storedPace;
        }

        int durationSeconds = PaceDisplayUtils.shouldUseMovingPace(context)
                && item.duracionMovimientoSegundos > 0
                ? item.duracionMovimientoSegundos
                : item.duracionSegundos;
        if (durationSeconds <= 0 || item.distanciaMetros <= 0) {
            return 0;
        }

        double paceSeconds = (durationSeconds * 1000.0) / item.distanciaMetros;
        if (!Double.isFinite(paceSeconds) || paceSeconds <= 0.0 || paceSeconds > 3600.0) {
            return 0;
        }
        return (int) Math.round(paceSeconds);
    }

    /**
     * Formatea el ritmo como min/km incluyendo explícitamente la unidad final.
     *
     * @param context contexto usado para resolver el placeholder cuando no hay ritmo disponible.
     * @param secondsPerKm ritmo expresado en segundos por kilómetro.
     * @return ritmo con el sufijo {@code /km}, salvo cuando se devuelve el placeholder vacío.
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
     * Formatea el contador de pasos para historial y tarjeta compartida.
     */
    @NonNull
    public static String formatSteps(@NonNull Context context, @Nullable Integer steps) {
        if (steps == null) {
            return context.getString(R.string.share_routes_steps_empty);
        }
        return NumberFormat.getIntegerInstance(AppLanguageManager.getActiveLocale(context))
                .format(Math.max(0, steps));
    }

    /**
     * Formatea la fecha ISO intentando soportar tanto {@link LocalDate} como {@link OffsetDateTime}.
     *
     * <p>Cuando la entrada trae offset, primero se normaliza a la zona horaria local del
     * dispositivo. Así la fecha mostrada en la tarjeta compartida coincide con la usada por las
     * estadísticas y por el historial.</p>
     *
     * @param context contexto desde el que se obtiene el {@link Locale} activo.
     * @param isoDate fecha original en formato ISO, con o sin offset.
     * @return fecha formateada para la UI o, como último recurso, una versión truncada del valor original.
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
