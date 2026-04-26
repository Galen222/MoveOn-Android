package com.proyecto.moveon.data.profile.sync;

import static org.junit.Assert.*;

import com.google.gson.JsonObject;

import org.junit.Test;

/**
 * Tests para el builder JSON {@link ProfilePatchPayload}.
 */
public class ProfilePatchPayloadTest {

    /**
     * Verifica que un payload recién creado está vacío.
     */
    @Test
    public void newPayload_isEmpty() {
        ProfilePatchPayload payload = new ProfilePatchPayload();

        assertTrue(payload.isEmpty());
        assertEquals(0, payload.toJson().size());
    }

    /**
     * Verifica que todos los campos del perfil se serializan con los nombres esperados.
     */
    @Test
    public void fluentSetters_addExpectedSnakeCaseFields() {
        JsonObject json = new ProfilePatchPayload()
                .nombreReal("Alice Runner")
                .email("alice@example.com")
                .fechaNacimiento("2000-01-01")
                .genero("female")
                .altura(170)
                .peso(62.5)
                .provincia("Madrid")
                .perfilVisible(true)
                .toJson();

        assertEquals("Alice Runner", json.get("nombre_real").getAsString());
        assertEquals("alice@example.com", json.get("email").getAsString());
        assertEquals("2000-01-01", json.get("fecha_nacimiento").getAsString());
        assertEquals("female", json.get("genero").getAsString());
        assertEquals(170, json.get("altura").getAsInt());
        assertEquals(62.5, json.get("peso").getAsDouble(), 0.0001);
        assertEquals("Madrid", json.get("provincia").getAsString());
        assertTrue(json.get("perfil_visible").getAsBoolean());
    }

    /**
     * Verifica que los setters opcionales con null generan JsonNull para limpiar campos en backend.
     */
    @Test
    public void nullableSetters_writeJsonNull() {
        JsonObject json = new ProfilePatchPayload()
                .nombreReal(null)
                .email(null)
                .fechaNacimiento(null)
                .genero(null)
                .altura(null)
                .peso(null)
                .provincia(null)
                .toJson();

        assertTrue(json.get("nombre_real").isJsonNull());
        assertTrue(json.get("email").isJsonNull());
        assertTrue(json.get("fecha_nacimiento").isJsonNull());
        assertTrue(json.get("genero").isJsonNull());
        assertTrue(json.get("altura").isJsonNull());
        assertTrue(json.get("peso").isJsonNull());
        assertTrue(json.get("provincia").isJsonNull());
    }

    /**
     * Verifica que {@link ProfilePatchPayload#toJson()} devuelve una copia defensiva.
     */
    @Test
    public void toJson_returnsDeepCopy() {
        ProfilePatchPayload payload = new ProfilePatchPayload().email("alice@example.com");
        JsonObject copy = payload.toJson();

        copy.addProperty("email", "mutated@example.com");

        assertEquals("alice@example.com", payload.toJson().get("email").getAsString());
    }
    /**
     * Verifica que todos los setters escriben los nombres esperados en snake_case.
     */
    @Test
    public void setters_writeExpectedSnakeCaseFields() {
        JsonObject json = new ProfilePatchPayload()
                .nombreReal("Alice")
                .email("alice@example.com")
                .fechaNacimiento("2000-01-01")
                .genero("female")
                .altura(170)
                .peso(62.5)
                .provincia("Madrid")
                .perfilVisible(false)
                .toJson();

        assertEquals("Alice", json.get("nombre_real").getAsString());
        assertEquals("alice@example.com", json.get("email").getAsString());
        assertEquals("2000-01-01", json.get("fecha_nacimiento").getAsString());
        assertEquals("female", json.get("genero").getAsString());
        assertEquals(170, json.get("altura").getAsInt());
        assertEquals(62.5, json.get("peso").getAsDouble(), 0.0);
        assertEquals("Madrid", json.get("provincia").getAsString());
        assertFalse(json.get("perfil_visible").getAsBoolean());
    }

    /**
     * Verifica que los campos nulos se serializan como JsonNull explícito para limpiar valores remotos.
     */
    @Test
    public void setters_withNullValues_writeExplicitJsonNulls() {
        JsonObject json = new ProfilePatchPayload()
                .nombreReal(null)
                .email(null)
                .fechaNacimiento(null)
                .genero(null)
                .altura(null)
                .peso(null)
                .provincia(null)
                .toJson();

        assertTrue(json.get("nombre_real").isJsonNull());
        assertTrue(json.get("email").isJsonNull());
        assertTrue(json.get("fecha_nacimiento").isJsonNull());
        assertTrue(json.get("genero").isJsonNull());
        assertTrue(json.get("altura").isJsonNull());
        assertTrue(json.get("peso").isJsonNull());
        assertTrue(json.get("provincia").isJsonNull());
        assertFalse(new ProfilePatchPayload().nombreReal(null).isEmpty());
    }

    /**
     * Verifica que {@link ProfilePatchPayload#toJson()} devuelve copia profunda y no permite mutar el builder.
     */
    @Test
    public void toJson_returnsDefensiveDeepCopy() {
        ProfilePatchPayload payload = new ProfilePatchPayload().nombreReal("Alice");

        JsonObject firstCopy = payload.toJson();
        firstCopy.addProperty("nombre_real", "Mallory");

        JsonObject secondCopy = payload.toJson();

        assertEquals("Alice", secondCopy.get("nombre_real").getAsString());
    }
}
