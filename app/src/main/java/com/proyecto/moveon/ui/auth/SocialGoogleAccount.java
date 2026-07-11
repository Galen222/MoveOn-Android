package com.proyecto.moveon.ui.auth;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
/**
 * Clase responsable de social google account.
 */
@SuppressWarnings("ClassCanBeRecord")
public final class SocialGoogleAccount {
    @NonNull public final String idToken;
    @Nullable public final String email;
    @Nullable public final String displayName;
    @Nullable public final String avatarUrl;

    /**
     * Empaqueta los datos devueltos por Google Sign-In para que el flujo de
     * auth y registro los consuma sin depender directamente del SDK de Google.
     *
     * @param idToken id token de Google que el backend verificará para confirmar la identidad.
     * @param email email de la cuenta Google, o {@code null} si el usuario lo ocultó.
     * @param displayName nombre público asociado a la cuenta, o {@code null} si no está disponible.
     * @param avatarUrl URL de la foto de la cuenta Google, o {@code null} si no tiene.
     */
    public SocialGoogleAccount(@NonNull String idToken,
                               @Nullable String email,
                               @Nullable String displayName,
                               @Nullable String avatarUrl) {
        this.idToken = idToken;
        this.email = email;
        this.displayName = displayName;
        this.avatarUrl = avatarUrl;
    }
}
