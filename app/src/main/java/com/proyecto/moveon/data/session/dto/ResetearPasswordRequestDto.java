package com.proyecto.moveon.data.session.dto;

import androidx.annotation.Keep;

import com.google.gson.annotations.SerializedName;
/**
 * DTO utilizado para enviar la solicitud de resetear password.
 */
@Keep
public final class ResetearPasswordRequestDto {

    @SerializedName("email")
    public final String email;

    @SerializedName("codigo")
    public final String codigo;

    // El backend FastAPI usa "nueva_password" (schemas.Confirmarpassword.nueva_password)
    @SerializedName("nueva_password")
    public final String nuevaPassword;

    public ResetearPasswordRequestDto(String email, String codigo, String nuevaPassword) {
        this.email         = email;
        this.codigo        = codigo;
        this.nuevaPassword = nuevaPassword;
    }
}