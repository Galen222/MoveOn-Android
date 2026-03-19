package com.proyecto.moveon.core.api;

import static org.junit.Assert.*;

import org.junit.Test;

public class ApiErrorTypeTest {

    @Test
    public void allExpectedTypesExist() {
        // Verifica que el enum contiene todos los tipos esperados
        ApiErrorType[] values = ApiErrorType.values();
        assertEquals(12, values.length);

        assertNotNull(ApiErrorType.valueOf("NETWORK"));
        assertNotNull(ApiErrorType.valueOf("TIMEOUT"));
        assertNotNull(ApiErrorType.valueOf("CANCELED"));
        assertNotNull(ApiErrorType.valueOf("UNAUTHORIZED"));
        assertNotNull(ApiErrorType.valueOf("FORBIDDEN"));
        assertNotNull(ApiErrorType.valueOf("NOT_FOUND"));
        assertNotNull(ApiErrorType.valueOf("CONFLICT"));
        assertNotNull(ApiErrorType.valueOf("RATE_LIMIT"));
        assertNotNull(ApiErrorType.valueOf("VALIDATION"));
        assertNotNull(ApiErrorType.valueOf("PAYLOAD_TOO_LARGE"));
        assertNotNull(ApiErrorType.valueOf("SERVER"));
        assertNotNull(ApiErrorType.valueOf("PARSE"));
        assertNotNull(ApiErrorType.valueOf("UNKNOWN"));
    }

    @Test
    public void networkErrors_areSeparateTypes() {
        assertNotEquals(ApiErrorType.NETWORK, ApiErrorType.TIMEOUT);
    }

    @Test
    public void clientErrors_areSeparateTypes() {
        assertNotEquals(ApiErrorType.UNAUTHORIZED, ApiErrorType.FORBIDDEN);
        assertNotEquals(ApiErrorType.FORBIDDEN, ApiErrorType.NOT_FOUND);
        assertNotEquals(ApiErrorType.NOT_FOUND, ApiErrorType.CONFLICT);
    }
}
