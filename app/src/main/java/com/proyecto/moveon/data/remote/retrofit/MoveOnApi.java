package com.proyecto.moveon.data.remote.retrofit;

import com.proyecto.moveon.data.session.dto.LoginRequestDto;
import com.proyecto.moveon.data.session.dto.LoginResponseDto;
import com.proyecto.moveon.data.session.dto.LogoutRequestDto;
import com.proyecto.moveon.data.session.dto.MessageResponseDto;
import com.proyecto.moveon.data.session.dto.RefreshRequestDto;
import com.proyecto.moveon.data.session.dto.RegisterRequestDto;
import com.proyecto.moveon.data.session.dto.RegisterResponseDto;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface MoveOnApi {
    // handshake() eliminado de aquí

    @POST("login")
    Call<LoginResponseDto> login(@Body LoginRequestDto body);

    @POST("registro")
    Call<RegisterResponseDto> register(@Body RegisterRequestDto body);

    @POST("token/refresh")
    Call<LoginResponseDto> refresh(@Body RefreshRequestDto body);

    @POST("logout")
    Call<MessageResponseDto> logout(@Body LogoutRequestDto body);
}