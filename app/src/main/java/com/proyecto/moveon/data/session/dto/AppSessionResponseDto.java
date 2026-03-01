package com.proyecto.moveon.data.session.dto;

import androidx.annotation.Keep;
import com.google.gson.annotations.SerializedName;

@Keep
public class AppSessionResponseDto {
    // "app_session_token" es el nombre que viene en el JSON de tu backend
    @SerializedName("app_session_token")
    public String appSession;
}