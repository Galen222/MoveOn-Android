package com.proyecto.moveon.domain.auth;

public final class RegisterInput {
    public final String nombreUsuario;
    public final String email;
    public final String password;
    public final String fechaNacimiento; // yyyy-MM-dd

    public RegisterInput(String nombreUsuario, String email, String password, String fechaNacimiento) {
        this.nombreUsuario = nombreUsuario;
        this.email = email;
        this.password = password;
        this.fechaNacimiento = fechaNacimiento;
    }
}