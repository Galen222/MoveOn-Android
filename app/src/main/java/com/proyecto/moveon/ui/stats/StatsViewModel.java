package com.proyecto.moveon.ui.stats;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.proyecto.moveon.R;
import com.proyecto.moveon.core.api.ApiError;
import com.proyecto.moveon.data.activities.ActivityRepository;
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
 *
 * <p>Fuente de verdad: Room (offline-first). Los datos se leen mediante
 * {@link ActivityRepository#observeActividades}, que devuelve un {@link LiveData}
 * reactivo que se actualiza automáticamente cuando Room cambia.
 *
 * <p>Los cálculos estadísticos se delegan a {@link StatsCalculator},
 * manteniendo este ViewModel limpio y centrado en la orquestación.
 *
 * <p>Responsabilidades:
 * <ul>
 *   <li>Observar actividades locales y calcular resumen + gráfico semanal.</li>
 *   <li>Solicitar refresh desde servidor al arrancar.</li>
 *   <li>Gestionar borrado de actividades individuales.</li>
 *   <li>Exponer {@link UiState} y {@link Event} para el Fragment.</li>
 * </ul>
 */
public class StatsViewModel extends AndroidViewModel {

    // ── LiveData expuesto ─────────────────────────────────────────────────────

    /** Estado principal: loading / success(StatsResumen) / error. */
    private final MediatorLiveData<UiState<StatsResumen>> statsState = new MediatorLiveData<>();

    /** Lista reactiva de actividades para el historial. */
    private final MediatorLiveData<List<ActividadItem>> actividades = new MediatorLiveData<>();

    /** Distancia por día de la semana actual. Índice 0=lunes, 6=domingo. */
    private final MediatorLiveData<long[]> distanciaSemanal = new MediatorLiveData<>();

    /** Evento puntual del resultado del borrado — consumo único en el Fragment. */
    private final MutableLiveData<Event<UiState<String>>> deleteEvent = new MutableLiveData<>();

    // ── Dependencias ──────────────────────────────────────────────────────────

    private final ActivityRepository actividadRepository;

    @Nullable private final String accountKey;
    @Nullable private LiveData<List<ActividadItem>> source;

    // ── Constructor ───────────────────────────────────────────────────────────

    public StatsViewModel(@NonNull Application application) {
        super(application);
        actividadRepository = new ActivityRepository(application);
        SecureSessionManager sessionManager = new SecureSessionManager(application);
        accountKey = ActivityRepository.buildAccountKey(sessionManager.getUsername());

        attachSource();
    }

    // ── Exposición de LiveData ────────────────────────────────────────────────

    /** Estado principal: loading / success(StatsResumen) / error. */
    @NonNull
    public LiveData<UiState<StatsResumen>> getStatsState() {
        return statsState;
    }

    /** Lista de actividades para el RecyclerView del historial. */
    @NonNull
    public LiveData<List<ActividadItem>> getActividades() {
        return actividades;
    }

    /** Array de 7 longs con la distancia en metros por día (Lun–Dom). */
    @NonNull
    public LiveData<long[]> getDistanciaSemanal() {
        return distanciaSemanal;
    }

    /** Evento puntual de resultado del borrado — consumir con getContentIfNotHandled(). */
    @NonNull
    public LiveData<Event<UiState<String>>> getDeleteEvent() {
        return deleteEvent;
    }

    // ── Acciones ──────────────────────────────────────────────────────────────

    /**
     * Carga inicial: muestra loading si no hay datos locales todavía,
     * encola sync de pendientes y refresca desde servidor.
     * Room notificará automáticamente a los observadores cuando cambie.
     */
    public void load() {
        if (accountKey == null) {
            statsState.setValue(UiState.error(ApiError.local(
                    getApplication().getString(R.string.vm_error_generico))));
            return;
        }

        // Solo mostrar loading si aún no hay datos locales
        UiState<StatsResumen> current = statsState.getValue();
        if (current == null || current.data == null) {
            statsState.setValue(UiState.loading());
        }

        actividadRepository.enqueueSync();
        actividadRepository.refreshFromServer(accountKey, error -> {
            // Error de red no es crítico si ya hay datos locales
            UiState<StatsResumen> latest = statsState.getValue();
            if (error != null && (latest == null || latest.data == null)) {
                statsState.postValue(UiState.error(error));
            }
        });
    }

    /**
     * Elimina una actividad individual.
     * Solo permite borrar actividades en estado SYNCED.
     * El resultado se emite como {@link Event} para consumo único en el Fragment.
     *
     * @param localId identificador local de la actividad en Room
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
     * Conecta Room con los LiveData del ViewModel.
     * Cada vez que Room cambia, se recalculan automáticamente
     * el resumen, el historial y el gráfico semanal.
     */
    private void attachSource() {
        if (accountKey == null) {
            actividades.setValue(Collections.emptyList());
            distanciaSemanal.setValue(new long[7]);
            statsState.setValue(UiState.success(
                    StatsResumen.empty(StatsCalculator.DEFAULT_WEEKLY_GOAL_METERS)));
            return;
        }

        source = actividadRepository.observeActividades(accountKey);

        statsState.addSource(source, items -> {
            List<ActividadItem> safeItems = items != null ? items : Collections.emptyList();

            // Actualizar lista para RecyclerView
            actividades.setValue(safeItems);

            // Calcular resumen y gráfico semanal delegando en StatsCalculator
            statsState.setValue(UiState.success(StatsCalculator.calcular(safeItems)));
            distanciaSemanal.setValue(StatsCalculator.calcularDistanciaPorDiaSemana(safeItems));
        });
    }

    // ── Ciclo de vida ─────────────────────────────────────────────────────────

    @Override
    protected void onCleared() {
        if (source != null) {
            statsState.removeSource(source);
        }
        actividadRepository.cancelAll();
        super.onCleared();
    }
}