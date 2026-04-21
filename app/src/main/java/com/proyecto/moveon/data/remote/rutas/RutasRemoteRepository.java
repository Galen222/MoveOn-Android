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
        /**
         * Callback invocado con el resultado de una operación sobre rutas.
         * Unifica éxito y error en un único tipo para que los llamadores no
         * tengan que manejar excepciones por separado.
         *
         * @param result datos devueltos o el error ocurrido.
         */
        void onResult(ApiResult<T> result);
    }

    private final AuthenticatedApiClient api;

    /**
     * Crea el repositorio construyendo su propio {@link AuthenticatedApiClient}
     * a partir del contexto de aplicación, para que el access token viaje
     * automáticamente en cada petición.
     *
     * @param context cualquier contexto; sólo se usa su {@code applicationContext}.
     */
    public RutasRemoteRepository(Context context) {
        this.api = new AuthenticatedApiClient(context.getApplicationContext());
    }

    /**
     * Pide al backend el listado de rutas del usuario y entrega la lista
     * ya extraída del campo {@code rutas} del payload al callback. Si el
     * backend responde con un JSON inesperado, devuelve un array vacío en
     * vez de propagar un error de parseo.
     *
     * @param callback callback que recibirá la lista o el error.
     */
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

    /**
     * Envía una ruta pendiente al backend como POST JSON. Extrae el objeto
     * del payload de respuesta o devuelve un objeto vacío si el backend no
     * responde con el formato esperado.
     *
     * @param rutaPayload cuerpo JSON de la ruta a subir.
     * @param callback callback que recibirá la respuesta o el error.
     */
    public void subirRutaPendiente(JsonObject rutaPayload, Callback<JsonObject> callback) {
        // El endpoint relativo se mantiene sin barra inicial para respetar la base URL.
        api.postJson("rutas", rutaPayload,
                json -> (json != null && json.isJsonObject()) ? json.getAsJsonObject() : new JsonObject(),
                callback::onResult);
    }

    @Override
    /**
     * Cancela cualquier petición en vuelo del repositorio y propaga la
     * cancelación al {@link AuthenticatedApiClient} subyacente. Se invoca
     * desde {@code onCleared} del ViewModel para no entregar resultados a
     * una UI ya destruida.
     */
    public void cancelAll() {
        super.cancelAll(); // Cancela cualquier petición directa si se añadiera en el futuro
        api.cancelAll();   // Propaga la cancelación al cliente API
    }
}