
package com.proyecto.moveon.ui.home.tracking;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.google.maps.android.PolyUtil;
import com.proyecto.moveon.BuildConfig;
import com.proyecto.moveon.R;
import com.proyecto.moveon.core.i18n.AppLanguageManager;
import com.proyecto.moveon.app.ServiceLocator;
import com.proyecto.moveon.core.i18n.ProfileValueLocalizer;
import com.proyecto.moveon.data.activities.ActivityRepository;
import com.proyecto.moveon.data.activities.dto.ActivityDiagnosticsRequestDto;
import com.proyecto.moveon.data.activities.dto.GuardarActividadRequestDto;
import com.proyecto.moveon.data.activities.dto.GuardarActividadResponseDto;
import com.proyecto.moveon.data.profile.dto.ProfileInfoDto;
import com.proyecto.moveon.ui.common.Event;
import com.proyecto.moveon.ui.common.UiState;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * ViewModel del módulo de tracking.
 *
 * <p>Orquesta servicio, persistencia y eventos UI. Toda la lógica de red
 * sigue viviendo en el repositorio; aquí se transforman las métricas del
 * tracking a un DTO listo para guardarse offline y sincronizarse.</p>
 */
public final class TrackingViewModel extends AndroidViewModel {

    private final ActivityRepository repository;
    private final TrackingServiceController trackingController;

    private final MediatorLiveData<TrackingState> trackingState = new MediatorLiveData<>();
    private final MutableLiveData<UiState<GuardarActividadResponseDto>> saveState =
            new MutableLiveData<>(UiState.success(null));
    private final MutableLiveData<Event<String>> errorEvent = new MutableLiveData<>();
    private final MutableLiveData<Event<TrackingAlert>> trackingAlertEvent = new MutableLiveData<>();

    private final Observer<TrackingAlert> alertObserver = alert -> {
        if (alert != null) {
            trackingAlertEvent.setValue(new Event<>(alert));
        }
    };

    public TrackingViewModel(@NonNull Application application) {
        super(application);
        repository = ServiceLocator.getInstance(application).newActivityRepository();
        trackingController = new TrackingServiceController(application);

        trackingState.setValue(TrackingState.idle());
        trackingState.addSource(trackingController.getTrackingState(), trackingState::setValue);
        trackingController.getTrackingAlert().observeForever(alertObserver);

        loadUserWeight();
    }

    @Override
    protected void onCleared() {
        trackingState.removeSource(trackingController.getTrackingState());
        trackingController.getTrackingAlert().removeObserver(alertObserver);
        trackingController.release();
        repository.cancelAll();
        super.onCleared();
    }

    @NonNull
    public LiveData<TrackingState> getTrackingState() {
        return trackingState;
    }

    @NonNull
    public LiveData<UiState<GuardarActividadResponseDto>> getSaveState() {
        return saveState;
    }

    @NonNull
    public LiveData<Event<String>> getErrorEvent() {
        return errorEvent;
    }

    @NonNull
    public LiveData<Event<TrackingAlert>> getTrackingAlertEvent() {
        return trackingAlertEvent;
    }

    /**
     * Inicia, reanuda manualmente o sale de una auto-pausa.
     */
    public void startTracking() {
        trackingController.startTracking();
    }

    /**
     * Pausa manualmente la sesión.
     */
    public void pauseTracking() {
        trackingController.pauseTracking();
    }

    /**
     * Descarta la sesión y vuelve a IDLE.
     */
    public void resetTracking() {
        trackingController.resetTracking();
        saveState.setValue(UiState.success(null));
    }

    /**
     * Finaliza la sesión actual y la guarda si hay datos válidos.
     */
    public void stopAndSave() {
        TrackingState current = trackingState.getValue();
        trackingController.stopTracking();

        if (!canSaveTracking(current)) {
            trackingController.resetTracking();
            return;
        }

        guardarActividad(current);
    }

    /**
     * Indica si una sesión concreta cumple los mínimos para persistirse.
     *
     * <p>La UI del diálogo de stop debe consultar este mismo criterio para no ofrecer
     * una acción de guardado que en realidad terminaría descartando la sesión.</p>
     */
    public boolean canSaveTracking(@Nullable TrackingState state) {
        return hasValidDistance(state) && hasValidMovingDuration(state);
    }

    /**
     * Devuelve el motivo visible por el que una sesión no puede guardarse.
     *
     * <p>Cuando la sesión sí es válida devuelve {@code null} para que la UI no muestre
     * texto extra en el diálogo.</p>
     */
    @Nullable
    public String getCannotSaveReason(@Nullable TrackingState state) {
        boolean invalidDistance = !hasValidDistance(state);
        boolean invalidMovingDuration = !hasValidMovingDuration(state);

        if (!invalidDistance && !invalidMovingDuration) {
            return null;
        }

        if (invalidDistance && invalidMovingDuration) {
            return AppLanguageManager.getString(getApplication(), 
                    R.string.tracking_dialog_stop_cannot_save_distance_and_moving_time);
        }

        if (invalidDistance) {
            return AppLanguageManager.getString(getApplication(), 
                    R.string.tracking_dialog_stop_cannot_save_distance);
        }

        return AppLanguageManager.getString(getApplication(), 
                R.string.tracking_dialog_stop_cannot_save_moving_time);
    }

