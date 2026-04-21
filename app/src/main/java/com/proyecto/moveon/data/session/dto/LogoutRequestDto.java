package com.proyecto.moveon.data.session.dto;

import androidx.annotation.Keep;

import com.google.gson.annotations.SerializedName;
/**
 * DTO utilizado para enviar la solicitud de logout.
 */
@Keep
public final class LogoutRequestDto {
    @SerializedName("refresh_token") public String refreshToken;

    public LogoutRequestDto(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}