package com.proyecto.moveon.ui.home.tracking;

import androidx.annotation.NonNull;

/**
 * Evento de alerta de tracking que la UI consume una sola vez.
 *
 * <p>Separa el estado persistente del flujo puntual de UX. El estado
 * indica que la sesión está corriendo o auto-pausada; esta clase indica
 * que además la interfaz debe enseñar un panel inferior contextual.</p>
 */
public final class TrackingAlert {

    /**
     * Tipos de alerta soportados por la sesión.
     */
    public enum Type {
        STATIONARY_AUTO_PAUSE,
        SUSPICIOUS_SPEED
    }

    @NonNull
    private final Type type;

    public TrackingAlert(@NonNull Type type) {
        this.type = type;
    }

    @NonNull
    public Type getType() {
        return type;
    }
}
