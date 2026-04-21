package com.proyecto.moveon.domain.auth;

import androidx.annotation.NonNull;
/**
 * Proveedor de datos o dependencias para social auth.
 */
public final class SocialAuthProvider {

    public static final String GOOGLE = "google";

    /**
     * Constructor privado para impedir la instanciación: esta clase solo
     * expone constantes y utilidades estáticas.
     */
    private SocialAuthProvider() {}

    /**
     * Comprueba si la cadena recibida corresponde a un proveedor de login
     * social soportado por la app. Se usa para validar input antes de
     * lanzar la petición al backend.
     *
     * @param provider identificador del proveedor tal y como lo envía la UI.
     * @return {@code true} si el proveedor es {@link #GOOGLE}; {@code false} en cualquier otro caso.
     */
    public static boolean isValid(@NonNull String provider) {
        return GOOGLE.equals(provider);
    }
}
