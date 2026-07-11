package com.proyecto.moveon.data.session.dto;

import androidx.annotation.Keep;

import com.google.gson.annotations.SerializedName;
/**
 * DTO utilizado para enviar la solicitud de login.
 */
@Keep
@SuppressWarnings("ClassCanBeRecord")
public final class LoginRequestDto {
    @SerializedName("identificador") public final String identificador;
    @SerializedName("password") public final String password;

    /**
     * Construye el cuerpo de la petición de login.
     *
     * @param identificador email o nombre de usuario con el que el usuario se autentica.
     * @param password contraseña en claro que el cliente envía al backend por HTTPS.
     */
    public LoginRequestDto(String identificador, String password) {
        this.identificador = identificador;
        this.password = password;
    }
}