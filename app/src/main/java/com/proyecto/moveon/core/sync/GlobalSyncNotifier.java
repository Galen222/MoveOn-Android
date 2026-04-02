package com.proyecto.moveon.core.sync;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.proyecto.moveon.ui.common.Event;
import com.proyecto.moveon.ui.common.GlobalSnackbarMessage;
import com.proyecto.moveon.ui.common.TopSnackbar;

/**
 * Notificador global para avisos de sincronización offline completada.
 *
 * <p>Este singleton permite que los {@code Worker}s publiquen un mensaje global cuando terminan
 * de vaciar una cola pendiente (perfil o actividades) y que la UI principal lo observe para
 * mostrar un snackbar sobre la ventana visible.</p>
 *
 * <p>En esta versión se homogeneiza el contrato con el resto de notifiers globales de la app:
 * perfil, estadísticas y sincronización transportan todos un {@link GlobalSnackbarMessage}
 * dentro de un {@link Event} consumible una sola vez.</p>
 */
public final class GlobalSyncNotifier {

    /** Ventana de deduplicación para evitar dos snackbars prácticamente simultáneos. */
    private static final long DISPATCH_DEBOUNCE_MS = 2500L;

    private static GlobalSyncNotifier instance;

    /** Evento one-shot consumible por la UI principal. */
    private final MutableLiveData<Event<GlobalSnackbarMessage>> messageEvent = new MutableLiveData<>();

    /** Último instante en el que se publicó el evento. */
    private long lastDispatchMs = 0L;

    private GlobalSyncNotifier() {
        // Singleton.
    }

    /**
     * Devuelve la instancia global del notificador.
     */
    @NonNull
    public static synchronized GlobalSyncNotifier getInstance() {
        if (instance == null) {
            instance = new GlobalSyncNotifier();
        }
        return instance;
    }

    /**
     * Publica un aviso global de sincronización completada.
     *
     * <p>El texto ya viene resuelto por el emisor para respetar el idioma activo de la app,
     * mientras que el tipo visual se fija aquí como {@link TopSnackbar.Type#SUCCESS} porque el
     * evento representa una finalización correcta de trabajo pendiente offline.</p>
     *
     * @param message texto final a mostrar al usuario.
     */
    public void notifySyncCompleted(@NonNull CharSequence message) {
        notifyMessage(new GlobalSnackbarMessage(TopSnackbar.Type.SUCCESS, message));
    }

    /**
     * Publica el mensaje global hacia la UI principal.
     *
     * <p>Si otro worker acaba de publicar el mismo aviso hace muy poco, se ignora para no mostrar
     * dos snackbars casi idénticos cuando ambas colas terminan casi a la vez tras recuperar red.</p>
     */
    public synchronized void notifyMessage(@NonNull GlobalSnackbarMessage message) {
        long now = System.currentTimeMillis();

        // Dedupe defensivo: evita dos avisos seguidos cuando perfil y actividades terminan juntos.
        if (now - lastDispatchMs < DISPATCH_DEBOUNCE_MS) {
            return;
        }

        lastDispatchMs = now;

        // Event evita re-consumir el mismo snackbar tras recreaciones de pantalla.
        messageEvent.postValue(new Event<>(message));
    }

    /**
     * Expone el canal observable para la UI.
     */
    @NonNull
    public LiveData<Event<GlobalSnackbarMessage>> getMessageEvent() {
        return messageEvent;
    }
}
