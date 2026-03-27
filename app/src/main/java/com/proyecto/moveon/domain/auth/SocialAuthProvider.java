package com.proyecto.moveon.domain.auth;

import androidx.annotation.NonNull;

public final class SocialAuthProvider {

    public static final String GOOGLE = "google";

    private SocialAuthProvider() {}

    public static boolean isValid(@NonNull String provider) {
        return GOOGLE.equals(provider);
    }
}
