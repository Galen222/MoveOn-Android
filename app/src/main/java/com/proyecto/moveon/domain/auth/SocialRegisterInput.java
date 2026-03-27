package com.proyecto.moveon.domain.auth;

public final class SocialRegisterInput {

    public final String provider;
    public final String token;
    public final String nombreUsuario;
    public final String fechaNacimiento;
    public final boolean aceptaTerminos;
    public final String fechaAceptacionTerminos;
    public final String versionTerminos;

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
