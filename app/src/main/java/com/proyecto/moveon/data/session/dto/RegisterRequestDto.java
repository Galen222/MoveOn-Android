package com.proyecto.moveon.data.session.dto;

import androidx.annotation.Keep;

import com.google.gson.annotations.SerializedName;
/**
 * DTO utilizado para enviar la solicitud de register.
 */
@Keep
public final class RegisterRequestDto {

    @SerializedName("nombre_usuario")
    public final String nombreUsuario;

    @SerializedName("email")
    public final String email;

    @SerializedName("password")
    public final String password;

    @SerializedName("fecha_nacimiento")
    public final String fechaNacimiento;

    @SerializedName("acepta_terminos")
    public final boolean aceptaTerminos;

    @SerializedName("fecha_aceptacion_terminos")
    public final String fechaAceptacionTerminos;

    @SerializedName("version_terminos")
    public final String versionTerminos;

    public RegisterRequestDto(
            String nombreUsuario,
            String email,
            String password,
            String fechaNacimiento,
            boolean aceptaTerminos,
            String fechaAceptacionTerminos,
            String versionTerminos) {

        this.nombreUsuario           = nombreUsuario;
        this.email                   = email;
        this.password                = password;
        this.fechaNacimiento         = fechaNacimiento;
        this.aceptaTerminos          = aceptaTerminos;
        this.fechaAceptacionTerminos = fechaAceptacionTerminos;
        this.versionTerminos         = versionTerminos;
    }
}