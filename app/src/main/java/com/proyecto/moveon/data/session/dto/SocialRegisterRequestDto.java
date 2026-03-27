package com.proyecto.moveon.data.session.dto;

import androidx.annotation.Keep;

import com.google.gson.annotations.SerializedName;

@Keep
public final class SocialRegisterRequestDto {

    @SerializedName("provider") public String provider;
    @SerializedName("token") public String token;
    @SerializedName("nombre_usuario") public String nombreUsuario;
    @SerializedName("fecha_nacimiento") public String fechaNacimiento;
    @SerializedName("acepta_terminos") public boolean aceptaTerminos;
    @SerializedName("fecha_aceptacion_terminos") public String fechaAceptacionTerminos;
    @SerializedName("version_terminos") public String versionTerminos;

    public SocialRegisterRequestDto(
            String provider,
            String token,
            String nombreUsuario,
            String fechaNacimiento,
            boolean aceptaTerminos,
            String fechaAceptacionTerminos,
            String versionTerminos) {
        this.provider = provider;
        this.token = token;
        this.nombreUsuario = nombreUsuario;
        this.fechaNacimiento = fechaNacimiento;
        this.aceptaTerminos = aceptaTerminos;
        this.fechaAceptacionTerminos = fechaAceptacionTerminos;
        this.versionTerminos = versionTerminos;
    }
}
