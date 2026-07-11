package com.proyecto.moveon.data.session.dto;

import androidx.annotation.Keep;

import com.google.gson.annotations.SerializedName;
/**
 * DTO utilizado para enviar la solicitud de register.
 */
@Keep
@SuppressWarnings("ClassCanBeRecord")
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

    /**
     * Construye el cuerpo de la petición de registro con todos los datos
     * requeridos por el backend, incluyendo la evidencia de aceptación de
     * los términos legales (fecha y versión) para auditoría posterior.
     *
     * @param nombreUsuario nombre de usuario único elegido por el usuario.
     * @param email email al que se asociará la cuenta.
     * @param password contraseña en claro; el backend se encarga del hash.
     * @param fechaNacimiento fecha de nacimiento en formato {@code yyyy-MM-dd}.
     * @param aceptaTerminos {@code true} si el usuario marcó la casilla de aceptación.
     * @param fechaAceptacionTerminos timestamp ISO-8601 del momento en que se aceptaron los términos.
     * @param versionTerminos identificador de la versión de los términos aceptados.
     */
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