package com.proyecto.moveon.data.session;

import android.content.Context;
import com.proyecto.moveon.data.remote.retrofit.RetrofitProvider;
import com.proyecto.moveon.utils.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;

public class AuthRepository {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private final Context appContext;
    // Tracking de llamadas en vuelo (para poder cancelar en onCleared del VM)
    private final List<Call<?>> inFlight = new CopyOnWriteArrayList<>();

    public AuthRepository(Context context) {
        this.appContext = context.getApplicationContext();
    }

    // Cancelar todas las llamadas pendientes
    public void cancelAll() {
        for (Call<?> c : inFlight) {
            if (c != null && !c.isCanceled()) c.cancel();
        }
        inFlight.clear();
    }

    public interface Callback<T> {
        void onSuccess(T result);
        void onError(String error);
    }

    public static class LoginResult {
        public final String nombreUsuario;
        public final String tokenAcceso;
        public final String refreshToken;
        public LoginResult(String nombreUsuario, String tokenAcceso, String refreshToken) {
            this.nombreUsuario = nombreUsuario;
            this.tokenAcceso = tokenAcceso;
            this.refreshToken = refreshToken;
        }
    }

    public static class RegisterRequest {
        public String nombreUsuario;
        public String email;
        public String password;
        public String fechaNacimiento; // yyyy-MM-dd
    }

