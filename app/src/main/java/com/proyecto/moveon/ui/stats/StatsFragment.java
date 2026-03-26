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

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.slider.Slider;
import com.proyecto.moveon.R;
import com.proyecto.moveon.core.concurrency.MoveOnExecutors;
import com.proyecto.moveon.core.i18n.AppLanguageManager;
import com.proyecto.moveon.databinding.FragmentStatsBinding;
import com.proyecto.moveon.domain.activity.ActividadItem;
import com.proyecto.moveon.domain.activity.StatsCalculator;
import com.proyecto.moveon.domain.activity.StatsResumen;
import com.proyecto.moveon.ui.common.TopSnackbar;
import com.proyecto.moveon.ui.profile.ShareRouteFormatter;
import com.proyecto.moveon.ui.profile.ShareRouteImageGenerator;
import com.proyecto.moveon.ui.profile.ShareRoutePreviewBottomSheet;
import com.proyecto.moveon.ui.ranking.RankingFragment;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Fragmento de estadísticas y resumen histórico del usuario.
 *
 * <p>Incluye acciones de borrado y compartición de rutas. El flujo de share se ejecuta
 * fuera del hilo principal y, tras esta corrección, siempre restablece la marca interna
 * {@code isSharingInProgress} incluso si el fragment se desacopla durante la generación
 * de la imagen.</p>
 */
public class StatsFragment extends Fragment {

