
package com.proyecto.moveon.data.activities.remote;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.proyecto.moveon.R;
import com.proyecto.moveon.core.i18n.AppLanguageManager;
import com.proyecto.moveon.core.api.ApiError;
import com.proyecto.moveon.core.api.ApiResult;
import com.proyecto.moveon.core.concurrency.MoveOnExecutors;
import com.proyecto.moveon.data.activities.dto.ActividadResponseDto;
import com.proyecto.moveon.data.activities.dto.ActividadesPageDto;
import com.proyecto.moveon.data.activities.dto.BorrarActividadResponseDto;
import com.proyecto.moveon.data.remote.AuthenticatedApiClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Fuente de datos remota de actividades construida sobre {@link AuthenticatedApiClient}.
 */
public class ActividadRemoteDataSource {

    /**
     * Callback estándar para operaciones remotas de actividades.
     */
    public interface Callback<T> {
        /**
         * Entrega el resultado final de la operación remota.
         *
         * @param result éxito o error de la petición.
         */
        void onResult(ApiResult<T> result);
    }

    private static final String ENDPOINT_CREATE = "actividad/guardar";
    private static final String ENDPOINT_LIST = "actividad/obtener_todas";
    private static final String ENDPOINT_DELETE = "actividad/borrar/";
    private static final int PAGE_SIZE = 100;

    private final AuthenticatedApiClient api;
    private final Gson gson = new Gson();
    private final Context appContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /**
     * Crea la fuente remota usando siempre el contexto de aplicación.
     *
     * @param context cualquier contexto Android desde el que obtener el {@code applicationContext}.
     */
    public ActividadRemoteDataSource(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        this.api = new AuthenticatedApiClient(appContext);
    }

    /**
     * Envía al backend una nueva actividad serializada en JSON.
     *
     * @param body cuerpo JSON de creación.
     * @param callback callback que recibe la actividad creada o el error correspondiente.
     */
    public void createActividad(@NonNull JsonObject body, @NonNull Callback<ActividadResponseDto> callback) {
        api.postJson(ENDPOINT_CREATE, body,
                json -> gson.fromJson(json, ActividadResponseDto.class),
                callback::onResult);
    }

    /**
     * Solicita al backend el borrado de una actividad ya sincronizada.
     *
     * @param remoteId identificador remoto de la actividad.
     * @param callback callback que recibe la respuesta de borrado o el error correspondiente.
     */
    public void deleteActividad(int remoteId, @NonNull Callback<BorrarActividadResponseDto> callback) {
        api.delete(ENDPOINT_DELETE + remoteId,
                json -> gson.fromJson(json, BorrarActividadResponseDto.class),
                callback::onResult);
    }

    /**
     * Obtiene todas las actividades con paginación automática.
     *
     * <p>La carga delega en {@link #fetchAllActividadesBlocking()} (iterativo),
     * ejecutándolo en hilo IO y devolviendo el resultado en main thread.
     * Así se evita la recursión de callbacks en {@code fetchPage()}.
     * Esto elimina el riesgo teórico de acumulación de stack frames
     * con backends que devuelvan muchas páginas.</p>
     */
    public void fetchAllActividades(@NonNull Callback<List<ActividadResponseDto>> callback) {
        MoveOnExecutors.executeIo(() -> {
            ApiResult<List<ActividadResponseDto>> result = fetchAllActividadesBlocking();
            mainHandler.post(() -> callback.onResult(result));
        });
    }

    /**
     * Envía al backend una nueva actividad en modo bloqueante.
     *
     * @param body cuerpo JSON de creación.
     * @return {@link ApiResult} con la actividad creada o el error correspondiente.
     */
    @NonNull
    public ApiResult<ActividadResponseDto> createActividadBlocking(@NonNull JsonObject body) {
        return api.postJsonBlocking(ENDPOINT_CREATE, body,
                json -> gson.fromJson(json, ActividadResponseDto.class));
    }

    /**
     * Descarga todas las actividades remotas paginando hasta agotar el backend.
     *
     * @return {@link ApiResult} con la lista agregada de actividades o el error encontrado durante la paginación.
     */
    @NonNull
    public ApiResult<List<ActividadResponseDto>> fetchAllActividadesBlocking() {
        List<ActividadResponseDto> acc = new ArrayList<>();
        int skip = 0;

        while (true) {
            String endpoint = ENDPOINT_LIST + "?skip=" + skip + "&limit=" + PAGE_SIZE;
            ApiResult<ActividadesPageDto> result = api.getBlocking(endpoint,
                    json -> gson.fromJson(json, ActividadesPageDto.class));

            if (!result.isSuccess()) {
                return ApiResult.failure(result.error != null
                        ? result.error
                        : ApiError.local(AppLanguageManager.getString(appContext, R.string.error_cargando_actividades)));
            }

            ActividadesPageDto page = result.data;
            if (page == null) {
                return ApiResult.success(acc);
            }

            if (page.items != null && !page.items.isEmpty()) {
                acc.addAll(page.items);
            }

            if (!page.hasMore) {
                return ApiResult.success(acc);
            }

            skip = page.skip + page.limit;
        }
    }

    /**
     * Cancela todas las llamadas remotas actualmente en vuelo de esta fuente de datos.
     */
    public void cancelAll() {
        api.cancelAll();
    }
}

