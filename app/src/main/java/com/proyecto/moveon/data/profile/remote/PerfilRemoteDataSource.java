package com.proyecto.moveon.data.profile.remote;

import android.content.Context;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.proyecto.moveon.R;
import com.proyecto.moveon.core.api.ApiError;
import com.proyecto.moveon.core.api.ApiErrorType;
import com.proyecto.moveon.core.api.ApiResult;
import com.proyecto.moveon.data.profile.dto.ProfileInfoDto;
import com.proyecto.moveon.data.remote.AuthenticatedApiClient;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;

public class PerfilRemoteDataSource {

    public interface Callback<T> {
        void onResult(ApiResult<T> result);
    }

    private static final String ENDPOINT_PROFILE = "perfil/informacion";
    private static final String ENDPOINT_UPDATE  = "perfil/actualizar";
    private static final String ENDPOINT_PHOTO   = "perfil/foto";
    private static final long BLOCKING_TIMEOUT_SECONDS = 45L;

    private final AuthenticatedApiClient api;
    private final Gson gson = new Gson();
    private final Context appContext; // BUG-07: Almacenar contexto para acceder a R.string

    public PerfilRemoteDataSource(@NonNull Context context) {
        this.appContext = context.getApplicationContext(); // BUG-07
        this.api = new AuthenticatedApiClient(appContext);
    }

    public void fetchPerfil(@NonNull Callback<ProfileInfoDto> callback) {
        api.get(ENDPOINT_PROFILE,
                json -> gson.fromJson(json, ProfileInfoDto.class),
                callback::onResult);
    }

    public void patchPerfil(@NonNull JsonObject body, @NonNull Callback<String> callback) {
        api.patchJson(ENDPOINT_UPDATE, body,
                json -> {
                    JsonObject obj = json.getAsJsonObject();
                    return obj.has("mensaje") ? obj.get("mensaje").getAsString() : "OK";
                },
                callback::onResult);
    }

    public void uploadPhoto(@NonNull File file, @NonNull Callback<String> callback) {
        MediaType mediaType = MediaType.parse(guessMimeType(file.getName()));
        RequestBody requestBody = RequestBody.create(file, mediaType);
        MultipartBody.Part part = MultipartBody.Part.createFormData(
                "archivo", file.getName(), requestBody);

        api.postMultipart(ENDPOINT_PHOTO, part,
                json -> {
                    JsonObject obj = json.getAsJsonObject();
                    return obj.has("mensaje") ? obj.get("mensaje").getAsString() : "OK";
                },
                callback::onResult);
    }

    @NonNull
    public ApiResult<ProfileInfoDto> fetchPerfilBlocking() {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ApiResult<ProfileInfoDto>> ref = new AtomicReference<>();
        fetchPerfil(result -> {
            ref.set(result);
            latch.countDown();
        });
        return await(ref, latch);
    }

    @NonNull
    public ApiResult<String> patchPerfilBlocking(@NonNull JsonObject body) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ApiResult<String>> ref = new AtomicReference<>();
        patchPerfil(body, result -> {
            ref.set(result);
            latch.countDown();
        });
        return await(ref, latch);
    }

    @NonNull
    public ApiResult<String> uploadPhotoBlocking(@NonNull File file) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ApiResult<String>> ref = new AtomicReference<>();
        uploadPhoto(file, result -> {
            ref.set(result);
            latch.countDown();
        });
        return await(ref, latch);
    }

    @NonNull
    private <T> ApiResult<T> await(@NonNull AtomicReference<ApiResult<T>> ref,
                                   @NonNull CountDownLatch latch) {
        try {
            boolean completed = latch.await(BLOCKING_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!completed) {
                return ApiResult.failure(ApiError.typed(
                        ApiErrorType.TIMEOUT,
                        // BUG-07: String hardcodeado sustituido por R.string
                        appContext.getString(R.string.error_timeout_sync_perfil)
                ));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ApiResult.failure(ApiError.typed(
                    ApiErrorType.CANCELED,
                    // BUG-07: String hardcodeado sustituido por R.string
                    appContext.getString(R.string.error_sync_perfil_interrumpida)
            ));
        }

        ApiResult<T> result = ref.get();
        // BUG-07: String hardcodeado sustituido por R.string
        return result != null ? result
                : ApiResult.failure(ApiError.local(
                appContext.getString(R.string.error_sin_respuesta_servidor)));
    }

    @NonNull
    private String guessMimeType(@NonNull String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }

    public void cancelAll() {
        api.cancelAll();
    }
}
