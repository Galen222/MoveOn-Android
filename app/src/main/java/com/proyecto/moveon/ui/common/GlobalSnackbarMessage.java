package com.proyecto.moveon.ui.common;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Modelo inmutable con todos los datos necesarios para renderizar un snackbar global.
 *
 * <p>Se transporta dentro de un {@link Event} desde un fragmento hasta
 * {@code MainActivity}, que es quien finalmente decide cómo pintarlo sobre la ventana activa.</p>
 */
public final class GlobalSnackbarMessage {

    /** Tipo visual del snackbar. */
    @NonNull
    public final TopSnackbar.Type type;

    /** Texto principal a mostrar. */
    @NonNull
    public final CharSequence message;

    /** Etiqueta opcional de la acción secundaria. */
    @Nullable
    public final String actionLabel;

    /** Acción opcional asociada al snackbar. */
    @Nullable
    public final Runnable action;

    /**
     * Construye un mensaje global sin acción secundaria.
     *
     * @param type tipo visual del snackbar.
     * @param message texto principal.
     */
    public GlobalSnackbarMessage(@NonNull TopSnackbar.Type type,
                                 @NonNull CharSequence message) {
        this(type, message, null, null);
    }

    /**
     * Construye un mensaje global completo.
     *
     * @param type tipo visual del snackbar.
     * @param message texto principal.
     * @param actionLabel etiqueta del botón de acción.
     * @param action callback ejecutado al pulsar la acción.
     */
    public GlobalSnackbarMessage(@NonNull TopSnackbar.Type type,
                                 @NonNull CharSequence message,
                                 @Nullable String actionLabel,
                                 @Nullable Runnable action) {
        this.type = type;
        this.message = message;
        this.actionLabel = actionLabel;
        this.action = action;
    }
}
