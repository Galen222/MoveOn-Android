package com.proyecto.moveon.ui.auth;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import static org.junit.Assert.*;
/**
 * Tests unitarios para {@link SocialRegisterConflictResolver}.
 *
 * <p>Verifican que, en el alta con Google, si el backend
 * indica username o email duplicados, la UI debe bloquear la finalización del
 * proceso y mostrar el conflicto correspondiente, sin fallback silencioso.</p>
 */
public class SocialRegisterConflictResolverTest {

    /**
     * Verifica el escenario cubierto por {@link #resolve_googleCompletionWithUsernameConflict_blocksAndRequestsAnotherUsername()}.
     */
    @Test
    public void resolve_googleCompletionWithUsernameConflict_blocksAndRequestsAnotherUsername() {
        SocialRegisterConflictResolver.Resolution resolution =
                SocialRegisterConflictResolver.resolve("USERNAME_ALREADY_IN_USE", true);

        assertEquals(
                SocialRegisterConflictResolver.Resolution.SHOW_USERNAME_TAKEN,
                resolution
        );
    }

    /**
     * Verifica el escenario cubierto por {@link #resolve_googleCompletionWithEmailConflict_blocksAndShowsExistingAccountMessage()}.
     */
    @Test
    public void resolve_googleCompletionWithEmailConflict_blocksAndShowsExistingAccountMessage() {
        SocialRegisterConflictResolver.Resolution resolution =
                SocialRegisterConflictResolver.resolve("EMAIL_ALREADY_IN_USE", true);

        assertEquals(
                SocialRegisterConflictResolver.Resolution.SHOW_EMAIL_ALREADY_REGISTERED,
                resolution
        );
    }

    /**
     * Verifica el escenario cubierto por {@link #resolve_nonSocialFlowDoesNotApplySpecialHandling()}.
     */
    @Test
    public void resolve_nonSocialFlowDoesNotApplySpecialHandling() {
        SocialRegisterConflictResolver.Resolution resolution =
                SocialRegisterConflictResolver.resolve("USERNAME_ALREADY_IN_USE", false);

        assertEquals(
                SocialRegisterConflictResolver.Resolution.NO_SPECIAL_HANDLING,
                resolution
        );
    }

    /**
     * Verifica el escenario cubierto por {@link #resolve_unknownErrorCodeFallsBackToGenericHandling()}.
     */
    @Test
    public void resolve_unknownErrorCodeFallsBackToGenericHandling() {
        SocialRegisterConflictResolver.Resolution resolution =
                SocialRegisterConflictResolver.resolve("SOME_OTHER_ERROR", true);

        assertEquals(
                SocialRegisterConflictResolver.Resolution.NO_SPECIAL_HANDLING,
                resolution
        );
    }

    /**
     * Verifica el escenario cubierto por {@link #resolve_nullErrorCodeFallsBackToGenericHandling()}.
     */
    @Test
    public void resolve_nullErrorCodeFallsBackToGenericHandling() {
        SocialRegisterConflictResolver.Resolution resolution =
                SocialRegisterConflictResolver.resolve(null, true);

        assertEquals(
                SocialRegisterConflictResolver.Resolution.NO_SPECIAL_HANDLING,
                resolution
        );
    }
    /**
     * Verifica que un conflicto de username en el flujo Google se transforma en acción específica.
     */
    @Test
    public void resolve_usernameConflictDuringGoogleCompletionShowsUsernameTaken() {
        assertEquals(
                SocialRegisterConflictResolver.Resolution.SHOW_USERNAME_TAKEN,
                SocialRegisterConflictResolver.resolve("USERNAME_ALREADY_IN_USE", true)
        );
    }

    /**
     * Verifica que un conflicto de email en el flujo Google se transforma en acción específica.
     */
    @Test
    public void resolve_emailConflictDuringGoogleCompletionShowsEmailRegistered() {
        assertEquals(
                SocialRegisterConflictResolver.Resolution.SHOW_EMAIL_ALREADY_REGISTERED,
                SocialRegisterConflictResolver.resolve("EMAIL_ALREADY_IN_USE", true)
        );
    }

    /**
     * Verifica que fuera del flujo Google no se aplica lógica especial aunque el código sea conocido.
     */
    @Test
    public void resolve_knownConflictOutsideGoogleCompletionUsesGenericHandling() {
        assertEquals(
                SocialRegisterConflictResolver.Resolution.NO_SPECIAL_HANDLING,
                SocialRegisterConflictResolver.resolve("USERNAME_ALREADY_IN_USE", false)
        );
    }

    /**
     * Verifica que códigos nulos, desconocidos o con diferente capitalización no activan acciones especiales.
     */
    @Test
    public void resolve_nullUnknownOrDifferentCaseUsesGenericHandling() {
        assertEquals(SocialRegisterConflictResolver.Resolution.NO_SPECIAL_HANDLING,
                SocialRegisterConflictResolver.resolve(null, true));
        assertEquals(SocialRegisterConflictResolver.Resolution.NO_SPECIAL_HANDLING,
                SocialRegisterConflictResolver.resolve("OTHER", true));
        assertEquals(SocialRegisterConflictResolver.Resolution.NO_SPECIAL_HANDLING,
                SocialRegisterConflictResolver.resolve("username_already_in_use", true));
    }
}