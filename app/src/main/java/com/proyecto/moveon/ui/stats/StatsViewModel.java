package com.proyecto.moveon.ui.stats;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

import com.proyecto.moveon.core.api.ApiError;
import com.proyecto.moveon.data.activities.ActivityRepository;
import com.proyecto.moveon.data.session.SecureSessionManager;
import com.proyecto.moveon.domain.activity.ActividadItem;
import com.proyecto.moveon.domain.activity.StatsResumen;
import com.proyecto.moveon.ui.common.UiState;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class StatsViewModel extends AndroidViewModel {

    public static final long DEFAULT_WEEKLY_GOAL_METERS = 50_000L;

    private final ActivityRepository actividadRepository;
    private final SecureSessionManager sessionManager;
    private final MediatorLiveData<UiState<StatsResumen>> statsState = new MediatorLiveData<>();

    @Nullable private final String accountKey;
    @Nullable private LiveData<List<ActividadItem>> source;

    public StatsViewModel(@NonNull Application application) {
        super(application);
        actividadRepository = new ActivityRepository(application);
        sessionManager = new SecureSessionManager(application);
        accountKey = ActivityRepository.buildAccountKey(sessionManager.getUsername());

        attachSource();
    }

    public LiveData<UiState<StatsResumen>> getStatsState() {
        return statsState;
    }

    public void load() {
        if (accountKey == null) {
            statsState.setValue(UiState.error(ApiError.local("No hay sesión activa")));
            return;
        }

        UiState<StatsResumen> current = statsState.getValue();
        if (current == null || current.data == null) {
            statsState.setValue(UiState.loading());
        }

        actividadRepository.enqueueSync();
        actividadRepository.refreshFromServer(accountKey, error -> {
            UiState<StatsResumen> latest = statsState.getValue();
            if (error != null && (latest == null || latest.data == null)) {
                statsState.postValue(UiState.error(error));
            }
        });
    }

    private void attachSource() {
        if (accountKey == null) return;

        source = actividadRepository.observeActividades(accountKey);
        statsState.addSource(source, items -> {
            StatsResumen summary = buildSummary(items);
            statsState.setValue(UiState.success(summary));
        });
    }

    @NonNull
    private StatsResumen buildSummary(@Nullable List<ActividadItem> items) {
        if (items == null || items.isEmpty()) {
            return StatsResumen.empty(DEFAULT_WEEKLY_GOAL_METERS);
        }

        long totalDistance = 0L;
        long totalDuration = 0L;
        int totalActivities = items.size();

        LocalDate today = LocalDate.now();
        YearMonth currentMonth = YearMonth.from(today);
        YearMonth previousMonth = currentMonth.minusMonths(1);

        Map<LocalDate, Long> distanceByDate = new HashMap<>();
        Set<LocalDate> activeDates = new HashSet<>();

        long currentMonthDistance = 0L;
        long previousMonthDistance = 0L;
        long weeklyDistance = 0L;

        for (ActividadItem item : items) {
            totalDistance += item.distanciaMetros;
            totalDuration += item.duracionSegundos;

            LocalDate activityDate = parseDate(item.fechaRutaIso);
            if (activityDate == null) continue;

            distanceByDate.put(
                    activityDate,
                    distanceByDate.getOrDefault(activityDate, 0L) + item.distanciaMetros
            );
            activeDates.add(activityDate);

            YearMonth ym = YearMonth.from(activityDate);
            if (currentMonth.equals(ym)) {
                currentMonthDistance += item.distanciaMetros;
            } else if (previousMonth.equals(ym)) {
                previousMonthDistance += item.distanciaMetros;
            }

            if (!activityDate.isBefore(today.minusDays(6)) && !activityDate.isAfter(today)) {
                weeklyDistance += item.distanciaMetros;
            }
        }

        long todayDistance = distanceByDate.getOrDefault(today, 0L);
        long yesterdayDistance = distanceByDate.getOrDefault(today.minusDays(1), 0L);
        long twoDaysAgoDistance = distanceByDate.getOrDefault(today.minusDays(2), 0L);

        int streak = 0;
        LocalDate cursor = today;
        while (activeDates.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }

        return new StatsResumen(
                totalActivities,
                totalDistance,
                totalDuration,
                streak,
                todayDistance,
                yesterdayDistance,
                twoDaysAgoDistance,
                currentMonthDistance,
                previousMonthDistance,
                weeklyDistance,
                DEFAULT_WEEKLY_GOAL_METERS
        );
    }

    @Nullable
    private LocalDate parseDate(@Nullable String iso) {
        if (iso == null || iso.trim().isEmpty()) return null;

        try {
            return OffsetDateTime.parse(iso).toLocalDate();
        } catch (Exception ignored) {
        }

        try {
            if (iso.length() >= 10) {
                return LocalDate.parse(iso.substring(0, 10));
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    @Override
    protected void onCleared() {
        if (source != null) {
            statsState.removeSource(source);
        }
        actividadRepository.cancelAll();
        super.onCleared();
    }
}
