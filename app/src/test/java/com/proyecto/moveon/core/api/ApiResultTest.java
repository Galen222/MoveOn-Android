package com.proyecto.moveon.core.api;

import static org.junit.Assert.*;

import org.junit.Test;
/**
 * Pruebas para validar el comportamiento de api result.
 */
public class ApiResultTest {

    /**
     * Verifica que {@link ApiResult#success} produce un resultado con
     * {@code isSuccess()=true}, el {@code data} poblado y {@code error}
     * nulo. Es la forma canónica del caso feliz.
     */
    @Test
    public void success_isSuccessTrue_dataPresent() {
        ApiResult<String> result = ApiResult.success("hello");

        assertTrue(result.isSuccess());
        assertEquals("hello", result.data);
        assertNull(result.error);
    }

    /**
     * Verifica que {@link ApiResult#successVoid} produce éxito sin payload.
     * Se usa para endpoints que no devuelven nada (DELETE, PATCH sin eco)
     * y la UI solo necesita saber si triunfó.
     */
    @Test
    public void successVoid_isSuccessTrue_dataNull() {
        ApiResult<Void> result = ApiResult.successVoid();

        assertTrue(result.isSuccess());
        assertNull(result.data);
        assertNull(result.error);
    }

    /**
     * Verifica que {@link ApiResult#failure} deja {@code data} a nulo,
     * marca {@code isSuccess()=false} y conserva el mensaje original del
     * error para que la UI pueda mostrarlo.
     */
    @Test
    public void failure_isSuccessFalse_errorPresent() {
        ApiError error = ApiError.local("algo falló");
        ApiResult<String> result = ApiResult.failure(error);

        assertFalse(result.isSuccess());
        assertNull(result.data);
        assertNotNull(result.error);
        assertEquals("algo falló", result.error.getMessage());
    }

    /**
     * Verifica que el tipo del error ({@link ApiErrorType}) sobrevive al
     * envolverlo en {@link ApiResult}. Es importante porque la UI decide
     * qué hacer (reintento, banner offline, logout) mirando este tipo.
     */
    @Test
    public void failure_preservesErrorType() {
        ApiError error = ApiError.typed(ApiErrorType.NETWORK, "sin red");
        ApiResult<Integer> result = ApiResult.failure(error);

        assertFalse(result.isSuccess());
        ApiError resultError = result.error;
        assertNotNull(resultError);
        assertEquals(ApiErrorType.NETWORK, resultError.getType());
    }

    @Test
    public void success_withComplexObject() {
        // Simula un DTO sencillo
        String[] data = {"a", "b", "c"};
        ApiResult<String[]> result = ApiResult.success(data);

        assertTrue(result.isSuccess());
        assertArrayEquals(new String[]{"a", "b", "c"}, result.data);
    }
}
