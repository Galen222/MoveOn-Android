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

import java.util.List;

public class StatsFragment extends Fragment {

    private FragmentStatsBinding binding;
    private StatsViewModel viewModel;
    private ActividadAdapter adapter;

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
    }

    // ── Observadores ──────────────────────────────────────────────────────────

    private void observeViewModel() {
        viewModel.getStatsState().observe(getViewLifecycleOwner(), state -> {
            if (state == null || binding == null) return;

            binding.layoutLoading.setVisibility(state.loading ? View.VISIBLE : View.GONE);

            if (state.data != null) {
                bindSummary(state.data);
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

        viewModel.getDistanciaSemanal().observe(getViewLifecycleOwner(), distancias -> {
            if (binding == null || distancias == null) return;
            renderChart(distancias);
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
                        state.error.getMessage(),
                        Snackbar.LENGTH_LONG).show();
            }
        });
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

    // ── Binding de datos ──────────────────────────────────────────────────────

    private void bindSummary(@NonNull StatsResumen summary) {
        if (binding == null) return;

        binding.tvDistance.setText(formatDistance(summary.totalDistanceMeters));
        binding.tvTime.setText(formatDuration(summary.totalDurationSeconds));
        binding.tvStreak.setText(formatStreak(summary.streakDays));

        int progress = summary.weeklyGoalMeters > 0
                ? (int) Math.min(100L, (summary.weeklyDistanceMeters * 100L) / summary.weeklyGoalMeters)
                : 0;

        binding.progressWeeklyGoal.setProgress(progress, true);
        binding.tvCurrentProgress.setText(formatDistance(summary.weeklyDistanceMeters));
        binding.tvGoalTarget.setText(formatDistance(summary.weeklyGoalMeters));

        long remaining = Math.max(0L, summary.weeklyGoalMeters - summary.weeklyDistanceMeters);
        if (remaining > 0) {
            binding.tvGoalRemaining.setText(
                    getString(R.string.stats_goal_remaining_format, formatDistance(remaining)));
        } else {
            binding.tvGoalRemaining.setText(R.string.stats_weekly_goal_done);
        }

        binding.tvTodayDistance.setText(formatDistance(summary.todayDistanceMeters));
        binding.tvYesterdayDistance.setText(formatDistance(summary.yesterdayDistanceMeters));
        binding.tvDay2Distance.setText(formatDistance(summary.twoDaysAgoDistanceMeters));

        binding.tvCurrentMonth.setText(formatDistance(summary.currentMonthDistanceMeters));
        binding.tvPreviousMonth.setText(formatDistance(summary.previousMonthDistanceMeters));
    }

    // ── Gráfico semanal ───────────────────────────────────────────────────────

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

        String[] dias = getResources().getStringArray(R.array.stats_week_days_short);

        int colorActive   = requireContext().getColor(R.color.greenPrimary);
        int colorInactive = requireContext().getColor(R.color.dividerColor);
        int colorLabel    = requireContext().getColor(R.color.textTertiary);

        int todayIndex = java.time.LocalDate.now().getDayOfWeek().getValue() - 1;

        for (int i = 0; i < 7; i++) {
            LinearLayout column = new LinearLayout(requireContext());
            column.setOrientation(LinearLayout.VERTICAL);
            column.setGravity(android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL);

            LinearLayout.LayoutParams colParams = new LinearLayout.LayoutParams(
                    0, chartHeightPx, 1f);
            column.setLayoutParams(colParams);

            // Barra
            View bar = new View(requireContext());
            int barHeightPx = distancias[i] == 0
                    ? barRadiusPx * 2
                    : (int) ((distancias[i] * (chartHeightPx - labelSizeSp * 2)) / maxVal);

            LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                    barWidthPx, barHeightPx);
            bar.setLayoutParams(barParams);

            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.RECTANGLE);
            shape.setCornerRadii(new float[]{
                    barRadiusPx, barRadiusPx, barRadiusPx, barRadiusPx, 0, 0, 0, 0});
            shape.setColor(i == todayIndex ? colorActive : colorInactive);
            bar.setBackground(shape);

            // Etiqueta día
            TextView label = new TextView(requireContext());
            label.setText(dias[i]);
            label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, labelSizeSp);
            label.setTextColor(colorLabel);
            label.setGravity(android.view.Gravity.CENTER);

            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            labelParams.topMargin = barRadiusPx;
            label.setLayoutParams(labelParams);

            column.addView(bar);
            column.addView(label);
            container.addView(column);
        }
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
        if (items != null && !items.isEmpty()) {
            showContent();
        }
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
    private String formatStreak(int streakDays) {
        return getString(R.string.stats_format_streak, streakDays);
    }
}