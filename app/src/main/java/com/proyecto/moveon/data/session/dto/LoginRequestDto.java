package com.proyecto.moveon.data.session.dto;

import androidx.annotation.Keep;

import com.google.gson.annotations.SerializedName;

@Keep
public final class LoginRequestDto {
    @SerializedName("identificador") public String identificador;
    @SerializedName("password") public String password;

    public LoginRequestDto(String identificador, String password) {
        this.identificador = identificador;
        this.password = password;
    }
}