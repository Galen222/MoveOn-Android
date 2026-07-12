package com.proyecto.moveon.core.validation;

import static org.junit.Assert.*;

import org.junit.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Set;

/**
 * Tests JVM seguros de ramas puras de {@link AppInputValidator} sin depender de {@code Context#getString(...)}.
 */
public class AppInputValidatorNegativeTest {

    /**
     * Verifica que ValidationResult.ok expone un resultado válido con el valor normalizado recibido.
     */
    @Test
    public void validationResultOk_exposesValueAndNoError() {
        AppInputValidator.ValidationResult<String> result = AppInputValidator.ValidationResult.ok("runner");

        assertTrue(result.isValid());
        assertEquals("runner", result.getValue());
        assertNull(result.getErrorMessage());
    }

    /**
     * Verifica que ValidationResult.error marca el resultado como inválido sin valor asociado.
     */
    @Test
    public void validationResultError_exposesErrorAndNoValue() {
        AppInputValidator.ValidationResult<String> result = AppInputValidator.ValidationResult.error("error");

        assertFalse(result.isValid());
        assertNull(result.getValue());
        assertEquals("error", result.getErrorMessage());
    }

    /**
     * Verifica que ValidationResult.error con espacios se considera inválido porque hay texto de error real.
     */
    @Test
    public void validationResultErrorWithTextAfterTrim_isInvalid() {
        AppInputValidator.ValidationResult<String> result = AppInputValidator.ValidationResult.error("  error  ");

        assertFalse(result.isValid());
        assertEquals("  error  ", result.getErrorMessage());
    }

    /**
     * Verifica que sameText compara de forma defensiva aplicando trim y tratando nulos como texto vacío.
     */
    @Test
    public void sameText_trimsBothSidesAndTreatsNullAsEmpty() {
        assertTrue(AppInputValidator.sameText("  abc ", "abc"));
        assertTrue(AppInputValidator.sameText(null, " "));
        assertTrue(AppInputValidator.sameText("", null));
        assertFalse(AppInputValidator.sameText("abc", "abd"));
    }

    /**
     * Verifica que safeTrim normaliza nulos y espacios sin necesitar recursos Android.
     */
    @Test
    public void safeTrim_returnsEmptyForNullAndTrimsText() throws Exception {
        assertEquals("", invokeSafeTrim(null));
        assertEquals("MoveOn", invokeSafeTrim("  MoveOn  "));
    }

    /**
     * Verifica que hasUppercase detecta mayúsculas ASCII en cualquier posición.
     */
    @Test
    public void hasUppercase_detectsUppercaseCharacters() throws Exception {
        assertTrue(invokeBoolean("hasUppercase", "moveOn"));
        assertTrue(invokeBoolean("hasUppercase", "MOVEON"));
        assertFalse(invokeBoolean("hasUppercase", "moveon123"));
    }

    /**
     * Verifica que hasDigit detecta dígitos y rechaza textos sin números.
     */
    @Test
    public void hasDigit_detectsNumericCharacters() throws Exception {
        assertTrue(invokeBoolean("hasDigit", "moveon2026"));
        assertFalse(invokeBoolean("hasDigit", "moveon"));
    }

    /**
     * Verifica que isDigitsOnly acepta textos numéricos y solo rechaza caracteres no numéricos.
     */
    @Test
    public void isDigitsOnly_acceptsNumericAndEmptyTextOnly() throws Exception {
        assertTrue(invokeBoolean("isDigitsOnly", "123456"));
        assertTrue(invokeBoolean("isDigitsOnly", ""));
        assertFalse(invokeBoolean("isDigitsOnly", "123a56"));
        assertFalse(invokeBoolean("isDigitsOnly", "12 56"));
    }

    /**
     * Verifica que parseCsv elimina espacios, entradas vacías y conserva el orden de los tokens válidos.
     */
    @Test
    public void parseCsv_trimsAndDropsBlankEntries() throws Exception {
        String[] values = (String[]) invoke("parseCsv", new Class<?>[]{String.class}, new Object[]{" es, ,en, fr "});

        assertArrayEquals(new String[]{"es", "en", "fr"}, values);
    }

    /**
     * Verifica que parseCsv devuelve un array vacío para texto nulo o en blanco.
     */
    @Test
    public void parseCsv_returnsEmptyArrayForNullOrBlankInput() throws Exception {
        assertArrayEquals(new String[0], (String[]) invoke("parseCsv", new Class<?>[]{String.class}, new Object[]{null}));
        assertArrayEquals(new String[0], (String[]) invoke("parseCsv", new Class<?>[]{String.class}, new Object[]{"   "}));
    }

    /**
     * Verifica que reservedTokens contiene los tokens protegidos esperados y ya normalizados.
     */
    @Test
    public void reservedTokens_containsExpectedNormalizedValues() throws Exception {
        @SuppressWarnings("unchecked")
        Set<String> tokens = (Set<String>) invoke("reservedTokens", new Class<?>[0], new Object[0]);

        assertTrue(tokens.contains("admin"));
        assertTrue(tokens.contains("support"));
        assertTrue(tokens.contains("contacto"));
        assertFalse(tokens.contains(""));
    }

    private static boolean invokeBoolean(String methodName, String value) throws Exception {
        return (Boolean) invoke(methodName, new Class<?>[]{String.class}, new Object[]{value});
    }

    private static String invokeSafeTrim(String value) throws Exception {
        return (String) invoke("safeTrim", new Class<?>[]{String.class}, new Object[]{value});
    }

    private static Object invoke(String methodName, Class<?>[] parameterTypes, Object[] args) throws Exception {
        Method method = AppInputValidator.class.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        try {
            return method.invoke(null, args);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw ex;
        }
    }
}
