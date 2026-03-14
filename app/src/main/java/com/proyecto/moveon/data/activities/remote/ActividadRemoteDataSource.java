package com.proyecto.moveon.data.activities.remote;

import android.content.Context;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.proyecto.moveon.R;
import com.proyecto.moveon.core.api.ApiError;
import com.proyecto.moveon.core.api.ApiErrorType;
import com.proyecto.moveon.core.api.ApiResult;
import com.proyecto.moveon.data.activities.dto.ActividadResponseDto;
import com.proyecto.moveon.data.activities.dto.ActividadesPageDto;
import com.proyecto.moveon.data.activities.dto.BorrarActividadResponseDto;
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
    private final Context appContext; // BUG-07: Almacenar contexto para acceder a R.string

    public ActividadRemoteDataSource(@NonNull Context context) {
        this.appContext = context.getApplicationContext(); // BUG-07
        this.api = new AuthenticatedApiClient(appContext);
    }

    public void createActividad(@NonNull JsonObject body, @NonNull Callback<ActividadResponseDto> callback) {
        api.postJson(ENDPOINT_CREATE, body,
                json -> gson.fromJson(json, ActividadResponseDto.class),
                callback::onResult);
    }

    // BUG-11: Ahora parsea el DTO completo (estatus + mensaje + nuevo_total_puntos)
    // en lugar de solo extraer el campo "mensaje" como String.
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
                                        // BUG-07: String hardcodeado sustituido por R.string
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
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ApiResult<ActividadResponseDto>> ref = new AtomicReference<>();
        createActividad(body, result -> {
            ref.set(result);
            latch.countDown();
        });
        // BUG-07: String hardcodeado sustituido por R.string
        return await(ref, latch,
                appContext.getString(R.string.error_timeout_sync_actividad));
    }

    // BUG-11: Tipo de retorno cambiado de ApiResult<String> a ApiResult<BorrarActividadResponseDto>
    @NonNull
    @SuppressWarnings("unused")
    public ApiResult<BorrarActividadResponseDto> deleteActividadBlocking(int remoteId) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ApiResult<BorrarActividadResponseDto>> ref = new AtomicReference<>();
        deleteActividad(remoteId, result -> {
            ref.set(result);
            latch.countDown();
        });
        // BUG-07: String hardcodeado sustituido por R.string
        return await(ref, latch,
                appContext.getString(R.string.error_timeout_borrar_actividad));
    }

    @NonNull
    public ApiResult<List<ActividadResponseDto>> fetchAllActividadesBlocking() {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ApiResult<List<ActividadResponseDto>>> ref = new AtomicReference<>();
        fetchAllActividades(result -> {
            ref.set(result);
            latch.countDown();
        });
        // BUG-07: String hardcodeado sustituido por R.string
        return await(ref, latch,
                appContext.getString(R.string.error_timeout_cargando_actividades));
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
                    // BUG-07: String hardcodeado sustituido por R.string
                    appContext.getString(R.string.error_sync_actividades_interrumpida)
            ));
        }

        ApiResult<T> result = ref.get();
        // BUG-07: String hardcodeado sustituido por R.string
        return result != null ? result
                : ApiResult.failure(ApiError.local(
                appContext.getString(R.string.error_sin_respuesta_servidor)));
    }

    public void cancelAll() {
        api.cancelAll();
    }
}
