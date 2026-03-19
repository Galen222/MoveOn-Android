package com.proyecto.moveon.core.sync;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.proyecto.moveon.ui.common.Event;

/**
 * Notificador global para avisos de sincronización offline completada.
 *
 * <p>Este singleton permite que los {@code Worker}s publiquen un evento cuando terminan de vaciar
 * una cola pendiente (perfil o actividades) y que la UI principal lo observe para mostrar un
 * snackbar.</p>
 *
 * <p>Incluye una pequeña ventana de deduplicación para evitar dos snackbars seguidos cuando los
 * workers de perfil y actividades finalizan casi al mismo tiempo tras recuperar conexión.</p>
 */
public final class GlobalSyncNotifier {

    /** Ventana de deduplicación para evitar dos snackbars prácticamente simultáneos. */
    private static final long DISPATCH_DEBOUNCE_MS = 2500L;

    private static GlobalSyncNotifier instance;

    /** Evento one-shot consumible por la UI. */
    private final MutableLiveData<Event<String>> syncCompletedEvent = new MutableLiveData<>();

    /** Último instante en el que se publicó el evento. */
    private long lastDispatchMs = 0L;

    private GlobalSyncNotifier() {
        // Singleton.
    }

    /**
     * Devuelve la instancia global del notificador.
     */
    public static synchronized GlobalSyncNotifier getInstance() {
        if (instance == null) {
            instance = new GlobalSyncNotifier();
        }
        return instance;
    }

    /**
     * Publica el evento de sincronización completada.
     *
     * <p>Si otro worker acaba de publicar el mismo aviso hace muy poco, se ignora para no mostrar
     * dos snackbars casi idénticos.</p>
     */
    public synchronized void notifySyncCompleted() {
        long now = System.currentTimeMillis();

        // Dedupe defensivo: evita dos avisos seguidos cuando perfil y actividades terminan juntos.
        if (now - lastDispatchMs < DISPATCH_DEBOUNCE_MS) {
            return;
        }

        lastDispatchMs = now;

        // Usamos Event para que el snackbar no se re-consuma en rotaciones de pantalla.
        syncCompletedEvent.postValue(new Event<>("sync_completed"));
    }

    /**
     * Expone el evento observable para la UI.
     */
    public LiveData<Event<String>> getSyncCompletedEvent() {
        return syncCompletedEvent;
    }
}
