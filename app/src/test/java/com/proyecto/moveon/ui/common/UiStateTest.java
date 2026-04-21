package com.proyecto.moveon.ui.common;

import static org.junit.Assert.*;

import com.proyecto.moveon.core.api.ApiError;
import com.proyecto.moveon.core.api.ApiErrorType;

import org.junit.Test;
/**
 * Pruebas para validar el comportamiento de ui.
 */
public class UiStateTest {

    @Test
    public void loading_hasCorrectFlags() {
        UiState<String> state = UiState.loading();

        assertTrue(state.loading);
        assertNull(state.data);
        assertNull(state.error);
    }

    @Test
    public void success_hasDataAndNotLoading() {
        UiState<String> state = UiState.success("perfil cargado");

        assertFalse(state.loading);
        assertEquals("perfil cargado", state.data);
        assertNull(state.error);
    }

    @Test
    public void success_withNull_isValid() {
        UiState<String> state = UiState.success(null);

        assertFalse(state.loading);
        assertNull(state.data);
        assertNull(state.error);
    }

    @Test
    public void error_hasErrorAndNotLoading() {
        ApiError apiError = ApiError.typed(ApiErrorType.NETWORK, "sin conexión");
        UiState<String> state = UiState.error(apiError);

        assertFalse(state.loading);
        assertNull(state.data);
        assertNotNull(state.error);
        assertEquals(ApiErrorType.NETWORK, state.error.getType());
        assertEquals("sin conexión", state.error.getMessage());
    }

    @Test
    public void genericType_worksWithIntegers() {
        UiState<Integer> state = UiState.success(42);
        assertEquals(Integer.valueOf(42), state.data);
    }

    @Test
    public void genericType_worksWithComplexObjects() {
        String[] data = {"a", "b"};
        UiState<String[]> state = UiState.success(data);
        assertArrayEquals(new String[]{"a", "b"}, state.data);
    }
}
