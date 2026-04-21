package com.proyecto.moveon.data.session.dto;

import androidx.annotation.Keep;

import com.google.gson.annotations.SerializedName;
/**
 * DTO utilizado para enviar la solicitud de social register.
 */
@Keep
public final class SocialRegisterRequestDto {

    @SerializedName("provider") public String provider;
    @SerializedName("token") public String token;
    @SerializedName("nombre_usuario") public String nombreUsuario;
    @SerializedName("fecha_nacimiento") public String fechaNacimiento;
    @SerializedName("acepta_terminos") public boolean aceptaTerminos;
    @SerializedName("fecha_aceptacion_terminos") public String fechaAceptacionTerminos;
    @SerializedName("version_terminos") public String versionTerminos;

    /**
     * Construye el cuerpo del registro social: combina la identidad validada
     * por el proveedor externo con los campos específicos de la app
     * (nombre de usuario y aceptación de términos).
     *
     * @param provider identificador del proveedor OAuth (p. ej. {@code google}).
     * @param token token del proveedor que el backend verificará para confirmar la identidad.
     * @param nombreUsuario nombre de usuario único elegido dentro de la app.
     * @param fechaNacimiento fecha de nacimiento en formato {@code yyyy-MM-dd}.
     * @param aceptaTerminos {@code true} si el usuario marcó la casilla de aceptación.
     * @param fechaAceptacionTerminos timestamp ISO-8601 del momento de aceptación de los términos.
     * @param versionTerminos versión de los términos aceptada, guardada para auditoría.
     */
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
