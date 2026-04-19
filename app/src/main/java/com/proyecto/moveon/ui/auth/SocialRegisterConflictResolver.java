package com.proyecto.moveon.ui.auth;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Resuelve de forma pura cómo debe reaccionar la UI ante conflictos devueltos
 * por el backend durante la finalización del registro social.
 *
 * <p>Su objetivo es garantizar que un conflicto de username o email bloquee el
 * proceso y NO active estrategias silenciosas de fallback.</p>
 */
public final class SocialRegisterConflictResolver {

    public enum Resolution {
        SHOW_USERNAME_TAKEN,
        SHOW_EMAIL_ALREADY_REGISTERED,
        NO_SPECIAL_HANDLING
    }

    private SocialRegisterConflictResolver() {
        // Utility class
    }

    @NonNull
    public static Resolution resolve(@Nullable String errorCode, boolean isGoogleCompletionFlow) {
        if (!isGoogleCompletionFlow || errorCode == null) {
            return Resolution.NO_SPECIAL_HANDLING;
        }

        switch (errorCode) {
            case "USERNAME_ALREADY_IN_USE":
                return Resolution.SHOW_USERNAME_TAKEN;
            case "EMAIL_ALREADY_IN_USE":
                return Resolution.SHOW_EMAIL_ALREADY_REGISTERED;
            default:
                return Resolution.NO_SPECIAL_HANDLING;
        }
    }
}