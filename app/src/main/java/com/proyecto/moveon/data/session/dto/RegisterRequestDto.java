package com.proyecto.moveon.data.session.dto;

import androidx.annotation.Keep;

import com.google.gson.annotations.SerializedName;

@Keep
public final class RegisterRequestDto {
    @SerializedName("nombre_usuario") public String nombreUsuario;
    @SerializedName("email") public String email;
    @SerializedName("password") public String password;
    @SerializedName("fecha_nacimiento") public String fechaNacimiento;

    public RegisterRequestDto(String nombreUsuario, String email, String password, String fechaNacimiento) {
        this.nombreUsuario = nombreUsuario;
        this.email = email;
        this.password = password;
        this.fechaNacimiento = fechaNacimiento;
    }
}