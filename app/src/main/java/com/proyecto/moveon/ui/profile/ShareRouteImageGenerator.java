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
        Paint pageBorderPaint = strokePaint(context, Color.parseColor("#E5E7EB"), 1f);
        Paint brandPaint = textPaint(context, Color.parseColor("#111111"), 82f, true);
        Paint sectionTitlePaint = textPaint(context, Color.parseColor("#111111"), 60f, true);
        Paint darkChipTextPaint = textPaint(context, Color.WHITE, 34f, true);
        Paint greenChipTextPaint = textPaint(context, Color.parseColor("#111111"), 34f, true);
        Paint legendPaint = textPaint(context, Color.parseColor("#4B5563"), 28f, true);
        Paint metricHeroLabelPaint = textPaint(context, Color.parseColor("#6B7280"), 30f, true);
        Paint metricHeroValuePaint = textPaint(context, Color.parseColor("#111111"), 112f, true);
        Paint metricHeroUnitPaint = textPaint(context, Color.parseColor("#374151"), 42f, true);
        Paint metricCardLabelPaint = textPaint(context, Color.parseColor("#6B7280"), 28f, true);
        Paint metricCardValuePaint = textPaint(context, Color.parseColor("#111111"), 68f, true);
        Paint metricCardPaint = fillPaint(Color.parseColor("#FAFAFA"));
        Paint metricCardBorderPaint = strokePaint(context, Color.parseColor("#E5E7EB"), 1f);
        Paint darkChipPaint = fillPaint(Color.parseColor("#111111"));
        Paint greenChipPaint = fillPaint(Color.parseColor("#3AB368"));
        Paint mapCardPaint = fillPaint(Color.parseColor("#F9FAFB"));
        Paint mapAreaPaint = fillPaint(Color.parseColor("#FCFCFD"));
        Paint gridPaint = strokePaint(context, Color.parseColor("#E5E7EB"), 0.7f);
        gridPaint.setAlpha(60);
        Paint routeShadowPaint = strokePaint(context, Color.parseColor("#BFE7CF"), 26f);
        Paint routePaint = strokePaint(context, Color.parseColor("#3AB368"), 18f);
        Paint startMarkerPaint = fillPaint(Color.parseColor("#0F172A"));
        Paint endMarkerPaint = fillPaint(Color.parseColor("#DC2626"));
        Paint dividerPaint = strokePaint(context, Color.parseColor("#ECEFF3"), 1f);

        routeShadowPaint.setStrokeCap(Paint.Cap.ROUND);
        routeShadowPaint.setStrokeJoin(Paint.Join.ROUND);
        routeShadowPaint.setPathEffect(new CornerPathEffect(dp(context, 14)));
        routePaint.setStrokeCap(Paint.Cap.ROUND);
        routePaint.setStrokeJoin(Paint.Join.ROUND);
        routePaint.setPathEffect(new CornerPathEffect(dp(context, 12)));

        // Sombra suave de la tarjeta principal.
        RectF shadowRect = new RectF(dp(context, 34), dp(context, 40), width - dp(context, 26), height - dp(context, 26));
        canvas.drawRoundRect(shadowRect, dp(context, 30), dp(context, 30), pageShadowPaint);

        // Tarjeta principal.
        RectF pageRect = new RectF(dp(context, 24), dp(context, 24), width - dp(context, 24), height - dp(context, 34));
        canvas.drawRoundRect(pageRect, dp(context, 30), dp(context, 30), pageCardPaint);
        canvas.drawRoundRect(pageRect, dp(context, 30), dp(context, 30), pageBorderPaint);

        // Marca principal.
        canvas.drawText("MoveOn", dp(context, 58), dp(context, 114), brandPaint);

        // Chips más deportivos y muy legibles.
        float chipTop = dp(context, 150);
        float chipLeft = dp(context, 58);
        chipLeft += drawFilledChip(canvas, chipLeft, chipTop,
                ShareRouteFormatter.displayType(context, item).toUpperCase(Locale.getDefault()),
                darkChipPaint, darkChipTextPaint, context);
        chipLeft += dp(context, 16);
        drawFilledChip(canvas, chipLeft, chipTop,
                ShareRouteFormatter.formatDate(context, item.fechaRutaIso),
                greenChipPaint, greenChipTextPaint, context);

        // Tarjeta grande del mapa.
        RectF mapCard = new RectF(dp(context, 58), dp(context, 248), width - dp(context, 58), dp(context, 972));
        canvas.drawRoundRect(mapCard, dp(context, 28), dp(context, 28), mapCardPaint);
        canvas.drawRoundRect(mapCard, dp(context, 28), dp(context, 28), pageBorderPaint);

        canvas.drawText(context.getString(R.string.share_routes_image_map_title), mapCard.left + dp(context, 32), mapCard.top + dp(context, 64), sectionTitlePaint);

        RectF legendArea = new RectF(
                mapCard.left + dp(context, 32),
                mapCard.top + dp(context, 84),
                mapCard.right - dp(context, 32),
                mapCard.top + dp(context, 144)
        );
        drawLegend(canvas, legendArea, startMarkerPaint, endMarkerPaint, legendPaint, context);

        RectF mapArea = new RectF(
                mapCard.left + dp(context, 28),
                mapCard.top + dp(context, 154),
                mapCard.right - dp(context, 28),
                mapCard.bottom - dp(context, 28)
        );
        canvas.drawRoundRect(mapArea, dp(context, 22), dp(context, 22), mapAreaPaint);
        drawMapGrid(canvas, mapArea, gridPaint, context);
        drawRoute(canvas, mapArea, points, routeShadowPaint, routePaint, startMarkerPaint, endMarkerPaint, context);

        // Resumen con un bloque hero protagonista, muy deportivo.
        canvas.drawText(context.getString(R.string.share_routes_image_summary_title), dp(context, 58), dp(context, 1060), sectionTitlePaint);

        // Distancia principal.
        float heroTop = dp(context, 1110);
        float heroLeft = dp(context, 58);
        canvas.drawText(context.getString(R.string.share_routes_metric_distance).toUpperCase(Locale.getDefault()), heroLeft, heroTop, metricHeroLabelPaint);
        float distanceBaseline = heroTop + dp(context, 126);
        String distanceValue = ShareRouteFormatter.formatDistanceNumber(context, item.distanciaMetros);
        String distanceUnit = "km";
        canvas.drawText(distanceValue, heroLeft, distanceBaseline, metricHeroValuePaint);
        float distanceWidth = metricHeroValuePaint.measureText(distanceValue);
        canvas.drawText(distanceUnit, heroLeft + distanceWidth + dp(context, 16), distanceBaseline - dp(context, 12), metricHeroUnitPaint);

        // Duración principal a la derecha.
        float durationLeft = width / 2f + dp(context, 36);
        canvas.drawText(context.getString(R.string.share_routes_metric_duration).toUpperCase(Locale.getDefault()), durationLeft, heroTop, metricHeroLabelPaint);
        drawAdaptiveText(
                canvas,
                ShareRouteFormatter.formatDuration(context, item.duracionSegundos),
                durationLeft,
                distanceBaseline,
                metricHeroValuePaint,
                dp(context, 430),
                112f,
                72f,
                context
        );

        // Línea divisoria suave entre héroe y métricas secundarias.
        float dividerY = dp(context, 1292);
        canvas.drawLine(dp(context, 58), dividerY, width - dp(context, 58), dividerY, dividerPaint);

        // Tarjetas secundarias compactas: calorías, pasos y los dos ritmos.
        float cardTop = dp(context, 1338);
        float cardGap = dp(context, 18);
        float horizontalPadding = dp(context, 58);
        float cardWidth = (width - horizontalPadding - horizontalPadding - (cardGap * 3f)) / 4f;
        float cardHeight = dp(context, 250);
        float firstCardX = horizontalPadding;
        float secondCardX = firstCardX + cardWidth + cardGap;
        float thirdCardX = secondCardX + cardWidth + cardGap;
        float fourthCardX = thirdCardX + cardWidth + cardGap;

        drawMetricCard(canvas,
                new RectF(firstCardX, cardTop, firstCardX + cardWidth, cardTop + cardHeight),
                context.getString(R.string.share_routes_metric_calories).toUpperCase(Locale.getDefault()),
                context.getString(R.string.share_routes_kcal_value, item.caloriasQuemadas),
                metricCardPaint, metricCardBorderPaint, metricCardLabelPaint, metricCardValuePaint, context);

        drawMetricCard(canvas,
                new RectF(secondCardX, cardTop, secondCardX + cardWidth, cardTop + cardHeight),
                context.getString(R.string.share_routes_metric_steps).toUpperCase(Locale.getDefault()),
                ShareRouteFormatter.formatSteps(context, item.pasos),
                metricCardPaint, metricCardBorderPaint, metricCardLabelPaint, metricCardValuePaint, context);

        drawMetricCard(canvas,
                new RectF(thirdCardX, cardTop, thirdCardX + cardWidth, cardTop + cardHeight),
                context.getString(R.string.share_routes_metric_average_pace).toUpperCase(Locale.getDefault()),
                ShareRouteFormatter.formatPaceWithUnit(
                        context,
                        ShareRouteFormatter.resolveShareAveragePaceSeconds(context, item)
                ),
                metricCardPaint, metricCardBorderPaint, metricCardLabelPaint, metricCardValuePaint, context);

        drawMetricCard(canvas,
                new RectF(fourthCardX, cardTop, fourthCardX + cardWidth, cardTop + cardHeight),
                context.getString(R.string.share_routes_metric_max_pace).toUpperCase(Locale.getDefault()),
                ShareRouteFormatter.formatPaceWithUnit(context, item.ritmoMaximoSegKm),
                metricCardPaint, metricCardBorderPaint, metricCardLabelPaint, metricCardValuePaint, context);

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
     * @param context contexto usado para convertir medidas de diseño.
     */
    private static void drawMetricCard(@NonNull Canvas canvas,
                                       @NonNull RectF rect,
                                       @NonNull String label,
                                       @NonNull String value,
                                       @NonNull Paint fillPaint,
                                       @NonNull Paint borderPaint,
                                       @NonNull Paint labelPaint,
                                       @NonNull Paint valuePaint,
                                       @NonNull Context context) {
        canvas.drawRoundRect(rect, dp(context, 24), dp(context, 24), fillPaint);
        canvas.drawRoundRect(rect, dp(context, 24), dp(context, 24), borderPaint);
        canvas.drawText(label, rect.left + dp(context, 26), rect.top + dp(context, 58), labelPaint);
        drawAdaptiveText(
                canvas,
                value,
                rect.left + dp(context, 26),
                rect.top + dp(context, 160),
                valuePaint,
                rect.width() - dp(context, 52),
                68f,
                46f,
                context
        );
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
     * @param context contexto usado para convertir medidas de diseño.
     * @return ancho total ocupado por el chip dibujado.
     */
    private static float drawFilledChip(@NonNull Canvas canvas,
                                        float left,
                                        float top,
                                        @NonNull String text,
                                        @NonNull Paint fillPaint,
                                        @NonNull Paint textPaint,
                                        @NonNull Context context) {
        float horizontalPadding = dp(context, 22);
        float chipHeight = dp(context, 72);
        float width = textPaint.measureText(text) + (horizontalPadding * 2f);
        RectF rect = new RectF(left, top, left + width, top + chipHeight);
        canvas.drawRoundRect(rect, dp(context, 18), dp(context, 18), fillPaint);
        canvas.drawText(text, rect.left + horizontalPadding, rect.centerY() + dp(context, 11), textPaint);
        return width;
    }

    /**
     * Dibuja una cuadrícula muy sutil para dar textura sin robar protagonismo a la ruta.
     *
     * @param canvas canvas de destino.
     * @param area rectángulo del mapa sobre el que debe recortarse la cuadrícula.
     * @param gridPaint paint usado para las líneas guía.
     * @param context contexto usado para convertir medidas de diseño.
     */
    private static void drawMapGrid(@NonNull Canvas canvas,
                                    @NonNull RectF area,
                                    @NonNull Paint gridPaint,
                                    @NonNull Context context) {
        float step = dp(context, 104);

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
     * @param context contexto usado para recursos y medidas.
     */
    private static void drawLegend(@NonNull Canvas canvas,
                                   @NonNull RectF area,
                                   @NonNull Paint startPaint,
                                   @NonNull Paint endPaint,
                                   @NonNull Paint textPaint,
                                   @NonNull Context context) {
        float y = area.centerY();
        float startX = area.left + dp(context, 18);
        float endX = startX + dp(context, 210);
        float radius = dp(context, 10);

        canvas.drawCircle(startX, y, radius, startPaint);
        canvas.drawText(context.getString(R.string.share_routes_legend_start).toUpperCase(Locale.getDefault()), startX + dp(context, 18), y + dp(context, 10), textPaint);

        canvas.drawCircle(endX, y, radius, endPaint);
        canvas.drawText(context.getString(R.string.share_routes_legend_end).toUpperCase(Locale.getDefault()), endX + dp(context, 18), y + dp(context, 10), textPaint);
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
     * @param context contexto usado para convertir medidas de diseño.
     */
    private static void drawRoute(@NonNull Canvas canvas,
                                  @NonNull RectF area,
                                  @NonNull List<GeoPoint> points,
                                  @NonNull Paint routeShadowPaint,
                                  @NonNull Paint routePaint,
                                  @NonNull Paint startMarkerPaint,
                                  @NonNull Paint endMarkerPaint,
                                  @NonNull Context context) {
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

        float innerPadding = dp(context, 30);
        float markerRadius = dp(context, 15);
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
     * @param context contexto usado para convertir tamaños tipográficos.
     */
    private static void drawAdaptiveText(@NonNull Canvas canvas,
                                         @NonNull String text,
                                         float x,
                                         float baseline,
                                         @NonNull Paint basePaint,
                                         float maxWidth,
                                         float startSp,
                                         float minSp,
                                         @NonNull Context context) {
        Paint paint = new Paint(basePaint);
        float size = startSp;
        paint.setTextSize(sp(context, size));

        while (paint.measureText(text) > maxWidth && size > minSp) {
            size -= 2f;
            paint.setTextSize(sp(context, size));
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
                int dlat = ((result & 1) != 0) ? ~(result >> 1) : (result >> 1);
                lat += dlat;

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
                int dlng = ((result & 1) != 0) ? ~(result >> 1) : (result >> 1);
                lng += dlng;

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
     * @param context contexto usado para convertir el tamaño tipográfico.
     * @param color color del texto.
     * @param sp tamaño base en unidades de diseño.
     * @param bold {@code true} para aplicar falso negrita.
     * @return paint configurado para dibujar texto.
     */
    @NonNull
    private static Paint textPaint(@NonNull Context context, int color, float sp, boolean bold) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        paint.setTextSize(sp(context, sp));
        paint.setFakeBoldText(bold);
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
     * @param context contexto usado para convertir el grosor de diseño.
     * @param color color del trazo.
     * @param dpWidth grosor base en unidades de diseño.
     * @return paint configurado en modo {@link Paint.Style#STROKE}.
     */
    @NonNull
    private static Paint strokePaint(@NonNull Context context, int color, float dpWidth) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(context, dpWidth));
        paint.setColor(color);
        return paint;
    }

    /**
     * Convierte unidades de diseño a píxeles del bitmap.
     *
     * <p>Esta imagen se dibuja sobre un canvas de tamaño fijo, así que aquí no usamos la densidad
     * real del dispositivo. De este modo evitamos que el contenido se salga del bitmap.</p>
     *
     * @param context contexto actual; se mantiene por simetría con otros helpers aunque no se use.
     * @param value valor de diseño a trasladar al bitmap.
     * @return valor redondeado en píxeles del canvas fijo.
     */
    private static int dp(@NonNull Context context, float value) {
        return Math.round(value);
    }

    /**
     * Convierte unidades tipográficas de diseño a píxeles del bitmap.
     *
     * @param context contexto actual; se mantiene por consistencia con el resto de helpers gráficos.
     * @param value tamaño tipográfico de diseño.
     * @return tamaño final en píxeles del bitmap.
     */
    private static float sp(@NonNull Context context, float value) {
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