    private FragmentStatsBinding binding;
    private StatsViewModel viewModel;

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
        setupListeners();
        observeViewModel();
        viewModel.load();
    }

    @Override
    public void onDestroyView() {
        // Bugfix: si la vista se destruye en mitad de un share, la siguiente vista no debe heredar
        // un estado de "compartiendo" atascado de la instancia anterior.
        isSharingInProgress = false;
        super.onDestroyView();
        binding = null;
    }

    private void setupListeners() {
        binding.btnRetry.setOnClickListener(v -> viewModel.load());
        binding.tvWeeklyGoalHeader.setOnClickListener(v -> showGoalDialog(true));
        binding.tvMonthlyGoalHeader.setOnClickListener(v -> showGoalDialog(false));
        binding.cardHistory.setOnClickListener(v -> openUnifiedHistory());
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
        bindCard5RecentActivity(r);
        bindCard1Today(r);
        bindWeeklySection(r);
        bindCard7WeekComparison(r);
        bindMonthlySection(r);
        bindCard6MonthComparison(r);
        bindCard8Totals(r);
        bindHistoryHub(r);
    }

    private void bindCard1Today(@NonNull StatsResumen r) {
        binding.tvTodayDist.setText(formatDistance(r.todayDistanceMeters));
        binding.tvTodayTime.setText(formatDuration(r.todayDurationSeconds));
        binding.tvTodayKcal.setText(formatKcal(r.todayCalories));
    }

    private void bindWeeklySection(@NonNull StatsResumen r) {
        renderWeeklyChart(r.weekDaysDistanceMeters);

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

    private void bindMonthlySection(@NonNull StatsResumen r) {
        renderMonthlyChart(r);

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
        bindRecentActivityLabels();
        binding.tvTodayDistance.setText(formatDistance(r.todayDistanceMeters));
        binding.tvYesterdayDistance.setText(formatDistance(r.yesterdayDistanceMeters));
        binding.tvDay2Distance.setText(formatDistance(r.twoDaysAgoDistanceMeters));
    }

    private void bindRecentActivityLabels() {
        if (binding == null) return;

        final Locale locale = getAppLocale();
        final LocalDate today = LocalDate.now();

        binding.tvRecentDay0Label.setText(getString(R.string.stats_period_today));
        binding.tvRecentDay1Label.setText(formatWeekdayLabel(today.minusDays(1), locale));
        binding.tvRecentDay2Label.setText(formatWeekdayLabel(today.minusDays(2), locale));
    }

    @NonNull
    private Locale getAppLocale() {
        final Context localizedContext = AppLanguageManager.localizedContext(requireContext());
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            return localizedContext.getResources().getConfiguration().getLocales().get(0);
        }
        return localizedContext.getResources().getConfiguration().locale;
    }

    @NonNull
    private String formatWeekdayLabel(@NonNull LocalDate date, @NonNull Locale locale) {
        String label = date.getDayOfWeek().getDisplayName(TextStyle.FULL, locale);
        if (label.isEmpty()) {
            return label;
        }
        return label.substring(0, 1).toUpperCase(locale) + label.substring(1);
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

    private void bindHistoryHub(@NonNull StatsResumen r) {
        boolean hasHistory = !r.monthBlocks.isEmpty() || r.totalActivities > 0;
        binding.cardHistory.setEnabled(hasHistory);
        binding.cardHistory.setAlpha(hasHistory ? 1.0f : 0.5f);
    }

    private void renderWeeklyChart(@NonNull long[] distancias) {
        if (binding == null) return;
        String[] dias = getResources().getStringArray(R.array.stats_week_days_short);
        int todayIndex = LocalDate.now().getDayOfWeek().getValue() - 1;
        renderBarChart(binding.llChartBars, distancias, dias, todayIndex);
    }

    private void renderMonthlyChart(@NonNull StatsResumen resumen) {
        if (binding == null) return;
        binding.llMonthChartBars.removeAllViews();

        StatsResumen.MonthBlock current = findCurrentMonthBlock(resumen.monthBlocks);
        if (current == null || current.weeks.isEmpty()) return;

        List<StatsResumen.WeekBlock> weeks = current.weeks;
        long[] distances = new long[weeks.size()];
        String[] labels = new String[weeks.size()];

        for (int i = 0; i < weeks.size(); i++) {
            StatsResumen.WeekBlock week = weeks.get(i);
            distances[i] = week.distanceMeters;
            labels[i] = week.startDay == week.endDay
                    ? String.valueOf(week.startDay)
                    : week.startDay + "-" + week.endDay;
        }

        renderBarChart(binding.llMonthChartBars, distances, labels, -1);
    }

    @Nullable
    private StatsResumen.MonthBlock findCurrentMonthBlock(@NonNull List<StatsResumen.MonthBlock> blocks) {
        LocalDate now = LocalDate.now();
        for (StatsResumen.MonthBlock block : blocks) {
            if (block.year == now.getYear() && block.month == now.getMonthValue()) {
                return block;
            }
        }
        return blocks.isEmpty() ? null : blocks.get(0);
    }

    private void renderBarChart(@NonNull LinearLayout container,
                                @NonNull long[] values,
                                @NonNull String[] labels,
                                int highlightedIndex) {
        container.removeAllViews();
        if (values.length == 0 || labels.length != values.length) return;

        long maxVal = 1L;
        for (long value : values) {
            if (value > maxVal) maxVal = value;
        }

        int chartHeightPx = (int) getResources().getDimension(R.dimen.stats_chart_height);
        int barWidthPx = (int) getResources().getDimension(R.dimen.stats_chart_bar_width);
        int barRadiusPx = (int) getResources().getDimension(R.dimen.stats_chart_bar_radius);
        int labelSizePx = (int) getResources().getDimension(R.dimen.stats_chart_label_size);

        int colorActive = ContextCompat.getColor(requireContext(), R.color.greenPrimary);
        int colorInactive = ContextCompat.getColor(requireContext(), R.color.dividerColor);
        int colorLabel = ContextCompat.getColor(requireContext(), R.color.textTertiary);

        for (int i = 0; i < values.length; i++) {
            LinearLayout column = new LinearLayout(requireContext());
            column.setOrientation(LinearLayout.VERTICAL);
            column.setGravity(android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL);
            column.setLayoutParams(new LinearLayout.LayoutParams(0, chartHeightPx, 1f));

            View bar = new View(requireContext());
            int drawableMaxHeight = Math.max(1, chartHeightPx - labelSizePx * 2);
            int computedHeight = values[i] == 0L
                    ? barRadiusPx * 2
                    : (int) ((values[i] * (long) drawableMaxHeight) / maxVal);
            int barHeightPx = Math.max(barRadiusPx * 2, computedHeight);
            bar.setLayoutParams(new LinearLayout.LayoutParams(barWidthPx, barHeightPx));

            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.RECTANGLE);
            shape.setCornerRadii(new float[]{
                    barRadiusPx, barRadiusPx,
                    barRadiusPx, barRadiusPx,
                    0, 0,
                    0, 0
            });
            shape.setColor(highlightedIndex < 0 || i == highlightedIndex ? colorActive : colorInactive);
            bar.setBackground(shape);

            TextView label = new TextView(requireContext());
            label.setText(labels[i]);
            label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, labelSizePx);
            label.setTextColor(colorLabel);
            label.setGravity(android.view.Gravity.CENTER);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            labelParams.topMargin = barRadiusPx;
            label.setLayoutParams(labelParams);

            column.addView(bar);
            column.addView(label);
            container.addView(column);
        }
    }

    private void showGoalDialog(boolean isWeekly) {
        final long currentMeters;
        if (lastResumen != null) {
            currentMeters = isWeekly ? lastResumen.weeklyGoalMeters : lastResumen.monthlyGoalMeters;
        } else {
            currentMeters = isWeekly
                    ? StatsCalculator.DEFAULT_WEEKLY_GOAL_METERS
                    : StatsCalculator.DEFAULT_MONTHLY_GOAL_METERS;
        }

        final float valueFrom = isWeekly ? 5f : 10f;
        final float valueTo = isWeekly ? 200f : 500f;
        final float stepSize = isWeekly ? 5f : 10f;

        float currentKm = currentMeters / 1000f;
        float clampedKm = Math.max(valueFrom, Math.min(valueTo, currentKm));
        float initialValue = Math.round(clampedKm / stepSize) * stepSize;
        initialValue = Math.max(valueFrom, Math.min(valueTo, initialValue));

        Context context = requireContext();
        View content = LayoutInflater.from(context).inflate(R.layout.dialog_goal_slider, null, false);

        TextView tvValue = content.findViewById(R.id.tvGoalSliderValue);
        Slider slider = content.findViewById(R.id.sliderGoal);

        tvValue.setText(getString(R.string.stats_format_km, initialValue));

        slider.setValueFrom(valueFrom);
        slider.setValueTo(valueTo);
        slider.setStepSize(stepSize);
        slider.setValue(initialValue);

        slider.addOnChangeListener((s, value, fromUser) ->
                tvValue.setText(getString(R.string.stats_format_km, value)));

        int titleRes = isWeekly
                ? R.string.stats_dialog_weekly_goal_title
                : R.string.stats_dialog_monthly_goal_title;

        TextView titleView = new TextView(context);
        titleView.setText(titleRes);
        titleView.setTextColor(ContextCompat.getColor(context, R.color.textPrimary));
        titleView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f);
        titleView.setTypeface(null, android.graphics.Typeface.NORMAL);
        titleView.setPadding(dp(24), dp(20), dp(24), dp(4));

        new MaterialAlertDialogBuilder(context)
                .setCustomTitle(titleView)
                .setView(content)
                .setNegativeButton(R.string.dialog_btn_cancel, null)
                .setPositiveButton(R.string.dialog_btn_save, (dialog, which) -> {
                    long selectedMeters = (long) slider.getValue() * 1_000L;
                    if (isWeekly) {
                        viewModel.setWeeklyGoal(selectedMeters);
                    } else {
                        viewModel.setMonthlyGoal(selectedMeters);
                    }
                })
                .show();
    }

    private void openUnifiedHistory() {
        if (lastResumen == null || lastResumen.monthBlocks.isEmpty()) return;
        List<ActividadItem> actividades = viewModel.getAllActividades().getValue();
        if (actividades == null) actividades = Collections.emptyList();
        HistorialBottomSheet.newInstance(lastResumen.monthBlocks, actividades)
                .show(getChildFragmentManager(), "historial");
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    void onDeleteClickPublic(@NonNull ActividadItem item) {
        onDeleteClick(item);
    }

    void onShareClickPublic(@NonNull ActividadItem item) {
        onShareClick(item);
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

    @SuppressWarnings("resource")
    private void onShareClick(@NonNull ActividadItem item) {
        if (binding == null || isSharingInProgress) return;

        isSharingInProgress = true;

        final Context localizedContext = AppLanguageManager.localizedContext(requireContext());
        MoveOnExecutors.io().execute(() -> {
            try {
                Uri uri = ShareRouteImageGenerator.generateShareImage(localizedContext, item);
                String shareText = ShareRouteFormatter.buildShareText(localizedContext, item);

                FragmentActivity activity = getActivity();
                if (activity == null) {
                    // Bugfix: el fragment puede quedar desacoplado mientras el trabajo en background
                    // sigue ejecutándose. Restablecemos la flag manualmente para evitar un bloqueo
                    // permanente de nuevos intentos de share en esta instancia.
                    isSharingInProgress = false;
                    return;
                }

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

    /**
     * Restablece el estado interno del flujo de share y muestra el error si la vista sigue activa.
     *
     * <p>Bugfix: si la activity ya no existe, igualmente se limpia la flag para que la instancia del
     * fragment no quede inutilizable al volver a la pestaña.</p>
     */
    private void handleShareError(int messageRes) {
        FragmentActivity activity = getActivity();
        if (activity == null) {
            isSharingInProgress = false;
            return;
        }
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
        if (kcal >= 1_000_000L) {
            return getString(R.string.stats_format_kcal_m, kcal / 1_000_000.0f);
        }
        if (kcal >= 1_000L) {
            return getString(R.string.stats_format_kcal_k, kcal / 1_000.0f);
        }
        return getString(R.string.stats_format_kcal, (int) kcal);
    }

    @NonNull
    private String formatStreak(int streakDays) {
        return getString(R.string.stats_format_streak, streakDays);
    }
}