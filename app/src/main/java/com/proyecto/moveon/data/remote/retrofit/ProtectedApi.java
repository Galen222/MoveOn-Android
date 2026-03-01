package com.proyecto.moveon.data.remote.retrofit;

import com.google.gson.JsonElement;

import retrofit2.Call;
import retrofit2.http.*;

public interface ProtectedApi {

    @GET
    Call<JsonElement> get(@Url String url);

    @HTTP(method = "DELETE")
    Call<JsonElement> delete(@Url String url);

    @POST
    Call<JsonElement> post(@Url String url, @Body JsonElement body);

    @PUT
    Call<JsonElement> put(@Url String url, @Body JsonElement body);

    @PATCH
    Call<JsonElement> patch(@Url String url, @Body JsonElement body);
}