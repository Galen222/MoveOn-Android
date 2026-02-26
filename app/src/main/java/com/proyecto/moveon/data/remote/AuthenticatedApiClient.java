package com.proyecto.moveon.data.remote;

import android.content.Context;

import androidx.annotation.Nullable;

import com.proyecto.moveon.data.remote.retrofit.RetrofitProvider;
import com.proyecto.moveon.data.session.SessionRefreshHelper;

import org.json.JSONObject;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;

public class AuthenticatedApiClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final Context appContext;
    private final SessionRefreshHelper refreshHelper;

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

    public <T> void request(String method,
                            String path,
                            @Nullable JSONObject body,
                            JsonMapper<T> mapper,
                            Callback<T> callback) {

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
        String url = path.startsWith("http") ? path : path;

        switch (method) {
            case "GET":
                return RetrofitProvider.protectedApi(appContext).get(url);
            case "DELETE":
                return RetrofitProvider.protectedApi(appContext).delete(url);
            case "POST":
                return RetrofitProvider.protectedApi(appContext).post(url, toBody(body));
            case "PUT":
                return RetrofitProvider.protectedApi(appContext).put(url, toBody(body));
            case "PATCH":
                return RetrofitProvider.protectedApi(appContext).patch(url, toBody(body));
            default:
                // fallback
                return RetrofitProvider.protectedApi(appContext).get(url);
        }
    }

    private RequestBody toBody(@Nullable JSONObject body) {
        String text = (body == null) ? "{}" : body.toString();
        return RequestBody.create(text, JSON);
    }

    private <T> void enqueueWithRefresh(Call<ResponseBody> call,
                                        JsonMapper<T> mapper,
                                        Callback<T> callback,
                                        boolean allowRefreshRetry) {

        call.enqueue(new retrofit2.Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> c, Response<ResponseBody> response) {
                try {
                    if (response.code() == 401 && allowRefreshRetry) {
                        // refresh + retry una vez
                        refreshHelper.refreshIfNeeded(new SessionRefreshHelper.Callback() {
                            @Override
                            public void onSuccess() {
                                enqueueWithRefresh(call.clone(), mapper, callback, false);
                            }

                            @Override
                            public void onSessionExpired(String message) {
                                callback.onSessionExpired(message);
                            }

                            @Override
                            public void onError(String message) {
                                callback.onError(message);
                            }
                        });
                        return;
                    }

                    if (!response.isSuccessful()) {
                        callback.onError("Error HTTP " + response.code());
                        return;
                    }

                    String text = response.body() != null ? response.body().string() : "{}";
                    JSONObject json = new JSONObject(text);
                    callback.onSuccess(mapper.map(json));

                } catch (Exception e) {
                    callback.onError(e.getMessage() != null ? e.getMessage() : "Respuesta inválida");
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> c, Throwable t) {
                callback.onError(t.getMessage() != null ? t.getMessage() : "Error de conexión");
            }
        });
    }
}