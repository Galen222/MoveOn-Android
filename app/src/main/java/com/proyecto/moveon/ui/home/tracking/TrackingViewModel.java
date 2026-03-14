package com.proyecto.moveon.ui.home.tracking;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

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
 * - Bind/Unbind con TrackingService
 * - Exponer TrackingState como LiveData al Fragment
 * - Cargar el peso del usuario tras conectar con el servicio
 * - Llamar a POST /actividad/guardar al finalizar
 */
public final class TrackingViewModel extends AndroidViewModel {

    private final ActivityRepository repository;

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

    @SuppressLint("StaticFieldLeak")
    @Nullable private TrackingService service;
    @Nullable private LiveData<TrackingState> serviceStateSource;
    private boolean bound = false;
    private boolean bindRequested = false;
    private boolean pendingStartAfterBind = false;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(@NonNull ComponentName name, @NonNull IBinder binder) {
            TrackingService.LocalBinder localBinder = (TrackingService.LocalBinder) binder;
            service = localBinder.getService();
            bound = true;
            bindRequested = false;

            if (serviceStateSource != null) {
                trackingState.removeSource(serviceStateSource);
            }
            serviceStateSource = service.getStateLiveData();
            trackingState.addSource(serviceStateSource, trackingState::setValue);

            loadUserWeight();

            if (pendingStartAfterBind) {
                pendingStartAfterBind = false;
                service.startTracking();
            }
        }

        @Override
        public void onServiceDisconnected(@NonNull ComponentName name) {
            if (serviceStateSource != null) {
                trackingState.removeSource(serviceStateSource);
                serviceStateSource = null;
            }
            bound = false;
            bindRequested = false;
            service = null;
        }
    };

    public TrackingViewModel(@NonNull Application application) {
        super(application);
        repository = new ActivityRepository(application);
        trackingState.setValue(TrackingState.idle());
        bindTrackingService();
    }

    private void bindTrackingService() {
        if (bound || bindRequested) return;

        Context ctx = getApplication();
        Intent intent = new Intent(ctx, TrackingService.class);
        bindRequested = true;
        ctx.bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (bound) {
            getApplication().unbindService(connection);
            bound = false;
        }
        repository.cancelAll();
    }

    /** Inicia o reanuda la grabación. */
    public void startTracking() {
        Context ctx = getApplication();
        Intent intent = new Intent(ctx, TrackingService.class);
        TrackingState currentState = trackingState.getValue();

        if (service != null) {
            if (currentState == null || currentState.isIdle() || currentState.isFinished()) {
                ContextCompat.startForegroundService(ctx, intent);
            }
            service.startTracking();
            return;
        }

        ContextCompat.startForegroundService(ctx, intent);
        pendingStartAfterBind = true;
        bindTrackingService();
    }

    /** Pausa la grabación. */
    public void pauseTracking() {
        if (service != null) service.pauseTracking();
    }

    /**
     * Finaliza la grabación y guarda la actividad en el backend.
     * Si no hay distancia o duración registrada, descarta sin llamar a la API.
     */
    public void stopAndSave() {
        if (service == null) return;
        service.stopTracking();

        TrackingState state = trackingState.getValue();
        if (state == null || state.getDistanceMeters() <= 0 || state.getElapsedSeconds() <= 0) {
            service.resetTracking();
            return;
        }

        guardarActividad(state);
    }

    /** Descarta la sesión actual y vuelve a IDLE. */
    public void resetTracking() {
        if (service != null) service.resetTracking();
        saveState.setValue(UiState.success(null));
    }

    private void loadUserWeight() {
        repository.obtenerPerfil(result -> {
            if (result.isSuccess() && result.data != null) {
                ProfileInfoDto perfil = result.data;
                if (perfil.peso != null && perfil.peso > 0 && service != null) {
                    service.setUserWeight(perfil.peso);
                }
            }
        });
    }

    private void guardarActividad(@NonNull TrackingState state) {
        saveState.setValue(UiState.loading());

        String tipo = (state.getActivityType() == TrackingState.ActivityType.RUNNING_ACTIVITY)
                ? "Correr"
                : "Caminar";

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
