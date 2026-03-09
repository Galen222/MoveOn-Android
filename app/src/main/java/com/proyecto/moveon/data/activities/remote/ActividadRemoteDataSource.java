package com.proyecto.moveon.data.activities.remote;

import android.content.Context;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.proyecto.moveon.core.api.ApiError;
import com.proyecto.moveon.core.api.ApiErrorType;
import com.proyecto.moveon.core.api.ApiResult;
import com.proyecto.moveon.data.activities.dto.ActividadResponseDto;
import com.proyecto.moveon.data.activities.dto.ActividadesPageDto;
import com.proyecto.moveon.data.remote.AuthenticatedApiClient;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class ActividadRemoteDataSource {

    public interface Callback<T> {
        void onResult(ApiResult<T> result);
    }

    private static final String ENDPOINT_CREATE = "actividad/guardar";
    private static final String ENDPOINT_LIST = "actividad/obtener_todas";
    private static final String ENDPOINT_DELETE = "actividad/borrar/";
    private static final int PAGE_SIZE = 100;
    private static final long BLOCKING_TIMEOUT_SECONDS = 45L;

    private final AuthenticatedApiClient api;
    private final Gson gson = new Gson();

    public ActividadRemoteDataSource(@NonNull Context context) {
        this.api = new AuthenticatedApiClient(context.getApplicationContext());
    }

    public void createActividad(@NonNull JsonObject body, @NonNull Callback<ActividadResponseDto> callback) {
        api.postJson(ENDPOINT_CREATE, body,
                json -> gson.fromJson(json, ActividadResponseDto.class),
                callback::onResult);
    }

    public void deleteActividad(int remoteId, @NonNull Callback<String> callback) {
        api.delete(ENDPOINT_DELETE + remoteId,
                json -> {
                    JsonObject obj = json.getAsJsonObject();
                    return obj.has("mensaje") ? obj.get("mensaje").getAsString() : "OK";
                },
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
                                result.error != null ? result.error : ApiError.local("Error cargando actividades")
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
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ApiResult<ActividadResponseDto>> ref = new AtomicReference<>();
        createActividad(body, result -> {
            ref.set(result);
            latch.countDown();
        });
        return await(ref, latch, "Tiempo de espera agotado durante la sincronización de actividad");
    }

    @NonNull
    public ApiResult<String> deleteActividadBlocking(int remoteId) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ApiResult<String>> ref = new AtomicReference<>();
        deleteActividad(remoteId, result -> {
            ref.set(result);
            latch.countDown();
        });
        return await(ref, latch, "Tiempo de espera agotado al borrar la actividad");
    }

    @NonNull
    public ApiResult<List<ActividadResponseDto>> fetchAllActividadesBlocking() {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ApiResult<List<ActividadResponseDto>>> ref = new AtomicReference<>();
        fetchAllActividades(result -> {
            ref.set(result);
            latch.countDown();
        });
        return await(ref, latch, "Tiempo de espera agotado cargando actividades");
    }

    @NonNull
    private <T> ApiResult<T> await(@NonNull AtomicReference<ApiResult<T>> ref,
                                   @NonNull CountDownLatch latch,
                                   @NonNull String timeoutMessage) {
        try {
            boolean completed = latch.await(BLOCKING_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!completed) {
                return ApiResult.failure(ApiError.typed(ApiErrorType.TIMEOUT, timeoutMessage));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ApiResult.failure(ApiError.typed(
                    ApiErrorType.CANCELED,
                    "Sincronización de actividades interrumpida"
            ));
        }

        ApiResult<T> result = ref.get();
        return result != null ? result : ApiResult.failure(ApiError.local("Sin respuesta del servidor"));
    }

    public void cancelAll() {
        api.cancelAll();
    }
}
