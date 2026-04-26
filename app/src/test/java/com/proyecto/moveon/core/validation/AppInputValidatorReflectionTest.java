package com.proyecto.moveon.core.validation;

import static org.junit.Assert.*;

import org.junit.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Set;

/**
 * Tests de reflexión para helpers puros de {@link AppInputValidator} que no requieren contexto Android.
 */
public class AppInputValidatorReflectionTest {

    /**
     * Verifica que {@link AppInputValidator.ValidationResult#ok(Object)} conserva el valor y queda marcado como válido.
     */
    @Test
    public void validationResultOk_preservesValueAndHasNoError() {
        AppInputValidator.ValidationResult<String> result = AppInputValidator.ValidationResult.ok("valor");

        assertTrue(result.isValid());
        assertEquals("valor", result.getValue());
        assertNull(result.getErrorMessage());
    }

    /**
     * Verifica que {@link AppInputValidator.ValidationResult#error(String)} no expone valor y queda marcado como inválido.
     */
    @Test
    public void validationResultError_preservesMessageAndHasNoValue() {
        AppInputValidator.ValidationResult<String> result = AppInputValidator.ValidationResult.error("mensaje");

        assertFalse(result.isValid());
        assertNull(result.getValue());
        assertEquals("mensaje", result.getErrorMessage());
    }

    /**
     * Comprueba que el trim defensivo trata null como cadena vacía y recorta extremos.
     */
    @Test
    public void safeTrim_handlesNullAndWhitespace() throws Exception {
        assertEquals("", invoke("safeTrim", new Class<?>[]{String.class}, new Object[]{null}));
        assertEquals("hola", invoke("safeTrim", new Class<?>[]{String.class}, "  hola  "));
    }

    /**
     * Comprueba que la normalización de frase compacta espacios, baja a minúsculas y elimina acentos.
     */
    @Test
    public void normalizePhrase_compactsLowercasesAndRemovesAccents() throws Exception {
        assertEquals("angel nino", invoke("normalizePhrase", new Class<?>[]{String.class}, "  Ángel   Niño  "));
        assertEquals("", invoke("normalizePhrase", new Class<?>[]{String.class}, new Object[]{null}));
    }

    /**
     * Comprueba que la normalización de usuario aplica sustituciones leet y elimina símbolos.
     */
    @Test
    public void normalizeUsername_appliesLeetSpeakAndRemovesSymbols() throws Exception {
        assertEquals("administrador", invoke("normalizeUsername", new Class<?>[]{String.class}, "4dm1n!str@d0r"));
        assertEquals("testuser", invoke("normalizeUsername", new Class<?>[]{String.class}, " Té-st_User "));
    }

    /**
     * Verifica que el parser CSV elimina blancos, ignora tokens vacíos y tolera entradas nulas.
     */
    @Test
    public void parseCsv_trimsTokensAndSkipsEmptyValues() throws Exception {
        assertArrayEquals(new String[]{"uno", "dos", "tres"},
                (String[]) invoke("parseCsv", new Class<?>[]{String.class}, " uno, ,dos,, tres "));
        assertArrayEquals(new String[0],
                (String[]) invoke("parseCsv", new Class<?>[]{String.class}, new Object[]{null}));
    }

    /**
     * Verifica que los tokens reservados se generan normalizados y que cada llamada crea una copia independiente.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void reservedTokens_areNormalizedAndIndependentBetweenCalls() throws Exception {
        Set<String> tokens = (Set<String>) invoke("reservedTokens", new Class<?>[0]);

        assertTrue(tokens.contains("admin"));
        assertTrue(tokens.contains("administrador"));
        assertTrue(tokens.contains("support"));

        tokens.add("otro");
        Set<String> freshTokens = (Set<String>) invoke("reservedTokens", new Class<?>[0]);

        assertTrue(tokens.contains("otro"));
        assertFalse(freshTokens.contains("otro"));
        assertTrue(freshTokens.contains("admin"));
    }

    /**
     * Verifica las ramas principales de detección de cadenas compuestas solo por dígitos.
     */
    @Test
    public void isDigitsOnly_handlesNumericEmptyAndMixedStrings() throws Exception {
        assertEquals(Boolean.TRUE, invoke("isDigitsOnly", new Class<?>[]{String.class}, "123456"));
        assertEquals(Boolean.TRUE, invoke("isDigitsOnly", new Class<?>[]{String.class}, ""));
        assertEquals(Boolean.FALSE, invoke("isDigitsOnly", new Class<?>[]{String.class}, "12a"));
    }

