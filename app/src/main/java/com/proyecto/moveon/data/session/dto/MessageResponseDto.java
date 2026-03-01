package com.proyecto.moveon.data.session.dto;

import androidx.annotation.Keep;

import com.google.gson.annotations.SerializedName;

@Keep
public final class MessageResponseDto {
    @SerializedName("mensaje") public String mensaje;
}