package com.proyecto.moveon.ui.home.tracking;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

/**
 * Adaptador de conexión con {@link TrackingService}.
 *
 * <p>Centraliza bind/unbind, retransmisión de LiveData y comandos públicos
 * para que el ViewModel no tenga que conocer detalles del servicio.</p>
 */
public final class TrackingServiceController {

    private final Context appContext;
    private final MediatorLiveData<TrackingState> trackingState;
    private final MediatorLiveData<TrackingAlert> trackingAlert = new MediatorLiveData<>();

    @Nullable private TrackingService service;
    @Nullable private LiveData<TrackingState> serviceStateSource;
    @Nullable private LiveData<TrackingAlert> serviceAlertSource;
    private boolean bound = false;
    private boolean bindRequested = false;
    private boolean pendingStartAfterBind = false;
    @Nullable private Double pendingUserWeightKg = null;
    private boolean released = false;

    private final android.content.ServiceConnection connection = new android.content.ServiceConnection() {
        /**
         * Completa el enlace con {@link TrackingService}, conecta sus fuentes reactivas
         * y aplica las operaciones aplazadas mientras el bind estaba pendiente.
         *
         * @param name componente del servicio que Android acaba de conectar.
         * @param binder binder expuesto por {@link TrackingService.LocalBinder}.
         */
        @Override
        public void onServiceConnected(@NonNull ComponentName name, @NonNull IBinder binder) {
            if (released) {
                safeUnbindIfNeeded();
                return;
            }

            TrackingService.LocalBinder localBinder = (TrackingService.LocalBinder) binder;
            service = localBinder.getService();
            bound = true;
            bindRequested = false;

            detachServiceStateSource();
            serviceStateSource = service.getStateLiveData();
            trackingState.addSource(serviceStateSource, trackingState::setValue);

            detachServiceAlertSource();
            serviceAlertSource = service.getTrackingAlertLiveData();
            trackingAlert.addSource(serviceAlertSource, trackingAlert::setValue);

            if (pendingUserWeightKg != null) {
                service.setUserWeight(pendingUserWeightKg);
                pendingUserWeightKg = null;
            }

            if (pendingStartAfterBind) {
                pendingStartAfterBind = false;
                service.startTracking();
            }
        }

        /**
         * Limpia las referencias al servicio cuando el sistema rompe la conexión del bind.
         *
         * @param name componente del servicio desconectado.
         */
        @Override
        public void onServiceDisconnected(@NonNull ComponentName name) {
            detachServiceStateSource();
            detachServiceAlertSource();
            bound = false;
            bindRequested = false;
            service = null;
        }
    };

    /**
     * Crea el controlador y solicita de inmediato el bind con {@link TrackingService}.
     *
     * @param context contexto usado para obtener el contexto de aplicación y enlazar el servicio.
     */
    public TrackingServiceController(@NonNull Context context) {
        appContext = context.getApplicationContext();
        trackingState = new MediatorLiveData<>(createInitialTrackingState(appContext));
        bindTrackingService();
    }

    /**
     * Construye el estado inicial consultando el hardware antes de que termine
     * el enlace asíncrono con {@link TrackingService}.
     *
     * <p>Así Inicio muestra {@code 0} desde el primer render cuando existe un
     * detector o contador de pasos, y reserva {@code N/D} para dispositivos
     * que realmente no ofrecen ninguno de los dos sensores.</p>
     */
    @NonNull
    private static TrackingState createInitialTrackingState(@NonNull Context context) {
        SensorManager sensorManager =
                (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        boolean stepSensorAvailable = sensorManager != null
                && (sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) != null
                || sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null);

        return TrackingState.idle()
                .toBuilder()
                .stepSensorAvailable(stepSensorAvailable)
                .build();
    }

    /**
     * Expone el estado reactivo retransmitido desde el servicio.
     *
     * @return {@link LiveData} con el último {@link TrackingState} disponible.
     */
    @NonNull
    public LiveData<TrackingState> getTrackingState() {
        return trackingState;
    }

    /**
     * Expone las alertas contextuales generadas por {@link TrackingService}.
     *
     * @return flujo observable de {@link TrackingAlert} para la UI.
     */
    @NonNull
    public LiveData<TrackingAlert> getTrackingAlert() {
        return trackingAlert;
    }