    /**
     * Verifica que las utilidades de contraseña detectan mayúsculas y números de forma independiente.
     */
    @Test
    public void passwordCharacterScanners_detectUppercaseAndDigits() throws Exception {
        assertEquals(Boolean.TRUE, invoke("hasUppercase", new Class<?>[]{String.class}, "abcD"));
        assertEquals(Boolean.FALSE, invoke("hasUppercase", new Class<?>[]{String.class}, "abcd"));
        assertEquals(Boolean.TRUE, invoke("hasDigit", new Class<?>[]{String.class}, "abc1"));
        assertEquals(Boolean.FALSE, invoke("hasDigit", new Class<?>[]{String.class}, "abcd"));
    }

    /**
     * Verifica que el helper de conjuntos elimina duplicados y devuelve una vista inmutable.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void unmodifiableSet_deduplicatesAndRejectsMutation() throws Exception {
        Set<String> values = (Set<String>) invoke("unmodifiableSet", new Class<?>[]{String[].class}, (Object) new String[]{"a", "b", "a"});

        assertEquals(2, values.size());
        assertTrue(values.contains("a"));
        try {
            values.remove("a");
            fail("El conjunto devuelto debe rechazar mutaciones");
        } catch (UnsupportedOperationException expected) {
            assertTrue(true);
        }
    }


    /**
     * Verifica que la comparación pública aplica trim defensivo y trata null como cadena vacía.
     */
    @Test
    public void sameText_comparesTrimmedValuesAndNulls() {
        assertTrue(AppInputValidator.sameText("  hola  ", "hola"));
        assertTrue(AppInputValidator.sameText(null, ""));
        assertFalse(AppInputValidator.sameText("hola", "adios"));
    }

    /**
     * Verifica que normalizeText compacta espacios internos sin alterar mayúsculas ni acentos.
     */
    @Test
    public void normalizeText_compactsWhitespaceAndKeepsOriginalLetters() throws Exception {
        assertEquals("Hola Mundo", invoke("normalizeText", new Class<?>[]{String.class}, "  Hola   Mundo  "));
        assertEquals("", invoke("normalizeText", new Class<?>[]{String.class}, new Object[]{null}));
    }

    /**
     * Verifica que removeAccents elimina marcas diacríticas preservando el resto del texto.
     */
    @Test
    public void removeAccents_stripsDiacriticsFromSpanishText() throws Exception {
        assertEquals("Cancion Nandu", invoke("removeAccents", new Class<?>[]{String.class}, "Canción Ñandú"));
        assertEquals("AEIOU aeiou", invoke("removeAccents", new Class<?>[]{String.class}, "ÁÉÍÓÚ áéíóú"));
    }

    /**
     * Verifica que tokenizeRealName divide por separadores no alfabéticos y normaliza acentos.
     */
    @Test
    public void tokenizeRealName_splitsByNonLettersAfterNormalization() throws Exception {
        assertArrayEquals(new String[]{"ana", "maria", "nunez"},
                (String[]) invoke("tokenizeRealName", new Class<?>[]{String.class}, "Ana-María  Núñez"));
        assertArrayEquals(new String[0],
                (String[]) invoke("tokenizeRealName", new Class<?>[]{String.class}, new Object[]{null}));
    }

    /**
     * Verifica que los usernames reservados se detectan por igualdad, bordes y tokens críticos internos.
     */
    @Test
    public void matchReservedUsername_detectsExactEdgesAndHighRiskContains() throws Exception {
        assertEquals("admin", invoke("matchReservedUsername", new Class<?>[]{String.class}, "admin"));
        assertEquals("admin", invoke("matchReservedUsername", new Class<?>[]{String.class}, "admin123"));
        assertEquals("admin", invoke("matchReservedUsername", new Class<?>[]{String.class}, "xxadminxx"));
        assertEquals("support", invoke("matchReservedUsername", new Class<?>[]{String.class}, "mysupport"));
    }

