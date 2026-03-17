package com.proyecto.moveon.data.profile.remote;

import android.content.Context;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.proyecto.moveon.data.profile.dto.ProfileInfoDto;
import com.proyecto.moveon.data.remote.AuthenticatedApiClient;

import java.io.File;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;

public class PerfilRemoteDataSource {

    public interface Callback<T> {
        void onResult(com.proyecto.moveon.core.api.ApiResult<T> result);
    }

    private static final String ENDPOINT_PROFILE        = "perfil/informacion";
    private static final String ENDPOINT_UPDATE         = "perfil/actualizar";
    private static final String ENDPOINT_PHOTO          = "perfil/foto";
    private static final String ENDPOINT_DELETE_ACCOUNT = "perfil/borrar";

    private final AuthenticatedApiClient api;
    private final Gson gson = new Gson();

    public PerfilRemoteDataSource(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
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

    public void eliminarCuenta(@NonNull Callback<String> callback) {
        api.delete(ENDPOINT_DELETE_ACCOUNT,
                json -> {
                    JsonObject obj = json.getAsJsonObject();
                    return obj.has("mensaje") ? obj.get("mensaje").getAsString() : "OK";
                },
                callback::onResult);
    }

    @NonNull
    public com.proyecto.moveon.core.api.ApiResult<ProfileInfoDto> fetchPerfilBlocking() {
        return api.getBlocking(ENDPOINT_PROFILE,
                json -> gson.fromJson(json, ProfileInfoDto.class));
    }

    @NonNull
    public com.proyecto.moveon.core.api.ApiResult<String> patchPerfilBlocking(@NonNull JsonObject body) {
        return api.patchJsonBlocking(ENDPOINT_UPDATE, body,
                json -> {
                    JsonObject obj = json.getAsJsonObject();
                    return obj.has("mensaje") ? obj.get("mensaje").getAsString() : "OK";
                });
    }

    @NonNull
    public com.proyecto.moveon.core.api.ApiResult<String> uploadPhotoBlocking(@NonNull File file) {
        MediaType mediaType = MediaType.parse(guessMimeType(file.getName()));
        RequestBody requestBody = RequestBody.create(file, mediaType);
        MultipartBody.Part part = MultipartBody.Part.createFormData(
                "archivo", file.getName(), requestBody);

        return api.postMultipartBlocking(ENDPOINT_PHOTO, part,
                json -> {
                    JsonObject obj = json.getAsJsonObject();
                    return obj.has("mensaje") ? obj.get("mensaje").getAsString() : "OK";
                });
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