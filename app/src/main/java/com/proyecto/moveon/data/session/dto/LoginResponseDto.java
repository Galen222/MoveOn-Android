package com.proyecto.moveon.data.session.dto;

import androidx.annotation.Keep;
import com.google.gson.annotations.SerializedName;
/**
 * DTO utilizado para deserializar la respuesta de login.
 */
@Keep
public final class LoginResponseDto {
    @SerializedName("nombre_usuario") public String nombreUsuario;
    @SerializedName("token_acceso") public String tokenAcceso;
    @SerializedName("refresh_token") public String refreshToken;
}