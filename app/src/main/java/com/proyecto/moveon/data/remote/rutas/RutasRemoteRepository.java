// ESTO ES CODIGO DE EJEMPLO PARA CUANDO SE HAGAN LAS RUTAS
package com.proyecto.moveon.data.remote.rutas;

import android.content.Context;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.proyecto.moveon.core.api.ApiResult;
import com.proyecto.moveon.data.common.BaseRepository;
import com.proyecto.moveon.data.remote.AuthenticatedApiClient;
/**
 * Repositorio encargado de centralizar las operaciones de rutas remote.
 */
public class RutasRemoteRepository extends BaseRepository {

    public interface Callback<T> {
        void onResult(ApiResult<T> result);
    }

    private final AuthenticatedApiClient api;

    public RutasRemoteRepository(Context context) {
        this.api = new AuthenticatedApiClient(context.getApplicationContext());
    }

    public void obtenerRutas(Callback<JsonArray> callback) {
        // El endpoint relativo se mantiene sin barra inicial para respetar la base URL.
        api.get("rutas", json -> {
            if (json != null && json.isJsonObject()) {
                JsonObject obj = json.getAsJsonObject();
                JsonElement rutas = obj.get("rutas");
                if (rutas != null && rutas.isJsonArray()) return rutas.getAsJsonArray();
            }
            return new JsonArray();
        }, callback::onResult);
    }

    public void subirRutaPendiente(JsonObject rutaPayload, Callback<JsonObject> callback) {
        // El endpoint relativo se mantiene sin barra inicial para respetar la base URL.
        api.postJson("rutas", rutaPayload,
                json -> (json != null && json.isJsonObject()) ? json.getAsJsonObject() : new JsonObject(),
                callback::onResult);
    }

    @Override
    public void cancelAll() {
        super.cancelAll(); // Cancela cualquier petición directa si se añadiera en el futuro
        api.cancelAll();   // Propaga la cancelación al cliente API
    }
}