    public void login(String identificador, String password, Callback<LoginResult> callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("identificador", identificador);
            body.put("contraseña", password);
            RequestBody rb = RequestBody.create(body.toString(), JSON);

            // Guardar call + remove al terminar
            Call<ResponseBody> call = RetrofitProvider.authApi(appContext).login(rb);
            inFlight.add(call);
            call.enqueue(new retrofit2.Callback<ResponseBody>() {
                @Override
                public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                    inFlight.remove(call);
                    handleLoginLikeResponse(response, callback);
                }

                @Override
                public void onFailure(Call<ResponseBody> call, Throwable t) {
                    inFlight.remove(call);
                    if (call.isCanceled()) return; // cancelado por onCleared, no tocar UI
                    callback.onError(StringUtils.hasText(t.getMessage()) ? t.getMessage() : "Error de conexión");
                }
            });
        } catch (Exception e) {
            callback.onError(StringUtils.hasText(e.getMessage()) ? e.getMessage() : "Error de login");
        }
    }

    public void register(RegisterRequest req, Callback<String> callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("nombre_usuario", req.nombreUsuario);
            body.put("email", req.email);
            body.put("contraseña", req.password);
            body.put("fecha_nacimiento", req.fechaNacimiento);

            RequestBody rb = RequestBody.create(body.toString(), JSON);
            Call<ResponseBody> call = RetrofitProvider.authApi(appContext).register(rb);
            inFlight.add(call);
            call.enqueue(new retrofit2.Callback<ResponseBody>() {
                @Override
                public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                    inFlight.remove(call);
                    if (!response.isSuccessful()) {
                        callback.onError(parseApiError(response));
                        return;
                    }
                    try {
                        String text = response.body() != null ? response.body().string() : "{}";
                        JSONObject json = new JSONObject(text);
                        callback.onSuccess(json.optString("mensaje", "Cuenta creada correctamente"));
                    } catch (Exception e) {
                        callback.onError("Respuesta inválida del servidor");
                    }
                }
                @Override
                public void onFailure(Call<ResponseBody> call, Throwable t) {
                    inFlight.remove(call);
                    if (call.isCanceled()) return;
                    callback.onError(StringUtils.hasText(t.getMessage()) ? t.getMessage() : "Error de conexión");
                }
            });
        } catch (Exception e) {
            callback.onError(StringUtils.hasText(e.getMessage()) ? e.getMessage() : "Error de registro");
        }
    }

    public void refreshSession(String refreshToken, Callback<LoginResult> callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("refresh_token", refreshToken);
            RequestBody rb = RequestBody.create(body.toString(), JSON);
            Call<ResponseBody> call = RetrofitProvider.authApi(appContext).refresh(rb);
            inFlight.add(call);
            call.enqueue(new retrofit2.Callback<ResponseBody>() {
                @Override
                public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                    inFlight.remove(call);
                    handleLoginLikeResponse(response, callback);
                }

                @Override
                public void onFailure(Call<ResponseBody> call, Throwable t) {
                    inFlight.remove(call);
                    if (call.isCanceled()) return;
                    callback.onError(StringUtils.hasText(t.getMessage()) ? t.getMessage() : "Error de conexión");
                }
            });
        } catch (Exception e) {
            callback.onError(StringUtils.hasText(e.getMessage()) ? e.getMessage() : "No se pudo renovar la sesión");
        }
    }

    public void logout(String refreshToken, Callback<String> callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("refresh_token", refreshToken);
            RequestBody rb = RequestBody.create(body.toString(), JSON);
            Call<ResponseBody> call = RetrofitProvider.authApi(appContext).logout(rb);
            inFlight.add(call);
            call.enqueue(new retrofit2.Callback<ResponseBody>() {
                @Override
                public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                    inFlight.remove(call);
                    // Logout best-effort: si falla, devolvemos error (la UI igual puede limpiar local)
                    if (!response.isSuccessful()) {
                        callback.onError(parseApiError(response));
                        return;
                    }
                    try {
                        String text = response.body() != null ? response.body().string() : "{}";
                        JSONObject json = new JSONObject(text);
                        callback.onSuccess(json.optString("mensaje", "Sesión cerrada"));
                    } catch (Exception e) {
                        callback.onSuccess("Sesión cerrada");
                    }
                }
                @Override
                public void onFailure(Call<ResponseBody> call, Throwable t) {
                    inFlight.remove(call);
                    if (call.isCanceled()) return;
                    callback.onError(StringUtils.hasText(t.getMessage()) ? t.getMessage() : "Error de conexión");
                }
            });
        } catch (Exception e) {
            callback.onError(StringUtils.hasText(e.getMessage()) ? e.getMessage() : "No se pudo cerrar sesión");
        }
    }

    private void handleLoginLikeResponse(Response<ResponseBody> response, Callback<LoginResult> callback) {
        if (!response.isSuccessful()) {
            callback.onError(parseApiError(response));
            return;
        }
        try {
            String text = response.body() != null ? response.body().string() : "{}";
            JSONObject json = new JSONObject(text);
            String nombreUsuario = json.optString("nombre_usuario", "");
            String tokenAcceso = json.optString("token_acceso", "");
            String refreshToken = json.optString("refresh_token", "");

            if (!StringUtils.hasText(tokenAcceso)) throw new Exception("No se recibió token_acceso");
            if (!StringUtils.hasText(refreshToken)) throw new Exception("No se recibió refresh_token");

            callback.onSuccess(new LoginResult(nombreUsuario, tokenAcceso, refreshToken));
        } catch (Exception e) {
            callback.onError("Respuesta inválida del servidor");
        }
    }

    private String parseApiError(Response<ResponseBody> response) {
        try {
            String body = response.errorBody() != null ? response.errorBody().string() : "";
            JSONObject obj = new JSONObject(body);
            if (obj.has("detail")) {
                Object detail = obj.get("detail");
                // Detail puede ser lista [{"columna":..., "mensaje":...}]
                if (detail instanceof JSONArray) {
                    JSONArray arr = (JSONArray) detail;
                    if (arr.length() > 0) {
                        JSONObject first = arr.optJSONObject(0);
                        if (first != null) {
                            String msg = first.optString("mensaje", null);
                            if (StringUtils.hasText(msg)) return msg;
                        }
                    }
                }
                return String.valueOf(detail);
            }
        } catch (Exception ignored) { }
        return "Error HTTP " + response.code();
    }
}