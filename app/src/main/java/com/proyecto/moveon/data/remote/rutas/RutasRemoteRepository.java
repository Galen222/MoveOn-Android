// ESTO ES CODIGO DE EJEMPLO PARA CUANDO SE HAGAN LAS RUTAS

package com.proyecto.moveon.data.remote.rutas;

import android.content.Context;

import com.proyecto.moveon.data.remote.AuthenticatedApiClient;

import org.json.JSONArray;
import org.json.JSONObject;

public class RutasRemoteRepository {

    private final AuthenticatedApiClient api;

    public RutasRemoteRepository(Context context) {
        this.api = new AuthenticatedApiClient(context);
    }

    public interface Callback<T> {
        void onSuccess(T result);
        void onSessionExpired(String message);
        void onError(String error);
    }

    public void obtenerRutas(Callback<JSONArray> callback) {
        api.get("/rutas", json -> {
            // Ajusta la clave al JSON real de tu backend
            return json.optJSONArray("rutas") != null ? json.optJSONArray("rutas") : new JSONArray();
        }, new AuthenticatedApiClient.Callback<JSONArray>() {
            @Override
            public void onSuccess(JSONArray result) {
                callback.onSuccess(result);
            }

            @Override
            public void onSessionExpired(String message) {
                callback.onSessionExpired(message);
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    public void subirRutaPendiente(JSONObject rutaPayload, Callback<JSONObject> callback) {
        api.postJson("/rutas", rutaPayload, new AuthenticatedApiClient.Callback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject result) {
                callback.onSuccess(result);
            }

            @Override
            public void onSessionExpired(String message) {
                callback.onSessionExpired(message);
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }
}