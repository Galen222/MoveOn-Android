package com.proyecto.moveon.core.api;

import static org.junit.Assert.*;

import org.junit.Test;
/**
 * Pruebas para validar el comportamiento de api error type.
 */
public class ApiErrorTypeTest {

    /**
     * Verifica que {@link ApiErrorType} sigue teniendo las 13 variantes
     * previstas por el contrato con el backend. Si alguien añade o quita
     * una categoría de error sin actualizar la UI, este test cae.
     */
    @Test
    public void allExpectedTypesExist() {
        // Verifica que el enum contiene todos los tipos esperados
        ApiErrorType[] values = ApiErrorType.values();
        assertEquals(13, values.length);

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

    /**
     * Verifica que {@code NETWORK} y {@code TIMEOUT} son valores distintos
     * del enum. Se separan a propósito porque la UI los presenta con
     * mensajes y comportamientos de reintento diferentes.
     */
    @Test
    public void networkErrors_areSeparateTypes() {
        assertNotEquals(ApiErrorType.NETWORK, ApiErrorType.TIMEOUT);
    }

    /**
     * Verifica que los 4xx más importantes ({@code UNAUTHORIZED},
     * {@code FORBIDDEN}, {@code NOT_FOUND}, {@code CONFLICT}) son
     * categorías distintas, para poder reaccionar específicamente a cada
     * una (refrescar sesión, mostrar permiso denegado, etc.).
     */
    @Test
    public void clientErrors_areSeparateTypes() {
        assertNotEquals(ApiErrorType.UNAUTHORIZED, ApiErrorType.FORBIDDEN);
        assertNotEquals(ApiErrorType.FORBIDDEN, ApiErrorType.NOT_FOUND);
        assertNotEquals(ApiErrorType.NOT_FOUND, ApiErrorType.CONFLICT);
    }
}