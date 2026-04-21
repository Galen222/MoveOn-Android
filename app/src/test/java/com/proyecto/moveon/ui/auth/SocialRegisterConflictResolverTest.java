package com.proyecto.moveon.ui.auth;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Tests unitarios para {@link SocialRegisterConflictResolver}.
 *
 * <p>Verifican que, en el alta con Google, si el backend
 * indica username o email duplicados, la UI debe bloquear la finalización del
 * proceso y mostrar el conflicto correspondiente, sin fallback silencioso.</p>
 */
public class SocialRegisterConflictResolverTest {

    @Test
    public void resolve_googleCompletionWithUsernameConflict_blocksAndRequestsAnotherUsername() {
        SocialRegisterConflictResolver.Resolution resolution =
                SocialRegisterConflictResolver.resolve("USERNAME_ALREADY_IN_USE", true);

        assertEquals(
                SocialRegisterConflictResolver.Resolution.SHOW_USERNAME_TAKEN,
                resolution
        );
    }

    @Test
    public void resolve_googleCompletionWithEmailConflict_blocksAndShowsExistingAccountMessage() {
        SocialRegisterConflictResolver.Resolution resolution =
                SocialRegisterConflictResolver.resolve("EMAIL_ALREADY_IN_USE", true);

        assertEquals(
                SocialRegisterConflictResolver.Resolution.SHOW_EMAIL_ALREADY_REGISTERED,
                resolution
        );
    }

    @Test
    public void resolve_nonSocialFlowDoesNotApplySpecialHandling() {
        SocialRegisterConflictResolver.Resolution resolution =
                SocialRegisterConflictResolver.resolve("USERNAME_ALREADY_IN_USE", false);

        assertEquals(
                SocialRegisterConflictResolver.Resolution.NO_SPECIAL_HANDLING,
                resolution
        );
    }

    @Test
    public void resolve_unknownErrorCodeFallsBackToGenericHandling() {
        SocialRegisterConflictResolver.Resolution resolution =
                SocialRegisterConflictResolver.resolve("SOME_OTHER_ERROR", true);

        assertEquals(
                SocialRegisterConflictResolver.Resolution.NO_SPECIAL_HANDLING,
                resolution
        );
    }

    @Test
    public void resolve_nullErrorCodeFallsBackToGenericHandling() {
        SocialRegisterConflictResolver.Resolution resolution =
                SocialRegisterConflictResolver.resolve(null, true);

        assertEquals(
                SocialRegisterConflictResolver.Resolution.NO_SPECIAL_HANDLING,
                resolution
        );
    }
}