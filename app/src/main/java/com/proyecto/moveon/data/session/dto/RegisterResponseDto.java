package com.proyecto.moveon.data.session.dto;

import androidx.annotation.Keep;

import com.google.gson.annotations.SerializedName;
/**
 * DTO utilizado para deserializar la respuesta de register.
 */
@Keep
public final class RegisterResponseDto {
    @SerializedName("mensaje") public String mensaje;
}