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

    /**
     * Crea una alerta emitida por el módulo de tracking. El {@code type}
     * indica qué mensaje mostrar en la UI (GPS débil, permisos revocados,
     * auto-pausa por inactividad, etc.).
     *
     * @param type tipo de alerta que se acaba de disparar.
     */
    public TrackingAlert(@NonNull Type type) {
        this.type = type;
    }

    @NonNull
    /**
     * Devuelve el tipo concreto de la alerta para que la UI decida el
     * texto, el icono y la severidad a mostrar.
     *
     * @return tipo de alerta con el que se construyó esta instancia.
     */
    public Type getType() {
        return type;
    }
}
