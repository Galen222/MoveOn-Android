package com.proyecto.moveon.data.remote.retrofit;

import com.google.gson.JsonElement;

import okhttp3.MultipartBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Url;

public interface ProtectedApi {

    @GET
    Call<JsonElement> get(@Url String url);

    @POST
    Call<JsonElement> post(@Url String url, @Body JsonElement body);

    @PATCH
    Call<JsonElement> patch(@Url String url, @Body JsonElement body);

    @Multipart
    @POST
    Call<JsonElement> postMultipart(@Url String url, @Part MultipartBody.Part file);
}