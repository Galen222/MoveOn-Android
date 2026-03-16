package com.proyecto.moveon.ui.home.tracking;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.proyecto.moveon.core.i18n.ProfileValueLocalizer;
import com.proyecto.moveon.data.activities.ActivityRepository;
import com.proyecto.moveon.data.activities.dto.GuardarActividadRequestDto;
import com.proyecto.moveon.data.activities.dto.GuardarActividadResponseDto;
import com.proyecto.moveon.data.profile.dto.ProfileInfoDto;
import com.proyecto.moveon.ui.common.Event;
import com.proyecto.moveon.ui.common.UiState;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * ViewModel del módulo de tracking.
 * Responsabilidades:
 * - Orquestar el flujo de tracking a través de TrackingServiceController
 * - Exponer TrackingState como LiveData al Fragment
 * - Cargar el peso del usuario tras conectar con el servicio
 * - Llamar a POST /actividad/guardar al finalizar
 */
public final class TrackingViewModel extends AndroidViewModel {

    private final ActivityRepository repository;
    private final TrackingServiceController trackingController;

    /** Estado en tiempo real del tracking (refleja lo que publica TrackingService). */
    private final MediatorLiveData<TrackingState> trackingState = new MediatorLiveData<>();

    /** Estado de la llamada al API para guardar la actividad. */
    private final MutableLiveData<UiState<GuardarActividadResponseDto>> saveState =
            new MutableLiveData<>(UiState.success(null));

    /** Evento único: mensaje de error que la UI muestra una sola vez. */
    private final MutableLiveData<Event<String>> errorEvent = new MutableLiveData<>();

    @NonNull
    public LiveData<TrackingState> getTrackingState() { return trackingState; }

    @NonNull
    public LiveData<UiState<GuardarActividadResponseDto>> getSaveState() { return saveState; }

    @NonNull
    public LiveData<Event<String>> getErrorEvent() { return errorEvent; }

    public TrackingViewModel(@NonNull Application application) {
        super(application);
        repository = new ActivityRepository(application);
        trackingController = new TrackingServiceController(application);
        trackingState.setValue(TrackingState.idle());
        trackingState.addSource(trackingController.getTrackingState(), trackingState::setValue);
        loadUserWeight();
    }

    @Override
    protected void onCleared() {
        trackingState.removeSource(trackingController.getTrackingState());
        trackingController.release();
        repository.cancelAll();
        super.onCleared();
    }

    /** Inicia o reanuda la grabación. */
    public void startTracking() {
        trackingController.startTracking();
    }

    /** Pausa la grabación. */
    public void pauseTracking() {
        trackingController.pauseTracking();
    }

    /**
     * Finaliza la grabación y guarda la actividad en el backend.
     * Si no hay distancia o duración registrada, descarta sin llamar a la API.
     */
    public void stopAndSave() {
        TrackingState state = trackingState.getValue();
        trackingController.stopTracking();

        if (state == null || state.getDistanceMeters() <= 0 || state.getElapsedSeconds() <= 0) {
            trackingController.resetTracking();
            return;
        }

        guardarActividad(state);
    }

    /** Descarta la sesión actual y vuelve a IDLE. */
    public void resetTracking() {
        trackingController.resetTracking();
        saveState.setValue(UiState.success(null));
    }

    private void loadUserWeight() {
        repository.obtenerPerfil(result -> {
            if (result.isSuccess() && result.data != null) {
                ProfileInfoDto perfil = result.data;
                if (perfil.peso != null && perfil.peso > 0) {
                    trackingController.setUserWeight(perfil.peso);
                }
            }
        });
    }

    private void guardarActividad(@NonNull TrackingState state) {
        saveState.setValue(UiState.loading());

        String activityTypeLabel = (state.getActivityType() == TrackingState.ActivityType.RUNNING_ACTIVITY)
                ? getApplication().getString(com.proyecto.moveon.R.string.activity_type_run)
                : getApplication().getString(com.proyecto.moveon.R.string.activity_type_walk);

        String tipo = ProfileValueLocalizer.canonicalActivityTypeFromLabel(getApplication(), activityTypeLabel);
        if (tipo == null) {
            tipo = "Caminar";
        }

        String fechaRuta = OffsetDateTime.now(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        int calorias = Math.min(state.getCalories(), 10000);
        if (calorias <= 0) calorias = 1;

        GuardarActividadRequestDto request = new GuardarActividadRequestDto(
                tipo,
                state.getDistanceMeters(),
                (int) state.getElapsedSeconds(),
                calorias,
                state.getEncodedPolyline(),
                fechaRuta);

        repository.guardarActividad(request, result -> {
            if (result.isSuccess()) {
                saveState.postValue(UiState.success(result.data));
            } else {
                String msg = result.error != null
                        ? result.error.getMessage()
                        : getApplication().getString(com.proyecto.moveon.R.string.vm_error_generico);
                saveState.postValue(UiState.error(result.error));
                errorEvent.postValue(new Event<>(msg));
            }
        });
    }

    /** true si hay una actividad en curso (RUNNING o PAUSED). */
    public boolean isTrackingActive() {
        TrackingState state = trackingState.getValue();
        return state != null && state.isActive();
    }
}
