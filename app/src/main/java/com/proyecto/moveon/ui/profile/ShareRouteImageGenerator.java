package com.proyecto.moveon.ui.profile;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;

import com.proyecto.moveon.R;
import com.proyecto.moveon.domain.activity.ActividadItem;
import com.proyecto.moveon.utils.StringUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Genera la imagen que se comparte por Android.
 *
 * <p>La imagen se dibuja completamente en un {@link Bitmap} usando la polilínea guardada en la
 * actividad. Así evitamos depender de un {@code GoogleMap} visible en pantalla, snapshots del mapa
 * o estados raros del ciclo de vida.</p>
 */
@SuppressWarnings("ClassCanBeRecord")
public final class ShareRouteImageGenerator {

    /**
     * Constructor privado: clase de utilidades con sólo métodos estáticos,
     * no está pensada para instanciarse.
     */
    private ShareRouteImageGenerator() {
        // Utility class.
    }

    /**
     * Genera un PNG temporal con diseño de tarjeta compartible y devuelve su {@link Uri} segura.
     *
     * @param context contexto de aplicación.
     * @param item    actividad que contiene métricas y polilínea.
     * @return uri segura vía {@link FileProvider}.
     * @throws IOException              si falla el guardado del archivo temporal.
     * @throws IllegalArgumentException si la actividad no tiene polilínea válida.
     */
    @NonNull
    public static Uri generateShareImage(@NonNull Context context,
                                         @NonNull ActividadItem item) throws IOException {
        if (!StringUtils.hasText(item.rutaPolilinea)) {
            throw new IllegalArgumentException("La actividad no tiene polilínea");
        }

        List<GeoPoint> points = decodePolyline(item.rutaPolilinea);
        if (points.isEmpty()) {
            throw new IllegalArgumentException("La polilínea está vacía");
        }

        Bitmap bitmap = createBitmap(context, item, points);
        File outputFile = saveBitmap(context, bitmap, item.localId);

        return FileProvider.getUriForFile(
                context,
                context.getPackageName() + ".fileprovider",
                outputFile
        );
    }

