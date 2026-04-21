package com.proyecto.moveon.data.session.dto;

import androidx.annotation.Keep;

import com.google.gson.annotations.SerializedName;
/**
 * DTO utilizado para enviar la solicitud de social auth.
 */
@Keep
public final class SocialAuthRequestDto {

    @SerializedName("provider") public String provider;
    @SerializedName("token") public String token;

    public SocialAuthRequestDto(String provider, String token) {
        this.provider = provider;
        this.token = token;
    }
}
