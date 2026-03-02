package com.proyecto.moveon.data.remote.retrofit;

import android.os.SystemClock;

import com.proyecto.moveon.BuildConfig;
import com.proyecto.moveon.data.session.dto.AppSessionResponseDto;
import com.proyecto.moveon.utils.StringUtils;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class AppSessionProvider {

    private static volatile String cachedSession = null;
    private static long lastFetchTime = 0;
    private static final long CACHE_TTL_MS = BuildConfig.APP_SESSION_CACHE_TTL_MS;
    private static volatile HandshakeApi handshakeApi;

    private static final Object LOCK = new Object();

    private AppSessionProvider() {}

    private static HandshakeApi getApi() {
        if (handshakeApi == null) {
            synchronized (AppSessionProvider.class) {
                if (handshakeApi == null) {
                    // PUNTO 1: Añadido writeTimeout por consistencia
                    OkHttpClient cleanClient = new OkHttpClient.Builder()
                            .connectTimeout(10, TimeUnit.SECONDS)
                            .readTimeout(15, TimeUnit.SECONDS)
                            .writeTimeout(15, TimeUnit.SECONDS)
                            .build();

                    String baseUrl = BuildConfig.BASE_URL;
                    if (!baseUrl.endsWith("/")) baseUrl += "/";

                    Retrofit retrofit = new Retrofit.Builder()
                            .baseUrl(baseUrl)
                            .client(cleanClient)
                            .addConverterFactory(GsonConverterFactory.create())
                            .build();
                    handshakeApi = retrofit.create(HandshakeApi.class);
                }
            }
        }
        return handshakeApi;
    }

    public static String getOrFetch() throws Exception {
        long now = SystemClock.elapsedRealtime();

        if (cachedSession != null && (now - lastFetchTime) < CACHE_TTL_MS) {
            return cachedSession;
        }

        synchronized (LOCK) {
            now = SystemClock.elapsedRealtime();
            if (cachedSession != null && (now - lastFetchTime) < CACHE_TTL_MS) {
                return cachedSession;
            }
            cachedSession = fetchNewSession();
            lastFetchTime = SystemClock.elapsedRealtime();
            return cachedSession;
        }
    }

    public static void invalidate() {
        synchronized (LOCK) {
            cachedSession = null;
            lastFetchTime = 0;
        }
    }

    private static String fetchNewSession() throws Exception {
        retrofit2.Response<AppSessionResponseDto> response =
                getApi().getHandshake(BuildConfig.APP_ID).execute();

        if (response.isSuccessful() && response.body() != null) {
            String token = response.body().appSession;
            if (StringUtils.hasText(token)) {
                return token;
            }
        }

        // PUNTO 2: Extracción del body para Debug sin fugas de memoria
        String errorSnippet = "";
        if (response.errorBody() != null) {
            try {
                if (BuildConfig.DEBUG) {
                    String fullError = response.errorBody().string();
                    // Recortamos a 200 caracteres para no saturar los logs si el servidor escupe un HTML gigante
                    errorSnippet = " - Detalles: " + (fullError.length() > 200 ? fullError.substring(0, 200) + "..." : fullError);
                }
            } catch (Exception ignored) {
            } finally {
                // El finally garantiza que SIEMPRE se cierra el recurso, haya error de lectura o no
                try {
                    response.errorBody().close();
                } catch (Exception ignored) {}
            }
        }

        throw new Exception("Error Handshake: HTTP " + response.code() + errorSnippet);
    }
}