    /**
     * Crea la tarjeta visual con una jerarquía fuerte tipo app deportiva:
     * mapa grande arriba y métricas protagonistas abajo.
     *
     * @param context contexto desde el que resolver recursos y tamaños de diseño.
     * @param item actividad cuyas métricas se volcarán en la tarjeta.
     * @param points puntos ya decodificados de la polilínea para pintar la ruta.
     * @return bitmap final listo para persistirse y compartirse.
     */
    @NonNull
    private static Bitmap createBitmap(@NonNull Context context,
                                       @NonNull ActividadItem item,
                                       @NonNull List<GeoPoint> points) {
        final int width = 1200;
        final int height = 1700;

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        // Fondo claro y limpio para que la tarjeta se vea bien al compartirse en apps de mensajería.
        canvas.drawColor(Color.parseColor("#F4F5F7"));

        Paint pageShadowPaint = fillPaint(Color.parseColor("#E3E6EB"));
        Paint pageCardPaint = fillPaint(Color.WHITE);
        Paint pageBorderPaint = strokePaint(Color.parseColor("#E5E7EB"), 1f);
        Paint brandPaint = textPaint(Color.parseColor("#111111"), 82f);
        Paint sectionTitlePaint = textPaint(Color.parseColor("#111111"), 60f);
        Paint darkChipTextPaint = textPaint(Color.WHITE, 34f);
        Paint greenChipTextPaint = textPaint(Color.parseColor("#111111"), 34f);
        Paint legendPaint = textPaint(Color.parseColor("#4B5563"), 28f);
        Paint metricHeroLabelPaint = textPaint(Color.parseColor("#6B7280"), 30f);
        Paint metricHeroValuePaint = textPaint(Color.parseColor("#111111"), 112f);
        Paint metricHeroUnitPaint = textPaint(Color.parseColor("#374151"), 42f);
        Paint metricCardLabelPaint = textPaint(Color.parseColor("#6B7280"), 28f);
        Paint metricCardValuePaint = textPaint(Color.parseColor("#111111"), 68f);
        Paint metricCardPaint = fillPaint(Color.parseColor("#FAFAFA"));
        Paint metricCardBorderPaint = strokePaint(Color.parseColor("#E5E7EB"), 1f);
        Paint darkChipPaint = fillPaint(Color.parseColor("#111111"));
        Paint greenChipPaint = fillPaint(Color.parseColor("#3AB368"));
        Paint mapCardPaint = fillPaint(Color.parseColor("#F9FAFB"));
        Paint mapAreaPaint = fillPaint(Color.parseColor("#FCFCFD"));
        Paint gridPaint = strokePaint(Color.parseColor("#E5E7EB"), 0.7f);
        gridPaint.setAlpha(60);
        Paint routeShadowPaint = strokePaint(Color.parseColor("#BFE7CF"), 26f);
        Paint routePaint = strokePaint(Color.parseColor("#3AB368"), 18f);
        Paint startMarkerPaint = fillPaint(Color.parseColor("#0F172A"));
        Paint endMarkerPaint = fillPaint(Color.parseColor("#DC2626"));
        Paint dividerPaint = strokePaint(Color.parseColor("#ECEFF3"), 1f);

        routeShadowPaint.setStrokeCap(Paint.Cap.ROUND);
        routeShadowPaint.setStrokeJoin(Paint.Join.ROUND);
        routeShadowPaint.setPathEffect(new CornerPathEffect(dp(14)));
        routePaint.setStrokeCap(Paint.Cap.ROUND);
        routePaint.setStrokeJoin(Paint.Join.ROUND);
        routePaint.setPathEffect(new CornerPathEffect(dp(12)));

        // Sombra suave de la tarjeta principal.
        RectF shadowRect = new RectF(dp(34), dp(40), width - dp(26), height - dp(26));
        canvas.drawRoundRect(shadowRect, dp(30), dp(30), pageShadowPaint);

        // Tarjeta principal.
        RectF pageRect = new RectF(dp(24), dp(24), width - dp(24), height - dp(34));
        canvas.drawRoundRect(pageRect, dp(30), dp(30), pageCardPaint);
        canvas.drawRoundRect(pageRect, dp(30), dp(30), pageBorderPaint);

        // Marca principal.
        canvas.drawText("MoveOn", dp(58), dp(114), brandPaint);

        // Chips más deportivos y muy legibles.
        float chipTop = dp(150);
        float chipLeft = dp(58);
        chipLeft += drawFilledChip(canvas, chipLeft, chipTop,
                ShareRouteFormatter.displayType(context, item).toUpperCase(Locale.getDefault()),
                darkChipPaint, darkChipTextPaint);
        chipLeft += dp(16);
        drawFilledChip(canvas, chipLeft, chipTop,
                ShareRouteFormatter.formatDate(context, item.fechaRutaIso),
                greenChipPaint, greenChipTextPaint);

        // Tarjeta grande del mapa.
        RectF mapCard = new RectF(dp(58), dp(248), width - dp(58), dp(972));
        canvas.drawRoundRect(mapCard, dp(28), dp(28), mapCardPaint);
        canvas.drawRoundRect(mapCard, dp(28), dp(28), pageBorderPaint);

        canvas.drawText(context.getString(R.string.share_routes_image_map_title), mapCard.left + dp(32), mapCard.top + dp(64), sectionTitlePaint);

        RectF legendArea = new RectF(
                mapCard.left + dp(32),
                mapCard.top + dp(84),
                mapCard.right - dp(32),
                mapCard.top + dp(144)
        );
        drawLegend(canvas, legendArea, startMarkerPaint, endMarkerPaint, legendPaint, context);

        RectF mapArea = new RectF(
                mapCard.left + dp(28),
                mapCard.top + dp(154),
                mapCard.right - dp(28),
                mapCard.bottom - dp(28)
        );
        canvas.drawRoundRect(mapArea, dp(22), dp(22), mapAreaPaint);
        drawMapGrid(canvas, mapArea, gridPaint);
        drawRoute(canvas, mapArea, points, routeShadowPaint, routePaint, startMarkerPaint, endMarkerPaint);

        // Resumen con un bloque hero protagonista, muy deportivo.
        canvas.drawText(context.getString(R.string.share_routes_image_summary_title), dp(58), dp(1060), sectionTitlePaint);

        // Distancia principal.
        float heroTop = dp(1110);
        float heroLeft = dp(58);
        canvas.drawText(context.getString(R.string.share_routes_metric_distance).toUpperCase(Locale.getDefault()), heroLeft, heroTop, metricHeroLabelPaint);
        float distanceBaseline = heroTop + dp(126);
        String distanceValue = ShareRouteFormatter.formatDistanceNumber(context, item.distanciaMetros);
        String distanceUnit = "km";
        canvas.drawText(distanceValue, heroLeft, distanceBaseline, metricHeroValuePaint);
        float distanceWidth = metricHeroValuePaint.measureText(distanceValue);
        canvas.drawText(distanceUnit, heroLeft + distanceWidth + dp(16), distanceBaseline - dp(12), metricHeroUnitPaint);

        // Duración principal a la derecha.
        float durationLeft = width / 2f + dp(36);
        canvas.drawText(context.getString(R.string.share_routes_metric_duration).toUpperCase(Locale.getDefault()), durationLeft, heroTop, metricHeroLabelPaint);
        drawAdaptiveText(
                canvas,
                ShareRouteFormatter.formatDuration(context, item.duracionSegundos),
                durationLeft,
                distanceBaseline,
                metricHeroValuePaint,
                dp(430),
                112f,
                72f);

        // Línea divisoria suave entre héroe y métricas secundarias.
        float dividerY = dp(1292);
        canvas.drawLine(dp(58), dividerY, width - dp(58), dividerY, dividerPaint);

        // Tarjetas secundarias compactas: calorías, pasos y los dos ritmos.
        float cardTop = dp(1338);
        float cardGap = dp(18);
        float horizontalPadding = dp(58);
        float cardWidth = (width - horizontalPadding - horizontalPadding - (cardGap * 3f)) / 4f;
        float cardHeight = dp(250);
        float secondCardX = horizontalPadding + cardWidth + cardGap;
        float thirdCardX = secondCardX + cardWidth + cardGap;
        float fourthCardX = thirdCardX + cardWidth + cardGap;

        drawMetricCard(canvas,
                new RectF(horizontalPadding, cardTop, horizontalPadding + cardWidth, cardTop + cardHeight),
                context.getString(R.string.share_routes_metric_calories).toUpperCase(Locale.getDefault()),
                context.getString(R.string.share_routes_kcal_value, item.caloriasQuemadas),
                metricCardPaint, metricCardBorderPaint, metricCardLabelPaint, metricCardValuePaint);

        drawMetricCard(canvas,
                new RectF(secondCardX, cardTop, secondCardX + cardWidth, cardTop + cardHeight),
                context.getString(R.string.share_routes_metric_steps).toUpperCase(Locale.getDefault()),
                ShareRouteFormatter.formatSteps(context, item.pasos),
                metricCardPaint, metricCardBorderPaint, metricCardLabelPaint, metricCardValuePaint);

        drawMetricCard(canvas,
                new RectF(thirdCardX, cardTop, thirdCardX + cardWidth, cardTop + cardHeight),
                context.getString(R.string.share_routes_metric_average_pace).toUpperCase(Locale.getDefault()),
                ShareRouteFormatter.formatPaceWithUnit(
                        context,
                        ShareRouteFormatter.resolveShareAveragePaceSeconds(context, item)
                ),
                metricCardPaint, metricCardBorderPaint, metricCardLabelPaint, metricCardValuePaint);

        drawMetricCard(canvas,
                new RectF(fourthCardX, cardTop, fourthCardX + cardWidth, cardTop + cardHeight),
                context.getString(R.string.share_routes_metric_max_pace).toUpperCase(Locale.getDefault()),
                ShareRouteFormatter.formatPaceWithUnit(context, item.ritmoMaximoSegKm),
                metricCardPaint, metricCardBorderPaint, metricCardLabelPaint, metricCardValuePaint);

        return bitmap;
    }

