package com.proyecto.moveon.ui.home.tracking;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

/**
 * Adaptador de conexión con TrackingService.
 *
 * El ViewModel no conserva ya una referencia directa al Service ni conoce el
 * ciclo de vida del bind. Toda esa responsabilidad queda encapsulada en una
 * pieza dedicada basada en application context.
 */
public final class TrackingServiceController {

    private final Context appContext;
    private final MediatorLiveData<TrackingState> trackingState =
            new MediatorLiveData<>(TrackingState.idle());

    @Nullable private TrackingService service;
    @Nullable private LiveData<TrackingState> serviceStateSource;
    private boolean bound = false;
    private boolean bindRequested = false;
    private boolean pendingStartAfterBind = false;
    @Nullable private Double pendingUserWeightKg = null;
    private boolean released = false;

    private final ServiceConnection connection = new ServiceConnection() {
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
            bound = false;
            bindRequested = false;
            service = null;
        }
    };

    public TrackingServiceController(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        bindTrackingService();
    }

    @NonNull
    public LiveData<TrackingState> getTrackingState() {
        return trackingState;
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

    private void safeUnbindIfNeeded() {
        if (!bound && !bindRequested) return;

        try {
            appContext.unbindService(connection);
        } catch (IllegalArgumentException ignored) {
        } finally {
            bound = false;
            bindRequested = false;
        }
    }
}
