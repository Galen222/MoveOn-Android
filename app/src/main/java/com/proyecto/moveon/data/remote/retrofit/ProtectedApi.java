package com.proyecto.moveon.data.remote.retrofit;

import com.google.gson.JsonElement;

import okhttp3.MultipartBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Url;
/**
 * Contrato que define las operaciones disponibles para protected api.
 */
public interface ProtectedApi {

    @GET
    /**
     * GET genérico a cualquier endpoint protegido. Usa {@code JsonElement}
     * como tipo de retorno para permitir a los repositorios parsear a su
     * DTO específico sin duplicar la API por recurso.
     *
     * @param url URL relativa al endpoint protegido.
     * @return llamada Retrofit con el JSON devuelto por el backend.
     */
    Call<JsonElement> get(@Url String url);

    @POST
    /**
     * POST genérico con cuerpo JSON. Pensado para endpoints protegidos
     * que crean recursos o lanzan acciones (login no, que es público).
     *
     * @param url URL relativa al endpoint.
     * @param body cuerpo JSON a enviar.
     * @return llamada Retrofit con la respuesta JSON del backend.
     */
    Call<JsonElement> post(@Url String url, @Body JsonElement body);

    @PATCH
    /**
     * PATCH genérico usado por los repositorios offline-first para enviar
     * solo los campos modificados del recurso (perfil, preferencias, etc.).
     *
     * @param url URL relativa al endpoint.
     * @param body JSON con los campos a actualizar.
     * @return llamada Retrofit con la respuesta del backend.
     */
    Call<JsonElement> patch(@Url String url, @Body JsonElement body);

    @DELETE
    /**
     * DELETE genérico para endpoints protegidos (p. ej. borrar una
     * actividad). Se mantiene devolviendo {@code JsonElement} por
     * simetría con el resto aunque algunas respuestas vengan vacías.
     *
     * @param url URL relativa al endpoint.
     * @return llamada Retrofit con la respuesta del backend.
     */
    Call<JsonElement> delete(@Url String url);

    @Multipart
    @POST
    /**
     * POST multipart para subir ficheros (p. ej. foto de perfil). Se define
     * por separado de {@link #post} porque Retrofit exige anotación
     * distinta para multipart.
     *
     * @param url URL relativa al endpoint que acepta el fichero.
     * @param file parte multipart con el fichero y sus metadatos.
     * @return llamada Retrofit con la respuesta del backend tras la subida.
     */
    Call<JsonElement> postMultipart(@Url String url, @Part MultipartBody.Part file);
}
