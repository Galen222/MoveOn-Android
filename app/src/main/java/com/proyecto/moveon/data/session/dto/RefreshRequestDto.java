package com.proyecto.moveon.data.session.dto;

import androidx.annotation.Keep;

import com.google.gson.annotations.SerializedName;
/**
 * DTO utilizado para enviar la solicitud de refresh.
 */
@Keep
@SuppressWarnings({"unused", "ClassCanBeRecord"}) // Campos leídos por Gson al serializar el body de Retrofit.
public final class RefreshRequestDto {
    @SerializedName("refresh_token") public final String refreshToken;

    /**
     * Construye el cuerpo del endpoint de refresh para obtener un nuevo
     * access token sin requerir credenciales al usuario.
     *
     * @param refreshToken refresh token vigente emitido por el backend en el último login o refresh.
     */
    public RefreshRequestDto(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}