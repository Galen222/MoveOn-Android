package com.proyecto.moveon.data.session.dto;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;

import com.google.gson.annotations.SerializedName;

@Keep
public final class RecuperarPasswordRequestDto {

    @SerializedName("email")
    @NonNull
    public final String email;

    public RecuperarPasswordRequestDto(@NonNull String email) {
        this.email = email;
    }
}