    /**
     * Devuelve {@code true} si la UI debe tratar la sesión como abierta.
     */
    public boolean isTrackingActive() {
        TrackingState state = trackingState.getValue();
        return state != null && state.isActive();
    }

    private boolean hasValidDistance(@Nullable TrackingState state) {
        return state != null && state.getDistanceMeters() > 0;
    }

    private boolean hasValidMovingDuration(@Nullable TrackingState state) {
        return state != null && state.getMovingSeconds() > 0;
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

    /**
     * Construye el DTO de guardado usando métricas ya depuradas por el servicio.
     *
     * <p>La duración y el ritmo total se calculan con el tiempo efectivo de actividad
     * ({@code moving + stopped}), excluyendo el tiempo pasado en auto-pausa. Esto acerca
     * el histórico al comportamiento de un reloj deportivo y evita inflar artificialmente
     * el ritmo total cuando el GPS o la detección de movimiento fuerzan pausas largas.</p>
     */
    private void guardarActividad(@NonNull TrackingState state) {
        saveState.setValue(UiState.loading());

        String tipo = resolvePredominantActivityType(state);

        String encodedPolyline = state.getEncodedPolyline();
        if (encodedPolyline == null && !state.getRoutePoints().isEmpty()) {
            encodedPolyline = PolyUtil.encode(state.getRoutePoints());
        }

        String fechaRuta = OffsetDateTime.now(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        int calorias = Math.max(1, Math.min(state.getCalories(), 10000));

        // duracion_total y duracion_movimiento son los valores reales del servicio.
        // duracion_parado se deriva de la diferencia para garantizar que la suma
        // cuadre exactamente con lo que valida el backend.
        int duracionTotal      = safeToInt(state.getEffectiveElapsedSeconds());
        int duracionMovimiento = safeToInt(state.getMovingSeconds());
        int duracionParado     = safeToInt(state.getStoppedSeconds());

        int averageMovingPace = calculatePaceSecondsPerKm(state.getMovingSeconds(), state.getPreciseDistanceMeters());
        int averageElapsedPace = calculatePaceSecondsPerKm(state.getEffectiveElapsedSeconds(), state.getPreciseDistanceMeters());
        int maxPace = parsePaceTextToSecondsPerKm(state.getMaxPace());
        int averageSpeedKmhX100 = calculateAverageSpeedKmhX100(
                state.getDistanceMeters(),
                state.getMovingSeconds()
        );

        GuardarActividadRequestDto request = new GuardarActividadRequestDto(
                tipo,
                state.getDistanceMeters(),
                duracionTotal,
                duracionMovimiento,
                duracionParado,
                safeToInt(state.getManualPausedSeconds()),
                calorias,
                averageMovingPace,
                averageElapsedPace,
                maxPace,
                averageSpeedKmhX100,
                state.getMaxSpeedKmhX100(),
                state.getAutoPauseCount(),
                state.getManualPauseCount(),
                state.getSuspiciousSpeedEventCount(),
                encodedPolyline,
                fechaRuta
        );

        repository.guardarActividad(request, result -> {
            if (result.isSuccess()) {
                trackingController.resetTracking();
                saveState.postValue(UiState.success(result.data));
                sendDiagnosticsIfEnabled(state, result.data);
            } else {
                String message = result.error != null
                        ? result.error.getMessage()
                        : AppLanguageManager.getString(getApplication(), R.string.vm_error_generico);
                saveState.postValue(UiState.error(result.error));
                errorEvent.postValue(new Event<>(message));
            }
        });
    }

    private int calculatePaceSecondsPerKm(long seconds, double distanceMeters) {
        if (seconds <= 0 || distanceMeters <= 0.0) {
            return 0;
        }
        return (int) Math.round((seconds * 1000.0) / distanceMeters);
    }


    private int parsePaceTextToSecondsPerKm(@Nullable String paceText) {
        if (paceText == null || paceText.trim().isEmpty()) {
            return 0;
        }

        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(\\d+)'(\\d{2})\\\"")
                .matcher(paceText.trim());

        if (!matcher.matches()) {
            return 0;
        }

        int minutes = Integer.parseInt(matcher.group(1));
        int seconds = Integer.parseInt(matcher.group(2));
        return (minutes * 60) + seconds;
    }


    /**
     * Resuelve el tipo final de actividad usando predominio temporal de la sesión.
     *
     * <p>Se considera carrera si al menos el 60% del tiempo clasificado fue running.
     * Así evitamos que la sesión completa quede marcada como caminar solo porque el
     * usuario aflojó o caminó en los últimos segundos antes de guardar.</p>
     */
    @NonNull
    private String resolvePredominantActivityType(@NonNull TrackingState state) {
        long runningSeconds = state.getRunningClassifiedSeconds();
        long walkingSeconds = state.getWalkingClassifiedSeconds();
        long classifiedSeconds = runningSeconds + walkingSeconds;

        boolean isRunning = classifiedSeconds > 0L
                && (runningSeconds * 100L) >= (classifiedSeconds * 60L);

        String activityTypeLabel = isRunning
                ? AppLanguageManager.getString(getApplication(), R.string.activity_type_run)
                : AppLanguageManager.getString(getApplication(), R.string.activity_type_walk);

        String tipo = ProfileValueLocalizer.canonicalActivityTypeFromLabel(getApplication(), activityTypeLabel);
        return tipo != null ? tipo : (isRunning ? "Correr" : "Caminar");
    }

    /**
     * Envía el bloque de diagnóstico al backend solo en builds internas.
     */
    private void sendDiagnosticsIfEnabled(@NonNull TrackingState state,
                                          @Nullable GuardarActividadResponseDto response) {
        if (!BuildConfig.ACTIVITY_DIAGNOSTICS_ENABLED) {
            return;
        }

        ActivityDiagnosticsRequestDto request = new ActivityDiagnosticsRequestDto();
        if (response != null && response.id > 0) {
            request.actividadId = response.id;
        }
        request.actividadLocalId = buildSyntheticLocalSessionId(state);
        request.sessionStartedAt = toIsoOrNull(state.getSessionStartedAtEpochMs());
        request.sessionFinishedAt = toIsoOrNull(state.getSessionFinishedAtEpochMs());
        request.lastTimerTickAt = toIsoOrNull(state.getLastTimerTickAtEpochMs());
        request.serviceCreatedAt = toIsoOrNull(state.getServiceCreatedAtEpochMs());
        request.serviceDestroyedAt = toIsoOrNull(state.getServiceDestroyedAtEpochMs());
        request.elapsedSeconds = safeToInt(state.getElapsedSeconds());
        request.movingSeconds = safeToInt(state.getMovingSeconds());
        request.stoppedSeconds = safeToInt(state.getStoppedSeconds());
        request.manualPauseSeconds = safeToInt(state.getManualPausedSeconds());
        request.distanceMeters = state.getDistanceMeters();
        request.averagePaceTotal = calculatePaceSecondsPerKm(state.getEffectiveElapsedSeconds(), state.getPreciseDistanceMeters());
        request.averagePaceMoving = calculatePaceSecondsPerKm(state.getMovingSeconds(), state.getPreciseDistanceMeters());
        request.maxPace = parsePaceTextToSecondsPerKm(state.getMaxPace());
        request.autoPauses = state.getAutoPauseCount();
        request.manualPauses = state.getManualPauseCount();
        request.speedAlerts = state.getSuspiciousSpeedEventCount();
        request.runningClassifiedSeconds = safeToInt(state.getRunningClassifiedSeconds());
        request.walkingClassifiedSeconds = safeToInt(state.getWalkingClassifiedSeconds());
        request.serviceRestartCount = state.getServiceRestartCount();
        request.currentStatus = state.getStatus().name();
        request.appVersion = BuildConfig.VERSION_NAME;
        request.osVersion = android.os.Build.VERSION.RELEASE;
        request.manufacturer = android.os.Build.MANUFACTURER;
        request.model = android.os.Build.MODEL;

        for (TrackingState.DiagnosticEvent event : state.getDiagnosticEvents()) {
            ActivityDiagnosticsRequestDto.EventItem item = new ActivityDiagnosticsRequestDto.EventItem();
            item.at = toIsoOrNull(event.getAtEpochMs());
            item.tipo = event.getType();
            item.detalle = event.getDetail();
            request.eventLog.add(item);
        }

        repository.guardarActividadDiagnostico(request);
    }

    @Nullable
    private String toIsoOrNull(long epochMs) {
        if (epochMs <= 0L) {
            return null;
        }
        return Instant.ofEpochMilli(epochMs).atOffset(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    @NonNull
    private String buildSyntheticLocalSessionId(@NonNull TrackingState state) {
        long seed = state.getSessionStartedAtEpochMs() > 0L
                ? state.getSessionStartedAtEpochMs()
                : System.currentTimeMillis();
        return "tracking_" + seed;
    }

    private int calculateAverageSpeedKmhX100(int distanceMeters, long movingSeconds) {
        if (distanceMeters <= 0 || movingSeconds <= 0) {
            return 0;
        }
        double kmh = (distanceMeters / 1000.0) / (movingSeconds / 3600.0);
        return (int) Math.round(kmh * 100.0);
    }

    private int safeToInt(long value) {
        if (value <= 0L) return 0;
        if (value > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return (int) value;
    }
}