    /**
     * Arranca o reanuda el tracking, iniciando el foreground service si todavía no está activo.
     */
    public void startTracking() {
        if (released) return;

        Intent intent = new Intent(appContext, TrackingService.class);
        TrackingState currentState = trackingState.getValue();

        if (service != null) {
            if (currentState == null || currentState.isIdle() || currentState.isFinished()) {
                ContextCompat.startForegroundService(appContext, intent);
            }
            service.startTracking();
            return;
        }

        pendingStartAfterBind = true;
        ContextCompat.startForegroundService(appContext, intent);
        bindTrackingService();
    }

    /**
     * Solicita una pausa manual de la sesión en curso y descarta cualquier arranque diferido.
     */
    public void pauseTracking() {
        pendingStartAfterBind = false;
        if (service != null) {
            service.pauseTracking();
        }
    }

    /**
     * Ordena detener la sesión actual sin liberar aún el enlace con el servicio.
     */
    public void stopTracking() {
        pendingStartAfterBind = false;
        if (service != null) {
            service.stopTracking();
        }
    }

    /**
     * Limpia la sesión activa y, si el servicio no está enlazado todavía, repone el estado local a IDLE.
     */
    public void resetTracking() {
        pendingStartAfterBind = false;
        if (service != null) {
            service.resetTracking();
        } else {
            trackingState.setValue(createInitialTrackingState(appContext));
        }
    }

    /**
     * Propaga el peso del usuario al servicio para afinar el cálculo de calorías.
     *
     * @param weightKg peso del usuario en kilogramos.
     */
    public void setUserWeight(double weightKg) {
        if (service != null) {
            service.setUserWeight(weightKg);
        } else {
            pendingUserWeightKg = weightKg;
        }
    }

    /**
     * Libera el controlador, desengancha observadores y evita nuevos comandos sobre el servicio.
     */
    public void release() {
        if (released) return;

        released = true;
        pendingStartAfterBind = false;
        pendingUserWeightKg = null;
        detachServiceStateSource();
        detachServiceAlertSource();
        safeUnbindIfNeeded();
        bindRequested = false;
        service = null;
    }

    /**
     * Solicita el bind con {@link TrackingService} si el controlador sigue activo y aún no existe conexión.
     */
    private void bindTrackingService() {
        if (released || bound || bindRequested) return;

        Intent intent = new Intent(appContext, TrackingService.class);

        // Si hay una sesión viva persistida (la app se reabre tras muerte del proceso
        // con un tracking en marcha), no basta con el bind: BIND_AUTO_CREATE recrea el
        // servicio sin pasar por onStartCommand(), así que nunca volvería a ser un
        // foreground service y perdería GPS/sensores al apagar la pantalla. Arrancarlo
        // explícitamente garantiza la promoción a foreground y que sobreviva al unbind.
        boolean shouldStartAsForeground;
        try {
            shouldStartAsForeground = new TrackingSessionStore(appContext).hasAliveSession();
        } catch (Exception e) {
            shouldStartAsForeground = false;
        }
        if (shouldStartAsForeground) {
            try {
                ContextCompat.startForegroundService(appContext, intent);
            } catch (Exception ignored) {
                // Si el sistema rechaza el arranque (restricciones de background),
                // el propio servicio intentará auto-promocionarse al restaurar sesión.
            }
        }

        bindRequested = true;
        boolean bindAccepted = appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE);
        if (!bindAccepted) {
            bindRequested = false;
        }
    }

    /**
     * Desconecta la fuente de estado actual del servicio para evitar observaciones duplicadas.
     */
    private void detachServiceStateSource() {
        if (serviceStateSource != null) {
            trackingState.removeSource(serviceStateSource);
            serviceStateSource = null;
        }
    }

    /**
     * Desconecta la fuente de alertas actual asociada al servicio enlazado.
     */
    private void detachServiceAlertSource() {
        if (serviceAlertSource != null) {
            trackingAlert.removeSource(serviceAlertSource);
            serviceAlertSource = null;
        }
    }

    /**
     * Intenta deshacer el bind ignorando el caso en que Android ya lo haya liberado internamente.
     */
    private void safeUnbindIfNeeded() {
        if (!bound && !bindRequested) return;

        try {
            appContext.unbindService(connection);
        } catch (IllegalArgumentException ignored) {
            // El sistema ya liberó el bind.
        } finally {
            bound = false;
            bindRequested = false;
        }
    }
}
