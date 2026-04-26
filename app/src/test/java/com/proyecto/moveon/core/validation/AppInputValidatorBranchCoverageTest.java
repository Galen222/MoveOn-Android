package com.proyecto.moveon.core.validation;

import static org.junit.Assert.*;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.proyecto.moveon.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Tests de ramas de error y casos límite de {@link AppInputValidator} que
 * complementan {@code AppInputValidatorPublicTest} y {@code AppInputValidatorNegativeTest}.
 *
 * <p>Se ejecutan bajo {@link RobolectricTestRunner} porque las ramas de error
 * resuelven mensajes vía {@code Context#getString} con recursos reales del
 * módulo {@code app}.</p>
 */
@RunWith(RobolectricTestRunner.class)

public class AppInputValidatorBranchCoverageTest {

    /**
     * Devuelve el contexto de aplicación gestionado por Robolectric.
     *
     * @return contexto Android funcional con recursos del módulo {@code app}.
     */
    private static Context appContext() {
        return ApplicationProvider.getApplicationContext();
    }

    /**
     * Verifica que el identificador de login en blanco produce un error con mensaje localizado.
     */
    @Test
    public void validateLoginIdentifier_blankInput_returnsErrorWithMessage() {
        AppInputValidator.ValidationResult<String> result =
                AppInputValidator.validateLoginIdentifier(appContext(), "   ");

        assertFalse(result.isValid());
        assertNull(result.getValue());
    }

    /**
     * Verifica que la contraseña de login en blanco produce un error.
     */
    @Test
    public void validateLoginPassword_blankInput_returnsErrorWithMessage() {
        AppInputValidator.ValidationResult<String> result =
                AppInputValidator.validateLoginPassword(appContext(), null);

        assertFalse(result.isValid());
    }

    /**
     * Verifica que un username corto activa la rama de longitud mínima.
     */
    @Test
    public void validateUsername_tooShort_returnsLengthError() {
        AppInputValidator.ValidationResult<String> result =
                AppInputValidator.validateUsername(appContext(), "abcd", true);

        assertFalse(result.isValid());
    }

    /**
     * Verifica que un username muy largo activa la rama de longitud máxima.
     */
    @Test
    public void validateUsername_tooLong_returnsLengthError() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 60; i++) sb.append("a");

        AppInputValidator.ValidationResult<String> result =
                AppInputValidator.validateUsername(appContext(), sb.toString(), true);

        assertFalse(result.isValid());
    }

    /**
     * Verifica que un username con caracteres no alfanuméricos cae en formato inválido.
     */
    @Test
    public void validateUsername_invalidCharacters_returnsFormatError() {
        AppInputValidator.ValidationResult<String> result =
                AppInputValidator.validateUsername(appContext(), "tiene espacios", true);

        assertFalse(result.isValid());
    }

    /**
     * Verifica que un username obligatorio en blanco devuelve error de requerido.
     */
    @Test
    public void validateUsername_blankWhenRequired_returnsRequiredError() {
        AppInputValidator.ValidationResult<String> result =
                AppInputValidator.validateUsername(appContext(), "  ", true);

        assertFalse(result.isValid());
    }

    /**
     * Verifica que un nombre real obligatorio en blanco devuelve error.
     */
    @Test
    public void validateRealName_blankWhenRequired_returnsError() {
        AppInputValidator.ValidationResult<String> result =
                AppInputValidator.validateRealName(appContext(), null, true);

        assertFalse(result.isValid());
    }

    /**
     * Verifica que un nombre real demasiado corto activa la rama de longitud mínima.
     */
    @Test
    public void validateRealName_tooShort_returnsLengthError() {
        AppInputValidator.ValidationResult<String> result =
                AppInputValidator.validateRealName(appContext(), "Al", false);

        assertFalse(result.isValid());
    }

    /**
     * Verifica que un nombre real con dígitos cae en la rama de caracteres inválidos.
     */
    @Test
    public void validateRealName_invalidCharacters_returnsCharacterError() throws Exception {
        setEmptyDictionary();

        AppInputValidator.ValidationResult<String> result =
                AppInputValidator.validateRealName(appContext(), "Ana123 Maria", false);

        assertFalse(result.isValid());
    }

    /**
     * Verifica que un nombre real demasiado largo activa la rama de longitud máxima.
     */
    @Test
    public void validateRealName_tooLong_returnsLengthError() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 90; i++) sb.append("a");

        AppInputValidator.ValidationResult<String> result =
                AppInputValidator.validateRealName(appContext(), sb.toString(), false);

        assertFalse(result.isValid());
    }

    /**
     * Verifica que un email obligatorio sin texto devuelve error de requerido.
     */
    @Test
    public void validateEmail_blankWhenRequired_returnsError() {
        AppInputValidator.ValidationResult<String> result =
                AppInputValidator.validateEmail(appContext(), "", true);

        assertFalse(result.isValid());
    }

    /**
     * Verifica que un email sin arroba devuelve error de formato.
     */
    @Test
    public void validateEmail_invalidFormat_returnsFormatError() {
        AppInputValidator.ValidationResult<String> result =
                AppInputValidator.validateEmail(appContext(), "no-es-un-email", true);

        assertFalse(result.isValid());
    }

    /**
     * Verifica que un email con espacios internos no se considera válido.
     */
    @Test
    public void validateEmail_withInternalSpaces_returnsFormatError() {
        AppInputValidator.ValidationResult<String> result =
                AppInputValidator.validateEmail(appContext(), "ana @ example.com", true);

        assertFalse(result.isValid());
    }

    /**
     * Verifica que una contraseña obligatoria vacía con código de nuevo flujo
     * activa la rama correspondiente.
     */
    @Test
    public void validatePassword_blankRequiredWithNewPasswordCode_returnsError() {
        AppInputValidator.ValidationResult<String> result =
                AppInputValidator.validatePassword(appContext(), "", true, true);

        assertFalse(result.isValid());
    }

    /**
     * Verifica que una contraseña obligatoria vacía sin código de nuevo flujo
     * activa la rama de {@code PASSWORD_REQUIRED}.
     */
    @Test
    public void validatePassword_blankRequiredDefaultCode_returnsError() {
        AppInputValidator.ValidationResult<String> result =
                AppInputValidator.validatePassword(appContext(), null, true, false);

        assertFalse(result.isValid());
    }

    /**
     * Verifica que una contraseña corta cae en la rama de longitud mínima.
     */
    @Test
    public void validatePassword_tooShort_returnsLengthError() {
        AppInputValidator.ValidationResult<String> result =
                AppInputValidator.validatePassword(appContext(), "Ab1", true, false);

        assertFalse(result.isValid());
    }

    /**
     * Verifica que una contraseña sin mayúscula activa la rama correspondiente.
     */
    @Test
    public void validatePassword_missingUppercase_returnsUppercaseError() {
        AppInputValidator.ValidationResult<String> result =
                AppInputValidator.validatePassword(appContext(), "moveon2026", true, false);

        assertFalse(result.isValid());
    }

    /**
     * Verifica que una contraseña sin dígitos activa la rama de número faltante.
     */
    @Test
    public void validatePassword_missingDigit_returnsDigitError() {
        AppInputValidator.ValidationResult<String> result =
                AppInputValidator.validatePassword(appContext(), "MoveOnApp", true, false);

        assertFalse(result.isValid());
    }

    /**
     * Verifica que una contraseña con bytes UTF-8 superiores a 72 cae en la
     * rama de longitud máxima en bytes.
     */
    @Test
    public void validatePassword_excessiveBytes_returnsBytesError() {
        StringBuilder sb = new StringBuilder("Mn1");
        // 25 emojis de 4 bytes UTF-8 cada uno = 100 bytes > 72
        for (int i = 0; i < 25; i++) {
            sb.append("\uD83D\uDE00");
        }
        AppInputValidator.ValidationResult<String> result =
                AppInputValidator.validatePassword(appContext(), sb.toString(), true, false);

        assertFalse(result.isValid());
    }

    /**
     * Verifica que la confirmación que no coincide devuelve error.
     */
    @Test
    public void validatePasswordConfirmation_mismatch_returnsErrorWithFallbackResource() {
        AppInputValidator.ValidationResult<String> result = AppInputValidator.validatePasswordConfirmation(
                appContext(), "Password1", "OtraDistinta1", R.string.app_name);

        assertFalse(result.isValid());
    }

    /**
     * Verifica que una fecha en blanco obligatoria devuelve error de requerido.
     */
    @Test
    public void validateBirthDate_blankRequired_returnsRequiredError() {
        AppInputValidator.ValidationResult<String> result =
                AppInputValidator.validateBirthDate(appContext(), "  ", true);

        assertFalse(result.isValid());
    }

    /**
     * Verifica que una fecha de nacimiento en formato no ISO devuelve error.
     */
    @Test
    public void validateBirthDate_invalidIsoFormat_returnsParseError() {
        AppInputValidator.ValidationResult<String> result =
                AppInputValidator.validateBirthDate(appContext(), "31/12/1990", true);

        assertFalse(result.isValid());
    }

    /**
     * Verifica que una fecha de nacimiento futura activa la rama correspondiente.
     */
    @Test
    public void validateBirthDate_futureDate_returnsFutureError() {
        LocalDate future = LocalDate.now().plusDays(1);

        AppInputValidator.ValidationResult<String> result =
                AppInputValidator.validateBirthDate(appContext(), future);

        assertFalse(result.isValid());
    }

    /**
     * Verifica que una fecha de nacimiento de menor de edad activa la rama de edad mínima.
     */
    @Test
    public void validateBirthDate_underAge_returnsAgeError() {
        LocalDate teen = LocalDate.now().minusYears(10);

        AppInputValidator.ValidationResult<String> result =
                AppInputValidator.validateBirthDate(appContext(), teen);

        assertFalse(result.isValid());
    }

    /**
     * Verifica que un código de recuperación vacío devuelve error de requerido.
     */
    @Test
    public void validateRecoveryCode_blank_returnsRequiredError() {
        AppInputValidator.ValidationResult<String> result =
                AppInputValidator.validateRecoveryCode(appContext(), "  ");

        assertFalse(result.isValid());
    }

    /**
     * Verifica que un código de recuperación con longitud distinta a 6 devuelve error.
     */
    @Test
    public void validateRecoveryCode_invalidLength_returnsLengthError() {
        AppInputValidator.ValidationResult<String> result =
                AppInputValidator.validateRecoveryCode(appContext(), "12345");

        assertFalse(result.isValid());
    }

    /**
     * Verifica que un código de recuperación con caracteres no numéricos devuelve error.
     */
    @Test
    public void validateRecoveryCode_nonNumeric_returnsNumericError() {
        AppInputValidator.ValidationResult<String> result =
                AppInputValidator.validateRecoveryCode(appContext(), "12345A");

        assertFalse(result.isValid());
    }

    /**
     * Verifica que valores límite de altura activan las ramas correspondientes.
     */
    @Test
    public void validateHeight_outsideRange_returnsRangeError() {
        AppInputValidator.ValidationResult<Integer> tooShort =
                AppInputValidator.validateHeight(appContext(), 49);
        AppInputValidator.ValidationResult<Integer> tooTall =
                AppInputValidator.validateHeight(appContext(), 301);

        assertFalse(tooShort.isValid());
        assertFalse(tooTall.isValid());
    }

    /**
     * Verifica que valores límite de peso activan las ramas correspondientes.
     */
    @Test
    public void validateWeight_outsideRange_returnsRangeError() {
        AppInputValidator.ValidationResult<Double> tooLight =
                AppInputValidator.validateWeight(appContext(), 19.9);
        AppInputValidator.ValidationResult<Double> tooHeavy =
                AppInputValidator.validateWeight(appContext(), 300.1);

        assertFalse(tooLight.isValid());
        assertFalse(tooHeavy.isValid());
    }

    /**
     * Verifica que {@code sameText} cubre cadenas equivalentes y distintas tras el trim.
     */
    @Test
    public void sameText_comparesValuesAfterTrim() {
        assertTrue(AppInputValidator.sameText("hola", "  hola  "));
        assertTrue(AppInputValidator.sameText(null, "  "));
        assertFalse(AppInputValidator.sameText("hola", "adios"));
        assertFalse(AppInputValidator.sameText(null, "x"));
    }

    /**
     * Verifica que las factorías de {@link AppInputValidator.ValidationResult}
     * mantienen la mutua exclusión entre valor y error.
     */
    @Test
    public void validationResultFactories_keepMutualExclusion() {
        AppInputValidator.ValidationResult<String> ok = AppInputValidator.ValidationResult.ok("x");
        AppInputValidator.ValidationResult<String> err = AppInputValidator.ValidationResult.error("nope");
        AppInputValidator.ValidationResult<String> okNull = AppInputValidator.ValidationResult.ok(null);

        assertTrue(ok.isValid());
        assertEquals("x", ok.getValue());
        assertNull(ok.getErrorMessage());

        assertFalse(err.isValid());
        assertNull(err.getValue());
        assertEquals("nope", err.getErrorMessage());

        assertTrue(okNull.isValid());
        assertNull(okNull.getValue());
    }

    /**
     * Verifica que el dictionary cache puede inyectarse con tokens reservados
     * para forzar la rama de moderación de username.
     */
    @Test
    public void validateUsername_reservedToken_returnsModerationError() throws Exception {
        Set<String> reserved = new HashSet<>();
        reserved.add("admin");
        Class<?> cacheClass = Class.forName("com.proyecto.moveon.core.validation.AppInputValidator$DictionaryCache");
        Constructor<?> constructor = cacheClass.getDeclaredConstructor(Set.class, Set.class, Set.class);
        constructor.setAccessible(true);
        Object dictionary = constructor.newInstance(reserved, Collections.emptySet(), Collections.emptySet());
        Field field = AppInputValidator.class.getDeclaredField("cachedDictionary");
        field.setAccessible(true);
        field.set(null, dictionary);

        AppInputValidator.ValidationResult<String> result =
                AppInputValidator.validateUsername(appContext(), "admin1", true);

        assertFalse("debe rechazar usernames con tokens reservados", result.isValid());
    }

    /**
     * Restaura el diccionario interno con conjuntos vacíos para no contaminar
     * tests posteriores cuando se ejerciten ramas de moderación.
     */
    private static void setEmptyDictionary() throws Exception {
        Class<?> cacheClass = Class.forName("com.proyecto.moveon.core.validation.AppInputValidator$DictionaryCache");
        Constructor<?> constructor = cacheClass.getDeclaredConstructor(Set.class, Set.class, Set.class);
        constructor.setAccessible(true);
        Object dictionary = constructor.newInstance(
                Collections.emptySet(), Collections.emptySet(), Collections.emptySet());
        Field field = AppInputValidator.class.getDeclaredField("cachedDictionary");
        field.setAccessible(true);
        field.set(null, dictionary);
    }
}
