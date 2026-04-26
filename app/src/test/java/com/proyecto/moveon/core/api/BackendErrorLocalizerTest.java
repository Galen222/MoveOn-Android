package com.proyecto.moveon.core.api;

import static org.junit.Assert.*;

import com.proyecto.moveon.R;

import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * Tests unitarios para la normalización y el catálogo de códigos de error del backend.
 */
public class BackendErrorLocalizerTest {

    /**
     * Verifica que se normalizan mayúsculas simples a minúsculas.
     */
    @Test
    public void normalizeErrorCode_lowercasesInput() {
        assertEquals("username_required", BackendErrorLocalizer.normalizeErrorCode("USERNAME_REQUIRED"));
    }

    /**
     * Verifica que guiones y espacios se convierten en guiones bajos.
     */
    @Test
    public void normalizeErrorCode_replacesDashesAndSpaces() {
        assertEquals("rate_limit_exceeded", BackendErrorLocalizer.normalizeErrorCode("rate-limit exceeded"));
    }

    /**
     * Verifica que varios guiones bajos consecutivos se colapsan a uno solo.
     */
    @Test
    public void normalizeErrorCode_collapsesMultipleUnderscores() {
        assertEquals("some_code", BackendErrorLocalizer.normalizeErrorCode("some___code"));
    }

    /**
     * Verifica que los guiones bajos iniciales y finales se eliminan.
     */
    @Test
    public void normalizeErrorCode_trimsLeadingTrailingUnderscores() {
        assertEquals("code", BackendErrorLocalizer.normalizeErrorCode("__code__"));
    }

    /**
     * Verifica que caracteres especiales se sustituyen y se conserva la parte alfanumérica.
     */
    @Test
    public void normalizeErrorCode_stripsSpecialCharacters() {
        assertEquals("error_123", BackendErrorLocalizer.normalizeErrorCode("error.123!@#"));
    }

    /**
     * Verifica que cadenas alfanuméricas válidas se preservan con formato snake_case.
     */
    @Test
    public void normalizeErrorCode_preservesAlphanumeric() {
        assertEquals("password_missing_uppercase", BackendErrorLocalizer.normalizeErrorCode("PASSWORD_MISSING_UPPERCASE"));
    }

    /**
     * Verifica que una cadena vacía permanece vacía.
     */
    @Test
    public void normalizeErrorCode_emptyStringStaysEmpty() {
        assertEquals("", BackendErrorLocalizer.normalizeErrorCode(""));
    }

    /**
     * Verifica que una cadena compuesta sólo por espacios se normaliza a cadena vacía.
     */
    @Test
    public void normalizeErrorCode_whitespaceOnlyBecomesEmpty() {
        assertEquals("", BackendErrorLocalizer.normalizeErrorCode("   "));
    }

    /**
     * Verifica que entradas mixtas con números se normalizan sin perder los dígitos.
     */
    @Test
    public void normalizeErrorCode_mixedCaseWithNumbers() {
        assertEquals("err_404_not_found", BackendErrorLocalizer.normalizeErrorCode("ERR-404-NOT_FOUND"));
    }

    /**
     * Verifica que los caracteres Unicode con tilde se transforman a su equivalente ASCII.
     */
    @Test
    public void normalizeErrorCode_unicodeCharactersStripped() {
        assertEquals("error", BackendErrorLocalizer.normalizeErrorCode("érror"));
    }

    /**
     * Verifica una batería de códigos reales con prefijos, espacios y signos mezclados.
     */
    @Test
    public void normalizeErrorCode_realisticInputs() {
        assertEquals("auth_invalid_credentials",
                BackendErrorLocalizer.normalizeErrorCode("AUTH.INVALID-CREDENTIALS"));
        assertEquals("username_already_exists",
                BackendErrorLocalizer.normalizeErrorCode(" username already exists "));
        assertEquals("password_missing_number",
                BackendErrorLocalizer.normalizeErrorCode("PASSWORD__MISSING__NUMBER"));
        assertEquals("rate_limit_exceeded",
                BackendErrorLocalizer.normalizeErrorCode("rate.limit-exceeded!!!"));
    }

    /**
     * Verifica que los códigos vacíos tras limpiar signos quedan como cadena vacía.
     */
    @Test
    public void normalizeErrorCode_symbolsOnlyBecomesEmpty() {
        assertEquals("", BackendErrorLocalizer.normalizeErrorCode("!!! --- ..."));
    }

    /**
     * Verifica que el catálogo interno contiene mappings representativos usados por el backend.
     */
    @Test
    public void errorResourceIds_containsRepresentativeBackendMappings() throws Exception {
        Map<String, Integer> ids = errorResourceIds();

        assertEquals(Integer.valueOf(R.string.backend_error_username_required), ids.get("username_required"));
        assertEquals(Integer.valueOf(R.string.backend_error_email_already_in_use), ids.get("email_already_in_use"));
        assertEquals(Integer.valueOf(R.string.backend_error_rate_limit_exceeded), ids.get("rate_limit_exceeded"));
        assertEquals(Integer.valueOf(R.string.backend_error_validation_error), ids.get("validation_error"));
    }

    /**
     * Verifica que el catálogo interno no se puede modificar desde fuera por reflexión.
     */
    @Test
    public void errorResourceIds_isUnmodifiable() throws Exception {
        Map<String, Integer> ids = errorResourceIds();

        try {
            ids.put("nuevo_codigo", R.string.app_name);
            fail("El mapa de recursos de errores debe ser inmodificable");
        } catch (UnsupportedOperationException expected) {
            assertTrue(ids.containsKey("username_required"));
        }
    }

    /**
     * Verifica que códigos desconocidos normalizados no aparecen en el catálogo.
     */
    @Test
    public void errorResourceIds_doesNotContainUnknownNormalizedCode() throws Exception {
        assertFalse(errorResourceIds().containsKey(BackendErrorLocalizer.normalizeErrorCode("unknown-code")));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Integer> errorResourceIds() throws Exception {
        Field field = BackendErrorLocalizer.class.getDeclaredField("ERROR_RESOURCE_IDS");
        field.setAccessible(true);
        return (Map<String, Integer>) field.get(null);
    }
}
