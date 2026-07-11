package com.proyecto.moveon.data.remote.retrofit;

import com.proyecto.moveon.data.session.dto.AppSessionResponseDto;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
/**
 * Contrato que define las operaciones disponibles para handshake api.
 */
public interface HandshakeApi {
    // Cambiamos JsonObject por AppSessionResponseDto para que coincida con el Provider
    /**
     * Pide al backend un nuevo token de sesión de app ({@code app_session}).
     * Se invoca al arranque y cada vez que el token caduca; el {@code x-app-id}
     * identifica la instalación concreta frente al servidor.
     *
     * @param appId identificador único de la instalación, enviado en la cabecera {@code x-app-id}.
     * @return llamada Retrofit que devuelve el token de sesión de app emitido por el backend.
     */
    @GET("handshake")
    Call<AppSessionResponseDto> getHandshake(@Header("x-app-id") String appId);
}