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
/**
 * Fuente de datos remota del perfil construida sobre {@link AuthenticatedApiClient}.
 */
public class PerfilRemoteDataSource {

    /**
     * Callback estándar para operaciones remotas de perfil.
     */
    public interface Callback<T> {
        /**
         * Entrega el resultado final de la operación remota.
         *
         * @param result éxito o error de la petición.
         */
        void onResult(com.proyecto.moveon.core.api.ApiResult<T> result);
    }

    private static final String ENDPOINT_PROFILE        = "perfil/informacion";
    private static final String ENDPOINT_UPDATE         = "perfil/actualizar";
    private static final String ENDPOINT_PHOTO          = "perfil/foto";
    private static final String ENDPOINT_DELETE_ACCOUNT = "perfil/borrar";

    private final AuthenticatedApiClient api;
    private final Gson gson = new Gson();

    /**
     * Crea la fuente remota usando un cliente autenticado ligado al contexto de aplicación.
     *
     * @param context cualquier contexto Android desde el que obtener el {@code applicationContext}.
     */
    public PerfilRemoteDataSource(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        this.api = new AuthenticatedApiClient(appContext);
    }

    /**
     * Recupera el perfil actual del usuario autenticado de forma asíncrona.
     *
     * @param callback callback que recibe un {@link ProfileInfoDto} o el error correspondiente.
     */
    public void fetchPerfil(@NonNull Callback<ProfileInfoDto> callback) {
        api.get(ENDPOINT_PROFILE,
                json -> gson.fromJson(json, ProfileInfoDto.class),
                callback::onResult);
    }

    /**
     * Envía un parche parcial del perfil y devuelve el mensaje textual del backend.
     *
     * @param body cuerpo JSON con solo los campos modificados.
     * @param callback callback que recibe el mensaje final o el error correspondiente.
     */
    public void patchPerfil(@NonNull JsonObject body, @NonNull Callback<String> callback) {
        api.patchJson(ENDPOINT_UPDATE, body,
                json -> {
                    JsonObject obj = json.getAsJsonObject();
                    return obj.has("mensaje") ? obj.get("mensaje").getAsString() : "OK";
                },
                callback::onResult);
    }

    /**
     * Solicita la eliminación de la cuenta autenticada.
     *
     * @param callback callback que recibe el mensaje final o el error correspondiente.
     */
    public void eliminarCuenta(@NonNull Callback<String> callback) {
        api.delete(ENDPOINT_DELETE_ACCOUNT,
                json -> {
                    JsonObject obj = json.getAsJsonObject();
                    return obj.has("mensaje") ? obj.get("mensaje").getAsString() : "OK";
                },
                callback::onResult);
    }

    /**
     * Recupera el perfil actual del usuario autenticado en modo bloqueante.
     *
     * @return {@link com.proyecto.moveon.core.api.ApiResult} con el {@link ProfileInfoDto} o el error correspondiente.
     */
    @NonNull
    public com.proyecto.moveon.core.api.ApiResult<ProfileInfoDto> fetchPerfilBlocking() {
        return api.getBlocking(ENDPOINT_PROFILE,
                json -> gson.fromJson(json, ProfileInfoDto.class));
    }

    /**
     * Envía un parche parcial del perfil en modo bloqueante.
     *
     * @param body cuerpo JSON con solo los campos modificados.
     * @return {@link com.proyecto.moveon.core.api.ApiResult} con el mensaje final o el error correspondiente.
     */
    @NonNull
    public com.proyecto.moveon.core.api.ApiResult<String> patchPerfilBlocking(@NonNull JsonObject body) {
        return api.patchJsonBlocking(ENDPOINT_UPDATE, body,
                json -> {
                    JsonObject obj = json.getAsJsonObject();
                    return obj.has("mensaje") ? obj.get("mensaje").getAsString() : "OK";
                });
    }

    /**
     * Sube una nueva foto de perfil en modo bloqueante construyendo internamente el multipart adecuado.
     *
     * @param file fichero local que debe enviarse al backend.
     * @return {@link com.proyecto.moveon.core.api.ApiResult} con el mensaje final o el error correspondiente.
     */
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

    /**
     * Deduce un MIME sencillo a partir de la extensión del archivo de foto.
     *
     * @param fileName nombre del archivo local.
     * @return MIME que se usará al construir el multipart.
     */
    @NonNull
    private String guessMimeType(@NonNull String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }

    /**
     * Cancela todas las llamadas remotas actualmente en vuelo de esta fuente de datos.
     */
    public void cancelAll() {
        api.cancelAll();
    }
}