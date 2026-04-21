package com.proyecto.moveon.data.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;
/**
 * Pruebas para validar el comportamiento de account key derivation.
 */
public class AccountKeyDerivationTest {

    @Test
    /**
     * Verifica que {@link SecureSessionManager#buildAccountKeyFromUserId(String)}
     * prefija siempre la clave con {@code uid_} y recorta espacios en blanco
     * alrededor. Evita accidentes donde un id con espacios genere una clave
     * distinta de la derivada en la siguiente sesión.
     */
    public void buildAccountKeyFromUserId_prefixesUidAndTrims() {
        assertEquals("uid_123", SecureSessionManager.buildAccountKeyFromUserId("123"));
        assertEquals("uid_456", SecureSessionManager.buildAccountKeyFromUserId(" 456 "));
    }

    @Test
    /**
     * Verifica que un id nulo, vacío o sólo con espacios devuelve
     * {@code null} en vez de producir claves tipo {@code "uid_"} o
     * {@code "uid_   "}, que serían indistinguibles entre usuarios
     * distintos.
     */
    public void buildAccountKeyFromUserId_returnsNullWhenBlank() {
        assertNull(SecureSessionManager.buildAccountKeyFromUserId(null));
        assertNull(SecureSessionManager.buildAccountKeyFromUserId(""));
        assertNull(SecureSessionManager.buildAccountKeyFromUserId("   "));
    }
}
