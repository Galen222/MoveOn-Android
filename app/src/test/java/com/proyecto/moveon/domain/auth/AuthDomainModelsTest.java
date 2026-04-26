package com.proyecto.moveon.domain.auth;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Tests de modelos puros del dominio de autenticación.
 */
public class AuthDomainModelsTest {

    /**
     * Verifica que {@link LoginSession} conserva usuario y tokens.
     */
    @Test
    public void loginSession_preservesConstructorValues() {
        LoginSession session = new LoginSession("alice", "access-token", "refresh-token");

        assertEquals("alice", session.nombreUsuario);
        assertEquals("access-token", session.tokenAcceso);
        assertEquals("refresh-token", session.refreshToken);
    }

    /**
     * Verifica que {@link RegisterInput} conserva todos los campos de alta clásica.
     */
    @Test
    public void registerInput_preservesConstructorValues() {
        RegisterInput input = new RegisterInput(
                "alice",
                "alice@example.com",
                "Pass1234",
                "2000-01-01",
                true,
                "2026-04-25T10:00:00Z",
                "v1"
        );

        assertEquals("alice", input.nombreUsuario);
        assertEquals("alice@example.com", input.email);
        assertEquals("Pass1234", input.password);
        assertEquals("2000-01-01", input.fechaNacimiento);
        assertTrue(input.aceptaTerminos);
        assertEquals("2026-04-25T10:00:00Z", input.fechaAceptacionTerminos);
        assertEquals("v1", input.versionTerminos);
    }

    /**
     * Verifica que {@link SocialRegisterInput} conserva proveedor, token y onboarding.
     */
    @Test
    public void socialRegisterInput_preservesConstructorValues() {
        SocialRegisterInput input = new SocialRegisterInput(
                "google",
                "id-token",
                "alice",
                "2000-01-01",
                true,
                "2026-04-25T10:00:00Z",
                "v2"
        );

        assertEquals("google", input.provider);
        assertEquals("id-token", input.token);
        assertEquals("alice", input.nombreUsuario);
        assertEquals("2000-01-01", input.fechaNacimiento);
        assertTrue(input.aceptaTerminos);
        assertEquals("2026-04-25T10:00:00Z", input.fechaAceptacionTerminos);
        assertEquals("v2", input.versionTerminos);
    }

    /**
     * Verifica que el único proveedor social válido actualmente es Google.
     */
    @Test
    public void socialAuthProvider_acceptsOnlyGoogle() {
        assertTrue(SocialAuthProvider.isValid(SocialAuthProvider.GOOGLE));
        assertFalse(SocialAuthProvider.isValid("facebook"));
        assertFalse(SocialAuthProvider.isValid("GOOGLE"));
    }
    /**
     * Verifica que Google es el único proveedor social aceptado por el dominio actual.
     */
    @Test
    public void isValid_acceptsOnlyGoogleProvider() {
        assertTrue(SocialAuthProvider.isValid(SocialAuthProvider.GOOGLE));
        assertFalse(SocialAuthProvider.isValid("facebook"));
        assertFalse(SocialAuthProvider.isValid("GOOGLE"));
        assertFalse(SocialAuthProvider.isValid(" google "));
    }
}
