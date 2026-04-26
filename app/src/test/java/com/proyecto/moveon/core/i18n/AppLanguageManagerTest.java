package com.proyecto.moveon.core.i18n;

import static org.junit.Assert.*;

import com.proyecto.moveon.core.settings.AppSettingsManager;
import com.proyecto.moveon.testutil.MemoryContext;

import org.junit.Test;

import java.util.Locale;

/**
 * Tests unitarios para la lógica pura de AppLanguageManager.
 * Los métodos que dependen de Context se testean en tests instrumentados.
 */
public class AppLanguageManagerTest {

    /**
     * Verifica el escenario cubierto por {@link #sanitizeSelectableMode_spanishReturnsSpanish()}.
     */
    @Test
    public void sanitizeSelectableMode_spanishReturnsSpanish() {
        assertEquals("es", AppLanguageManager.sanitizeSelectableMode("es"));
    }

    /**
     * Verifica el escenario cubierto por {@link #sanitizeSelectableMode_englishReturnsEnglish()}.
     */
    @Test
    public void sanitizeSelectableMode_englishReturnsEnglish() {
        assertEquals("en", AppLanguageManager.sanitizeSelectableMode("en"));
    }

    /**
     * Verifica el escenario cubierto por {@link #sanitizeSelectableMode_unknownDefaultsToEnglish()}.
     */
    @Test
    public void sanitizeSelectableMode_unknownDefaultsToEnglish() {
        assertEquals("en", AppLanguageManager.sanitizeSelectableMode("fr"));
    }

    /**
     * Verifica el escenario cubierto por {@link #sanitizeSelectableMode_nullDefaultsToEnglish()}.
     */
    @Test
    public void sanitizeSelectableMode_nullDefaultsToEnglish() {
        assertEquals("en", AppLanguageManager.sanitizeSelectableMode(null));
    }

    /**
     * Verifica el escenario cubierto por {@link #sanitizeSelectableMode_emptyDefaultsToEnglish()}.
     */
    @Test
    public void sanitizeSelectableMode_emptyDefaultsToEnglish() {
        assertEquals("en", AppLanguageManager.sanitizeSelectableMode(""));
    }

    /**
     * Verifica el escenario cubierto por {@link #sanitizeSelectableMode_caseExactMatch()}.
     */
    @Test
    public void sanitizeSelectableMode_caseExactMatch() {
        // "ES" no es igual a "es", debería defaultear a "en"
        assertEquals("en", AppLanguageManager.sanitizeSelectableMode("ES"));
    }

    /**
     * Verifica el escenario cubierto por {@link #constants_haveExpectedValues()}.
     */
    @Test
    public void constants_haveExpectedValues() {
        assertEquals("es", AppLanguageManager.MODE_SPANISH);
        assertEquals("en", AppLanguageManager.MODE_ENGLISH);
    }
    /**
     * Verifica que saveOnly persiste únicamente modos seleccionables ya normalizados.
     */
    @Test
    public void saveOnly_persistsSanitizedSelectableModes() {
        MemoryContext context = new MemoryContext();

        AppLanguageManager.saveOnly(context, AppLanguageManager.MODE_SPANISH);

        assertEquals(AppLanguageManager.MODE_SPANISH, AppSettingsManager.getStoredAppLanguage(context));
        assertTrue(AppLanguageManager.hasManualSelection(context));

        AppLanguageManager.saveOnly(context, "fr");

        assertEquals(AppLanguageManager.MODE_ENGLISH, AppSettingsManager.getStoredAppLanguage(context));
        assertTrue(AppLanguageManager.hasManualSelection(context));
    }

    /**
     * Verifica que getSelectedMode devuelve la selección manual cuando está guardada.
     */
    @Test
    public void getSelectedMode_returnsManualSelectionWhenPresent() {
        MemoryContext context = new MemoryContext();
        AppSettingsManager.setAppLanguage(context, AppLanguageManager.MODE_SPANISH);

        assertEquals(AppLanguageManager.MODE_SPANISH, AppLanguageManager.getSelectedMode(context));
        assertEquals(AppLanguageManager.MODE_SPANISH, AppLanguageManager.getResolvedLanguageTag(context));
        assertEquals(Locale.forLanguageTag("es"), AppLanguageManager.getActiveLocale(context));
    }

    /**
     * Verifica que hasManualSelection refleja la existencia de una preferencia validada.
     */
    @Test
    public void hasManualSelection_tracksValidatedStoredLanguage() {
        MemoryContext context = new MemoryContext();

        assertFalse(AppLanguageManager.hasManualSelection(context));

        AppSettingsManager.setAppLanguage(context, AppLanguageManager.MODE_ENGLISH);

        assertTrue(AppLanguageManager.hasManualSelection(context));

        AppSettingsManager.clearAppLanguage(context);

        assertFalse(AppLanguageManager.hasManualSelection(context));
    }

}
