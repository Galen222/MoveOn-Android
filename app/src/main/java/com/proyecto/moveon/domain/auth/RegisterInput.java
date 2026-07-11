package com.proyecto.moveon.domain.auth;
/**
 * Clase responsable de register input.
 */
@SuppressWarnings("ClassCanBeRecord")
public final class RegisterInput {

    public final String  nombreUsuario;
    public final String  email;
    public final String  password;
    public final String  fechaNacimiento;
    public final boolean aceptaTerminos;
    public final String  fechaAceptacionTerminos;
    public final String  versionTerminos;

    /**
     * Empaqueta los datos de un registro clásico (email + contraseña)
     * junto con la evidencia de aceptación de términos (fecha y versión)
     * que el backend guarda para auditoría.
     *
     * @param nombreUsuario nombre de usuario único elegido por el usuario.
     * @param email email al que se asocia la cuenta.
     * @param password contraseña en claro; el backend se encarga del hash.
     * @param fechaNacimiento fecha de nacimiento en formato {@code yyyy-MM-dd}.
     * @param aceptaTerminos {@code true} si el usuario aceptó los términos.
     * @param fechaAceptacionTerminos timestamp ISO-8601 del momento de aceptación.
     * @param versionTerminos versión de los términos aceptada.
     */
    public RegisterInput(
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