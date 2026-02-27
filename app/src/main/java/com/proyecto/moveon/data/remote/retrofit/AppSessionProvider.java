package com.proyecto.moveon.data.remote.retrofit;

import com.proyecto.moveon.BuildConfig;
import com.proyecto.moveon.utils.StringUtils;

import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public final class AppSessionProvider {

    private static final Object LOCK = new Object();
    private static String cachedAppSession = null;
    private static long cachedAtMs = 0L;
    // El token dura 5 min en backend. Nos curamos en salud renovando a los 4 min.
    private static final long TTL_MS = TimeUnit.MINUTES.toMillis(4);
    private static final OkHttpClient handshakeClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();

    private AppSessionProvider() {}

    public static String getOrFetch() throws Exception {
        synchronized (LOCK) {
            long now = System.currentTimeMillis();
            if (cachedAppSession != null && (now - cachedAtMs) < TTL_MS) {
                return cachedAppSession;
            }

            String baseUrl = BuildConfig.BASE_URL;
            String appId = BuildConfig.APP_ID;

            if (!StringUtils.hasText(baseUrl)) throw new Exception("BASE_URL no está configurada");
            if (!StringUtils.hasText(appId)) throw new Exception("APP_ID no está configurado");

            Request req = new Request.Builder()
                    .url(baseUrl + "/handshake")
                    .get()
                    .addHeader("Accept", "application/json")
                    .addHeader("x-app-id", appId)
                    .build();

            try (Response resp = handshakeClient.newCall(req).execute()) {
                String body = resp.body() != null ? resp.body().string() : "";
                if (!resp.isSuccessful()) {
                    throw new Exception("Handshake falló: HTTP " + resp.code() + " " + body);
                }
                JSONObject json = new JSONObject(body);
                String token = json.optString("app_session_token", null);
                if (!StringUtils.hasText(token)) {
                    throw new Exception("Handshake: no se recibió app_session_token");
                }
                cachedAppSession = token;
                cachedAtMs = now;
                return cachedAppSession;
            }
        }
    }

    public static void invalidate() {
        synchronized (LOCK) {
            cachedAppSession = null;
            cachedAtMs = 0L;
        }
    }
}