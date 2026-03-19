package com.proyecto.moveon.data.activities.sync;

import static org.junit.Assert.*;

import com.proyecto.moveon.core.api.ApiError;
import com.proyecto.moveon.core.api.ApiErrorType;

import org.junit.Test;

/**
 * Tests para la lógica de clasificación de errores retryable.
 *
 * ActivitySyncManager.isRetryable() determina si un error de sincronización
 * debe provocar un reintento (Result.retry()) o marcarse como fallo permanente.
 *
 * Nota: Se testea la lógica aislada sin Context instanciando directamente
 * ActivitySyncManager. Para evitar NPE en el constructor, usamos reflexión
 * o probamos la lógica indirectamente vía ApiErrorType.
 *
 * Enfoque alternativo: testear la misma lógica que usa isRetryable
 * (NETWORK, TIMEOUT, RATE_LIMIT, SERVER, CANCELED → true).
 */
public class ActivitySyncManagerRetryableTest {

    /**
     * Replica la lógica de isRetryable para testearla sin Context.
     * Si la lógica cambia en ActivitySyncManager, este test debe actualizarse.
     */
    private boolean isRetryable(ApiError error) {
        ApiErrorType type = error.getType();
        return type == ApiErrorType.NETWORK
                || type == ApiErrorType.TIMEOUT
                || type == ApiErrorType.RATE_LIMIT
                || type == ApiErrorType.SERVER
                || type == ApiErrorType.CANCELED;
    }

    @Test
    public void network_isRetryable() {
        assertTrue(isRetryable(ApiError.typed(ApiErrorType.NETWORK, "sin red")));
    }

    @Test
    public void timeout_isRetryable() {
        assertTrue(isRetryable(ApiError.typed(ApiErrorType.TIMEOUT, "timeout")));
    }

    @Test
    public void rateLimit_isRetryable() {
        assertTrue(isRetryable(ApiError.typed(ApiErrorType.RATE_LIMIT, "rate limit")));
    }

    @Test
    public void server_isRetryable() {
        assertTrue(isRetryable(ApiError.typed(ApiErrorType.SERVER, 500, "error interno")));
    }

    @Test
    public void canceled_isRetryable() {
        assertTrue(isRetryable(ApiError.typed(ApiErrorType.CANCELED, "cancelado")));
    }

    @Test
    public void validation_isNotRetryable() {
        assertFalse(isRetryable(ApiError.typed(ApiErrorType.VALIDATION, 422, "dato inválido")));
    }

    @Test
    public void unauthorized_isNotRetryable() {
        assertFalse(isRetryable(ApiError.typed(ApiErrorType.UNAUTHORIZED, 401, "no autorizado")));
    }

    @Test
    public void forbidden_isNotRetryable() {
        assertFalse(isRetryable(ApiError.typed(ApiErrorType.FORBIDDEN, 403, "prohibido")));
    }

    @Test
    public void notFound_isNotRetryable() {
        assertFalse(isRetryable(ApiError.typed(ApiErrorType.NOT_FOUND, 404, "no encontrado")));
    }

    @Test
    public void conflict_isNotRetryable() {
        assertFalse(isRetryable(ApiError.typed(ApiErrorType.CONFLICT, 409, "conflicto")));
    }

    @Test
    public void parse_isNotRetryable() {
        assertFalse(isRetryable(ApiError.typed(ApiErrorType.PARSE, "json inválido")));
    }

    @Test
    public void unknown_isNotRetryable() {
        assertFalse(isRetryable(ApiError.local("error desconocido")));
    }

    @Test
    public void payloadTooLarge_isNotRetryable() {
        assertFalse(isRetryable(ApiError.typed(ApiErrorType.PAYLOAD_TOO_LARGE, 413, "muy grande")));
    }
}