    /**
     * Dibuja una tarjeta de métrica secundaria del bloque resumen.
     *
     * @param canvas canvas de destino sobre el que se pinta la tarjeta.
     * @param rect rectángulo que delimita la tarjeta.
     * @param label etiqueta superior de la métrica.
     * @param value valor principal a destacar.
     * @param fillPaint paint de fondo de la tarjeta.
     * @param borderPaint paint del borde exterior.
     * @param labelPaint paint usado para la etiqueta.
     * @param valuePaint paint base del valor, que luego puede adaptarse al ancho.
     */
    private static void drawMetricCard(@NonNull Canvas canvas,
                                       @NonNull RectF rect,
                                       @NonNull String label,
                                       @NonNull String value,
                                       @NonNull Paint fillPaint,
                                       @NonNull Paint borderPaint,
                                       @NonNull Paint labelPaint,
                                       @NonNull Paint valuePaint) {
        canvas.drawRoundRect(rect, dp(24), dp(24), fillPaint);
        canvas.drawRoundRect(rect, dp(24), dp(24), borderPaint);
        canvas.drawText(label, rect.left + dp(26), rect.top + dp(58), labelPaint);
        drawAdaptiveText(
                canvas,
                value,
                rect.left + dp(26),
                rect.top + dp(160),
                valuePaint,
                rect.width() - dp(52),
                68f,
                46f);
    }

