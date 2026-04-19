package com.proyecto.moveon.data.remote.retrofit;

import com.proyecto.moveon.data.session.dto.LoginRequestDto;
import com.proyecto.moveon.data.session.dto.LoginResponseDto;
import com.proyecto.moveon.data.session.dto.LogoutRequestDto;
import com.proyecto.moveon.data.session.dto.MessageResponseDto;
import com.proyecto.moveon.data.session.dto.RecuperarPasswordRequestDto;
import com.proyecto.moveon.data.session.dto.RefreshRequestDto;
import com.proyecto.moveon.data.session.dto.RegisterRequestDto;
import com.proyecto.moveon.data.session.dto.RegisterResponseDto;
import com.proyecto.moveon.data.session.dto.ResetearPasswordRequestDto;
import com.proyecto.moveon.data.session.dto.SocialAuthRequestDto;
import com.proyecto.moveon.data.session.dto.SocialRegisterRequestDto;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface MoveOnApi {

    @POST("login")
    Call<LoginResponseDto> login(@Body LoginRequestDto body);

    @POST("registro")
    Call<RegisterResponseDto> register(@Body RegisterRequestDto body);

    @POST("login/social")
    Call<LoginResponseDto> loginSocial(@Body SocialAuthRequestDto body);

    @POST("registro/social")
    Call<LoginResponseDto> registerSocial(@Body SocialRegisterRequestDto body);

    @POST("token/refresh")
    Call<LoginResponseDto> refresh(@Body RefreshRequestDto body);

    @POST("logout")
    Call<MessageResponseDto> logout(@Body LogoutRequestDto body);

    /**
     * Paso 1: inicia el flujo de recuperación de contraseña.
     *
     * <p>El backend debe responder de forma neutra para cuentas locales, cuentas
     * vinculadas a Google y correos inexistentes. El campo {@code locale}
     * del body permite seleccionar la plantilla del correo (es/en) sin revelar
     * el tipo de cuenta en la respuesta pública.</p>
     */
    @POST("password/solicitar")
    Call<MessageResponseDto> solicitarRecuperacion(@Body RecuperarPasswordRequestDto body);

    /** Paso 2: valida el código y establece la nueva contraseña para cuentas locales. */
    @POST("password/confirmar")
    Call<MessageResponseDto> resetearPassword(@Body ResetearPasswordRequestDto body);
}
