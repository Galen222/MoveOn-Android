package com.proyecto.moveon.ui.stats;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.proyecto.moveon.R;
import com.proyecto.moveon.domain.activity.StatsResumen;

import java.time.Month;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

public class HistorialBottomSheet extends BottomSheetDialogFragment {

    private static final Locale SPANISH_LOCALE = Locale.forLanguageTag("es-ES");

    @NonNull
    public static HistorialBottomSheet newInstance(@NonNull List<StatsResumen.MonthBlock> blocks) {
        HistorialBottomSheet sheet = new HistorialBottomSheet();
        sheet.monthBlocks = blocks;
        return sheet;
    }

    @Nullable private List<StatsResumen.MonthBlock> monthBlocks;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.bottom_sheet_historial, container, false);
        if (monthBlocks != null) buildContent(root);
        return root;
    }

    private void buildContent(@NonNull View root) {
        LinearLayout container = root.findViewById(R.id.ll_historial_container);
        if (container == null || monthBlocks == null) return;
        container.removeAllViews();

        if (monthBlocks.isEmpty()) {
            TextView empty = new TextView(requireContext());
            empty.setText(R.string.stats_historial_empty);
            empty.setTextColor(ContextCompat.getColor(requireContext(), R.color.textSecondary));
            empty.setTextSize(14f);
            int pad = dp(16);
            empty.setPadding(pad, pad, pad, pad);
            container.addView(empty);
            return;
        }

        for (StatsResumen.MonthBlock block : monthBlocks) {
            container.addView(buildMonthSection(block));
        }
    }

    @NonNull
    private View buildMonthSection(@NonNull StatsResumen.MonthBlock block) {
        LinearLayout section = new LinearLayout(requireContext());
        section.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(20);
        section.setLayoutParams(params);

        String monthName = Month.of(block.month)
                .getDisplayName(TextStyle.FULL, SPANISH_LOCALE);
        monthName = Character.toUpperCase(monthName.charAt(0)) + monthName.substring(1);

        // Cabecera: "Marzo 2025"
        TextView tvMes = new TextView(requireContext());
        tvMes.setText(getString(R.string.stats_historial_mes_anio, monthName, block.year));
        tvMes.setTextSize(17f);
        tvMes.setTextColor(ContextCompat.getColor(requireContext(), R.color.textPrimary));
        tvMes.setTypeface(null, android.graphics.Typeface.BOLD);
        tvMes.setPadding(0, 0, 0, dp(4));
        section.addView(tvMes);

        // Totales del mes: "12.5 km  ·  1h 20m  ·  840 kcal"
        TextView tvTotales = new TextView(requireContext());
        tvTotales.setText(getString(R.string.stats_historial_totales,
                formatDistance(block.distanceMeters),
                formatDuration(block.durationSeconds),
                formatKcal(block.calories)));
        tvTotales.setTextSize(13f);
        tvTotales.setTextColor(ContextCompat.getColor(requireContext(), R.color.textTertiary));
        tvTotales.setPadding(0, 0, 0, dp(10));
        section.addView(tvTotales);

        // Divisor
        View divisor = new View(requireContext());
        LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        divParams.bottomMargin = dp(10);
        divisor.setLayoutParams(divParams);
        divisor.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.dividerColor));
        section.addView(divisor);

        String monthShort = Month.of(block.month)
                .getDisplayName(TextStyle.SHORT, SPANISH_LOCALE)
                .toLowerCase(SPANISH_LOCALE);

        for (int i = 0; i < block.weeks.size(); i++) {
            StatsResumen.WeekBlock week = block.weeks.get(i);
            if (week.distanceMeters == 0 && week.durationSeconds == 0 && week.calories == 0) continue;
            section.addView(buildWeekRow(week, monthShort, i == block.weeks.size() - 1));
        }

        return section;
    }

    @NonNull
    private View buildWeekRow(@NonNull StatsResumen.WeekBlock week,
                              @NonNull String monthShort,
                              boolean isLast) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.bottomMargin = dp(8);
        row.setLayoutParams(rowParams);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView tvTree = new TextView(requireContext());
        tvTree.setText(isLast ? "└  " : "├  ");
        tvTree.setTextSize(14f);
        tvTree.setTextColor(ContextCompat.getColor(requireContext(), R.color.textTertiary));
        row.addView(tvTree);

        // Rango: "1–7 mar"
        TextView tvRango = new TextView(requireContext());
        tvRango.setText(getString(R.string.stats_historial_semana_rango,
                week.startDay, week.endDay, monthShort));
        tvRango.setTextSize(14f);
        tvRango.setTextColor(ContextCompat.getColor(requireContext(), R.color.textSecondary));
        tvRango.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(tvRango);

        LinearLayout colDatos = new LinearLayout(requireContext());
        colDatos.setOrientation(LinearLayout.VERTICAL);
        colDatos.setGravity(android.view.Gravity.END);

        TextView tvDist = new TextView(requireContext());
        tvDist.setText(formatDistance(week.distanceMeters));
        tvDist.setTextSize(14f);
        tvDist.setTextColor(ContextCompat.getColor(requireContext(), R.color.textPrimary));
        tvDist.setTypeface(null, android.graphics.Typeface.BOLD);
        tvDist.setGravity(android.view.Gravity.END);
        colDatos.addView(tvDist);

        // Tiempo · kcal
        TextView tvTiempoKcal = new TextView(requireContext());
        tvTiempoKcal.setText(getString(R.string.stats_historial_tiempo_kcal,
                formatDuration(week.durationSeconds),
                formatKcal(week.calories)));
        tvTiempoKcal.setTextSize(12f);
        tvTiempoKcal.setTextColor(ContextCompat.getColor(requireContext(), R.color.textTertiary));
        tvTiempoKcal.setGravity(android.view.Gravity.END);
        colDatos.addView(tvTiempoKcal);

        row.addView(colDatos);
        return row;
    }

    @NonNull private String formatDistance(long meters) {
        return getString(R.string.stats_format_km, meters / 1000.0f);
    }

    @NonNull private String formatDuration(long seconds) {
        long hours = seconds / 3600L, minutes = (seconds % 3600L) / 60L;
        return hours > 0L
                ? getString(R.string.stats_format_time_hm, hours, minutes)
                : getString(R.string.stats_format_time_m, minutes);
    }

    @NonNull private String formatKcal(long kcal) {
        return getString(R.string.stats_format_kcal, (int) kcal);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
