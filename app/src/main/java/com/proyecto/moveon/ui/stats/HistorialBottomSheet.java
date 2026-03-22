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
import com.proyecto.moveon.databinding.ItemActividadBinding;
import com.proyecto.moveon.domain.activity.ActividadItem;
import com.proyecto.moveon.domain.activity.StatsResumen;

import java.time.LocalDate;
import java.time.Month;
import java.time.OffsetDateTime;
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

public class HistorialBottomSheet extends BaseExpandedBottomSheetDialogFragment {

    private static final int PAGE_SIZE = 30;
    private static final String STATE_VISIBLE_MONTH_COUNT = "visible_month_count";

    @Nullable private List<StatsResumen.MonthBlock> monthBlocks;
    @NonNull private List<ActividadItem> activities = Collections.emptyList();
    @NonNull private final Set<String> expandedIds = new HashSet<>();
    private int visibleMonthCount = 0;

    @NonNull
    public static HistorialBottomSheet newInstance(@NonNull List<StatsResumen.MonthBlock> blocks,
                                                   @NonNull List<ActividadItem> activities) {
        HistorialBottomSheet sheet = new HistorialBottomSheet();
        sheet.monthBlocks = blocks;
        sheet.activities = activities;
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.bottom_sheet_historial, container, false);
        View closeButton = root.findViewById(R.id.btnHistorialClose);
        if (closeButton != null) {
            closeButton.setOnClickListener(v -> dismissAllowingStateLoss());
        }

        if (savedInstanceState != null) {
            visibleMonthCount = savedInstanceState.getInt(STATE_VISIBLE_MONTH_COUNT, visibleMonthCount);
        }

        MaterialButton showMoreButton = root.findViewById(R.id.btnHistorialShowMore);
        if (showMoreButton != null) {
            showMoreButton.setOnClickListener(v -> {
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

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_VISIBLE_MONTH_COUNT, visibleMonthCount);
    }

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
        int safeVisibleCount = Math.max(0, Math.min(visibleMonthCount, totalMonthCount));
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

    private int getTotalMonthCount() {
        return monthBlocks != null ? monthBlocks.size() : 0;
    }

    private int nextMonthBatchSize() {
        return computeMonthBatchSize(visibleMonthCount);
    }

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
        binding.tvActivityPace.setText(
                context.getString(
                        R.string.stats_item_pace_format,
                        formatPace(item.ritmoMedioMovimientoSegKm)
                )
        );
        binding.tvActivityMoving.setText(
                formatDuracion(item.duracionMovimientoSegundos, context)
        );
        binding.tvActivityStopped.setText(
                formatDuracion(item.duracionParadoSegundos, context)
        );

        binding.btnDelete.setEnabled(!pendiente);
        binding.btnDelete.setAlpha(pendiente ? 0.3f : 1.0f);
        binding.btnDelete.setOnClickListener(v -> {
            if (getParentFragment() instanceof StatsFragment) {
                ((StatsFragment) getParentFragment()).onDeleteClickPublic(item);
            }
        });

        boolean tienePolilinea = item.rutaPolilinea != null && !item.rutaPolilinea.isEmpty();
        binding.btnShareRoute.setVisibility(tienePolilinea ? View.VISIBLE : View.GONE);
        binding.viewShareDivider.setVisibility(tienePolilinea ? View.VISIBLE : View.GONE);
        if (tienePolilinea) {
            binding.btnShareRoute.setOnClickListener(v -> {
                if (getParentFragment() instanceof StatsFragment) {
                    ((StatsFragment) getParentFragment()).onShareClickPublic(item);
                }
            });
        } else {
            binding.btnShareRoute.setOnClickListener(null);
        }

        applyExpandState(binding, item.localId, false);
        binding.layoutHeader.setOnClickListener(v -> toggleExpand(binding, item.localId));
    }

    private void toggleExpand(@NonNull ItemActividadBinding binding, @NonNull String localId) {
        if (expandedIds.contains(localId)) {
            expandedIds.remove(localId);
        } else {
            expandedIds.add(localId);
        }
        applyExpandState(binding, localId, true);
    }

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

    @Nullable
    private LocalDate parseFecha(@NonNull String fechaIso) {
        try {
            return OffsetDateTime.parse(fechaIso).toLocalDate();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    @NonNull
    private String formatFechaActividad(@NonNull String fechaIso, @NonNull Context context) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(
                    "d MMM yyyy",
                    AppLanguageManager.getActiveLocale(context)
            );
            return OffsetDateTime.parse(fechaIso)
                    .toLocalDate()
                    .format(formatter);
        } catch (DateTimeParseException e) {
            return fechaIso.length() >= 10 ? fechaIso.substring(0, 10) : fechaIso;
        }
    }

    @NonNull
    private String formatDuracion(int segundos, @NonNull Context context) {
        long horas = segundos / 3600L;
        long minutos = (segundos % 3600L) / 60L;
        if (horas > 0) {
            return context.getString(R.string.stats_format_time_hm, horas, minutos);
        }
        return context.getString(R.string.stats_format_time_m, Math.max(1L, minutos));
    }

    @NonNull
    private String formatPace(int secondsPerKm) {
        if (secondsPerKm <= 0) {
            return "--'--\"";
        }
        int minutes = secondsPerKm / 60;
        int seconds = secondsPerKm % 60;
        return String.format(Locale.US, "%d'%02d\"", minutes, seconds);
    }

    @NonNull
    private String formatDistance(long meters) {
        return getString(R.string.stats_format_km, meters / 1000.0f);
    }

    @NonNull
    private String formatDuration(long seconds) {
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        return hours > 0L
                ? getString(R.string.stats_format_time_hm, hours, minutes)
                : getString(R.string.stats_format_time_m, minutes);
    }

    @NonNull
    private String formatKcal(long kcal) {
        return getString(R.string.stats_format_kcal, (int) kcal);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
