
package com.proyecto.moveon.data.remote;

import android.content.Context;

import androidx.annotation.NonNull;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.proyecto.moveon.R;
import com.proyecto.moveon.core.i18n.AppLanguageManager;
import com.proyecto.moveon.core.api.ApiError;
import com.proyecto.moveon.core.api.ApiErrorParser;
import com.proyecto.moveon.core.api.ApiResult;
import com.proyecto.moveon.data.common.BaseRepository;
import com.proyecto.moveon.data.remote.retrofit.RetrofitProvider;

import java.io.IOException;

import okhttp3.MultipartBody;
import retrofit2.Call;
import retrofit2.Response;
/**
 * Clase responsable de authenticated api client.
 */
public final class AuthenticatedApiClient extends BaseRepository {

    public interface Mapper<I, O> {
        O map(I input) throws Exception;
    }

    public interface Callback<T> {
        void onResult(ApiResult<T> result);
    }

    private final Context appContext;

    public AuthenticatedApiClient(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
    }

    private boolean isInvalidUrl(String url) {
        if (url == null || url.trim().isEmpty()) return true;
        String clean = url.trim().toLowerCase();
        return clean.matches("^[a-z][a-z0-9+.-]*://.*") || clean.startsWith("//");
    }

    private String sanitizeUrl(String url) {
        if (url == null) return "";
        String clean = url.trim();
        if (clean.startsWith("/")) {
            return clean.substring(1);
        }
        return clean;
    }

    public <T> void get(String url, Mapper<JsonElement, T> mapper, Callback<T> callback) {
        if (isInvalidUrl(url)) {
            callback.onResult(ApiResult.failure(ApiError.local(AppLanguageManager.getString(appContext, R.string.api_error_url_invalida))));
            return;
        }
        enqueueCall(RetrofitProvider.protectedApi(appContext).get(sanitizeUrl(url)), mapper, callback);
    }

    public <T> ApiResult<T> getBlocking(String url, Mapper<JsonElement, T> mapper) {
        if (isInvalidUrl(url)) {
            return ApiResult.failure(ApiError.local(AppLanguageManager.getString(appContext, R.string.api_error_url_invalida)));
        }
        return executeCall(RetrofitProvider.protectedApi(appContext).get(sanitizeUrl(url)), mapper);
    }

    public <T> void postJson(String url, JsonElement body, Mapper<JsonElement, T> mapper, Callback<T> callback) {
        if (isInvalidUrl(url)) {
            callback.onResult(ApiResult.failure(ApiError.local(AppLanguageManager.getString(appContext, R.string.api_error_url_invalida))));
            return;
        }
        enqueueCall(RetrofitProvider.protectedApi(appContext).post(sanitizeUrl(url), body), mapper, callback);
    }

    public <T> ApiResult<T> postJsonBlocking(String url, JsonElement body, Mapper<JsonElement, T> mapper) {
        if (isInvalidUrl(url)) {
            return ApiResult.failure(ApiError.local(AppLanguageManager.getString(appContext, R.string.api_error_url_invalida)));
        }
        return executeCall(RetrofitProvider.protectedApi(appContext).post(sanitizeUrl(url), body), mapper);
    }

    public <T> void patchJson(String url, JsonElement body, Mapper<JsonElement, T> mapper, Callback<T> callback) {
        if (isInvalidUrl(url)) {
            callback.onResult(ApiResult.failure(ApiError.local(AppLanguageManager.getString(appContext, R.string.api_error_url_invalida))));
            return;
        }
        enqueueCall(RetrofitProvider.protectedApi(appContext).patch(sanitizeUrl(url), body), mapper, callback);
    }

    public <T> ApiResult<T> patchJsonBlocking(String url, JsonElement body, Mapper<JsonElement, T> mapper) {
        if (isInvalidUrl(url)) {
            return ApiResult.failure(ApiError.local(AppLanguageManager.getString(appContext, R.string.api_error_url_invalida)));
        }
        return executeCall(RetrofitProvider.protectedApi(appContext).patch(sanitizeUrl(url), body), mapper);
    }