    /**
     * Verifica que un token reservado de bajo riesgo no se bloquea cuando aparece sólo dentro de otra palabra.
     */
    @Test
    public void matchReservedUsername_allowsLowRiskTokenInsideAnotherWord() throws Exception {
        assertNull(invoke("matchReservedUsername", new Class<?>[]{String.class}, "xhelpy"));
        assertNull(invoke("matchReservedUsername", new Class<?>[]{String.class}, new Object[]{null}));
    }

    /**
     * Verifica la detección de términos de diccionario en username con match exacto y dígitos no leet alrededor.
     */
    @Test
    public void matchUsernameDictionary_detectsExactAndDigitWrappedTerms() throws Exception {
        setDictionary(set("term"), set(), set("badword"));

        assertEquals("badword", invoke("matchUsernameDictionary", new Class<?>[]{android.content.Context.class, String.class}, null, "badword"));
        assertEquals("badword", invoke("matchUsernameDictionary", new Class<?>[]{android.content.Context.class, String.class}, null, "222badword666"));
        assertNull(invoke("matchUsernameDictionary", new Class<?>[]{android.content.Context.class, String.class}, null, "xxbadwordyy"));
    }

    /**
     * Verifica que la detección por username aplica normalización leet antes de comparar diccionario.
     */
    @Test
    public void matchUsernameDictionary_appliesLeetNormalizationBeforeLookup() throws Exception {
        setDictionary(set(), set(), set("badword"));

        assertEquals("badword", invoke("matchUsernameDictionary", new Class<?>[]{android.content.Context.class, String.class}, null, "b4dw0rd"));
    }

    /**
     * Verifica que el diccionario de nombre real detecta frases completas normalizadas.
     */
    @Test
    public void matchRealNameDictionary_detectsNormalizedPhraseTerms() throws Exception {
        setDictionary(set(), set("mala frase"), set());

        assertEquals("mala frase", invoke("matchRealNameDictionary", new Class<?>[]{android.content.Context.class, String.class}, null, "  MÁLA   Frase  "));
    }

    /**
     * Verifica que el diccionario de nombre real detecta términos simples tras tokenizar.
     */
    @Test
    public void matchRealNameDictionary_detectsSingleTokenTerms() throws Exception {
        setDictionary(set("prohibido"), set(), set());

        assertEquals("prohibido", invoke("matchRealNameDictionary", new Class<?>[]{android.content.Context.class, String.class}, null, "Nombre-Prohibido"));
        assertNull(invoke("matchRealNameDictionary", new Class<?>[]{android.content.Context.class, String.class}, null, "Nombre Limpio"));
    }

    /**
     * Verifica que loadDictionary reutiliza la caché privada si ya está precargada.
     */
    @Test
    public void loadDictionary_returnsPreloadedCacheWithoutReadingAssets() throws Exception {
        Object dictionary = setDictionary(set("uno"), set("dos palabras"), set("usuario"));

        assertSame(dictionary, invoke("loadDictionary", new Class<?>[]{android.content.Context.class}, new Object[]{null}));
    }


    private static Object setDictionary(Set<String> singleTerms,
                                        Set<String> phraseTerms,
                                        Set<String> usernameTerms) throws Exception {
        Class<?> cacheClass = Class.forName("com.proyecto.moveon.core.validation.AppInputValidator$DictionaryCache");
        java.lang.reflect.Constructor<?> constructor = cacheClass.getDeclaredConstructor(Set.class, Set.class, Set.class);
        constructor.setAccessible(true);
        Object dictionary = constructor.newInstance(singleTerms, phraseTerms, usernameTerms);
        java.lang.reflect.Field field = AppInputValidator.class.getDeclaredField("cachedDictionary");
        field.setAccessible(true);
        field.set(null, dictionary);
        return dictionary;
    }

    private static Set<String> set(String... values) {
        return new java.util.LinkedHashSet<>(java.util.Arrays.asList(values));
    }

    private static Object invoke(String methodName, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = AppInputValidator.class.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        try {
            return method.invoke(null, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw e;
        }
    }
}
