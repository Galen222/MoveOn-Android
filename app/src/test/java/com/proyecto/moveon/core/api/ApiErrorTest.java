package com.proyecto.moveon.core.api;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ApiErrorTest {

    // ── Factory methods ─────────────────────────────────────────────────────

    @Test
    public void local_createsUnknownTypeWithZeroCode() {
        ApiError error = ApiError.local("something broke");

        assertEquals(ApiErrorType.UNKNOWN, error.getType());
        assertEquals(0, error.getHttpCode());
        assertEquals("something broke", error.getMessage());
        assertNull(error.getErrorCode());
        assertFalse(error.hasFieldErrors());
        assertNull(error.getRaw());
    }

    @Test
    public void typed_twoArgs_setsTypeAndMessage() {
        ApiError error = ApiError.typed(ApiErrorType.NETWORK, "sin conexión");

        assertEquals(ApiErrorType.NETWORK, error.getType());
        assertEquals(0, error.getHttpCode());
        assertEquals("sin conexión", error.getMessage());
    }

    @Test
    public void typed_threeArgs_setsTypeCodeMessage() {
        ApiError error = ApiError.typed(ApiErrorType.SERVER, 503, "servicio no disponible");

        assertEquals(ApiErrorType.SERVER, error.getType());
        assertEquals(503, error.getHttpCode());
        assertEquals("servicio no disponible", error.getMessage());
    }

    @Test
    public void typed_fourArgs_includesErrorCode() {
        ApiError error = ApiError.typed(ApiErrorType.VALIDATION, 422, "campo inválido", "FIELD_INVALID");

        assertEquals(ApiErrorType.VALIDATION, error.getType());
        assertEquals(422, error.getHttpCode());
        assertEquals("campo inválido", error.getMessage());
        assertEquals("FIELD_INVALID", error.getErrorCode());
    }

    // ── Field errors ────────────────────────────────────────────────────────

    @Test
    public void hasFieldErrors_falseWhenEmpty() {
        ApiError error = ApiError.local("test");
        assertFalse(error.hasFieldErrors());
        assertTrue(error.getFieldErrors().isEmpty());
    }

    @Test
    public void withFieldError_returnsNewInstanceWithField() {
        ApiError original = ApiError.local("test");
        ApiError withField = original.withFieldError("email", "Email inválido");

        // Original no cambia (inmutabilidad)
        assertFalse(original.hasFieldErrors());

        // Nueva instancia tiene el error
        assertTrue(withField.hasFieldErrors());
        assertEquals(1, withField.getFieldErrors().get("email").size());
        assertEquals("Email inválido", withField.getFieldErrors().get("email").get(0));
    }

    @Test
    public void withFieldError_accumulates_multipleForSameKey() {
        ApiError error = ApiError.local("test")
                .withFieldError("password", "Muy corta")
                .withFieldError("password", "Falta mayúscula");

        List<String> msgs = error.getFieldErrors().get("password");
        assertNotNull(msgs);
        assertEquals(2, msgs.size());
        assertEquals("Muy corta", msgs.get(0));
        assertEquals("Falta mayúscula", msgs.get(1));
    }

    @Test
    public void withFieldError_preservesOriginalTypeAndCode() {
        ApiError original = ApiError.typed(ApiErrorType.VALIDATION, 422, "error", "CODE");
        ApiError withField = original.withFieldError("email", "bad");

        assertEquals(ApiErrorType.VALIDATION, withField.getType());
        assertEquals(422, withField.getHttpCode());
        assertEquals("error", withField.getMessage());
        assertEquals("CODE", withField.getErrorCode());
    }

    // ── firstFieldMessage ───────────────────────────────────────────────────

    @Test
    public void firstFieldMessage_returnsNullWhenNoFieldErrors() {
        ApiError error = ApiError.local("test");
        assertNull(error.firstFieldMessage("email"));
    }

    @Test
    public void firstFieldMessage_returnsNullForUnknownKey() {
        ApiError error = ApiError.local("test").withFieldError("email", "bad");
        assertNull(error.firstFieldMessage("password"));
    }

    @Test
    public void firstFieldMessage_findsFirstMatchingKey() {
        ApiError error = ApiError.local("test")
                .withFieldError("email", "email inválido")
                .withFieldError("password", "password corta");

        assertEquals("email inválido", error.firstFieldMessage("email", "password"));
    }

    @Test
    public void firstFieldMessage_skipsKeysWithEmptyMessages() {
        // Construir manualmente con mensaje vacío
        Map<String, List<String>> fields = new HashMap<>();
        fields.put("email", Collections.singletonList("   "));
        fields.put("password", Collections.singletonList("error real"));

        ApiError error = new ApiError(ApiErrorType.VALIDATION, 422, "msg", null, fields, null);

        // "email" tiene un mensaje en blanco -> lo salta
        assertEquals("error real", error.firstFieldMessage("email", "password"));
    }

    @Test
    public void firstFieldMessage_returnsNullForEmptyKeys() {
        ApiError error = ApiError.local("test").withFieldError("email", "bad");
        assertNull(error.firstFieldMessage(/* vacío */));
    }

    @Test
    public void firstFieldMessage_skipsNullKeys() {
        ApiError error = ApiError.local("test").withFieldError("email", "bad");
        assertEquals("bad", error.firstFieldMessage(null, "email"));
    }

    // ── Constructor directo con campo raw ────────────────────────────────────

    @Test
    public void constructor_preservesRawBody() {
        String rawJson = "{\"error\":\"test\"}";
        ApiError error = new ApiError(
                ApiErrorType.SERVER, 500, "error servidor", null, null, rawJson);

        assertEquals(rawJson, error.getRaw());
    }

    @Test
    public void constructor_nullFieldErrors_becomesEmptyMap() {
        ApiError error = new ApiError(ApiErrorType.UNKNOWN, 0, "msg", null, null, null);
        assertNotNull(error.getFieldErrors());
        assertTrue(error.getFieldErrors().isEmpty());
    }
}
