package com.proyecto.moveon.core.validation;

import static org.junit.Assert.*;

import com.proyecto.moveon.R;
import com.proyecto.moveon.testutil.MemoryContext;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Set;

/**
 * Tests de caminos públicos válidos de {@link AppInputValidator} evitando dependencias reales de assets Android.
 */
public class AppInputValidatorPublicTest {

    /**
     * Verifica que el identificador de login se recorta y se conserva cuando tiene contenido útil.
     */
    @Test
    public void validateLoginIdentifier_trimsValidIdentifier() {
        AppInputValidator.ValidationResult<String> result =
                AppInputValidator.validateLoginIdentifier(new MemoryContext(), "  ana@example.com  ");

        assertTrue(result.isValid());
        assertEquals("ana@example.com", result.getValue());
        assertNull(result.getErrorMessage());
    }

    /**
     * Verifica que la contraseña de login se recorta sin aplicar reglas de complejidad en este flujo.
     */
    @Test
    public void validateLoginPassword_trimsWithoutComplexityRules() {
        AppInputValidator.ValidationResult<String> result =
                AppInputValidator.validateLoginPassword(new MemoryContext(), "  simple  ");

        assertTrue(result.isValid());
        assertEquals("simple", result.getValue());
    }

    /**
     * Verifica que el username opcional vacío devuelve cadena vacía sin consultar diccionarios.
     */
    @Test
    public void validateUsername_allowsBlankWhenOptional() {
        AppInputValidator.ValidationResult<String> result =
                AppInputValidator.validateUsername(new MemoryContext(), "   ", false);

        assertTrue(result.isValid());
        assertEquals("", result.getValue());
    }

    /**
     * Verifica que un username válido supera formato, longitud y moderación con diccionario limpio.
     */
    @Test
    public void validateUsername_acceptsCleanRequiredUsername() throws Exception {
        setEmptyDictionary();

        AppInputValidator.ValidationResult<String> result =
                AppInputValidator.validateUsername(new MemoryContext(), "Runner2026", true);

        assertTrue(result.isValid());
        assertEquals("Runner2026", result.getValue());
    }

    /**
     * Verifica que el nombre real opcional vacío devuelve cadena vacía sin error.
     */
    @Test
    public void validateRealName_allowsBlankWhenOptional() {
        AppInputValidator.ValidationResult<String> result =
                AppInputValidator.validateRealName(new MemoryContext(), " ", false);

        assertTrue(result.isValid());
        assertEquals("", result.getValue());
    }

    /**
     * Verifica que un nombre real limpio conserva acentos permitidos y supera moderación vacía.
     */
    @Test
    public void validateRealName_acceptsCleanNameWithAccents() throws Exception {
        setEmptyDictionary();

        AppInputValidator.ValidationResult<String> result =
                AppInputValidator.validateRealName(new MemoryContext(), "Ana María Núñez", true);

        assertTrue(result.isValid());
        assertEquals("Ana María Núñez", result.getValue());
    }

    /**
     * Verifica que el email opcional vacío se acepta como cadena vacía.
     */
    @Test
    public void validateEmail_allowsBlankWhenOptional() {
        AppInputValidator.ValidationResult<String> result =
                AppInputValidator.validateEmail(new MemoryContext(), null, false);

        assertTrue(result.isValid());
        assertEquals("", result.getValue());
    }

    /**
     * Verifica que el email válido se recorta y se normaliza a minúsculas.
     */
    @Test
    public void validateEmail_trimsAndLowercasesValidAddress() {
        AppInputValidator.ValidationResult<String> result =
                AppInputValidator.validateEmail(new MemoryContext(), "  ANA@Example.COM  ", true);

        assertTrue(result.isValid());
        assertEquals("ana@example.com", result.getValue());
    }

    /**
     * Verifica que la contraseña opcional vacía se acepta como cadena vacía.
     */
    @Test
    public void validatePassword_allowsBlankWhenOptional() {
        AppInputValidator.ValidationResult<String> result =
                AppInputValidator.validatePassword(new MemoryContext(), "", false, false);

        assertTrue(result.isValid());
        assertEquals("", result.getValue());
    }

