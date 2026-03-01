package com.proyecto.moveon.data.remote.retrofit;

import com.proyecto.moveon.data.session.dto.AppSessionResponseDto;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;

public interface HandshakeApi {
    // Cambiamos JsonObject por AppSessionResponseDto para que coincida con el Provider
    @GET("handshake")
    Call<AppSessionResponseDto> getHandshake(@Header("x-app-id") String appId);
}