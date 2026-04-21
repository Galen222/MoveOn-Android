package com.proyecto.moveon.ui.common;

import androidx.annotation.Nullable;
/**
 * Clase responsable de event.
 */
public final class Event<T> {

    private final T content;
    private boolean handled = false;

    /**
     * Envuelve un contenido en un evento de un solo consumo. Se usa sobre
     * {@code LiveData} para emitir mensajes como "navegar", "mostrar
     * snackbar" o "cerrar sesión" que no deben reentregarse tras una
     * rotación de pantalla.
     *
     * @param content payload asociado al evento, puede ser {@code null} si el evento es un simple "disparo".
     */
    public Event(T content) {
        this.content = content;
    }

    @Nullable
    /**
     * Devuelve el contenido sólo la primera vez que se llama y lo marca
     * como consumido. Las siguientes llamadas ven {@code null}, evitando
     * que una rotación de pantalla reentregue un evento ya procesado.
     *
     * @return el contenido la primera vez, {@code null} después.
     */
    public T getContentIfNotHandled() {
        if (handled) return null;
        handled = true;
        return content;
    }

    /**
     * Devuelve el contenido sin marcarlo como consumido, útil para tests
     * y para pintar de nuevo el estado asociado tras recrear la vista sin
     * disparar la acción.
     *
     * @return el contenido actual del evento (puede ser {@code null}).
     */
    public T peekContent() {
        return content;
    }
}