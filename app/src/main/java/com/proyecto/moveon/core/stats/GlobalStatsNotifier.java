package com.proyecto.moveon.core.stats;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.proyecto.moveon.ui.common.Event;
import com.proyecto.moveon.ui.common.GlobalSnackbarMessage;
import com.proyecto.moveon.ui.common.TopSnackbar;

/**
 * Notificador global para mensajes nacidos en StatsFragment.
 *
 * <p>Este singleton desacopla la generación del mensaje de su render final:
 * el fragmento emite el evento y {@code MainActivity} lo dibuja sobre la ventana visible.</p>
 *
 * <p>Incluye una deduplicación temporal corta para evitar ráfagas de snackbars idénticos
 * cuando la misma acción dispara varias emisiones casi simultáneas.</p>
 */
public final class GlobalStatsNotifier {

    /** Ventana mínima entre emisiones para suavizar duplicados accidentales. */
    private static final long DISPATCH_DEBOUNCE_MS = 350L;

    private static GlobalStatsNotifier instance;

    /** Evento one-shot consumible por la UI principal. */
    private final MutableLiveData<Event<GlobalSnackbarMessage>> messageEvent = new MutableLiveData<>();

    /** Último instante en que se publicó un mensaje. */
    private long lastDispatchMs = 0L;

    /**
     * Constructor privado: esta clase se usa como singleton a nivel de
     * proceso para notificar cambios de estadísticas entre componentes.
     */
    private GlobalStatsNotifier() {
        // Singleton.
    }

    /**
     * Devuelve la instancia singleton usada para transportar mensajes globales de estadísticas.
     *
     * @return instancia única de {@link GlobalStatsNotifier}.
     */
    @NonNull
    public static synchronized GlobalStatsNotifier getInstance() {
        if (instance == null) {
            instance = new GlobalStatsNotifier();
        }
        return instance;
    }

    /**
     * Publica un mensaje de éxito sin acción secundaria.
     *
     * @param message texto que debe mostrarse al usuario.
     */
    public void notifySuccess(@NonNull CharSequence message) {
        notifyMessage(new GlobalSnackbarMessage(TopSnackbar.Type.SUCCESS, message));
    }

    /**
     * Publica un mensaje de aviso sin acción secundaria.
     *
     * @param message texto que debe mostrarse al usuario.
     */
    public void notifyWarning(@NonNull CharSequence message) {
        notifyMessage(new GlobalSnackbarMessage(TopSnackbar.Type.WARNING, message));
    }

    /**
     * Publica un mensaje de error sin acción secundaria.
     *
     * @param message texto que debe mostrarse al usuario.
     */
    public void notifyError(@NonNull CharSequence message) {
        notifyMessage(new GlobalSnackbarMessage(TopSnackbar.Type.ERROR, message));
    }

    /**
     * Publica un mensaje de error con una acción opcional asociada al snackbar.
     *
     * @param message texto principal del aviso.
     * @param actionLabel texto del CTA opcional; puede ser {@code null} si no debe mostrarse botón.
     * @param action callback opcional a ejecutar al pulsar la acción del {@link TopSnackbar}.
     */
    public void notifyError(@NonNull CharSequence message,
                            @Nullable String actionLabel,
                            @Nullable Runnable action) {
        notifyMessage(new GlobalSnackbarMessage(
                TopSnackbar.Type.ERROR,
                message,
                actionLabel,
                action
        ));
    }

    /**
     * Publica un mensaje global hacia la UI principal.
     *
     * <p>Si otro aviso se emitió hace apenas unas décimas, se ignora para evitar dobles
     * renderizados provocados por callbacks encadenados o toques repetidos muy rápidos.</p>
     *
     * @param message mensaje visual ya resuelto que debe transportarse a la actividad principal.
     */
    public synchronized void notifyMessage(@NonNull GlobalSnackbarMessage message) {
        long now = System.currentTimeMillis();
        if (now - lastDispatchMs < DISPATCH_DEBOUNCE_MS) {
            return;
        }

        lastDispatchMs = now;

        // Event evita re-consumir el mismo snackbar tras recreaciones de pantalla.
        messageEvent.postValue(new Event<>(message));
    }

    /**
     * Expone el canal observable para que {@code MainActivity} pinte el snackbar.
     *
     * @return flujo one-shot de {@link GlobalSnackbarMessage} envuelto en {@link Event}.
     */
    @NonNull
    public LiveData<Event<GlobalSnackbarMessage>> getMessageEvent() {
        return messageEvent;
    }
}
