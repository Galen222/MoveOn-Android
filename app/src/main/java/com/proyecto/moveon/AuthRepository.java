package com.proyecto.moveon;

import android.os.Handler;
import android.os.Looper;

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

public class AuthRepository {

    private static final String BASE_URL = BuildConfig.BASE_URL;
    private static final String APP_ID = BuildConfig.APP_ID;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface Callback<T> {
        void onSuccess(T result);
        void onError(String error);
    }

    public static class LoginResult {
        public final String nombreUsuario;
        public final String tokenAcceso;

        public LoginResult(String nombreUsuario, String tokenAcceso) {
            this.nombreUsuario = nombreUsuario;
            this.tokenAcceso = tokenAcceso;
        }
    }

    public static class RegisterRequest {
        public String nombreUsuario;
        public String email;
        public String password;
        public String fechaNacimiento; // yyyy-MM-dd
        // ciudad NO se envía porque backend espera "provincia" enum
        public String provincia;       // opcional, exacta al backend o null
    }



    public void login(String identificador, String password, Callback<LoginResult> callback) {
        executor.execute(() -> {
            try {
                String appSession = handshake();

                JSONObject body = new JSONObject();
                body.put("identificador", identificador);
                body.put("contraseña", password);

                JSONObject resp = request("POST", "/login", body, appSession, null);

                String nombreUsuario = resp.optString("nombre_usuario");
                String token = resp.optString("token_acceso");

                mainHandler.post(() -> callback.onSuccess(new LoginResult(nombreUsuario, token)));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e.getMessage() != null ? e.getMessage() : "Error de login"));
            }
        });
    }

    public void register(RegisterRequest req, Callback<String> callback) {
        executor.execute(() -> {
            try {
                String appSession = handshake();

                JSONObject body = new JSONObject();
                body.put("nombre_usuario", req.nombreUsuario);
                body.put("email", req.email);
                body.put("contraseña", req.password);
                body.put("fecha_nacimiento", req.fechaNacimiento);

                if (req.provincia != null && !req.provincia.trim().isEmpty()) {
                    body.put("provincia", req.provincia.trim());
                }

                JSONObject resp = request("POST", "/registro", body, appSession, null);
                String msg = resp.optString("mensaje", "Cuenta creada correctamente");

                mainHandler.post(() -> callback.onSuccess(msg));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e.getMessage() != null ? e.getMessage() : "Error de registro"));
            }
        });
    }

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

        JSONObject resp = request("GET", "/handshake", null, null, APP_ID);
        String appSession = resp.optString("app_session_token", null);

        if (appSession == null || appSession.isEmpty()) {
            throw new Exception("No se recibió app_session_token");
        }

        return appSession;
    }

    private JSONObject request(String method,
                               String path,
                               JSONObject body,
                               String xAppSession,
                               String xAppId) throws Exception {

        URL url = new URL(BASE_URL + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);
        conn.setRequestProperty("Accept", "application/json");

        if (xAppId != null) conn.setRequestProperty("x-app-id", xAppId);
        if (xAppSession != null) conn.setRequestProperty("x-app-session", xAppSession);

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

        if (code < 200 || code >= 300) {
            throw new Exception(parseApiError(responseText, code));
        }

        return new JSONObject(responseText);
    }

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
}