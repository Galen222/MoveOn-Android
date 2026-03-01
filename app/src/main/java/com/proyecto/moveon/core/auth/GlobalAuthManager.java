package com.proyecto.moveon.core.auth;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

/**
 * Singleton para gestionar la expiración de sesión a nivel global.
 */
public class GlobalAuthManager {
    private static GlobalAuthManager instance;
    private final MutableLiveData<Boolean> sessionExpiredEvent = new MutableLiveData<>();

    private GlobalAuthManager() {}

    public static synchronized GlobalAuthManager getInstance() {
        if (instance == null) {
            instance = new GlobalAuthManager();
        }
        return instance;
    }

    // Se llama desde el hilo de red (OkHttp) cuando el refresh falla
    public void notifySessionExpired() {
        sessionExpiredEvent.postValue(true);
    }

    public LiveData<Boolean> getSessionExpiredEvent() {
        return sessionExpiredEvent;
    }

    // Para limpiar el evento y que no se repita al girar la pantalla
    public void resetEvent() { sessionExpiredEvent.postValue(false); }
}