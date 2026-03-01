package com.proyecto.moveon.domain.auth;

public final class LoginSession {
    public final String nombreUsuario;
    public final String tokenAcceso;
    public final String refreshToken;

    public LoginSession(String nombreUsuario, String tokenAcceso, String refreshToken) {
        this.nombreUsuario = nombreUsuario;
        this.tokenAcceso = tokenAcceso;
        this.refreshToken = refreshToken;
    }
}