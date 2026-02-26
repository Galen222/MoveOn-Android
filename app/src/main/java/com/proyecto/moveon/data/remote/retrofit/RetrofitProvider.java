package com.proyecto.moveon.data.remote.retrofit;

import android.content.Context;

import com.proyecto.moveon.BuildConfig;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;

public final class RetrofitProvider {

    private static volatile Retrofit retrofit;
    private static volatile MoveOnApi moveOnApi;
    private static volatile ProtectedApi protectedApi;

    private RetrofitProvider() {}

    public static MoveOnApi authApi(Context context) {
        ensureInit(context);
        return moveOnApi;
    }

    public static ProtectedApi protectedApi(Context context) {
        ensureInit(context);
        return protectedApi;
    }

    private static void ensureInit(Context context) {
        if (retrofit != null) return;

        synchronized (RetrofitProvider.class) {
            if (retrofit != null) return;

            OkHttpClient.Builder http = new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .writeTimeout(15, TimeUnit.SECONDS)
                    .addInterceptor(new AppSessionInterceptor())
                    .addInterceptor(new AuthHeaderInterceptor(context));

            if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor log = new HttpLoggingInterceptor();
                log.setLevel(HttpLoggingInterceptor.Level.BODY);
                http.addInterceptor(log);
            }

            retrofit = new Retrofit.Builder()
                    .baseUrl(BuildConfig.BASE_URL + "/")
                    .client(http.build())
                    .build();

            moveOnApi = retrofit.create(MoveOnApi.class);
            protectedApi = retrofit.create(ProtectedApi.class);
        }
    }
}