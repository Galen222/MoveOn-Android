package com.proyecto.moveon.utils;

import static org.junit.Assert.*;

import org.junit.Test;
/**
 * Pruebas para validar el comportamiento de string utils.
 */
public class StringUtilsTest {

    // ── textOf ──────────────────────────────────────────────────────────────

    @Test
    public void textOf_nullReturnsEmptyString() {
        assertEquals("", StringUtils.textOf(null));
    }

    @Test
    public void textOf_emptyStringReturnsEmpty() {
        assertEquals("", StringUtils.textOf(""));
    }

    @Test
    public void textOf_trimsWhitespace() {
        assertEquals("hello", StringUtils.textOf("  hello  "));
    }

    @Test
    public void textOf_normalStringUnchanged() {
        assertEquals("test", StringUtils.textOf("test"));
    }

    @Test
    public void textOf_charSequenceConverted() {
        CharSequence cs = new StringBuilder("builder");
        assertEquals("builder", StringUtils.textOf(cs));
    }

    @Test
    public void textOf_whitespaceOnlyReturnsEmpty() {
        assertEquals("", StringUtils.textOf("   "));
    }

    // ── hasText(String) ─────────────────────────────────────────────────────

    @Test
    public void hasText_nullReturnsFalse() {
        assertFalse(StringUtils.hasText(null));
    }

    @Test
    public void hasText_emptyReturnsFalse() {
        assertFalse(StringUtils.hasText(""));
    }

    @Test
    public void hasText_whitespaceOnlyReturnsFalse() {
        assertFalse(StringUtils.hasText("   "));
    }

    @Test
    public void hasText_tabsAndNewlinesReturnsFalse() {
        assertFalse(StringUtils.hasText("\t\n  "));
    }

    @Test
    public void hasText_validTextReturnsTrue() {
        assertTrue(StringUtils.hasText("hello"));
    }

    @Test
    public void hasText_textWithSpacesReturnsTrue() {
        assertTrue(StringUtils.hasText("  hello  "));
    }

    @Test
    public void hasText_singleCharReturnsTrue() {
        assertTrue(StringUtils.hasText("a"));
    }

    // ── hasText(CharSequence) ───────────────────────────────────────────────

    @Test
    public void hasText_charSequence_nullReturnsFalse() {
        assertFalse(StringUtils.hasText((CharSequence) null));
    }

    @Test
    public void hasText_charSequence_validReturnsTrue() {
        CharSequence cs = new StringBuilder("text");
        assertTrue(StringUtils.hasText(cs));
    }

    @Test
    public void hasText_charSequence_blankReturnsFalse() {
        CharSequence cs = new StringBuilder("   ");
        assertFalse(StringUtils.hasText(cs));
    }
    /**
     * Verifica que textOf convierte null a cadena vacía y recorta espacios laterales.
     */
    @Test
    public void textOf_nullAndWhitespace_areNormalizedSafely() {
        assertEquals("", StringUtils.textOf(null));
        assertEquals("hola", StringUtils.textOf("  hola  "));
        assertEquals("42", StringUtils.textOf(new StringBuilder(" 42 ")));
    }

    /**
     * Verifica que hasText(String) distingue texto útil de entradas nulas o blancas.
     */
    @Test
    public void hasText_stringOnlyAcceptsVisibleCharacters() {
        assertFalse(StringUtils.hasText(null));
        assertFalse(StringUtils.hasText(""));
        assertFalse(StringUtils.hasText("   \t\n"));
        assertTrue(StringUtils.hasText(" a "));
    }

    /**
     * Verifica que hasText(CharSequence) aplica la misma normalización que textOf.
     */
    @Test
    public void hasText_charSequenceUsesTrimmedContent() {
        assertFalse(StringUtils.hasText((CharSequence) null));
        assertFalse(StringUtils.hasText(new StringBuilder("   ")));
        assertTrue(StringUtils.hasText(new StringBuilder(" user ")));
    }

    /**
     * Verifica que las sobrecargas no se confunden cuando se castea explícitamente a CharSequence.
     */
    @Test
    public void hasText_explicitCharSequenceCastKeepsExpectedSemantics() {
        CharSequence value = "  contenido  ";

        assertTrue(StringUtils.hasText(value));
        assertEquals("contenido", StringUtils.textOf(value));
    }
}
