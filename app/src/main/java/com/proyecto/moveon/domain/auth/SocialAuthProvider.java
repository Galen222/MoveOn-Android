package com.proyecto.moveon.domain.auth;

import androidx.annotation.NonNull;
/**
 * Proveedor de datos o dependencias para social auth.
 */
public final class SocialAuthProvider {

    public static final String GOOGLE = "google";

    private SocialAuthProvider() {}

    public static boolean isValid(@NonNull String provider) {
        return GOOGLE.equals(provider);
    }
}
