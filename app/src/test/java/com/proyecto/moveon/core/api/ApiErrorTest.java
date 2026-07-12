package com.proyecto.moveon.core.api;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pruebas para validar el comportamiento de api error.
 */
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
        List<String> emailMessages = withField.getFieldErrors().get("email");
        assertNotNull(emailMessages);
        assertEquals(Collections.singletonList("Email inválido"), emailMessages);
    }

    @Test
    public void withFieldError_accumulates_multipleForSameKey() {
        ApiError error = ApiError.local("test")
                .withFieldError("password", "Muy corta")
                .withFieldError("password", "Falta mayúscula");

        List<String> messages = error.getFieldErrors().get("password");
        assertNotNull(messages);
        assertEquals(2, messages.size());
        assertEquals("Muy corta", messages.get(0));
        assertEquals("Falta mayúscula", messages.get(1));
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
    /**
     * Verifica que la factoría local crea un error desconocido sin metadatos remotos.
     */
    @Test
    public void localFactory_createsUnknownErrorWithoutRemoteMetadata() {
        ApiError error = ApiError.local("fallo local");

        assertEquals(ApiErrorType.UNKNOWN, error.getType());
        assertEquals(0, error.getHttpCode());
        assertEquals("fallo local", error.getMessage());
        assertNull(error.getErrorCode());
        assertNull(error.getRaw());
        assertFalse(error.hasFieldErrors());
    }

    /**
     * Verifica que las factorías tipadas conservan tipo, código HTTP y código funcional.
     */
    @Test
    public void typedFactories_preserveTypeHttpCodeAndBusinessCode() {
        ApiError simple = ApiError.typed(ApiErrorType.NETWORK, "sin red");
        ApiError withHttp = ApiError.typed(ApiErrorType.UNAUTHORIZED, 401, "caducada");
        ApiError withBusinessCode = ApiError.typed(ApiErrorType.VALIDATION, 422, "inválido", "invalid_payload");

        assertEquals(ApiErrorType.NETWORK, simple.getType());
        assertEquals(0, simple.getHttpCode());
        assertEquals("sin red", simple.getMessage());

        assertEquals(ApiErrorType.UNAUTHORIZED, withHttp.getType());
        assertEquals(401, withHttp.getHttpCode());
        assertEquals("caducada", withHttp.getMessage());

        assertEquals(ApiErrorType.VALIDATION, withBusinessCode.getType());
        assertEquals(422, withBusinessCode.getHttpCode());
        assertEquals("invalid_payload", withBusinessCode.getErrorCode());
    }

    /**
     * Verifica que el constructor tolera un mapa nulo de errores por campo.
     */
    @Test
    public void constructor_withNullFieldErrors_usesEmptyMap() {
        ApiError error = new ApiError(ApiErrorType.SERVER, 500, "server", "E500", null, "raw");

        assertFalse(error.hasFieldErrors());
        assertTrue(error.getFieldErrors().isEmpty());
        assertEquals("raw", error.getRaw());
    }

    /**
     * Verifica que {@link ApiError#withFieldError(String, String)} acumula mensajes y conserva el error original.
     */
    @Test
    public void withFieldError_accumulatesMessagesWithoutMutatingOriginal() {
        ApiError original = ApiError.typed(ApiErrorType.VALIDATION, 422, "formulario", "invalid_form");

        ApiError withEmail = original.withFieldError("email", "Email obligatorio");
        ApiError withTwoEmailErrors = withEmail.withFieldError("email", "Email inválido");
        ApiError withPassword = withTwoEmailErrors.withFieldError("password", "Password corta");

        assertFalse(original.hasFieldErrors());
        assertEquals(Collections.singletonList("Email obligatorio"), withEmail.getFieldErrors().get("email"));
        assertEquals(Arrays.asList("Email obligatorio", "Email inválido"), withTwoEmailErrors.getFieldErrors().get("email"));
        assertEquals("Password corta", withPassword.firstFieldMessage("password"));
        assertEquals(ApiErrorType.VALIDATION, withPassword.getType());
        assertEquals(422, withPassword.getHttpCode());
        assertEquals("invalid_form", withPassword.getErrorCode());
    }

    /**
     * Verifica que la búsqueda de mensaje por campo respeta prioridad, ignora nulls y salta textos en blanco.
     */
    @Test
    public void firstFieldMessage_usesPriorityAndSkipsBlankValues() {
        Map<String, List<String>> fieldErrors = new HashMap<>();
        fieldErrors.put("username", Arrays.asList("   ", "segundo ignorado"));
        fieldErrors.put("email", Collections.singletonList("Email inválido"));
        fieldErrors.put("password", Collections.singletonList("Password corta"));
        ApiError error = new ApiError(ApiErrorType.VALIDATION, 422, "form", null, fieldErrors, null);

        assertEquals("Email inválido", error.firstFieldMessage(null, "username", "email", "password"));
        assertEquals("Password corta", error.firstFieldMessage("password", "email"));
        assertNull(error.firstFieldMessage("unknown"));
        assertNull(error.firstFieldMessage());
    }

    /**
     * Verifica que se informa correctamente de la existencia de errores por campo.
     */
    @Test
    public void hasFieldErrors_reflectsCurrentFieldErrorMap() {
        ApiError empty = ApiError.local("x");
        ApiError enriched = empty.withFieldError("field", "message");

        assertFalse(empty.hasFieldErrors());
        assertTrue(enriched.hasFieldErrors());
    }
}