    /**
     * Verifica que una contraseña válida conserva el texto recortado y supera longitud, mayúscula y número.
     */
    @Test
    public void validatePassword_acceptsStrongPassword() {
        AppInputValidator.ValidationResult<String> result =
                AppInputValidator.validatePassword(new MemoryContext(), "  MoveOn2026  ", true, false);

        assertTrue(result.isValid());
        assertEquals("MoveOn2026", result.getValue());
    }

    /**
     * Verifica que la confirmación válida usa el mismo trim defensivo que la contraseña original.
     */
    @Test
    public void validatePasswordConfirmation_acceptsTrimmedMatch() {
        AppInputValidator.ValidationResult<String> result =
                AppInputValidator.validatePasswordConfirmation(new MemoryContext(), "  MoveOn2026  ", "MoveOn2026", R.string.app_name);

        assertTrue(result.isValid());
        assertEquals("MoveOn2026", result.getValue());
    }

    /**
     * Verifica que una fecha de nacimiento antigua en texto ISO se acepta normalizada.
     */
    @Test
    public void validateBirthDate_acceptsAdultIsoDate() {
        AppInputValidator.ValidationResult<String> result =
                AppInputValidator.validateBirthDate(new MemoryContext(), "1990-05-20", true);

        assertTrue(result.isValid());
        assertEquals("1990-05-20", result.getValue());
    }

    /**
     * Verifica que una fecha de nacimiento adulta ya parseada devuelve su representación ISO.
     */
    @Test
    public void validateBirthDate_acceptsAdultLocalDate() {
        AppInputValidator.ValidationResult<String> result =
                AppInputValidator.validateBirthDate(new MemoryContext(), LocalDate.of(1988, 3, 14));

        assertTrue(result.isValid());
        assertEquals("1988-03-14", result.getValue());
    }

    /**
     * Verifica que el código de recuperación válido se devuelve sin cambios tras el trim.
     */
    @Test
    public void validateRecoveryCode_acceptsSixDigits() {
        AppInputValidator.ValidationResult<String> result =
                AppInputValidator.validateRecoveryCode(new MemoryContext(), " 123456 ");

        assertTrue(result.isValid());
        assertEquals("123456", result.getValue());
    }

    /**
     * Verifica que una altura ausente se acepta como nula y una altura dentro de rango se conserva.
     */
    @Test
    public void validateHeight_acceptsNullAndInRangeValues() {
        AppInputValidator.ValidationResult<Integer> empty =
                AppInputValidator.validateHeight(new MemoryContext(), null);
        AppInputValidator.ValidationResult<Integer> valid =
                AppInputValidator.validateHeight(new MemoryContext(), 175);

        assertTrue(empty.isValid());
        assertNull(empty.getValue());
        assertTrue(valid.isValid());
        assertEquals(Integer.valueOf(175), valid.getValue());
    }

    /**
     * Verifica que un peso ausente se acepta como nulo y un peso dentro de rango se conserva.
     */
    @Test
    public void validateWeight_acceptsNullAndInRangeValues() {
        AppInputValidator.ValidationResult<Double> empty =
                AppInputValidator.validateWeight(new MemoryContext(), null);
        AppInputValidator.ValidationResult<Double> valid =
                AppInputValidator.validateWeight(new MemoryContext(), 72.5);

        assertTrue(empty.isValid());
        assertNull(empty.getValue());
        assertTrue(valid.isValid());
        assertEquals(Double.valueOf(72.5), valid.getValue());
    }

    private static void setEmptyDictionary() throws Exception {
        Class<?> cacheClass = Class.forName("com.proyecto.moveon.core.validation.AppInputValidator$DictionaryCache");
        Constructor<?> constructor = cacheClass.getDeclaredConstructor(Set.class, Set.class, Set.class);
        constructor.setAccessible(true);
        Object dictionary = constructor.newInstance(Collections.emptySet(), Collections.emptySet(), Collections.emptySet());
        Field field = AppInputValidator.class.getDeclaredField("cachedDictionary");
        field.setAccessible(true);
        field.set(null, dictionary);
    }
}