    public <T> void delete(String url, Mapper<JsonElement, T> mapper, Callback<T> callback) {
        if (isInvalidUrl(url)) {
            callback.onResult(ApiResult.failure(ApiError.local(AppLanguageManager.getString(appContext, R.string.api_error_url_invalida))));
            return;
        }
        enqueueCall(RetrofitProvider.protectedApi(appContext).delete(sanitizeUrl(url)), mapper, callback);
    }

    public <T> ApiResult<T> deleteBlocking(String url, Mapper<JsonElement, T> mapper) {
        if (isInvalidUrl(url)) {
            return ApiResult.failure(ApiError.local(AppLanguageManager.getString(appContext, R.string.api_error_url_invalida)));
        }
        return executeCall(RetrofitProvider.protectedApi(appContext).delete(sanitizeUrl(url)), mapper);
    }

    public <T> void postMultipart(String url, MultipartBody.Part file, Mapper<JsonElement, T> mapper, Callback<T> callback) {
        if (isInvalidUrl(url)) {
            callback.onResult(ApiResult.failure(ApiError.local(AppLanguageManager.getString(appContext, R.string.api_error_url_invalida))));
            return;
        }
        enqueueCall(RetrofitProvider.protectedApi(appContext).postMultipart(sanitizeUrl(url), file), mapper, callback);
    }

    public <T> ApiResult<T> postMultipartBlocking(String url, MultipartBody.Part file, Mapper<JsonElement, T> mapper) {
        if (isInvalidUrl(url)) {
            return ApiResult.failure(ApiError.local(AppLanguageManager.getString(appContext, R.string.api_error_url_invalida)));
        }
        return executeCall(RetrofitProvider.protectedApi(appContext).postMultipart(sanitizeUrl(url), file), mapper);
    }

    private <T> void enqueueCall(Call<JsonElement> call,
                                 Mapper<JsonElement, T> mapper,
                                 Callback<T> callback) {
        enqueueTracked(call, new retrofit2.Callback<JsonElement>() {
            @Override
            public void onResponse(@NonNull Call<JsonElement> c, @NonNull Response<JsonElement> response) {
                if (!response.isSuccessful()) {
                    callback.onResult(ApiResult.failure(ApiErrorParser.fromHttp(appContext, response)));
                    return;
                }

                try {
                    JsonElement body = response.body();
                    if (body == null) body = JsonParser.parseString("{}");
                    callback.onResult(ApiResult.success(mapper.map(body)));
                } catch (Exception e) {
                    callback.onResult(ApiResult.failure(ApiError.local(AppLanguageManager.getString(appContext, R.string.api_error_respuesta_invalida))));
                }
            }

            @Override
            public void onFailure(@NonNull Call<JsonElement> c, @NonNull Throwable t) {
                if (c.isCanceled()) return;
                callback.onResult(ApiResult.failure(ApiErrorParser.fromThrowable(appContext, t, false)));
            }
        });
    }

    private <T> ApiResult<T> executeCall(@NonNull Call<JsonElement> call,
                                         @NonNull Mapper<JsonElement, T> mapper) {
        trackCall(call);
        try {
            Response<JsonElement> response = call.execute();
            if (!response.isSuccessful()) {
                return ApiResult.failure(ApiErrorParser.fromHttp(appContext, response));
            }

            JsonElement body = response.body();
            if (body == null) body = JsonParser.parseString("{}");
            return ApiResult.success(mapper.map(body));
        } catch (IOException e) {
            return ApiResult.failure(ApiErrorParser.fromThrowable(appContext, e, call.isCanceled()));
        } catch (Exception e) {
            return ApiResult.failure(ApiError.local(AppLanguageManager.getString(appContext, R.string.api_error_respuesta_invalida)));
        } finally {
            untrackCall(call);
        }
    }
}

