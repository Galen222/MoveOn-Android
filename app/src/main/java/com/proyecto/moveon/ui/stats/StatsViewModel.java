
package com.proyecto.moveon.ui.stats;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.proyecto.moveon.R;
import com.proyecto.moveon.core.i18n.AppLanguageManager;
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
 * ViewModel del módulo de estadísticas.
 *
 * <p>Combina dos fuentes reactivas:</p>
 * <ol>
 *   <li>{@link ActivityRepository#observeActividades(String)} para recalcular cuando cambia
 *   cualquier actividad.</li>
 *   <li>{@link UserPrefsRepository#observe(String)} para recalcular cuando cambian los
 *   objetivos semanal o mensual.</li>
 * </ol>
 *
 * <p>Los cálculos puros se delegan en {@link StatsCalculator} y este ViewModel se limita a
 * orquestar carga, borrado y exposición de {@link UiState} para la UI.</p>
 */
public class StatsViewModel extends AndroidViewModel {

    // ── LiveData expuesto ─────────────────────────────────────────────────────

    /** Estado principal: loading / success(StatsResumen) / error. */
    private final MediatorLiveData<UiState<StatsResumen>> statsState = new MediatorLiveData<>();

    /** Lista completa de actividades (para el bottom sheet "Ver todas"). */
    private final MediatorLiveData<List<ActividadItem>> allActividades = new MediatorLiveData<>();

    /** Evento puntual del resultado del borrado — consumo único en el Fragment. */
    private final MutableLiveData<Event<UiState<String>>> deleteEvent = new MutableLiveData<>();

    // Estado interno compartido entre las dos fuentes

    @NonNull
    private List<ActividadItem> lastItems = Collections.emptyList();
    private long lastWeeklyGoal = StatsCalculator.DEFAULT_WEEKLY_GOAL_METERS;
    private long lastMonthlyGoal = StatsCalculator.DEFAULT_MONTHLY_GOAL_METERS;

    // ── Dependencias ──────────────────────────────────────────────────────────

    private final ActivityRepository actividadRepository;
    private final UserPrefsRepository userPrefsRepository;

    @Nullable private final String accountKey;

    @Nullable private LiveData<List<ActividadItem>> actividadesSource;
    @Nullable private LiveData<UserPrefsEntity> prefsSource;

    // ── Constructor ───────────────────────────────────────────────────────────

    /**
     * Crea el ViewModel y conecta las fuentes reactivas de actividades y preferencias.
     *
     * @param application aplicación usada para resolver repositorios y sesión activa.
     */
    public StatsViewModel(@NonNull Application application) {
        super(application);
        ServiceLocator locator = ServiceLocator.getInstance(application);
        actividadRepository = locator.newActivityRepository();
        userPrefsRepository = locator.getUserPrefsRepository();
        SecureSessionManager sessionManager = SecureSessionManager.getInstance(application);
        accountKey = sessionManager.getAccountKey();

        attachSources();
    }

    // ── Exposición de LiveData ────────────────────────────────────────────────

    /**
     * Expone el resumen estadístico recalculado con las últimas actividades y objetivos.
     *
     * @return {@link LiveData} con el {@link UiState} del {@link StatsResumen} actual.
     */
    @NonNull
    public LiveData<UiState<StatsResumen>> getStatsState() {
        return statsState;
    }


    /**
     * Expone el historial completo para componentes como {@link TodasActividadesBottomSheet}.
     *
     * @return {@link LiveData} con la lista íntegra de actividades locales.
     */
    @NonNull
    public LiveData<List<ActividadItem>> getAllActividades() {
        return allActividades;
    }

    /**
     * Expone el resultado puntual de una operación de borrado.
     *
     * @return flujo de {@link Event} para mostrar feedback de eliminación una sola vez.
     */
    @NonNull
    public LiveData<Event<UiState<String>>> getDeleteEvent() {
        return deleteEvent;
    }

    // ── Acciones ──────────────────────────────────────────────────────────────

    /**
     * Lanza la carga inicial del módulo de estadísticas.
     *
     * <p>Si todavía no hay resumen local visible, emite {@link UiState#loading()}; después
     * pide sincronizar pendientes y refresca desde servidor para consolidar la caché.</p>
     */
    public void load() {
        if (accountKey == null) {
            statsState.setValue(UiState.error(ApiError.local(
                    AppLanguageManager.getString(getApplication(), R.string.vm_error_generico))));
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
     * Elimina una actividad individual y publica el resultado como evento de un solo consumo.
     *
     * <p>El borrado real se delega en {@link ActivityRepository} mediante
     * <code>borrarActividad(...)</code>, y el feedback se reemite en {@link #deleteEvent}
     * para que el fragment lo consuma una sola vez.</p>
     *
     * @param localId identificador local estable de la actividad a eliminar.
     */
    public void borrarActividad(@NonNull String localId) {
        actividadRepository.borrarActividad(localId, result -> {
            if (result.isSuccess()) {
                deleteEvent.postValue(new Event<>(UiState.success(
                        AppLanguageManager.getString(getApplication(), R.string.stats_delete_ok))));
            } else {
                ApiError error = result.error != null
                        ? result.error
                        : ApiError.local(AppLanguageManager.getString(getApplication(), R.string.stats_delete_error));
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
            allActividades.setValue(Collections.emptyList());
            statsState.setValue(UiState.success(
                    StatsResumen.empty(
                            StatsCalculator.DEFAULT_WEEKLY_GOAL_METERS,
                            StatsCalculator.DEFAULT_MONTHLY_GOAL_METERS)));
            return;
        }

        actividadesSource = actividadRepository.observeActividades(accountKey);
        statsState.addSource(actividadesSource, items -> {
            lastItems = items != null ? items : Collections.emptyList();
            allActividades.setValue(lastItems);
            recalcular();
        });

        prefsSource = userPrefsRepository.observe(accountKey);
        statsState.addSource(prefsSource, prefs -> {
            if (prefs != null) {
                lastWeeklyGoal = prefs.weeklyGoalMeters;
                lastMonthlyGoal = prefs.monthlyGoalMeters;
            } else {
                lastWeeklyGoal = StatsCalculator.DEFAULT_WEEKLY_GOAL_METERS;
                lastMonthlyGoal = StatsCalculator.DEFAULT_MONTHLY_GOAL_METERS;
            }
            recalcular();
        });
    }

    /**
     * Recalcula el resumen con el estado más reciente de actividades y objetivos.
     *
     * <p>Es el único punto que invoca a {@link StatsCalculator#calcular(List, long, long)}
     * para asegurar que ambos flujos reactivos usan exactamente la misma lógica.</p>
     */
    private void recalcular() {
        statsState.setValue(UiState.success(
                StatsCalculator.calcular(lastItems, lastWeeklyGoal, lastMonthlyGoal)));
    }

    // ── Ciclo de vida ─────────────────────────────────────────────────────────

    /**
     * Desconecta las fuentes del {@link MediatorLiveData} y cancela llamadas pendientes del repositorio.
     */
    @Override
    protected void onCleared() {
        if (actividadesSource != null) statsState.removeSource(actividadesSource);
        if (prefsSource != null) statsState.removeSource(prefsSource);
        actividadRepository.cancelAll();
    }
}

