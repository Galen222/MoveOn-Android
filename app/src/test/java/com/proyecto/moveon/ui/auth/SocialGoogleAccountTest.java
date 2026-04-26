package com.proyecto.moveon.ui.auth;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Tests del contenedor {@link SocialGoogleAccount}.
 */
public class SocialGoogleAccountTest {

    /**
     * Verifica que el contenedor conserva los datos devueltos por Google Sign-In.
     */
    @Test
    public void constructor_preservesAllValues() {
        SocialGoogleAccount account = new SocialGoogleAccount(
                "token",
                "alice@example.com",
                "Alice",
                "https://example.test/avatar.png"
        );

        assertEquals("token", account.idToken);
        assertEquals("alice@example.com", account.email);
        assertEquals("Alice", account.displayName);
        assertEquals("https://example.test/avatar.png", account.avatarUrl);
    }

    /**
     * Verifica que email, nombre y avatar pueden no venir informados.
     */
    @Test
    public void constructor_allowsNullableOptionalValues() {
        SocialGoogleAccount account = new SocialGoogleAccount("token", null, null, null);

        assertEquals("token", account.idToken);
        assertNull(account.email);
        assertNull(account.displayName);
        assertNull(account.avatarUrl);
    }
    /**
     * Verifica que SocialGoogleAccount conserva todos los datos devueltos por Google.
     */
    @Test
    public void socialGoogleAccount_preservesAllProvidedFields() {
        SocialGoogleAccount account = new SocialGoogleAccount(
                "token-123",
                "user@example.com",
                "User Name",
                "https://example.com/avatar.png"
        );

        assertEquals("token-123", account.idToken);
        assertEquals("user@example.com", account.email);
        assertEquals("User Name", account.displayName);
        assertEquals("https://example.com/avatar.png", account.avatarUrl);
    }

    /**
     * Verifica que SocialGoogleAccount permite datos opcionales nulos sin alterar el token obligatorio.
     */
    @Test
    public void socialGoogleAccount_optionalFieldsMayBeNull() {
        SocialGoogleAccount account = new SocialGoogleAccount("token", null, null, null);

        assertEquals("token", account.idToken);
        assertNull(account.email);
        assertNull(account.displayName);
        assertNull(account.avatarUrl);
    }
}
