package com.proyecto.moveon.ui.stats;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.proyecto.moveon.databinding.FragmentStatsBinding;
import com.proyecto.moveon.domain.activity.StatsResumen;

import java.util.Locale;

public class StatsFragment extends Fragment {

    private FragmentStatsBinding binding;
    private StatsViewModel viewModel;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentStatsBinding.inflate(inflater, container, false);
        viewModel = new ViewModelProvider(this).get(StatsViewModel.class);

        bindSummary(StatsResumen.empty(StatsViewModel.DEFAULT_WEEKLY_GOAL_METERS));
        observeViewModel();
        viewModel.load();

        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void observeViewModel() {
        viewModel.getStatsState().observe(getViewLifecycleOwner(), state -> {
            if (state == null || binding == null) return;
            if (state.data != null) {
                bindSummary(state.data);
            } else if (state.error != null) {
                bindSummary(StatsResumen.empty(StatsViewModel.DEFAULT_WEEKLY_GOAL_METERS));
            }
        });
    }

    private void bindSummary(@NonNull StatsResumen summary) {
        if (binding == null) return;

        binding.tvDistance.setText(formatDistance(summary.totalDistanceMeters));
        binding.tvTime.setText(formatDuration(summary.totalDurationSeconds));
        binding.tvStreak.setText(formatStreak(summary.streakDays));

        int progress = summary.weeklyGoalMeters > 0
                ? (int) Math.min(100L, (summary.weeklyDistanceMeters * 100L) / summary.weeklyGoalMeters)
                : 0;

        binding.progressWeeklyGoal.setProgress(progress);
        binding.tvCurrentProgress.setText(formatDistance(summary.weeklyDistanceMeters));
        binding.tvGoalTarget.setText(formatDistance(summary.weeklyGoalMeters));

        long remaining = Math.max(0L, summary.weeklyGoalMeters - summary.weeklyDistanceMeters);
        if (remaining > 0) {
            binding.tvGoalRemaining.setText("Faltan " + formatDistance(remaining));
        } else {
            binding.tvGoalRemaining.setText("Objetivo semanal completado");
        }

        binding.tvTodayDistance.setText(formatDistance(summary.todayDistanceMeters));
        binding.tvYesterdayDistance.setText(formatDistance(summary.yesterdayDistanceMeters));
        binding.tvDay2Distance.setText(formatDistance(summary.twoDaysAgoDistanceMeters));

        binding.tvCurrentMonth.setText(formatDistance(summary.currentMonthDistanceMeters));
        binding.tvPreviousMonth.setText(formatDistance(summary.previousMonthDistanceMeters));
    }

    @NonNull
    private String formatDistance(long meters) {
        return String.format(Locale.getDefault(), "%.1f km", meters / 1000.0d);
    }

    @NonNull
    private String formatDuration(long seconds) {
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;

        if (hours > 0L) {
            return String.format(Locale.getDefault(), "%dh %02dm", hours, minutes);
        }
        return String.format(Locale.getDefault(), "%dm", minutes);
    }

    @NonNull
    private String formatStreak(int streakDays) {
        return streakDays == 1 ? "1 día" : streakDays + " días";
    }
}
