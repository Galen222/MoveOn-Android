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

    /**
     * Constructor privado: clase de utilidades con sólo métodos estáticos,
     * no está pensada para instanciarse.
     */
    private SocialRegisterConflictResolver() {
        // Utility class
    }

    /**
     * Traduce un código de error devuelto por el backend durante el flujo
     * de completar registro por Google a una acción concreta de la UI.
     *
     * <p>Fuera del flujo de Google, o con {@code errorCode} nulo, siempre
     * devuelve {@link Resolution#NO_SPECIAL_HANDLING} para que la pantalla
     * muestre el error genérico.</p>
     *
     * @param errorCode código de error recibido del backend, o {@code null} si no hubo.
     * @param isGoogleCompletionFlow {@code true} cuando estamos en el paso de completar registro tras Google.
     * @return acción concreta a ejecutar: avisar de usuario en uso, de email ya registrado, o manejo genérico.
     */
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