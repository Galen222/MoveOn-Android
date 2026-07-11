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
 * Centraliza el seguimiento y cancelación de peticiones Retrofit.
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

    /**
     * Cancela y elimina todas las llamadas Retrofit que este repositorio sigue teniendo en vuelo.
     *
     * <p>La lista se vacía bajo bloqueo antes de cancelar cada {@link Call} para evitar carreras
     * con hilos que estén añadiendo o retirando peticiones en paralelo.</p>
     */
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

    /**
     * Registra una llamada como activa para poder cancelarla más adelante desde {@link #cancelAll()}.
     *
     * @param call llamada a seguir; se ignora si es {@code null}.
     */
    protected void trackCall(@Nullable Call<?> call) {
        if (call != null) {
            inFlight.add(call);
        }
    }

    /**
     * Elimina una llamada del conjunto de peticiones activas cuando ya ha terminado o se ha descartado.
     *
     * @param call llamada a retirar; se ignora si es {@code null}.
     */
    protected void untrackCall(@Nullable Call<?> call) {
        if (call != null) {
            inFlight.remove(call);
        }
    }

    /**
     * Encola una llamada Retrofit delegando la respuesta pero ocupándose del ciclo de tracking.
     *
     * @param call llamada que debe seguirse y encolarse.
     * @param delegate callback real que procesará la respuesta o el fallo.
     * @param <T> tipo de cuerpo esperado por la llamada.
     *
     * @see #trackCall(Call)
     * @see #untrackCall(Call)
     */
    protected final <T> void enqueueTracked(@NonNull Call<T> call, @NonNull Callback<T> delegate) {
        trackCall(call);
        call.enqueue(new Callback<>() {
            /**
             * Retira la llamada del registro activo antes de delegar el procesamiento de la respuesta real.
             *
             * @param c llamada que acaba de completarse.
             * @param response respuesta entregada por Retrofit.
             */
            @Override
            public void onResponse(@NonNull Call<T> c, @NonNull Response<T> response) {
                untrackCall(call);
                delegate.onResponse(c, response);
            }

            /**
             * Retira la llamada del registro activo y reenvía el fallo al callback original.
             *
             * @param c llamada que falló.
             * @param t causa del error o cancelación.
             */
            @Override
            public void onFailure(@NonNull Call<T> c, @NonNull Throwable t) {
                untrackCall(call);
                delegate.onFailure(c, t);
            }
        });
    }

    /**
     * Expone el número de llamadas activas para pruebas que validan deduplicación o cancelación.
     *
     * @return tamaño actual del conjunto de llamadas en vuelo.
     */
    protected final int getTrackedCallCountForTest() {
        synchronized (inFlight) {
            return inFlight.size();
        }
    }
}
