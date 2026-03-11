package com.proyecto.moveon.core.auth;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.proyecto.moveon.ui.common.Event;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Singleton para gestionar la expiración de sesión a nivel global.
 * Usa Event para evitar re-consumo tras rotaciones y AtomicBoolean para deduplicar ráfagas de 401.
 */
public class GlobalAuthManager {
    private static GlobalAuthManager instance;

    private final MutableLiveData<Event<String>> sessionExpiredEvent = new MutableLiveData<>();
    private final AtomicBoolean dispatchInProgress = new AtomicBoolean(false);

    private GlobalAuthManager() {}

    public static synchronized GlobalAuthManager getInstance() {
        if (instance == null) {
            instance = new GlobalAuthManager();
        }
        return instance;
    }

    // Se llama desde el hilo de red (OkHttp) cuando el refresh falla
    public void notifySessionExpired() {
        if (dispatchInProgress.compareAndSet(false, true)) {
            sessionExpiredEvent.postValue(new Event<>("session_expired"));
        }
    }

    public LiveData<Event<String>> getSessionExpiredEvent() {
        return sessionExpiredEvent;
    }

    public void acknowledgeSessionExpired() {
        dispatchInProgress.set(false);
    }
}
