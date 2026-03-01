package com.proyecto.moveon.data.common;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import retrofit2.Call;

/**
 * Clase base para todos los repositorios y clientes de red.
 * Centraliza la lógica de seguimiento y cancelación de peticiones (evita crashes por fugas de memoria).
 */
public abstract class BaseRepository {

    private final List<Call<?>> inFlight = new CopyOnWriteArrayList<>();

    public void cancelAll() {
        for (Call<?> c : inFlight) {
            if (c != null && !c.isCanceled()) {
                c.cancel();
            }
        }
        inFlight.clear();
    }

    protected void trackCall(Call<?> call) {
        if (call != null) {
            inFlight.add(call);
        }
    }

    protected void untrackCall(Call<?> call) {
        if (call != null) {
            inFlight.remove(call);
        }
    }
}