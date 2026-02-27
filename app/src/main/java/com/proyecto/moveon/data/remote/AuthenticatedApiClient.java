package com.proyecto.moveon.data.remote;

import android.content.Context;
import androidx.annotation.Nullable;
import com.proyecto.moveon.data.remote.retrofit.RetrofitProvider;
import com.proyecto.moveon.data.session.SessionRefreshHelper;
import com.proyecto.moveon.utils.StringUtils;
import org.json.JSONObject;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;

public class AuthenticatedApiClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private final Context appContext;
    private final SessionRefreshHelper refreshHelper;
    // Lista segura para hilos que rastrea las llamadas en vuelo
    private final List<Call<?>> inFlight = new CopyOnWriteArrayList<>();

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
        this.refreshHelper = new SessionRefreshHelper(appContext);
    }

    public void cancelAll() {
        for (Call<?> c : inFlight) { if (c != null && !c.isCanceled()) c.cancel(); }
        inFlight.clear();
    }

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

    public void requestJson(String method, String path, @Nullable JSONObject body, Callback<JSONObject> callback) {
        request(method, path, body, json -> json, callback);
    }

    public <T> void request(String method, String path, @Nullable JSONObject body, JsonMapper<T> mapper, Callback<T> callback) {
        Call<ResponseBody> call = buildCall(method, path, body);
        enqueueWithRefresh(call, mapper, callback, true);
    }

    public <T> void get(String path, JsonMapper<T> mapper, Callback<T> callback) {
        request("GET", path, null, mapper, callback);
    }

    public <T> void post(String path, JSONObject body, JsonMapper<T> mapper, Callback<T> callback) {
        request("POST", path, body, mapper, callback);
    }

    private Call<ResponseBody> buildCall(String method, String path, @Nullable JSONObject body) {
        switch (method) {
            case "DELETE": return RetrofitProvider.protectedApi(appContext).delete(path);
            case "POST":   return RetrofitProvider.protectedApi(appContext).post(path, toBody(body));
            case "PUT":    return RetrofitProvider.protectedApi(appContext).put(path, toBody(body));
            case "PATCH":  return RetrofitProvider.protectedApi(appContext).patch(path, toBody(body));
            default:       return RetrofitProvider.protectedApi(appContext).get(path);
        }
    }

    private RequestBody toBody(@Nullable JSONObject body) {
        String text = (body == null) ? "{}" : body.toString();
        return RequestBody.create(text, JSON);
    }

    private <T> void enqueueWithRefresh(Call<ResponseBody> call, JsonMapper<T> mapper, Callback<T> callback, boolean allowRefreshRetry) {
        // Registramos la llamada antes de enviarla
        inFlight.add(call);
        call.enqueue(new retrofit2.Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> c, Response<ResponseBody> response) {
                // La quitamos de la lista porque ya terminó
                inFlight.remove(call);
                try {
                    if (response.code() == 401 && allowRefreshRetry) {
                        // refresh + retry una vez
                        refreshHelper.refreshIfNeeded(new SessionRefreshHelper.Callback() {
                            @Override public void onSuccess() { enqueueWithRefresh(call.clone(), mapper, callback, false); }
                            @Override public void onSessionExpired(String msg) { callback.onSessionExpired(msg); }
                            @Override public void onError(String msg) { callback.onError(msg); }
                        });
                        return;
                    }

                    if (!response.isSuccessful()) {
                        callback.onError("Error HTTP " + response.code());
                        return;
                    }

                    String text = response.body() != null ? response.body().string() : "{}";
                    callback.onSuccess(mapper.map(new JSONObject(text)));

                } catch (Exception e) {
                    callback.onError(StringUtils.hasText(e.getMessage()) ? e.getMessage() : "Respuesta inválida");
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> c, Throwable t) {
                // La quitamos de la lista
                inFlight.remove(call);
                // Si la llamada fue cancelada intencionalmente, no hacemos nada para evitar crashear la UI
                if (call.isCanceled()) return;
                callback.onError(StringUtils.hasText(t.getMessage()) ? t.getMessage() : "Error de conexión");
            }
        });
    }
}