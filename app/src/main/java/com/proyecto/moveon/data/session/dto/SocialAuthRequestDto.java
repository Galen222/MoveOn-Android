package com.proyecto.moveon.data.session.dto;

import androidx.annotation.Keep;

import com.google.gson.annotations.SerializedName;
/**
 * DTO utilizado para enviar la solicitud de social auth.
 */
@Keep
public final class SocialAuthRequestDto {

    @SerializedName("provider") public String provider;
    @SerializedName("token") public String token;

    /**
     * Construye el cuerpo de login social, donde el backend valida el
     * {@code token} contra el proveedor indicado antes de emitir sesión propia.
     *
     * @param provider identificador del proveedor OAuth (p. ej. {@code google}).
     * @param token id token o access token emitido por el proveedor para verificar la identidad.
     */
    public SocialAuthRequestDto(String provider, String token) {
        this.provider = provider;
        this.token = token;
    }
}