    /**
     * Dibuja un chip relleno y devuelve el ancho usado para poder encadenar chips.
     *
     * @param canvas canvas de destino.
     * @param left coordenada izquierda del chip.
     * @param top coordenada superior del chip.
     * @param text texto mostrado dentro del chip.
     * @param fillPaint paint del fondo del chip.
     * @param textPaint paint del texto del chip.
     * @return ancho total ocupado por el chip dibujado.
     */
    private static float drawFilledChip(@NonNull Canvas canvas,
                                        float left,
                                        float top,
                                        @NonNull String text,
                                        @NonNull Paint fillPaint,
                                        @NonNull Paint textPaint) {
        float horizontalPadding = dp(22);
        float chipHeight = dp(72);
        float width = textPaint.measureText(text) + (horizontalPadding * 2f);
        RectF rect = new RectF(left, top, left + width, top + chipHeight);
        canvas.drawRoundRect(rect, dp(18), dp(18), fillPaint);
        canvas.drawText(text, rect.left + horizontalPadding, rect.centerY() + dp(11), textPaint);
        return width;
    }

    /**
     * Dibuja una cuadrícula muy sutil para dar textura sin robar protagonismo a la ruta.
     *
     * @param canvas canvas de destino.
     * @param area rectángulo del mapa sobre el que debe recortarse la cuadrícula.
     * @param gridPaint paint usado para las líneas guía.
     */
    private static void drawMapGrid(@NonNull Canvas canvas,
                                    @NonNull RectF area,
                                    @NonNull Paint gridPaint) {
        float step = dp(104);

        canvas.save();
        canvas.clipRect(area);
        for (float x = area.left; x <= area.right; x += step) {
            canvas.drawLine(x, area.top, x, area.bottom, gridPaint);
        }
        for (float y = area.top; y <= area.bottom; y += step) {
            canvas.drawLine(area.left, y, area.right, y, gridPaint);
        }
        canvas.restore();
    }

    /**
     * Dibuja una pequeña leyenda de inicio y fin sobre el mapa.
     *
     * @param canvas canvas de destino.
     * @param area zona reservada para la leyenda dentro de la tarjeta del mapa.
     * @param startPaint paint del marcador de inicio.
     * @param endPaint paint del marcador de fin.
     * @param textPaint paint usado para las etiquetas de texto.
     * @param context contexto usado para resolver las etiquetas localizadas.
     */
    private static void drawLegend(@NonNull Canvas canvas,
                                   @NonNull RectF area,
                                   @NonNull Paint startPaint,
                                   @NonNull Paint endPaint,
                                   @NonNull Paint textPaint,
                                   @NonNull Context context) {
        float y = area.centerY();
        float startX = area.left + dp(18);
        float endX = startX + dp(210);
        float radius = dp(10);

        canvas.drawCircle(startX, y, radius, startPaint);
        canvas.drawText(context.getString(R.string.share_routes_legend_start).toUpperCase(Locale.getDefault()), startX + dp(18), y + dp(10), textPaint);

        canvas.drawCircle(endX, y, radius, endPaint);
        canvas.drawText(context.getString(R.string.share_routes_legend_end).toUpperCase(Locale.getDefault()), endX + dp(18), y + dp(10), textPaint);
    }

