package com.proyecto.moveon.ui.stats;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.proyecto.moveon.R;
import com.proyecto.moveon.app.ServiceLocator;
import com.proyecto.moveon.core.api.ApiError;
import com.proyecto.moveon.data.activities.ActivityRepository;
import com.proyecto.moveon.data.local.entity.UserPrefsEntity;
import com.proyecto.moveon.data.profile.UserPrefsRepository;
import com.proyecto.moveon.data.session.SecureSessionManager;
import com.proyecto.moveon.domain.activity.ActividadItem;
import com.proyecto.moveon.domain.activity.StatsCalculator;
import com.proyecto.moveon.domain.activity.StatsResumen;
import com.proyecto.moveon.ui.common.Event;
import com.proyecto.moveon.ui.common.UiState;

import java.util.Collections;
import java.util.List;

/**
 * ViewModel del módulo de Estadísticas.

 * Dos fuentes reactivas:
 * 1. {@link ActivityRepository#observeActividades} — se recalcula al cambiar cualquier actividad.
 * 2. {@link UserPrefsRepository#observe} — se recalcula al cambiar los objetivos semanal/mensual.

 * Los cálculos estadísticos se delegan a {@link StatsCalculator}.
 */
public class StatsViewModel extends AndroidViewModel {

    // ── LiveData expuesto ─────────────────────────────────────────────────────

    /** Estado principal: loading / success(StatsResumen) / error. */
    private final MediatorLiveData<UiState<StatsResumen>> statsState = new MediatorLiveData<>();

    /** Lista reactiva de actividades para el historial reciente. */
    private final MediatorLiveData<List<ActividadItem>> actividades = new MediatorLiveData<>();

    /** Evento puntual del resultado del borrado — consumo único en el Fragment. */
    private final MutableLiveData<Event<UiState<String>>> deleteEvent = new MutableLiveData<>();

    // Estado interno compartido entre las dos fuentes

    @NonNull  private List<ActividadItem> lastItems = Collections.emptyList();
    private long lastWeeklyGoal  = StatsCalculator.DEFAULT_WEEKLY_GOAL_METERS;
    private long lastMonthlyGoal = StatsCalculator.DEFAULT_MONTHLY_GOAL_METERS;

    // ── Dependencias ──────────────────────────────────────────────────────────

    private final ActivityRepository    actividadRepository;
    private final UserPrefsRepository   userPrefsRepository;

    @Nullable private final String accountKey;

    @Nullable private LiveData<List<ActividadItem>> actividadesSource;
    @Nullable private LiveData<UserPrefsEntity>     prefsSource;

    // ── Constructor ───────────────────────────────────────────────────────────

    public StatsViewModel(@NonNull Application application) {
        super(application);
        // MEJ-01: Creación centralizada vía ServiceLocator.
        ServiceLocator locator = ServiceLocator.getInstance(application);
        actividadRepository = locator.newActivityRepository();
        userPrefsRepository = locator.getUserPrefsRepository();
        SecureSessionManager sessionManager = SecureSessionManager.getInstance(application);
        accountKey = sessionManager.getAccountKey();

        attachSources();
    }

    // ── Exposición de LiveData ────────────────────────────────────────────────

    @NonNull
    public LiveData<UiState<StatsResumen>> getStatsState() {
        return statsState;
    }

    @NonNull
    public LiveData<List<ActividadItem>> getActividades() {
        return actividades;
    }

    @NonNull
    public LiveData<Event<UiState<String>>> getDeleteEvent() {
        return deleteEvent;
    }

    // ── Acciones ──────────────────────────────────────────────────────────────

    /**
     * Carga inicial: muestra loading si no hay datos locales,
     * encola sync de pendientes y refresca desde servidor.
     */
    public void load() {
        if (accountKey == null) {
            statsState.setValue(UiState.error(ApiError.local(
                    getApplication().getString(R.string.vm_error_generico))));
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

    /**
     * Cambia el objetivo semanal: guarda en Room y sincroniza con el servidor.
     *
     * @param meters nuevo objetivo en metros
     */
    public void setWeeklyGoal(long meters) {
        if (accountKey == null) return;
        userPrefsRepository.setWeeklyGoal(accountKey, meters);
    }

    /**
     * Cambia el objetivo mensual: guarda en Room y sincroniza con el servidor.
     *
     * @param meters nuevo objetivo en metros
     */
    public void setMonthlyGoal(long meters) {
        if (accountKey == null) return;
        userPrefsRepository.setMonthlyGoal(accountKey, meters);
    }

    /**
     * Elimina una actividad individual (solo permite SYNCED).
     * El resultado se emite como {@link Event} para consumo único en el Fragment.
     */
    public void borrarActividad(@NonNull String localId) {
        actividadRepository.borrarActividad(localId, result -> {
            if (result.isSuccess()) {
                deleteEvent.postValue(new Event<>(UiState.success(
                        getApplication().getString(R.string.stats_delete_ok))));
            } else {
                ApiError error = result.error != null
                        ? result.error
                        : ApiError.local(getApplication().getString(R.string.stats_delete_error));
                deleteEvent.postValue(new Event<>(UiState.error(error)));
            }
        });
    }

    // ── Privados ──────────────────────────────────────────────────────────────

    /**
     * Conecta las dos fuentes reactivas (actividades + preferencias) con el MediatorLiveData.
     * Cada vez que cualquiera de las dos cambia, se recalcula el StatsResumen completo.
     */
    private void attachSources() {
        if (accountKey == null) {
            actividades.setValue(Collections.emptyList());
            statsState.setValue(UiState.success(
                    StatsResumen.empty(
                            StatsCalculator.DEFAULT_WEEKLY_GOAL_METERS,
                            StatsCalculator.DEFAULT_MONTHLY_GOAL_METERS)));
            return;
        }

        // Fuente 1: actividades
        actividadesSource = actividadRepository.observeActividades(accountKey);
        statsState.addSource(actividadesSource, items -> {
            lastItems = items != null ? items : Collections.emptyList();
            actividades.setValue(lastItems);
            recalcular();
        });

        // Fuente 2: preferencias (objetivos)
        prefsSource = userPrefsRepository.observe(accountKey);
        statsState.addSource(prefsSource, prefs -> {
            if (prefs != null) {
                lastWeeklyGoal  = prefs.weeklyGoalMeters;
                lastMonthlyGoal = prefs.monthlyGoalMeters;
            } else {
                lastWeeklyGoal  = StatsCalculator.DEFAULT_WEEKLY_GOAL_METERS;
                lastMonthlyGoal = StatsCalculator.DEFAULT_MONTHLY_GOAL_METERS;
            }
            recalcular();
        });
    }

    /** Recalcula el resumen con el estado más reciente de ambas fuentes. */
    private void recalcular() {
        statsState.setValue(UiState.success(
                StatsCalculator.calcular(lastItems, lastWeeklyGoal, lastMonthlyGoal)));
    }

    // ── Ciclo de vida ─────────────────────────────────────────────────────────

    @Override
    protected void onCleared() {
        if (actividadesSource != null) statsState.removeSource(actividadesSource);
        if (prefsSource != null)       statsState.removeSource(prefsSource);
        actividadRepository.cancelAll();
        super.onCleared();
    }
}
