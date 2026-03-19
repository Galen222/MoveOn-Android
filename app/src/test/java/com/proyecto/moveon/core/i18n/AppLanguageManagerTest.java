package com.proyecto.moveon.core.i18n;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Tests unitarios para la lógica pura de AppLanguageManager.
 * Los métodos que dependen de Context se testean en tests instrumentados.
 */
public class AppLanguageManagerTest {

    @Test
    public void sanitizeSelectableMode_spanishReturnsSpanish() {
        assertEquals("es", AppLanguageManager.sanitizeSelectableMode("es"));
    }

    @Test
    public void sanitizeSelectableMode_englishReturnsEnglish() {
        assertEquals("en", AppLanguageManager.sanitizeSelectableMode("en"));
    }

    @Test
    public void sanitizeSelectableMode_unknownDefaultsToEnglish() {
        assertEquals("en", AppLanguageManager.sanitizeSelectableMode("fr"));
    }

    @Test
    public void sanitizeSelectableMode_nullDefaultsToEnglish() {
        assertEquals("en", AppLanguageManager.sanitizeSelectableMode(null));
    }

    @Test
    public void sanitizeSelectableMode_emptyDefaultsToEnglish() {
        assertEquals("en", AppLanguageManager.sanitizeSelectableMode(""));
    }

    @Test
    public void sanitizeSelectableMode_caseExactMatch() {
        // "ES" no es igual a "es", debería defaultear a "en"
        assertEquals("en", AppLanguageManager.sanitizeSelectableMode("ES"));
    }

    @Test
    public void constants_haveExpectedValues() {
        assertEquals("es", AppLanguageManager.MODE_SPANISH);
        assertEquals("en", AppLanguageManager.MODE_ENGLISH);
    }
}
