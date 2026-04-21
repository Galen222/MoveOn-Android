package com.proyecto.moveon.data.session.dto;

import androidx.annotation.Keep;

import com.google.gson.annotations.SerializedName;
/**
 * DTO utilizado para enviar la solicitud de refresh.
 */
@Keep
public final class RefreshRequestDto {
    @SerializedName("refresh_token") public String refreshToken;

    public RefreshRequestDto(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}