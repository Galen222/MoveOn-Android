package com.proyecto.moveon.data.remote.retrofit;

import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface MoveOnApi {

    @POST("/login")
    Call<ResponseBody> login(@Body RequestBody body);

    @POST("/registro")
    Call<ResponseBody> register(@Body RequestBody body);

    @POST("/token/refresh")
    Call<ResponseBody> refresh(@Body RequestBody body);

    @POST("/logout")
    Call<ResponseBody> logout(@Body RequestBody body);
}