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

    /**
     * Constructor privado: esta clase es singleton; obtener la instancia
     * por {@link #getInstance()} garantiza que toda la app comparte el
     * mismo estado de expiración de sesión.
     */
    private GlobalAuthManager() {}

    /**
     * Devuelve la instancia única del manager, creándola bajo bloqueo la
     * primera vez. {@code synchronized} es suficiente aquí porque la
     * creación ocurre solo una vez y el coste del bloqueo es despreciable.
     *
     * @return instancia singleton del manager global de autenticación.
     */
    public static synchronized GlobalAuthManager getInstance() {
        if (instance == null) {
            instance = new GlobalAuthManager();
        }
        return instance;
    }

    // Se llama desde el hilo de red (OkHttp) cuando el refresh falla
    /**
     * Señaliza que el backend ha invalidado la sesión (401 en refresh,
     * logout remoto, cuenta deshabilitada). Usa un flag atómico para que
     * si varios componentes reciben el 401 a la vez, sólo el primero
     * publique el evento y no se acumulen múltiples redirecciones a login.
     */
    public void notifySessionExpired() {
        if (dispatchInProgress.compareAndSet(false, true)) {
            sessionExpiredEvent.postValue(new Event<>("session_expired"));
        }
    }

    /**
     * LiveData observable por cualquier Activity o Fragment principal para
     * reaccionar a la expiración de sesión (mostrar mensaje y redirigir a
     * login). El uso de {@link Event} impide reentregar la notificación
     * tras una rotación.
     *
     * @return LiveData de eventos de sesión expirada, nunca {@code null}.
     */
    public LiveData<Event<String>> getSessionExpiredEvent() {
        return sessionExpiredEvent;
    }

    /**
     * La UI llama a este método tras manejar el evento (mostrar diálogo y
     * navegar a login) para liberar el flag y permitir que la próxima
     * expiración vuelva a notificarse.
     */
    public void acknowledgeSessionExpired() {
        dispatchInProgress.set(false);
    }
}
