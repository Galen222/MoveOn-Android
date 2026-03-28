package com.proyecto.moveon.ui.auth;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class SocialGoogleAccount {
    @NonNull public final String idToken;
    @Nullable public final String email;
    @Nullable public final String displayName;
    @Nullable public final String avatarUrl;

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
