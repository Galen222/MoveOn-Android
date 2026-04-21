package com.proyecto.moveon.core.api;

import static org.junit.Assert.*;

import org.junit.Test;
/**
 * Pruebas para validar el comportamiento de api result.
 */
public class ApiResultTest {

    @Test
    public void success_isSuccessTrue_dataPresent() {
        ApiResult<String> result = ApiResult.success("hello");

        assertTrue(result.isSuccess());
        assertEquals("hello", result.data);
        assertNull(result.error);
    }

    @Test
    public void successVoid_isSuccessTrue_dataNull() {
        ApiResult<Void> result = ApiResult.successVoid();

        assertTrue(result.isSuccess());
        assertNull(result.data);
        assertNull(result.error);
    }

    @Test
    public void failure_isSuccessFalse_errorPresent() {
        ApiError error = ApiError.local("algo falló");
        ApiResult<String> result = ApiResult.failure(error);

        assertFalse(result.isSuccess());
        assertNull(result.data);
        assertNotNull(result.error);
        assertEquals("algo falló", result.error.getMessage());
    }

    @Test
    public void failure_preservesErrorType() {
        ApiError error = ApiError.typed(ApiErrorType.NETWORK, "sin red");
        ApiResult<Integer> result = ApiResult.failure(error);

        assertFalse(result.isSuccess());
        assertEquals(ApiErrorType.NETWORK, result.error.getType());
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
