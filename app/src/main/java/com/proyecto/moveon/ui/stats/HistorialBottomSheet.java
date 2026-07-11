package com.proyecto.moveon.ui.stats;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.proyecto.moveon.ui.common.BaseExpandedBottomSheetDialogFragment;
import com.proyecto.moveon.R;
import com.proyecto.moveon.core.i18n.AppLanguageManager;
import com.proyecto.moveon.core.i18n.ProfileValueLocalizer;
import com.proyecto.moveon.core.settings.PaceDisplayUtils;
import com.proyecto.moveon.databinding.ItemActividadBinding;
import com.proyecto.moveon.domain.activity.ActividadItem;
import com.proyecto.moveon.domain.activity.StatsResumen;
import com.proyecto.moveon.ui.profile.ShareRouteFormatter;

import java.time.LocalDate;
import java.time.Month;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Bottom sheet que renderiza el historial mensual y semanal de actividades.
 *
 * <p>Tras esta corrección, tanto el filtrado interno como el formateo visual de fechas usan la
 * misma conversión a zona horaria local que {@code StatsCalculator}, evitando discrepancias entre
 * estadísticas, histórico y tarjetas compartidas.</p>
 */
public class HistorialBottomSheet extends BaseExpandedBottomSheetDialogFragment {

    private static final int PAGE_SIZE = 30;
    private static final String STATE_VISIBLE_MONTH_COUNT = "visible_month_count";

    @Nullable private List<StatsResumen.MonthBlock> monthBlocks;
    @NonNull private List<ActividadItem> activities = Collections.emptyList();
    @NonNull private final Set<String> expandedIds = new HashSet<>();
    private int visibleMonthCount = 0;

    /**
     * Crea una instancia del histórico con los bloques mensuales y las actividades ya calculadas.
     *
     * @param blocks bloques mensuales resumidos.
     * @param activities actividades completas usadas para el detalle expandido.
     * @return bottom sheet listo para mostrarse.
     */
    @NonNull
    public static HistorialBottomSheet newInstance(@NonNull List<StatsResumen.MonthBlock> blocks,
                                                   @NonNull List<ActividadItem> activities) {
        HistorialBottomSheet sheet = new HistorialBottomSheet();
        sheet.monthBlocks = blocks;
        sheet.activities = activities;
        return sheet;
    }

