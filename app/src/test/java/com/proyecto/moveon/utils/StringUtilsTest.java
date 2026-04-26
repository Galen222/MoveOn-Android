package com.proyecto.moveon.utils;

import static org.junit.Assert.*;

import org.junit.Test;
/**
 * Pruebas para validar el comportamiento de string utils.
 */
public class StringUtilsTest {

    // ── textOf ──────────────────────────────────────────────────────────────

    /**
     * Verifica el escenario cubierto por {@link #textOf_nullReturnsEmptyString()}.
     */
    @Test
    public void textOf_nullReturnsEmptyString() {
        assertEquals("", StringUtils.textOf(null));
    }

    /**
     * Verifica el escenario cubierto por {@link #textOf_emptyStringReturnsEmpty()}.
     */
    @Test
    public void textOf_emptyStringReturnsEmpty() {
        assertEquals("", StringUtils.textOf(""));
    }

    /**
     * Verifica el escenario cubierto por {@link #textOf_trimsWhitespace()}.
     */
    @Test
    public void textOf_trimsWhitespace() {
        assertEquals("hello", StringUtils.textOf("  hello  "));
    }

    /**
     * Verifica el escenario cubierto por {@link #textOf_normalStringUnchanged()}.
     */
    @Test
    public void textOf_normalStringUnchanged() {
        assertEquals("test", StringUtils.textOf("test"));
    }

    /**
     * Verifica el escenario cubierto por {@link #textOf_charSequenceConverted()}.
     */
    @Test
    public void textOf_charSequenceConverted() {
        CharSequence cs = new StringBuilder("builder");
        assertEquals("builder", StringUtils.textOf(cs));
    }

    /**
     * Verifica el escenario cubierto por {@link #textOf_whitespaceOnlyReturnsEmpty()}.
     */
    @Test
    public void textOf_whitespaceOnlyReturnsEmpty() {
        assertEquals("", StringUtils.textOf("   "));
    }

    // ── hasText(String) ─────────────────────────────────────────────────────

    /**
     * Verifica el escenario cubierto por {@link #hasText_nullReturnsFalse()}.
     */
    @Test
    public void hasText_nullReturnsFalse() {
        assertFalse(StringUtils.hasText((String) null));
    }

    /**
     * Verifica el escenario cubierto por {@link #hasText_emptyReturnsFalse()}.
     */
    @Test
    public void hasText_emptyReturnsFalse() {
        assertFalse(StringUtils.hasText(""));
    }

    /**
     * Verifica el escenario cubierto por {@link #hasText_whitespaceOnlyReturnsFalse()}.
     */
    @Test
    public void hasText_whitespaceOnlyReturnsFalse() {
        assertFalse(StringUtils.hasText("   "));
    }

    /**
     * Verifica el escenario cubierto por {@link #hasText_tabsAndNewlinesReturnsFalse()}.
     */
    @Test
    public void hasText_tabsAndNewlinesReturnsFalse() {
        assertFalse(StringUtils.hasText("\t\n  "));
    }

    /**
     * Verifica el escenario cubierto por {@link #hasText_validTextReturnsTrue()}.
     */
    @Test
    public void hasText_validTextReturnsTrue() {
        assertTrue(StringUtils.hasText("hello"));
    }

    /**
     * Verifica el escenario cubierto por {@link #hasText_textWithSpacesReturnsTrue()}.
     */
    @Test
    public void hasText_textWithSpacesReturnsTrue() {
        assertTrue(StringUtils.hasText("  hello  "));
    }

    /**
     * Verifica el escenario cubierto por {@link #hasText_singleCharReturnsTrue()}.
     */
    @Test
    public void hasText_singleCharReturnsTrue() {
        assertTrue(StringUtils.hasText("a"));
    }

    // ── hasText(CharSequence) ───────────────────────────────────────────────

    /**
     * Verifica el escenario cubierto por {@link #hasText_charSequence_nullReturnsFalse()}.
     */
    @Test
    public void hasText_charSequence_nullReturnsFalse() {
        assertFalse(StringUtils.hasText((CharSequence) null));
    }

    /**
     * Verifica el escenario cubierto por {@link #hasText_charSequence_validReturnsTrue()}.
     */
    @Test
    public void hasText_charSequence_validReturnsTrue() {
        CharSequence cs = new StringBuilder("text");
        assertTrue(StringUtils.hasText(cs));
    }

    /**
     * Verifica el escenario cubierto por {@link #hasText_charSequence_blankReturnsFalse()}.
     */
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
        assertFalse(StringUtils.hasText((String) null));
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
