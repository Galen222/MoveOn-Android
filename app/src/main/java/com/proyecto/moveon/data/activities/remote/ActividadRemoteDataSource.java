package com.proyecto.moveon.data.activities.remote;

import android.content.Context;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.proyecto.moveon.R;
import com.proyecto.moveon.core.api.ApiError;
import com.proyecto.moveon.core.api.ApiResult;
import com.proyecto.moveon.data.activities.dto.ActividadResponseDto;
import com.proyecto.moveon.data.activities.dto.ActividadesPageDto;
import com.proyecto.moveon.data.activities.dto.BorrarActividadResponseDto;
import com.proyecto.moveon.data.remote.AuthenticatedApiClient;

import java.util.ArrayList;
import java.util.List;

public class ActividadRemoteDataSource {

    public interface Callback<T> {
        void onResult(ApiResult<T> result);
    }

    private static final String ENDPOINT_CREATE = "actividad/guardar";
    private static final String ENDPOINT_LIST = "actividad/obtener_todas";
    private static final String ENDPOINT_DELETE = "actividad/borrar/";
    private static final int PAGE_SIZE = 100;

    private final AuthenticatedApiClient api;
    private final Gson gson = new Gson();
    private final Context appContext;

    public ActividadRemoteDataSource(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        this.api = new AuthenticatedApiClient(appContext);
    }

    public void createActividad(@NonNull JsonObject body, @NonNull Callback<ActividadResponseDto> callback) {
        api.postJson(ENDPOINT_CREATE, body,
                json -> gson.fromJson(json, ActividadResponseDto.class),
                callback::onResult);
    }

    public void deleteActividad(int remoteId, @NonNull Callback<BorrarActividadResponseDto> callback) {
        api.delete(ENDPOINT_DELETE + remoteId,
                json -> gson.fromJson(json, BorrarActividadResponseDto.class),
                callback::onResult);
    }

    public void fetchAllActividades(@NonNull Callback<List<ActividadResponseDto>> callback) {
        fetchPage(0, new ArrayList<>(), callback);
    }

    private void fetchPage(int skip,
                           @NonNull List<ActividadResponseDto> acc,
                           @NonNull Callback<List<ActividadResponseDto>> callback) {
        String endpoint = ENDPOINT_LIST + "?skip=" + skip + "&limit=" + PAGE_SIZE;
        api.get(endpoint,
                json -> gson.fromJson(json, ActividadesPageDto.class),
                result -> {
                    if (!result.isSuccess()) {
                        callback.onResult(ApiResult.failure(
                                result.error != null ? result.error
                                        : ApiError.local(appContext.getString(R.string.error_cargando_actividades))
                        ));
                        return;
                    }

                    ActividadesPageDto page = result.data;
                    if (page == null) {
                        callback.onResult(ApiResult.success(acc));
                        return;
                    }

                    if (page.items != null && !page.items.isEmpty()) {
                        acc.addAll(page.items);
                    }

                    if (page.hasMore) {
                        int nextSkip = page.skip + page.limit;
                        fetchPage(nextSkip, acc, callback);
                    } else {
                        callback.onResult(ApiResult.success(acc));
                    }
                });
    }

    @NonNull
    public ApiResult<ActividadResponseDto> createActividadBlocking(@NonNull JsonObject body) {
        return api.postJsonBlocking(ENDPOINT_CREATE, body,
                json -> gson.fromJson(json, ActividadResponseDto.class));
    }

    @NonNull
    @SuppressWarnings("unused")
    public ApiResult<BorrarActividadResponseDto> deleteActividadBlocking(int remoteId) {
        return api.deleteBlocking(ENDPOINT_DELETE + remoteId,
                json -> gson.fromJson(json, BorrarActividadResponseDto.class));
    }

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
                        : ApiError.local(appContext.getString(R.string.error_cargando_actividades)));
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

    public void cancelAll() {
        api.cancelAll();
    }
}