    /**
     * Dibuja la ruta escalada dentro del rectángulo disponible.
     *
     * <p>Se añade un margen de seguridad alrededor del bounding box original para evitar que la
     * ruta o sus marcadores queden pegados al borde o recortados.</p>
     *
     * @param canvas canvas de destino.
     * @param area área disponible para el mapa.
     * @param points puntos geográficos ya decodificados.
     * @param routeShadowPaint paint de la sombra ancha de la ruta.
     * @param routePaint paint principal de la ruta.
     * @param startMarkerPaint paint del marcador de inicio.
     * @param endMarkerPaint paint del marcador de fin.
     */
    private static void drawRoute(@NonNull Canvas canvas,
                                  @NonNull RectF area,
                                  @NonNull List<GeoPoint> points,
                                  @NonNull Paint routeShadowPaint,
                                  @NonNull Paint routePaint,
                                  @NonNull Paint startMarkerPaint,
                                  @NonNull Paint endMarkerPaint) {
        if (points.isEmpty()) return;

        double minLat = Double.MAX_VALUE;
        double maxLat = -Double.MAX_VALUE;
        double minLng = Double.MAX_VALUE;
        double maxLng = -Double.MAX_VALUE;

        for (GeoPoint point : points) {
            minLat = Math.min(minLat, point.lat);
            maxLat = Math.max(maxLat, point.lat);
            minLng = Math.min(minLng, point.lng);
            maxLng = Math.max(maxLng, point.lng);
        }

        double latSpan = Math.max(0.00001d, maxLat - minLat);
        double lngSpan = Math.max(0.00001d, maxLng - minLng);

        double latPadding = Math.max(latSpan * 0.16d, 0.00001d);
        double lngPadding = Math.max(lngSpan * 0.16d, 0.00001d);

        double paddedMinLat = minLat - latPadding;
        double paddedMaxLat = maxLat + latPadding;
        double paddedMinLng = minLng - lngPadding;
        double paddedMaxLng = maxLng + lngPadding;

        double paddedLatSpan = paddedMaxLat - paddedMinLat;
        double paddedLngSpan = paddedMaxLng - paddedMinLng;

        float innerPadding = dp(30);
        float markerRadius = dp(15);
        float usableWidth = area.width() - ((innerPadding + markerRadius) * 2f);
        float usableHeight = area.height() - ((innerPadding + markerRadius) * 2f);

        double scaleX = usableWidth / paddedLngSpan;
        double scaleY = usableHeight / paddedLatSpan;
        double scale = Math.min(scaleX, scaleY);

        float renderedWidth = (float) (paddedLngSpan * scale);
        float renderedHeight = (float) (paddedLatSpan * scale);
        float offsetX = area.left + innerPadding + markerRadius + ((usableWidth - renderedWidth) / 2f);
        float offsetY = area.top + innerPadding + markerRadius + ((usableHeight - renderedHeight) / 2f);

        Path path = new Path();
        float firstX = 0f;
        float firstY = 0f;
        float lastX = 0f;
        float lastY = 0f;

        for (int i = 0; i < points.size(); i++) {
            GeoPoint point = points.get(i);
            float x = (float) (offsetX + ((point.lng - paddedMinLng) * scale));
            float y = (float) (offsetY + ((paddedMaxLat - point.lat) * scale));

            if (i == 0) {
                firstX = x;
                firstY = y;
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }

            lastX = x;
            lastY = y;
        }

        canvas.save();
        canvas.clipRect(area);
        canvas.drawPath(path, routeShadowPaint);
        canvas.drawPath(path, routePaint);
        canvas.drawCircle(firstX, firstY, markerRadius, startMarkerPaint);
        canvas.drawCircle(lastX, lastY, markerRadius, endMarkerPaint);
        canvas.restore();
    }

    /**
     * Dibuja texto adaptándolo al ancho disponible para evitar que valores largos se corten.
     *
     * @param canvas canvas de destino.
     * @param text texto a dibujar.
     * @param x coordenada horizontal de inicio.
     * @param baseline línea base del texto.
     * @param basePaint paint base desde el que se clonará estilo y color.
     * @param maxWidth ancho máximo permitido antes de reducir tipografía.
     * @param startSp tamaño inicial probado.
     * @param minSp tamaño mínimo aceptable.
     */
    private static void drawAdaptiveText(@NonNull Canvas canvas,
                                         @NonNull String text,
                                         float x,
                                         float baseline,
                                         @NonNull Paint basePaint,
                                         float maxWidth,
                                         float startSp,
                                         float minSp) {
        Paint paint = new Paint(basePaint);
        float size = startSp;
        paint.setTextSize(sp(size));

        while (paint.measureText(text) > maxWidth && size > minSp) {
            size -= 2f;
            paint.setTextSize(sp(size));
        }

        canvas.drawText(text, x, baseline, paint);
    }

