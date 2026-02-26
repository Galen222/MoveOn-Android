package com.proyecto.moveon.data.remote.retrofit;

import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.*;

public interface ProtectedApi {

    @GET
    Call<ResponseBody> get(@Url String url);

    @HTTP(method = "DELETE")
    Call<ResponseBody> delete(@Url String url);

    @POST
    Call<ResponseBody> post(@Url String url, @Body RequestBody body);

    @PUT
    Call<ResponseBody> put(@Url String url, @Body RequestBody body);

    @PATCH
    Call<ResponseBody> patch(@Url String url, @Body RequestBody body);
}