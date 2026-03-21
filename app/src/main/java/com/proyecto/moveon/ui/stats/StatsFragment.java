package com.proyecto.moveon.ui.stats;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.proyecto.moveon.R;
import com.proyecto.moveon.core.concurrency.MoveOnExecutors;
import com.proyecto.moveon.core.i18n.AppLanguageManager;
import com.proyecto.moveon.databinding.FragmentStatsBinding;
import com.proyecto.moveon.domain.activity.ActividadItem;
import com.proyecto.moveon.domain.activity.StatsResumen;
import com.proyecto.moveon.ui.common.TopSnackbar;
import com.proyecto.moveon.ui.profile.ShareRouteFormatter;
import com.proyecto.moveon.ui.profile.ShareRouteImageGenerator;
import com.proyecto.moveon.ui.profile.ShareRoutePreviewBottomSheet;
import com.proyecto.moveon.ui.ranking.RankingFragment;

import java.time.LocalDate;
import java.util.List;

public class StatsFragment extends Fragment {

    private FragmentStatsBinding binding;
    private StatsViewModel viewModel;
    private ActividadAdapter adapter;

    @Nullable private StatsResumen lastResumen = null;
    private boolean isSharingInProgress = false;

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

    private void setupRecyclerView() {
        adapter = new ActividadAdapter(this::onDeleteClick, this::onShareClick);
        binding.rvHistory.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvHistory.setAdapter(adapter);
        binding.rvHistory.setHasFixedSize(false);
    }

    private void setupListeners() {
        binding.btnRetry.setOnClickListener(v -> viewModel.load());
        binding.tvWeeklyGoalHeader.setOnClickListener(v -> showGoalDialog(true));
        binding.tvMonthlyGoalHeader.setOnClickListener(v -> showGoalDialog(false));

        binding.cardCalendar.setOnClickListener(v -> {
            if (lastResumen == null || lastResumen.monthBlocks.isEmpty()) return;
            HistorialBottomSheet.newInstance(lastResumen.monthBlocks)
                    .show(getChildFragmentManager(), "historial");
        });

        binding.cardRanking.setOnClickListener(v ->
                RankingFragment.newInstance(null)
                        .show(getChildFragmentManager(), RankingFragment.TAG));
    }

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
                TopSnackbar.success(binding.getRoot(), getString(R.string.stats_delete_ok));
            } else if (state.error != null) {
                TopSnackbar.error(binding.getRoot(), state.error.getMessage());
            }
        });
    }

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
        bindCard9Historial(r);
    }

    private void bindCard1Today(@NonNull StatsResumen r) {
        binding.tvTodayDist.setText(formatDistance(r.todayDistanceMeters));
        binding.tvTodayTime.setText(formatDuration(r.todayDurationSeconds));
        binding.tvTodayKcal.setText(formatKcal(r.todayCalories));
    }

    private void bindCard3WeeklyGoal(@NonNull StatsResumen r) {
        int progress = r.weeklyGoalMeters > 0
                ? (int) Math.min(100L, (r.weeklyDistanceMeters * 100L) / r.weeklyGoalMeters) : 0;
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
                ? (int) Math.min(100L, (r.currentMonthDistanceMeters * 100L) / r.monthlyGoalMeters) : 0;
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

    private void bindCard9Historial(@NonNull StatsResumen r) {
        if (binding == null) return;
        boolean tieneHistorial = !r.monthBlocks.isEmpty();
        binding.cardCalendar.setClickable(tieneHistorial);
        binding.cardCalendar.setAlpha(tieneHistorial ? 1.0f : 0.5f);
    }

    private void renderChart(@NonNull long[] distancias) {
        if (binding == null) return;
        LinearLayout container = binding.llChartBars;
        container.removeAllViews();

        long maxVal = 1L;
        for (long d : distancias) if (d > maxVal) maxVal = d;

        int chartHeightPx = (int) getResources().getDimension(R.dimen.stats_chart_height);
        int barWidthPx    = (int) getResources().getDimension(R.dimen.stats_chart_bar_width);
        int barRadiusPx   = (int) getResources().getDimension(R.dimen.stats_chart_bar_radius);
        int labelSizeSp   = (int) getResources().getDimension(R.dimen.stats_chart_label_size);

        String[] dias     = getResources().getStringArray(R.array.stats_week_days_short);
        int colorActive   = ContextCompat.getColor(requireContext(), R.color.greenPrimary);
        int colorInactive = ContextCompat.getColor(requireContext(), R.color.dividerColor);
        int colorLabel    = ContextCompat.getColor(requireContext(), R.color.textTertiary);
        int todayIndex    = LocalDate.now().getDayOfWeek().getValue() - 1;

        for (int i = 0; i < 7; i++) {
            LinearLayout column = new LinearLayout(requireContext());
            column.setOrientation(LinearLayout.VERTICAL);
            column.setGravity(android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL);
            column.setLayoutParams(new LinearLayout.LayoutParams(0, chartHeightPx, 1f));

            View bar = new View(requireContext());
            int barHeightPx = distancias[i] == 0
                    ? barRadiusPx * 2
                    : (int) ((distancias[i] * (chartHeightPx - labelSizeSp * 2)) / maxVal);
            bar.setLayoutParams(new LinearLayout.LayoutParams(barWidthPx, barHeightPx));

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
                    if (isWeekly) viewModel.setWeeklyGoal(valoresMetros[which]);
                    else          viewModel.setMonthlyGoal(valoresMetros[which]);
                })
                .setNegativeButton(R.string.dialog_btn_cancel, null)
                .show();
    }

    private void onDeleteClick(@NonNull ActividadItem item) {
        if (item.isPendingSync()) {
            TopSnackbar.warning(binding.getRoot(), getString(R.string.stats_delete_no_sync));
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

    @SuppressWarnings("resource") // MoveOnExecutors.io() es un executor compartido — no se debe cerrar
    private void onShareClick(@NonNull ActividadItem item) {
        if (binding == null || isSharingInProgress) return;

        isSharingInProgress = true;

        final Context localizedContext = AppLanguageManager.localizedContext(requireContext());
        MoveOnExecutors.io().execute(() -> {
            try {
                Uri uri = ShareRouteImageGenerator.generateShareImage(localizedContext, item);
                String shareText = ShareRouteFormatter.buildShareText(localizedContext, item);

                FragmentActivity activity = getActivity();
                if (activity == null) return;

                activity.runOnUiThread(() -> {
                    isSharingInProgress = false;
                    if (binding == null || !isAdded()) return;
                    if (getChildFragmentManager().isStateSaved()) {
                        TopSnackbar.error(binding.getRoot(),
                                getString(R.string.share_routes_error_opening_preview));
                        return;
                    }
                    ShareRoutePreviewBottomSheet.newInstance(uri, shareText)
                            .show(getChildFragmentManager(), ShareRoutePreviewBottomSheet.TAG);
                });
            } catch (IllegalArgumentException e) {
                handleShareError(R.string.share_routes_error_no_polyline);
            } catch (Exception e) {
                handleShareError(R.string.share_routes_error_generating_image);
            }
        });
    }

    private void handleShareError(int messageRes) {
        FragmentActivity activity = getActivity();
        if (activity == null) return;
        activity.runOnUiThread(() -> {
            isSharingInProgress = false;
            if (binding != null) {
                TopSnackbar.error(binding.getRoot(), getString(messageRes));
            }
        });
    }

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

    @NonNull private String formatStreak(int streakDays) {
        return getString(R.string.stats_format_streak, streakDays);
    }
}