    /**
     * Guarda el bitmap en caché para compartirlo como archivo temporal.
     *
     * @param context contexto desde el que resolver el directorio de caché.
     * @param bitmap imagen ya renderizada.
     * @param localId identificador local usado para nombrar el archivo.
     * @return archivo PNG temporal listo para exponerse vía {@link FileProvider}.
     * @throws IOException si no puede crearse el directorio o escribirse el PNG.
     */
    @NonNull
    private static File saveBitmap(@NonNull Context context,
                                   @NonNull Bitmap bitmap,
                                   @NonNull String localId) throws IOException {
        File dir = new File(context.getCacheDir(), "shared_routes");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("No se pudo crear el directorio de cache compartido");
        }

        File file = new File(dir, "route_" + localId + "_" + System.currentTimeMillis() + ".png");
        try (FileOutputStream out = new FileOutputStream(file)) {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                throw new IOException("No se pudo comprimir el bitmap");
            }
            out.flush();
        }

        return file;
    }

    /**
     * Decodifica una polyline codificada por Google a una lista de puntos.
     *
     * @param encoded polilínea codificada recibida desde la actividad.
     * @return lista ordenada de puntos geográficos lista para dibujarse.
     * @throws IllegalArgumentException si la cadena viene malformada o truncada.
     */
    @NonNull
    private static List<GeoPoint> decodePolyline(@NonNull String encoded) {
        List<GeoPoint> poly = new ArrayList<>();
        int index = 0;
        int lat = 0;
        int lng = 0;

        try {
            while (index < encoded.length()) {
                int result = 0;
                int shift = 0;
                int b;
                do {
                    if (index >= encoded.length()) {
                        throw new IllegalArgumentException("Polilínea malformada");
                    }
                    b = encoded.charAt(index++) - 63;
                    result |= (b & 0x1f) << shift;
                    shift += 5;
                } while (b >= 0x20);
                int deltaLatitude = ((result & 1) != 0) ? ~(result >> 1) : (result >> 1);
                lat += deltaLatitude;

                result = 0;
                shift = 0;
                do {
                    if (index >= encoded.length()) {
                        throw new IllegalArgumentException("Polilínea malformada");
                    }
                    b = encoded.charAt(index++) - 63;
                    result |= (b & 0x1f) << shift;
                    shift += 5;
                } while (b >= 0x20);
                int deltaLongitude = ((result & 1) != 0) ? ~(result >> 1) : (result >> 1);
                lng += deltaLongitude;

                poly.add(new GeoPoint(lat / 1E5, lng / 1E5));
            }
        } catch (IndexOutOfBoundsException e) {
            throw new IllegalArgumentException("Polilínea malformada", e);
        }

        return poly;
    }

    /**
     * Crea un {@link Paint} de texto reutilizable.
     *
     * @param color color del texto.
     * @param sp tamaño base en unidades de diseño.
     * @return paint configurado para dibujar texto.
     */
    @NonNull
    private static Paint textPaint(int color, float sp) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        paint.setTextSize(sp(sp));
        paint.setFakeBoldText(true);
        return paint;
    }

    /**
     * Crea un {@link Paint} de relleno.
     *
     * @param color color sólido del relleno.
     * @return paint configurado en modo {@link Paint.Style#FILL}.
     */
    @NonNull
    private static Paint fillPaint(int color) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        return paint;
    }

    /**
     * Crea un {@link Paint} de trazo.
     *
     * @param color color del trazo.
     * @param dpWidth grosor base en unidades de diseño.
     * @return paint configurado en modo {@link Paint.Style#STROKE}.
     */
    @NonNull
    private static Paint strokePaint(int color, float dpWidth) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(dpWidth));
        paint.setColor(color);
        return paint;
    }

    /**
     * Convierte unidades de diseño a píxeles del bitmap.
     *
     * <p>Esta imagen se dibuja sobre un canvas de tamaño fijo, así que aquí no usamos la densidad
     * real del dispositivo. De este modo evitamos que el contenido se salga del bitmap.</p>
     *
     * @param value valor de diseño a trasladar al bitmap.
     * @return valor redondeado en píxeles del canvas fijo.
     */
    private static int dp(float value) {
        return Math.round(value);
    }

    /**
     * Convierte unidades tipográficas de diseño a píxeles del bitmap.
     *
     * @param value tamaño tipográfico de diseño.
     * @return tamaño final en píxeles del bitmap.
     */
    private static float sp(float value) {
        return value;
    }

    /**
     * Punto geográfico simple usado solo para pintar la ruta.
     */
    private static final class GeoPoint {
        final double lat;
        final double lng;

        GeoPoint(double lat, double lng) {
            this.lat = lat;
            this.lng = lng;
        }
    }
}
