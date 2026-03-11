package com.proyecto.moveon.data.common;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Clase base para repositorios y clientes de red.
 * Centraliza el seguimiento y cancelación de peticiones.
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
        return inFlight.size();
    }
}
