package com.proyecto.moveon.data.ranking;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import com.proyecto.moveon.core.api.ApiResult;
import com.proyecto.moveon.data.ranking.dto.RankingItemDto;
import com.proyecto.moveon.data.remote.AuthenticatedApiClient;

import java.lang.reflect.Type;
import java.net.URLEncoder;
import java.util.List;

public final class RankingRepository {

    public interface Callback {
        void onResult(@NonNull ApiResult<List<RankingItemDto>> result);
    }

    private static final String ENDPOINT = "ranking/obtener";
    private static final Gson GSON = new Gson();
    private static final Type LIST_TYPE = new TypeToken<List<RankingItemDto>>() {}.getType();

    private final AuthenticatedApiClient apiClient;

    public RankingRepository(@NonNull Context context) {
        this.apiClient = new AuthenticatedApiClient(context.getApplicationContext());
    }

    public void obtenerRanking(@Nullable String provincia, @NonNull Callback callback) {
        apiClient.get(buildUrl(provincia), this::parseRanking, callback::onResult);
    }

    public void cancelAll() {
        apiClient.cancelAll();
    }

    @NonNull
    private String buildUrl(@Nullable String provincia) {
        if (provincia == null || provincia.trim().isEmpty()) {
            return ENDPOINT;
        }
        try {
            //noinspection CharsetObjectCanBeUsed
            String encoded = URLEncoder.encode(provincia.trim(), "UTF-8");
            return ENDPOINT + "?provincia=" + encoded;
        } catch (java.io.UnsupportedEncodingException e) {
            return ENDPOINT;
        }
    }

    @Nullable
    private List<RankingItemDto> parseRanking(@Nullable JsonElement json) {
        if (json == null || !json.isJsonArray()) return null;
        return GSON.fromJson(json, LIST_TYPE);
    }
}