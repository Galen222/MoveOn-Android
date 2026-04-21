package com.proyecto.moveon.domain.auth;
/**
 * Clase responsable de register input.
 */
public final class RegisterInput {

    public final String  nombreUsuario;
    public final String  email;
    public final String  password;
    public final String  fechaNacimiento;
    public final boolean aceptaTerminos;
    public final String  fechaAceptacionTerminos;
    public final String  versionTerminos;

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