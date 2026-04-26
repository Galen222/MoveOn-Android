package com.proyecto.moveon.domain.profile;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Tests del modelo de dominio {@link PerfilUsuario}.
 */
public class PerfilUsuarioTest {

    /**
     * Verifica que el constructor conserva datos remotos, preferencias y estado local de foto.
     */
    @Test
    public void constructor_preservesAllFields() {
        PerfilUsuario perfil = new PerfilUsuario(
                "alice",
                "alice@example.com",
                "2000-01-01",
                123,
                "Alice Runner",
                "female",
                170,
                62.5,
                "Madrid",
                "https://example.test/photo.png",
                7,
                "/local/photo.png",
                "/pending/photo.png",
                "PENDING",
                true
        );

        assertEquals("alice", perfil.nombreUsuario);
        assertEquals("alice@example.com", perfil.email);
        assertEquals("2000-01-01", perfil.fechaNacimiento);
        assertEquals(123, perfil.totalPuntos);
        assertEquals("Alice Runner", perfil.nombreReal);
        assertEquals("female", perfil.genero);
        assertEquals(Integer.valueOf(170), perfil.altura);
        assertEquals(Double.valueOf(62.5), perfil.peso);
        assertEquals("Madrid", perfil.provincia);
        assertEquals("https://example.test/photo.png", perfil.fotoPerfil);
        assertEquals(7, perfil.fotoVersion);
        assertEquals("/local/photo.png", perfil.localPhotoPath);
        assertEquals("/pending/photo.png", perfil.pendingLocalPhotoPath);
        assertEquals("PENDING", perfil.photoSyncState);
        assertTrue(perfil.perfilVisible);
    }

    /**
     * Verifica que el perfil permite campos opcionales nulos sin alterar los campos obligatorios.
     */
    @Test
    public void constructor_allowsNullableOptionalFields() {
        PerfilUsuario perfil = new PerfilUsuario(
                "bob",
                "bob@example.com",
                "1999-02-02",
                0,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                null,
                null,
                null,
                false
        );

        assertEquals("bob", perfil.nombreUsuario);
        assertNull(perfil.nombreReal);
        assertNull(perfil.altura);
        assertNull(perfil.peso);
        assertFalse(perfil.perfilVisible);
    }
}
