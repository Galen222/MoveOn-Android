package com.proyecto.moveon.ui.home.tracking;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
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
    private final MediatorLiveData<TrackingState> trackingState =
            new MediatorLiveData<>(TrackingState.idle());
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

        @Override
        public void onServiceDisconnected(@NonNull ComponentName name) {
            detachServiceStateSource();
            detachServiceAlertSource();
            bound = false;
            bindRequested = false;
            service = null;
        }
    };

    public TrackingServiceController(@NonNull Context context) {
        appContext = context.getApplicationContext();
        bindTrackingService();
    }

    @NonNull
    public LiveData<TrackingState> getTrackingState() {
        return trackingState;
    }

    @NonNull
    public LiveData<TrackingAlert> getTrackingAlert() {
        return trackingAlert;
    }

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

    public void pauseTracking() {
        pendingStartAfterBind = false;
        if (service != null) {
            service.pauseTracking();
        }
    }

    public void stopTracking() {
        pendingStartAfterBind = false;
        if (service != null) {
            service.stopTracking();
        }
    }

    public void resetTracking() {
        pendingStartAfterBind = false;
        if (service != null) {
            service.resetTracking();
        } else {
            trackingState.setValue(TrackingState.idle());
        }
    }

    public void setUserWeight(double weightKg) {
        if (service != null) {
            service.setUserWeight(weightKg);
        } else {
            pendingUserWeightKg = weightKg;
        }
    }

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

    private void bindTrackingService() {
        if (released || bound || bindRequested) return;

        Intent intent = new Intent(appContext, TrackingService.class);
        bindRequested = true;
        boolean bindAccepted = appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE);
        if (!bindAccepted) {
            bindRequested = false;
        }
    }

    private void detachServiceStateSource() {
        if (serviceStateSource != null) {
            trackingState.removeSource(serviceStateSource);
            serviceStateSource = null;
        }
    }

    private void detachServiceAlertSource() {
        if (serviceAlertSource != null) {
            trackingAlert.removeSource(serviceAlertSource);
            serviceAlertSource = null;
        }
    }

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
