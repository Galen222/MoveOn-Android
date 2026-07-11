package com.proyecto.moveon.domain.auth;
/**
 * Clase responsable de social register input.
 */
@SuppressWarnings("ClassCanBeRecord")
public final class SocialRegisterInput {

    public final String provider;
    public final String token;
    public final String nombreUsuario;
    public final String fechaNacimiento;
    public final boolean aceptaTerminos;
    public final String fechaAceptacionTerminos;
    public final String versionTerminos;

    /**
     * Empaqueta los datos necesarios para completar un registro social: el
     * par {@code provider}/{@code token} identifica al usuario en el
     * proveedor externo, y el resto son los campos que la app exige
     * propios (nombre de usuario y aceptación de términos).
     *
     * @param provider identificador del proveedor OAuth (p. ej. {@code google}).
     * @param token token del proveedor que el backend verificará.
     * @param nombreUsuario nombre de usuario único elegido en la app.
     * @param fechaNacimiento fecha de nacimiento en formato {@code yyyy-MM-dd}.
     * @param aceptaTerminos {@code true} si el usuario aceptó explícitamente los términos.
     * @param fechaAceptacionTerminos timestamp ISO-8601 del momento de aceptación.
     * @param versionTerminos versión de los términos aceptada, guardada para auditoría.
     */
    public SocialRegisterInput(
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
