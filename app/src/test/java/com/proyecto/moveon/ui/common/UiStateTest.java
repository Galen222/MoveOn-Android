package com.proyecto.moveon.ui.common;

import static org.junit.Assert.*;

import com.proyecto.moveon.core.api.ApiError;
import com.proyecto.moveon.core.api.ApiErrorType;

import org.junit.Test;
/**
 * Pruebas para validar el comportamiento de ui.
 */
public class UiStateTest {

    /**
     * Verifica el escenario cubierto por {@link #loading_hasCorrectFlags()}.
     */
    @Test
    public void loading_hasCorrectFlags() {
        UiState<String> state = UiState.loading();

        assertTrue(state.loading);
        assertNull(state.data);
        assertNull(state.error);
    }

    /**
     * Verifica el escenario cubierto por {@link #success_hasDataAndNotLoading()}.
     */
    @Test
    public void success_hasDataAndNotLoading() {
        UiState<String> state = UiState.success("perfil cargado");

        assertFalse(state.loading);
        assertEquals("perfil cargado", state.data);
        assertNull(state.error);
    }

    /**
     * Verifica el escenario cubierto por {@link #success_withNull_isValid()}.
     */
    @Test
    public void success_withNull_isValid() {
        UiState<String> state = UiState.success(null);

        assertFalse(state.loading);
        assertNull(state.data);
        assertNull(state.error);
    }

    /**
     * Verifica el escenario cubierto por {@link #error_hasErrorAndNotLoading()}.
     */
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

    /**
     * Verifica el escenario cubierto por {@link #genericType_worksWithIntegers()}.
     */
    @Test
    public void genericType_worksWithIntegers() {
        UiState<Integer> state = UiState.success(42);
        assertEquals(Integer.valueOf(42), state.data);
    }

    /**
     * Verifica el escenario cubierto por {@link #genericType_worksWithComplexObjects()}.
     */
    @Test
    public void genericType_worksWithComplexObjects() {
        String[] data = {"a", "b"};
        UiState<String[]> state = UiState.success(data);
        assertArrayEquals(new String[]{"a", "b"}, state.data);
    }
    /**
     * Verifica el estado loading canónico sin datos ni error.
     */
    @Test
    public void uiState_loadingHasOnlyLoadingFlag() {
        UiState<String> state = UiState.loading();

        assertTrue(state.loading);
        assertNull(state.data);
        assertNull(state.error);
    }

    /**
     * Verifica el estado success con datos y sin error.
     */
    @Test
    public void uiState_successCarriesDataOnly() {
        UiState<Integer> state = UiState.success(7);

        assertFalse(state.loading);
        assertEquals(Integer.valueOf(7), state.data);
        assertNull(state.error);
    }

    /**
     * Verifica el estado error con ApiError y sin datos.
     */
    @Test
    public void uiState_errorCarriesErrorOnly() {
        ApiError error = ApiError.typed(ApiErrorType.NETWORK, "sin conexión");

        UiState<String> state = UiState.error(error);

        assertFalse(state.loading);
        assertNull(state.data);
        assertSame(error, state.error);
    }
}
