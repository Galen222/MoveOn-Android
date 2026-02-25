package com.proyecto.moveon.data.remote;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import com.proyecto.moveon.BuildConfig;
import com.proyecto.moveon.data.session.SecureSessionManager;
import com.proyecto.moveon.data.session.SessionRefreshHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Wrapper para endpoints protegidos:
 * - usa access token de SecureSessionManager
 * - SIEMPRE obtiene x-app-session (handshake) para endpoints protegidos
 * - si recibe 401 -> refresh (1 vez) -> reintenta
 * - si refresh invalida sesión -> notifica onSessionExpired
 *
 * No usar para login/registro/logout/refresh (eso ya va en AuthRepository).
 */
public class AuthenticatedApiClient {

    private static final String BASE_URL = BuildConfig.BASE_URL;
    private static final String APP_ID = BuildConfig.APP_ID;

    private final Context appContext;
    private final SecureSessionManager sessionManager;
    private final SessionRefreshHelper refreshHelper;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface Callback<T> {
        void onSuccess(T result);
        void onSessionExpired(String message);
        void onError(String error);
    }

    public interface JsonMapper<T> {
        T map(JSONObject json) throws Exception;
    }

    public AuthenticatedApiClient(Context context) {
        this.appContext = context.getApplicationContext();
        this.sessionManager = new SecureSessionManager(appContext);
        this.refreshHelper = new SessionRefreshHelper(appContext);
    }

    // =========================
    // API pública (JSON)
    // =========================

    public void getJson(String path, Callback<JSONObject> callback) {
        requestJson("GET", path, null, callback);
    }

    public void deleteJson(String path, Callback<JSONObject> callback) {
        requestJson("DELETE", path, null, callback);
    }

    public void postJson(String path, JSONObject body, Callback<JSONObject> callback) {
        requestJson("POST", path, body, callback);
    }

    public void putJson(String path, JSONObject body, Callback<JSONObject> callback) {
        requestJson("PUT", path, body, callback);
    }

    public void patchJson(String path, JSONObject body, Callback<JSONObject> callback) {
        requestJson("PATCH", path, body, callback);
    }

    public <T> void get(String path, JsonMapper<T> mapper, Callback<T> callback) {
        request("GET", path, null, mapper, callback);
    }

    public <T> void post(String path, JSONObject body, JsonMapper<T> mapper, Callback<T> callback) {
        request("POST", path, body, mapper, callback);
    }

    public <T> void put(String path, JSONObject body, JsonMapper<T> mapper, Callback<T> callback) {
        request("PUT", path, body, mapper, callback);
    }

    public <T> void patch(String path, JSONObject body, JsonMapper<T> mapper, Callback<T> callback) {
        request("PATCH", path, body, mapper, callback);
    }

    public <T> void delete(String path, JsonMapper<T> mapper, Callback<T> callback) {
        request("DELETE", path, null, mapper, callback);
    }

    public void requestJson(String method, String path, @Nullable JSONObject body, Callback<JSONObject> callback) {
        request(method, path, body, json -> json, callback);
    }

    public <T> void request(String method,
                            String path,
                            @Nullable JSONObject body,
                            JsonMapper<T> mapper,
                            Callback<T> callback) {

        executor.execute(() -> {
            try {
                T result = executeProtectedRequest(method, path, body, mapper);
                postSuccess(callback, result);

            } catch (UnauthorizedException first401) {
                // Access token inválido/expirado => refresh + retry (1 sola vez)
                refreshHelper.refreshIfNeeded(new SessionRefreshHelper.Callback() {
                    @Override
                    public void onSuccess() {
                        executor.execute(() -> {
                            try {
                                T retryResult = executeProtectedRequest(method, path, body, mapper);
                                postSuccess(callback, retryResult);
                            } catch (UnauthorizedException second401) {
                                postError(callback, second401.getMessage());
                            } catch (Exception e) {
                                postError(callback, safeMessage(e, "Error reintentando petición"));
                            }
                        });
                    }

                    @Override
                    public void onSessionExpired(String message) {
                        postSessionExpired(callback, message);
                    }

                    @Override
                    public void onError(String message) {
                        // Error temporal (sin red/timeout) => no cerramos sesión
                        postError(callback, message);
                    }
                });

            } catch (Exception e) {
                postError(callback, safeMessage(e, "Error de conexión"));
            }
        });
    }

