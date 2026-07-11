package com.proyecto.moveon.data.session.dto;

import androidx.annotation.Keep;

import com.google.gson.annotations.SerializedName;
/**
 * DTO utilizado para enviar la solicitud de resetear password.
 */
@Keep
@SuppressWarnings({"unused", "ClassCanBeRecord"}) // Campos leídos por Gson al serializar el body de Retrofit.
public final class ResetearPasswordRequestDto {

    @SerializedName("email")
    public final String email;

    @SerializedName("codigo")
    public final String codigo;

    // El backend FastAPI usa "nueva_password" (schemas.Confirmarpassword.nueva_password)
    @SerializedName("nueva_password")
    public final String nuevaPassword;

    /**
     * Construye el cuerpo del reseteo de contraseña, donde el usuario aporta
     * el código temporal recibido por email junto con la nueva contraseña.
     *
     * @param email email de la cuenta cuya contraseña se está reseteando.
     * @param codigo código de verificación enviado al email por el backend.
     * @param nuevaPassword nueva contraseña en claro que sustituirá a la anterior.
     */
    public ResetearPasswordRequestDto(String email, String codigo, String nuevaPassword) {
        this.email         = email;
        this.codigo        = codigo;
        this.nuevaPassword = nuevaPassword;
    }
}