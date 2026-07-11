package com.proyecto.moveon.domain.auth;
/**
 * Clase responsable de login session.
 */
@SuppressWarnings("ClassCanBeRecord")
public final class LoginSession {
    public final String nombreUsuario;
    public final String tokenAcceso;
    public final String refreshToken;

    /**
     * Empaqueta el resultado de un login exitoso: el nombre del usuario para
     * la UI y el par de tokens que el cliente debe persistir para mantener
     * la sesión.
     *
     * @param nombreUsuario nombre de usuario devuelto por el backend.
     * @param tokenAcceso access token JWT de corta vida con el que se firman las peticiones.
     * @param refreshToken refresh token de larga vida para renovar el access token sin pedir credenciales.
     */
    public LoginSession(String nombreUsuario, String tokenAcceso, String refreshToken) {
        this.nombreUsuario = nombreUsuario;
        this.tokenAcceso = tokenAcceso;
        this.refreshToken = refreshToken;
    }
}