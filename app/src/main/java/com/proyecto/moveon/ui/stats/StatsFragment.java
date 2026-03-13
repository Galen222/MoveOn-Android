package com.proyecto.moveon.ui.stats;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.proyecto.moveon.R;
import com.proyecto.moveon.databinding.FragmentStatsBinding;
import com.proyecto.moveon.domain.activity.ActividadItem;
import com.proyecto.moveon.domain.activity.StatsResumen;

import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

public class StatsFragment extends Fragment {

    private FragmentStatsBinding binding;
    private StatsViewModel viewModel;
    private ActividadAdapter adapter;

    private int calendarMonthsVisible = 3;

    @Nullable private StatsResumen lastResumen = null;

    // ── Ciclo de vida ─────────────────────────────────────────────────────────

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentStatsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(StatsViewModel.class);

        setupRecyclerView();
        setupListeners();
        observeViewModel();

        viewModel.load();
    }

    @Override
    public void onDestroyView() {
        binding.rvHistory.setAdapter(null);
        super.onDestroyView();
        binding = null;
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    private void setupRecyclerView() {
        adapter = new ActividadAdapter(this::onDeleteClick);
        binding.rvHistory.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvHistory.setAdapter(adapter);
        binding.rvHistory.setHasFixedSize(false);
    }

    private void setupListeners() {
        binding.btnRetry.setOnClickListener(v -> viewModel.load());
        binding.tvWeeklyGoalHeader.setOnClickListener(v -> showGoalDialog(true));
        binding.tvMonthlyGoalHeader.setOnClickListener(v -> showGoalDialog(false));
        binding.btnCalendarMore.setOnClickListener(v -> {
            calendarMonthsVisible += 3;
            if (lastResumen != null) renderCalendar(lastResumen);
        });
    }

    // ── Observadores ──────────────────────────────────────────────────────────

    private void observeViewModel() {
        viewModel.getStatsState().observe(getViewLifecycleOwner(), state -> {
            if (state == null || binding == null) return;

            binding.layoutLoading.setVisibility(state.loading ? View.VISIBLE : View.GONE);

            if (state.data != null) {
                lastResumen = state.data;
                bindAll(state.data);
                showContent();
            } else if (state.error != null && !state.loading) {
                showEmpty();
            }
        });

        viewModel.getActividades().observe(getViewLifecycleOwner(), items -> {
            if (binding == null) return;
            adapter.submitList(items);
            updateEmptyState(items);
        });

        viewModel.getDeleteEvent().observe(getViewLifecycleOwner(), event -> {
            if (binding == null || event == null) return;
            var state = event.getContentIfNotHandled();
            if (state == null) return;

            if (state.data != null) {
                Snackbar.make(binding.getRoot(),
                        R.string.stats_delete_ok, Snackbar.LENGTH_SHORT).show();
            } else if (state.error != null) {
                Snackbar.make(binding.getRoot(),
                        state.error.getMessage(), Snackbar.LENGTH_LONG).show();
            }
        });
    }

    // ── Binding de datos ──────────────────────────────────────────────────────

    private void bindAll(@NonNull StatsResumen r) {
        if (binding == null) return;
        bindCard1Today(r);
        renderChart(r.weekDaysDistanceMeters);
        bindCard3WeeklyGoal(r);
        bindCard4MonthlyGoal(r);
        bindCard5RecentActivity(r);
        bindCard6MonthComparison(r);
        bindCard7WeekComparison(r);
        bindCard8Totals(r);
        renderCalendar(r);
    }

    private void bindCard1Today(@NonNull StatsResumen r) {
        binding.tvTodayDist.setText(formatDistance(r.todayDistanceMeters));
        binding.tvTodayTime.setText(formatDuration(r.todayDurationSeconds));
        binding.tvTodayKcal.setText(formatKcal(r.todayCalories));
    }

    private void bindCard3WeeklyGoal(@NonNull StatsResumen r) {
        int progress = r.weeklyGoalMeters > 0
                ? (int) Math.min(100L, (r.weeklyDistanceMeters * 100L) / r.weeklyGoalMeters)
                : 0;
        binding.progressWeeklyGoal.setProgress(progress, true);
        binding.tvWeeklyProgress.setText(formatDistance(r.weeklyDistanceMeters));
        binding.tvWeeklyGoalTarget.setText(formatDistance(r.weeklyGoalMeters));

        long remaining = Math.max(0L, r.weeklyGoalMeters - r.weeklyDistanceMeters);
        binding.tvWeeklyGoalRemaining.setText(remaining > 0
                ? getString(R.string.stats_goal_remaining_format, formatDistance(remaining))
                : getString(R.string.stats_weekly_goal_done));
    }

    private void bindCard4MonthlyGoal(@NonNull StatsResumen r) {
        int progress = r.monthlyGoalMeters > 0
                ? (int) Math.min(100L, (r.currentMonthDistanceMeters * 100L) / r.monthlyGoalMeters)
                : 0;
        binding.progressMonthlyGoal.setProgress(progress, true);
        binding.tvMonthlyProgress.setText(formatDistance(r.currentMonthDistanceMeters));
        binding.tvMonthlyGoalTarget.setText(formatDistance(r.monthlyGoalMeters));

        long remaining = Math.max(0L, r.monthlyGoalMeters - r.currentMonthDistanceMeters);
        binding.tvMonthlyGoalRemaining.setText(remaining > 0
                ? getString(R.string.stats_monthly_goal_remaining_format, formatDistance(remaining))
                : getString(R.string.stats_monthly_goal_done));
    }

    private void bindCard5RecentActivity(@NonNull StatsResumen r) {
        binding.tvTodayDistance.setText(formatDistance(r.todayDistanceMeters));
        binding.tvYesterdayDistance.setText(formatDistance(r.yesterdayDistanceMeters));
        binding.tvDay2Distance.setText(formatDistance(r.twoDaysAgoDistanceMeters));
    }

    private void bindCard6MonthComparison(@NonNull StatsResumen r) {
        binding.tvCurrentMonthDist.setText(formatDistance(r.currentMonthDistanceMeters));
        binding.tvCurrentMonthKcal.setText(formatKcal(r.currentMonthCalories));
        binding.tvPreviousMonthDist.setText(formatDistance(r.previousMonthDistanceMeters));
        binding.tvPreviousMonthKcal.setText(formatKcal(r.previousMonthCalories));
    }

    private void bindCard7WeekComparison(@NonNull StatsResumen r) {
        binding.tvCurrentWeekDist.setText(formatDistance(r.weeklyDistanceMeters));
        binding.tvCurrentWeekKcal.setText(formatKcal(r.weeklyCalories));
        binding.tvPreviousWeekDist.setText(formatDistance(r.previousWeekDistanceMeters));
        binding.tvPreviousWeekKcal.setText(formatKcal(r.previousWeekCalories));
    }

    private void bindCard8Totals(@NonNull StatsResumen r) {
        binding.tvTotalDistance.setText(formatDistance(r.totalDistanceMeters));
        binding.tvTotalTime.setText(formatDuration(r.totalDurationSeconds));
        binding.tvTotalKcal.setText(formatKcal(r.totalCalories));
        binding.tvStreak.setText(formatStreak(r.streakDays));
    }

    // ── Gráfico semanal (Card 2) ──────────────────────────────────────────────

    private void renderChart(@NonNull long[] distancias) {
        if (binding == null) return;

        LinearLayout container = binding.llChartBars;
        container.removeAllViews();

        long maxVal = 1L;
        for (long d : distancias) {
            if (d > maxVal) maxVal = d;
        }

        int chartHeightPx = (int) getResources().getDimension(R.dimen.stats_chart_height);
        int barWidthPx    = (int) getResources().getDimension(R.dimen.stats_chart_bar_width);
        int barRadiusPx   = (int) getResources().getDimension(R.dimen.stats_chart_bar_radius);
        int labelSizeSp   = (int) getResources().getDimension(R.dimen.stats_chart_label_size);

        String[] dias     = getResources().getStringArray(R.array.stats_week_days_short);
        int colorActive   = requireContext().getColor(R.color.greenPrimary);
        int colorInactive = requireContext().getColor(R.color.dividerColor);
        int colorLabel    = requireContext().getColor(R.color.textTertiary);
        int todayIndex    = LocalDate.now().getDayOfWeek().getValue() - 1;

        for (int i = 0; i < 7; i++) {
            LinearLayout column = new LinearLayout(requireContext());
            column.setOrientation(LinearLayout.VERTICAL);
            column.setGravity(android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL);
            LinearLayout.LayoutParams colParams = new LinearLayout.LayoutParams(0, chartHeightPx, 1f);
            column.setLayoutParams(colParams);

            View bar = new View(requireContext());
            int barHeightPx = distancias[i] == 0
                    ? barRadiusPx * 2
                    : (int) ((distancias[i] * (chartHeightPx - labelSizeSp * 2)) / maxVal);
            LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(barWidthPx, barHeightPx);
            bar.setLayoutParams(barParams);

            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.RECTANGLE);
            shape.setCornerRadii(new float[]{barRadiusPx, barRadiusPx, barRadiusPx, barRadiusPx, 0, 0, 0, 0});
            shape.setColor(i == todayIndex ? colorActive : colorInactive);
            bar.setBackground(shape);

            TextView label = new TextView(requireContext());
            label.setText(dias[i]);
            label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, labelSizeSp);
            label.setTextColor(colorLabel);
            label.setGravity(android.view.Gravity.CENTER);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            labelParams.topMargin = barRadiusPx;
            label.setLayoutParams(labelParams);

            column.addView(bar);
            column.addView(label);
            container.addView(column);
        }
    }

    // ── Historial por semanas (Card 9) ────────────────────────────────────────

    private void renderCalendar(@NonNull StatsResumen r) {
        if (binding == null) return;

        LinearLayout container = binding.llCalendarBlocks;
        container.removeAllViews();

        List<StatsResumen.MonthBlock> blocks = r.monthBlocks;
        if (blocks.isEmpty()) {
            binding.btnCalendarMore.setVisibility(View.GONE);
            return;
        }

        int toShow = Math.min(calendarMonthsVisible, blocks.size());
        for (int i = 0; i < toShow; i++) {
            container.addView(buildMonthBlock(blocks.get(i)));
        }

        binding.btnCalendarMore.setVisibility(
                calendarMonthsVisible < blocks.size() ? View.VISIBLE : View.GONE);
    }

    @NonNull
    private View buildMonthBlock(@NonNull StatsResumen.MonthBlock block) {
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rootParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rootParams.bottomMargin = (int) getResources().getDimension(R.dimen.activity_vertical_margin);
        root.setLayoutParams(rootParams);

        String monthName = Month.of(block.month)
                .getDisplayName(TextStyle.FULL, new Locale("es", "ES"))
                .toUpperCase(new Locale("es", "ES"));

        TextView tvMonth = new TextView(requireContext());
        tvMonth.setText(getString(R.string.stats_calendar_distance_kcal,
                monthName + " " + block.year + "   " + formatDistance(block.distanceMeters),
                (int) block.calories));
        tvMonth.setTextSize(14f);
        tvMonth.setTextColor(requireContext().getColor(R.color.textPrimary));
        tvMonth.setTypeface(null, android.graphics.Typeface.BOLD);
        tvMonth.setPadding(0, 0, 0,
                (int) getResources().getDimension(R.dimen.stats_item_padding_vertical));
        root.addView(tvMonth);

        for (int i = 0; i < block.weeks.size(); i++) {
            StatsResumen.WeekBlock week = block.weeks.get(i);
            boolean isLast = (i == block.weeks.size() - 1);

            String monthShort = Month.of(block.month)
                    .getDisplayName(TextStyle.SHORT, new Locale("es", "ES"))
                    .toLowerCase(new Locale("es", "ES"));

            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rowParams.bottomMargin = 4;
            row.setLayoutParams(rowParams);

            TextView tvTree = new TextView(requireContext());
            tvTree.setText(isLast ? "└── " : "├── ");
            tvTree.setTextSize(13f);
            tvTree.setTextColor(requireContext().getColor(R.color.textTertiary));
            row.addView(tvTree);

            TextView tvRange = new TextView(requireContext());
            tvRange.setText(getString(R.string.stats_calendar_week_range,
                    week.startDay, week.endDay, monthShort));
            tvRange.setTextSize(13f);
            tvRange.setTextColor(requireContext().getColor(R.color.textSecondary));
            LinearLayout.LayoutParams rangeParams = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            tvRange.setLayoutParams(rangeParams);
            row.addView(tvRange);

            TextView tvStats = new TextView(requireContext());
            tvStats.setText(getString(R.string.stats_calendar_distance_kcal,
                    formatDistance(week.distanceMeters), (int) week.calories));
            tvStats.setTextSize(13f);
            tvStats.setTextColor(requireContext().getColor(R.color.textTertiary));
            row.addView(tvStats);

            root.addView(row);
        }

        return root;
    }

    // ── Diálogo cambio de objetivo ────────────────────────────────────────────

    private void showGoalDialog(boolean isWeekly) {
        String[] options = isWeekly
                ? new String[]{"10 km", "20 km", "30 km", "40 km", "50 km", "70 km", "100 km", "150 km", "200 km"}
                : new String[]{"50 km", "100 km", "150 km", "200 km", "250 km", "300 km", "400 km", "500 km"};

        long[] valoresMetros = isWeekly
                ? new long[]{10_000, 20_000, 30_000, 40_000, 50_000, 70_000, 100_000, 150_000, 200_000}
                : new long[]{50_000, 100_000, 150_000, 200_000, 250_000, 300_000, 400_000, 500_000};

        int titleRes = isWeekly
                ? R.string.stats_dialog_weekly_goal_title
                : R.string.stats_dialog_monthly_goal_title;

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(titleRes)
                .setItems(options, (dialog, which) -> {
                    long metros = valoresMetros[which];
                    if (isWeekly) {
                        viewModel.setWeeklyGoal(metros);
                    } else {
                        viewModel.setMonthlyGoal(metros);
                    }
                })
                .setNegativeButton(R.string.dialog_btn_cancel, null)
                .show();
    }

    // ── Acciones ──────────────────────────────────────────────────────────────

    private void onDeleteClick(@NonNull ActividadItem item) {
        if (item.isPendingSync()) {
            Snackbar.make(binding.getRoot(),
                    R.string.stats_delete_no_sync, Snackbar.LENGTH_LONG).show();
            return;
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.stats_delete_title)
                .setMessage(R.string.stats_delete_message)
                .setNegativeButton(R.string.dialog_btn_cancel, null)
                .setPositiveButton(R.string.stats_delete_confirm,
                        (dialog, which) -> viewModel.borrarActividad(item.localId))
                .show();
    }

    // ── Visibilidad de estados ────────────────────────────────────────────────

    private void showContent() {
        if (binding == null) return;
        binding.scrollContent.setVisibility(View.VISIBLE);
        binding.layoutEmpty.setVisibility(View.GONE);
    }

    private void showEmpty() {
        if (binding == null) return;
        binding.scrollContent.setVisibility(View.GONE);
        binding.layoutEmpty.setVisibility(View.VISIBLE);
    }

    private void updateEmptyState(@Nullable List<ActividadItem> items) {
        if (items != null && !items.isEmpty()) showContent();
    }

    // ── Formato ───────────────────────────────────────────────────────────────

    @NonNull
    private String formatDistance(long meters) {
        return getString(R.string.stats_format_km, meters / 1000.0f);
    }

    @NonNull
    private String formatDuration(long seconds) {
        long hours   = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        if (hours > 0L) {
            return getString(R.string.stats_format_time_hm, hours, minutes);
        }
        return getString(R.string.stats_format_time_m, minutes);
    }

    @NonNull
    private String formatKcal(long kcal) {
        return getString(R.string.stats_format_kcal, (int) kcal);
    }

    @NonNull
    private String formatStreak(int streakDays) {
        return getString(R.string.stats_format_streak, streakDays);
    }
}