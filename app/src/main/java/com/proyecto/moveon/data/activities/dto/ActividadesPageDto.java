package com.proyecto.moveon.data.activities.dto;

import androidx.annotation.Keep;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * DTO que agrupa una página de resultados de actividades page.
 */
@Keep
public final class ActividadesPageDto {

    @SerializedName("items") public List<ActividadResponseDto> items;
    @SerializedName("total") public int total;
    @SerializedName("skip") public int skip;
    @SerializedName("limit") public int limit;
    @SerializedName("has_more") public boolean hasMore;
}
