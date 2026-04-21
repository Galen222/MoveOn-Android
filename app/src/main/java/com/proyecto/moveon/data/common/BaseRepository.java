package com.proyecto.moveon.data.common;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Clase base para repositorios y clientes de red.
 * Centraliza el seguimiento y cancelación de peticiones.
 *
 * <p>Usa {@code Collections.synchronizedList(new ArrayList<>())}
 * porque esta lista se muta con frecuencia y
 * {@code CopyOnWriteArrayList} copiaría el array completo en cada add/remove,
 * lo cual es innecesariamente costoso para listas que se mutan
 * frecuentemente (track/untrack por cada petición).
 * {@code synchronizedList} solo necesita un lock y es más eficiente
 * para el patrón de escrituras frecuentes + lecturas infrecuentes.</p>
 *
 * <p>En {@link #cancelAll()} se hace un snapshot bajo lock para
 * evitar iterar mientras otro hilo muta la lista.</p>
 */
public abstract class BaseRepository {

    private final List<Call<?>> inFlight =
            Collections.synchronizedList(new ArrayList<>());

    public void cancelAll() {
        List<Call<?>> snapshot;
        synchronized (inFlight) {
            snapshot = new ArrayList<>(inFlight);
            inFlight.clear();
        }

        for (Call<?> c : snapshot) {
            if (c != null && !c.isCanceled()) {
                c.cancel();
            }
        }
    }

    protected void trackCall(@Nullable Call<?> call) {
        if (call != null) {
            inFlight.add(call);
        }
    }

    protected void untrackCall(@Nullable Call<?> call) {
        if (call != null) {
            inFlight.remove(call);
        }
    }

    /**
     * Helper para nuevas llamadas Retrofit:
     * registra la call antes de encolarla y la elimina automáticamente
     * tanto en onResponse como en onFailure.
     */
    protected final <T> void enqueueTracked(@NonNull Call<T> call, @NonNull Callback<T> delegate) {
        trackCall(call);
        call.enqueue(new Callback<T>() {
            @Override
            public void onResponse(@NonNull Call<T> c, @NonNull Response<T> response) {
                untrackCall(call);
                delegate.onResponse(c, response);
            }

            @Override
            public void onFailure(@NonNull Call<T> c, @NonNull Throwable t) {
                untrackCall(call);
                delegate.onFailure(c, t);
            }
        });
    }

    protected final int getTrackedCallCountForTest() {
        synchronized (inFlight) {
            return inFlight.size();
        }
    }
}
