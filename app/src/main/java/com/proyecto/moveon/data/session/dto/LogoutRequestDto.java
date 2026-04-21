package com.proyecto.moveon.data.session.dto;

import androidx.annotation.Keep;

import com.google.gson.annotations.SerializedName;
/**
 * DTO utilizado para enviar la solicitud de logout.
 */
@Keep
public final class LogoutRequestDto {
    @SerializedName("refresh_token") public String refreshToken;

    /**
     * Construye el cuerpo del logout. El backend revoca el refresh token
     * recibido para que no pueda usarse para pedir nuevos access tokens.
     *
     * @param refreshToken refresh token actual de la sesión que se quiere cerrar.
     */
    public LogoutRequestDto(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}