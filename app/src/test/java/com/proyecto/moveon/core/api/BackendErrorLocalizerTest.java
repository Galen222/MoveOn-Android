package com.proyecto.moveon.core.api;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Tests unitarios para la normalización de error codes.
 *
 * Los métodos que necesitan Context (localize) se testean en tests instrumentados.
 * Aquí se testea la lógica pura de normalización.
 */
public class BackendErrorLocalizerTest {

    // ── normalizeErrorCode ──────────────────────────────────────────────────

    @Test
    public void normalizeErrorCode_lowercasesInput() {
        assertEquals("username_required", BackendErrorLocalizer.normalizeErrorCode("USERNAME_REQUIRED"));
    }

    @Test
    public void normalizeErrorCode_replacesDashesAndSpaces() {
        assertEquals("rate_limit_exceeded", BackendErrorLocalizer.normalizeErrorCode("rate-limit exceeded"));
    }

    @Test
    public void normalizeErrorCode_collapsesMultipleUnderscores() {
        assertEquals("some_code", BackendErrorLocalizer.normalizeErrorCode("some___code"));
    }

    @Test
    public void normalizeErrorCode_trimsLeadingTrailingUnderscores() {
        assertEquals("code", BackendErrorLocalizer.normalizeErrorCode("__code__"));
    }

    @Test
    public void normalizeErrorCode_stripsSpecialCharacters() {
        assertEquals("error_123", BackendErrorLocalizer.normalizeErrorCode("error.123!@#"));
    }

    @Test
    public void normalizeErrorCode_preservesAlphanumeric() {
        assertEquals("password_missing_uppercase", BackendErrorLocalizer.normalizeErrorCode("PASSWORD_MISSING_UPPERCASE"));
    }

    @Test
    public void normalizeErrorCode_emptyStringStaysEmpty() {
        assertEquals("", BackendErrorLocalizer.normalizeErrorCode(""));
    }

    @Test
    public void normalizeErrorCode_whitespaceOnlyBecomesEmpty() {
        assertEquals("", BackendErrorLocalizer.normalizeErrorCode("   "));
    }

    @Test
    public void normalizeErrorCode_mixedCaseWithNumbers() {
        assertEquals("err_404_not_found", BackendErrorLocalizer.normalizeErrorCode("ERR-404-NOT_FOUND"));
    }

    @Test
    public void normalizeErrorCode_unicodeCharactersStripped() {
        assertEquals("error", BackendErrorLocalizer.normalizeErrorCode("érror"));
    }
}