    /**
     * Infla el bottom sheet del historial y restaura el número de meses visibles.
     *
     * @param inflater inflater del fragment.
     * @param container contenedor padre.
     * @param savedInstanceState estado previo del fragment.
     * @return vista raíz del histórico.
     */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.bottom_sheet_historial, container, false);
        View closeButton = root.findViewById(R.id.btnHistorialClose);
        if (closeButton != null) {
            closeButton.setOnClickListener(_ -> dismissAllowingStateLoss());
        }

        if (savedInstanceState != null) {
            visibleMonthCount = savedInstanceState.getInt(STATE_VISIBLE_MONTH_COUNT, visibleMonthCount);
        }

        MaterialButton showMoreButton = root.findViewById(R.id.btnHistorialShowMore);
        if (showMoreButton != null) {
            showMoreButton.setOnClickListener(_ -> {
                visibleMonthCount = Math.min(getTotalMonthCount(), visibleMonthCount + nextMonthBatchSize());
                buildContent(root);
            });
        }

        if (monthBlocks != null) {
            if (visibleMonthCount <= 0) {
                visibleMonthCount = Math.min(getTotalMonthCount(), computeMonthBatchSize(0));
            }
            buildContent(root);
        }
        return root;
    }

    /**
     * Guarda el número de meses visibles para conservar la paginación al recrear la vista.
     *
     * @param outState bundle donde persistir el estado.
     */
    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_VISIBLE_MONTH_COUNT, visibleMonthCount);
    }

    /**
     * Reconstruye por completo el contenido visible del histórico a partir del estado actual.
     *
     * @param root raíz del bottom sheet.
     */
    private void buildContent(@NonNull View root) {
        LinearLayout container = root.findViewById(R.id.ll_historial_container);
        MaterialButton showMoreButton = root.findViewById(R.id.btnHistorialShowMore);
        List<StatsResumen.MonthBlock> blocks = monthBlocks;
        if (container == null || blocks == null) return;
        container.removeAllViews();

        if (blocks.isEmpty()) {
            if (showMoreButton != null) showMoreButton.setVisibility(View.GONE);
            TextView empty = new TextView(requireContext());
            empty.setText(R.string.stats_historial_empty);
            empty.setTextColor(ContextCompat.getColor(requireContext(), R.color.textSecondary));
            empty.setTextSize(14f);
            int pad = dp(20);
            empty.setPadding(pad, pad, pad, pad);
            container.addView(empty);
            return;
        }

        int totalMonthCount = blocks.size();
        int safeVisibleCount = visibleMonthCount;
        if (safeVisibleCount < 0) {
            safeVisibleCount = 0;
        } else if (safeVisibleCount > totalMonthCount) {
            safeVisibleCount = totalMonthCount;
        }
        List<StatsResumen.MonthBlock> visibleBlocks = blocks.subList(0, safeVisibleCount);

        for (StatsResumen.MonthBlock block : visibleBlocks) {
            container.addView(buildMonthSection(block));
        }

        if (showMoreButton != null) {
            int nextBatchSize = nextMonthBatchSize();
            boolean hasMore = safeVisibleCount < totalMonthCount && nextBatchSize > 0;
            showMoreButton.setVisibility(hasMore ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * Devuelve el número total de bloques mensuales disponibles.
     *
     * @return cantidad de meses renderizables.
     */
    private int getTotalMonthCount() {
        return monthBlocks != null ? monthBlocks.size() : 0;
    }

    /**
     * Calcula cuántos meses adicionales deben mostrarse en la siguiente tanda.
     *
     * @return tamaño del próximo lote de meses.
     */
    private int nextMonthBatchSize() {
        return computeMonthBatchSize(visibleMonthCount);
    }

    /**
     * Calcula un lote de meses intentando no superar el tamaño lógico de página por actividades.
     *
     * @param startIndex índice del primer mes aún no visible.
     * @return número de meses a revelar en la siguiente expansión.
     */
    private int computeMonthBatchSize(int startIndex) {
        List<StatsResumen.MonthBlock> blocks = monthBlocks;
        if (blocks == null || startIndex >= blocks.size()) return 0;

        int addedMonths = 0;
        int countedActivities = 0;
        for (int i = Math.max(0, startIndex); i < blocks.size(); i++) {
            countedActivities += countActivitiesForMonth(blocks.get(i));
            addedMonths++;
            if (countedActivities >= PAGE_SIZE) {
                break;
            }
        }
        return addedMonths;
    }

    /**
     * Cuenta cuántas actividades pertenecen al mes indicado.
     *
     * @param monthBlock bloque mensual resumido.
     * @return número de actividades asociadas a ese mes.
     */
    private int countActivitiesForMonth(@NonNull StatsResumen.MonthBlock monthBlock) {
        int count = 0;
        for (ActividadItem item : activities) {
            LocalDate date = parseFecha(item.fechaRutaIso);
            if (date == null) continue;
            if (date.getYear() == monthBlock.year && date.getMonthValue() == monthBlock.month) {
                count++;
            }
        }
        return count;
    }

    /**
     * Construye la sección visual completa de un mes con cabecera, totales y semanas.
     *
     * @param block bloque mensual a renderizar.
     * @return vista del mes lista para añadirse al contenedor.
     */
    @NonNull
    private View buildMonthSection(@NonNull StatsResumen.MonthBlock block) {
        Context context = requireContext();
        Locale activeLocale = AppLanguageManager.getActiveLocale(context);

        LinearLayout section = new LinearLayout(context);
        section.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = dp(24);
        section.setLayoutParams(params);

        String monthName = Month.of(block.month).getDisplayName(TextStyle.FULL, activeLocale);
        if (!monthName.isEmpty()) {
            monthName = Character.toUpperCase(monthName.charAt(0)) + monthName.substring(1);
        }

        TextView tvMes = new TextView(context);
        tvMes.setText(getString(R.string.stats_historial_mes_anio, monthName, block.year));
        tvMes.setTextSize(17f);
        tvMes.setTextColor(ContextCompat.getColor(context, R.color.textPrimary));
        tvMes.setTypeface(null, Typeface.BOLD);
        tvMes.setPadding(dp(20), 0, dp(20), dp(4));
        section.addView(tvMes);

        TextView tvTotales = new TextView(context);
        tvTotales.setText(getString(R.string.stats_historial_totales,
                formatDistance(block.distanceMeters),
                formatDuration(block.durationSeconds),
                formatKcal(block.calories)));
        tvTotales.setTextSize(13f);
        tvTotales.setTextColor(ContextCompat.getColor(context, R.color.textTertiary));
        tvTotales.setPadding(dp(20), 0, dp(20), dp(10));
        section.addView(tvTotales);

        View divisor = new View(context);
        LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1)
        );
        divParams.leftMargin = dp(20);
        divParams.rightMargin = dp(20);
        divParams.bottomMargin = dp(10);
        divisor.setLayoutParams(divParams);
        divisor.setBackgroundColor(ContextCompat.getColor(context, R.color.dividerColor));
        section.addView(divisor);

        String monthShort = Month.of(block.month)
                .getDisplayName(TextStyle.SHORT, activeLocale)
                .toLowerCase(activeLocale);

        for (StatsResumen.WeekBlock week : block.weeks) {
            List<ActividadItem> weekActivities = getActivitiesForWeek(block, week);
            boolean hasSummary = week.distanceMeters > 0 || week.durationSeconds > 0 || week.calories > 0;
            if (!hasSummary && weekActivities.isEmpty()) continue;
            section.addView(buildWeekSection(week, monthShort, weekActivities));
        }

        return section;
    }

    /**
     * Construye la sección de una semana concreta incluyendo su resumen y sus actividades.
     *
     * @param week bloque semanal resumido.
     * @param monthShort nombre corto del mes para el rango visual.
     * @param weekActivities actividades pertenecientes a esa semana.
     * @return vista de la semana.
     */
    @NonNull
    private View buildWeekSection(@NonNull StatsResumen.WeekBlock week,
                                  @NonNull String monthShort,
                                  @NonNull List<ActividadItem> weekActivities) {
        Context context = requireContext();

        LinearLayout section = new LinearLayout(context);
        section.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams sectionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        sectionParams.bottomMargin = dp(12);
        section.setLayoutParams(sectionParams);

        section.addView(buildWeekSummaryRow(week, monthShort));

        for (ActividadItem item : weekActivities) {
            section.addView(buildActivityView(section, item));
        }

        return section;
    }

    /**
     * Genera la fila resumen de una semana con rango, distancia, tiempo y calorías.
     *
     * @param week bloque semanal.
     * @param monthShort nombre corto del mes mostrado en el rango.
     * @return fila de cabecera semanal.
     */
    @NonNull
    private View buildWeekSummaryRow(@NonNull StatsResumen.WeekBlock week,
                                     @NonNull String monthShort) {
        Context context = requireContext();

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(20), dp(6), dp(20), dp(10));

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        row.setLayoutParams(rowParams);

        TextView tvRango = new TextView(context);
        tvRango.setText(getString(R.string.stats_historial_semana_rango,
                week.startDay, week.endDay, monthShort));
        tvRango.setTextSize(14f);
        tvRango.setTextColor(ContextCompat.getColor(context, R.color.textSecondary));
        tvRango.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
        ));
        row.addView(tvRango);

        LinearLayout colDatos = new LinearLayout(context);
        colDatos.setOrientation(LinearLayout.VERTICAL);
        colDatos.setGravity(Gravity.END);

        TextView tvDist = new TextView(context);
        tvDist.setText(formatDistance(week.distanceMeters));
        tvDist.setTextSize(14f);
        tvDist.setTextColor(ContextCompat.getColor(context, R.color.textPrimary));
        tvDist.setTypeface(null, Typeface.BOLD);
        tvDist.setGravity(Gravity.END);
        colDatos.addView(tvDist);

        TextView tvTiempoKcal = new TextView(context);
        tvTiempoKcal.setText(getString(R.string.stats_historial_tiempo_kcal,
                formatDuration(week.durationSeconds),
                formatKcal(week.calories)));
        tvTiempoKcal.setTextSize(12f);
        tvTiempoKcal.setTextColor(ContextCompat.getColor(context, R.color.textTertiary));
        tvTiempoKcal.setGravity(Gravity.END);
        colDatos.addView(tvTiempoKcal);

        row.addView(colDatos);
        return row;
    }

    /**
     * Infla una tarjeta individual de actividad para insertarla dentro del histórico expandido.
     *
     * @param parent contenedor padre.
     * @param item actividad a mostrar.
     * @return raíz de la tarjeta inflada.
     */
    @NonNull
    private View buildActivityView(@NonNull ViewGroup parent, @NonNull ActividadItem item) {
        ItemActividadBinding binding = ItemActividadBinding.inflate(
                LayoutInflater.from(requireContext()),
                parent,
                false
        );
        bindActivityCard(binding, item);
        return binding.getRoot();
    }

    /**
     * Vincula una actividad a la tarjeta interna usada por el histórico expandido.
     *
     * @param binding binding de la tarjeta.
     * @param item actividad a pintar.
     */
    private void bindActivityCard(@NonNull ItemActividadBinding binding,
                                  @NonNull ActividadItem item) {
        final Context context = binding.getRoot().getContext();

        String canonicalTipo = ProfileValueLocalizer.canonicalActivityTypeFromLabel(context, item.tipo);
        final int iconRes;
        if ("Caminar".equals(canonicalTipo)) {
            iconRes = R.drawable.walk_icon;
        } else if ("Correr".equals(canonicalTipo)) {
            iconRes = R.drawable.run_icon;
        } else {
            iconRes = R.drawable.walk_icon;
        }
        binding.ivActivityIcon.setImageResource(iconRes);
        binding.tvActivityType.setText(
                ProfileValueLocalizer.displayActivityType(context, canonicalTipo)
        );

        binding.tvActivityDate.setText(formatFechaActividad(item.fechaRutaIso, context));

        boolean pendiente = item.isPendingSync();
        binding.tvPendingBadge.setVisibility(pendiente ? View.VISIBLE : View.GONE);

        binding.tvActivityDistance.setText(
                context.getString(R.string.stats_format_km, item.distanciaMetros / 1000.0f)
        );
        binding.tvActivityDuration.setText(
                formatDuracion(item.duracionSegundos, context)
        );

        binding.tvActivityCalories.setText(
                context.getString(R.string.stats_format_kcal, item.caloriasQuemadas)
        );
        binding.tvActivitySteps.setText(ShareRouteFormatter.formatSteps(context, item.pasos));
        // Este detalle expandido del historial usa un binder propio dentro del
        // bottom sheet, distinto del adapter principal. Aquí se compone también "/km".
        binding.tvActivityPace.setText(
                formatPace(PaceDisplayUtils.getPreferredAveragePaceSeconds(context, item), context)
        );
        binding.tvActivityMaxPace.setText(
                formatPace(item.ritmoMaximoSegKm, context)
        );
        binding.tvActivityMoving.setText(
                formatDuracion(item.duracionMovimientoSegundos, context)
        );
        binding.tvActivityStopped.setText(
                formatDuracion(item.duracionParadoSegundos, context)
        );
        binding.tvActivityTotal.setText(
                formatDuracion(item.duracionSegundos, context)
        );

        binding.btnDelete.setEnabled(!pendiente);
        binding.btnDelete.setAlpha(pendiente ? 0.3f : 1.0f);
        binding.btnDelete.setOnClickListener(_ -> {
            if (getParentFragment() instanceof StatsFragment) {
                ((StatsFragment) getParentFragment()).onDeleteClickPublic(item);
            }
        });

        boolean tienePolilinea = item.rutaPolilinea != null && !item.rutaPolilinea.isEmpty();
        binding.btnShareRoute.setVisibility(tienePolilinea ? View.VISIBLE : View.GONE);
        binding.viewShareDivider.setVisibility(tienePolilinea ? View.VISIBLE : View.GONE);
        if (tienePolilinea) {
            binding.btnShareRoute.setOnClickListener(_ -> {
                if (getParentFragment() instanceof StatsFragment) {
                    ((StatsFragment) getParentFragment()).onShareClickPublic(item);
                }
            });
        } else {
            binding.btnShareRoute.setOnClickListener(null);
        }

        applyExpandState(binding, item.localId, false);
        binding.layoutHeader.setOnClickListener(_ -> toggleExpand(binding, item.localId));
    }

    /**
     * Alterna el estado expandido de una tarjeta del histórico.
     *
     * @param binding binding de la tarjeta pulsada.
     * @param localId identificador local estable de la actividad.
     */
    private void toggleExpand(@NonNull ItemActividadBinding binding, @NonNull String localId) {
        if (expandedIds.contains(localId)) {
            expandedIds.remove(localId);
        } else {
            expandedIds.add(localId);
        }
        applyExpandState(binding, localId, true);
    }

    /**
     * Aplica visualmente el estado expandido o colapsado de una tarjeta del histórico.
     *
     * @param binding binding de la tarjeta.
     * @param localId identificador local de la actividad.
     * @param animate indica si debe animarse la rotación del chevron.
     */
    private void applyExpandState(@NonNull ItemActividadBinding binding,
                                  @NonNull String localId,
                                  boolean animate) {
        boolean expanded = expandedIds.contains(localId);
        int detailVisibility = expanded ? View.VISIBLE : View.GONE;

        binding.layoutDetails.setVisibility(detailVisibility);
        binding.viewDivider.setVisibility(detailVisibility);

        float targetRotation = expanded ? 180f : 0f;
        if (animate) {
            binding.ivChevron.animate()
                    .rotation(targetRotation)
                    .setDuration(200)
                    .start();
        } else {
            binding.ivChevron.setRotation(targetRotation);
        }
    }

    /**
     * Filtra y ordena las actividades que pertenecen a una semana concreta del mes.
     *
     * @param monthBlock bloque mensual contenedor.
     * @param week semana objetivo.
     * @return lista de actividades de esa semana ordenadas de más reciente a más antigua.
     */
    @NonNull
    private List<ActividadItem> getActivitiesForWeek(@NonNull StatsResumen.MonthBlock monthBlock,
                                                     @NonNull StatsResumen.WeekBlock week) {
        List<ActividadItem> result = new ArrayList<>();
        for (ActividadItem item : activities) {
            LocalDate date = parseFecha(item.fechaRutaIso);
            if (date == null) continue;
            if (date.getYear() != monthBlock.year) continue;
            if (date.getMonthValue() != monthBlock.month) continue;

            int dayOfMonth = date.getDayOfMonth();
            if (dayOfMonth < week.startDay || dayOfMonth > week.endDay) continue;

            result.add(item);
        }

        result.sort(Comparator.comparing(
                (ActividadItem item) -> {
                    LocalDate date = parseFecha(item.fechaRutaIso);
                    return date != null ? date : LocalDate.MIN;
                }
        ).reversed());

        return result;
    }

    /**
     * Convierte la fecha ISO de la actividad a la fecha local visible por el usuario.
     *
     * <p>Se alinea con {@link com.proyecto.moveon.domain.activity.StatsCalculator(String)}
     * para que el histórico no clasifique una actividad en un día distinto al usado por las estadísticas.</p>
     *
     * @param fechaIso fecha/hora ISO persistida para la actividad.
     * @return fecha local visible o {@code null} cuando el texto no puede parsearse.
     */
    @Nullable
    private LocalDate parseFecha(@NonNull String fechaIso) {
        try {
            return OffsetDateTime.parse(fechaIso)
                    .atZoneSameInstant(ZoneId.systemDefault())
                    .toLocalDate();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * Formatea la fecha de la tarjeta usando la misma zona local aplicada al filtrado.
     *
     * @param fechaIso fecha/hora ISO persistida para la actividad.
     * @param context contexto usado para resolver el patrón localizado.
     * @return fecha visible lista para la cabecera de la tarjeta.
     */
    @NonNull
    private String formatFechaActividad(@NonNull String fechaIso, @NonNull Context context) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(
                    "d MMM yyyy",
                    AppLanguageManager.getActiveLocale(context)
            );
            return OffsetDateTime.parse(fechaIso)
                    .atZoneSameInstant(ZoneId.systemDefault())
                    .toLocalDate()
                    .format(formatter);
        } catch (DateTimeParseException e) {
            return fechaIso.length() >= 10 ? fechaIso.substring(0, 10) : fechaIso;
        }
    }

    /**
     * Formatea una duración expresada en segundos para la tarjeta detallada de actividad.
     *
     * @param segundos duración total.
     * @param context contexto para resolver recursos plurales/formateados.
     * @return duración en minutos o en horas y minutos.
     */
    @NonNull
    private String formatDuracion(int segundos, @NonNull Context context) {
        long horas = segundos / 3600L;
        long minutos = (segundos % 3600L) / 60L;
        if (horas > 0) {
            return context.getString(R.string.stats_format_time_hm, horas, minutos);
        }
        return context.getString(R.string.stats_format_time_m, Math.max(1L, minutos));
    }
    /**
     * Formatea el ritmo para el detalle expandido del historial.
     *
     * <p>Este bottom sheet no reutiliza el {@link ActividadAdapter}, sino que
     * vuelve a inflar y bindear {@link ItemActividadBinding} manualmente.
     * Por eso necesita su propio formateo y aquí devolvemos siempre el
     * valor final con la unidad {@code /km} ya incluida.</p>
     *
     * @param secondsPerKm ritmo expresado en segundos por kilómetro.
     * @param context contexto usado para acceder a recursos localizados.
     * @return ritmo listo para mostrarse en la sección expandida.
     */
    @NonNull
    private String formatPace(int secondsPerKm, @NonNull Context context) {
        String basePace;
        if (secondsPerKm <= 0) {
            basePace = "--'--\"";
        } else {
            int minutes = secondsPerKm / 60;
            int seconds = secondsPerKm % 60;
            basePace = String.format(Locale.US, "%d'%02d\"", minutes, seconds);
        }

        // Añadimos la unidad aquí, de forma explícita, porque este detalle
        // expandido del historial construye su propio texto y no pasa por el
        // formatter del adapter principal.
        return context.getString(R.string.stats_item_pace_format, basePace);
    }

    /**
     * Formatea una distancia en metros usando el formato de kilómetros de la app.
     *
     * @param meters distancia original.
     * @return distancia visible en kilómetros.
     */
    @NonNull
    private String formatDistance(long meters) {
        return getString(R.string.stats_format_km, meters / 1000.0f);
    }

    /**
     * Formatea una duración resumida de bloque semanal o mensual.
     *
     * @param seconds duración agregada en segundos.
     * @return texto resumido en minutos u horas y minutos.
     */
    @NonNull
    private String formatDuration(long seconds) {
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        return hours > 0L
                ? getString(R.string.stats_format_time_hm, hours, minutes)
                : getString(R.string.stats_format_time_m, minutes);
    }


    /**
     * Formatea calorías agregadas adaptando la unidad cuando el valor es muy grande.
     *
     * @param kcal calorías totales.
     * @return texto abreviado o completo según magnitud.
     */
    @NonNull
    private String formatKcal(long kcal) {
        if (kcal >= 1_000_000L) {
            return getString(R.string.stats_format_kcal_m, kcal / 1_000_000.0f);
        }
        if (kcal >= 1_000L) {
            return getString(R.string.stats_format_kcal_k, kcal / 1_000.0f);
        }
        return getString(R.string.stats_format_kcal, (int) kcal);
    }

    /**
     * Convierte dp a píxeles enteros para construir vistas programáticamente.
     *
     * @param value valor en dp.
     * @return valor equivalente en píxeles.
     */
    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