    // =========================
    // Núcleo protegido
    // =========================

    private <T> T executeProtectedRequest(String method,
                                          String path,
                                          @Nullable JSONObject body,
                                          JsonMapper<T> mapper) throws Exception {

        String accessToken = sessionManager.getAccessToken();
        if (accessToken == null || accessToken.trim().isEmpty()) {
            throw new UnauthorizedException("No hay access token");
        }

        // Tu backend exige x-app-session también en endpoints protegidos
        String appSession = handshake();

        JSONObject response = rawProtectedRequest(method, path, body, accessToken, appSession);
        return mapper.map(response);
    }

    private JSONObject rawProtectedRequest(String method,
                                           String path,
                                           @Nullable JSONObject body,
                                           String bearerAccessToken,
                                           String xAppSession) throws Exception {

        validateClientConfig();

        URL url = new URL(BASE_URL + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);
        conn.setRequestProperty("Accept", "application/json");

        conn.setRequestProperty("x-app-session", xAppSession);

        if (bearerAccessToken != null && !bearerAccessToken.trim().isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + bearerAccessToken);
        }

        if (body != null) {
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

            try (OutputStream os = conn.getOutputStream();
                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8))) {
                writer.write(body.toString());
                writer.flush();
            }
        }

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        String responseText = readAll(is);

        if (code == 401) {
            throw new UnauthorizedException(parseApiError(responseText, code));
        }

        if (code < 200 || code >= 300) {
            throw new Exception(parseApiError(responseText, code));
        }

        if (responseText == null || responseText.trim().isEmpty()) {
            return new JSONObject();
        }

        return new JSONObject(responseText);
    }

    // =========================
    // Handshake (x-app-session)
    // =========================

    private void validateClientConfig() throws Exception {
        if (BASE_URL == null || BASE_URL.trim().isEmpty()) {
            throw new Exception("BASE_URL no está configurada. Revisa BuildConfig.BASE_URL.");
        }

        if (APP_ID == null || APP_ID.trim().isEmpty()) {
            throw new Exception("APP_ID está vacío. Revisa local.properties (APP_ID) y recompila la app.");
        }
    }

    private String handshake() throws Exception {
        validateClientConfig();

        URL url = new URL(BASE_URL + "/handshake");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("x-app-id", APP_ID);

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        String responseText = readAll(is);

        if (code < 200 || code >= 300) {
            throw new Exception(parseApiError(responseText, code));
        }

        JSONObject resp = new JSONObject(responseText);
        String appSession = resp.optString("app_session_token", null);

        if (appSession == null || appSession.trim().isEmpty()) {
            throw new Exception("No se recibió app_session_token");
        }

        return appSession;
    }

    // =========================
    // Utils
    // =========================

    private String readAll(InputStream is) throws Exception {
        if (is == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    private String parseApiError(String body, int statusCode) {
        try {
            JSONObject obj = new JSONObject(body);
            if (obj.has("detail")) {
                Object detail = obj.get("detail");

                if (detail instanceof JSONArray) {
                    JSONArray arr = (JSONArray) detail;
                    if (arr.length() > 0) {
                        JSONObject first = arr.optJSONObject(0);
                        if (first != null) {
                            String mensaje = first.optString("mensaje", null);
                            if (mensaje != null && !mensaje.isEmpty()) return mensaje;
                        }
                    }
                }
                return String.valueOf(detail);
            }
        } catch (Exception ignored) { }

        return "Error HTTP " + statusCode;
    }

    private <T> void postSuccess(Callback<T> callback, T result) {
        mainHandler.post(() -> callback.onSuccess(result));
    }

    private <T> void postSessionExpired(Callback<T> callback, String message) {
        mainHandler.post(() -> callback.onSessionExpired(message));
    }

    private <T> void postError(Callback<T> callback, String message) {
        mainHandler.post(() -> callback.onError(message));
    }

    private String safeMessage(Exception e, String fallback) {
        String msg = e.getMessage();
        return (msg == null || msg.trim().isEmpty()) ? fallback : msg;
    }

    private static class UnauthorizedException extends Exception {
        UnauthorizedException(String message) {
            super(message);
        }
    }
}