package com.proyecto.moveon.core.validation;

import android.content.Context;
import android.content.res.AssetManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.proyecto.moveon.R;
import com.proyecto.moveon.core.api.BackendErrorLocalizer;
import com.proyecto.moveon.utils.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validador único de inputs del cliente.
 *
 * Incluye:
 * - Validación funcional alineada con backend para username, email, password, fecha, altura y peso.
 * - Moderación de texto replicando la lógica del backend:
 *   - mismos tokens reservados de username
 *   - misma normalización/leetspeak
 *   - mismo split para nombre real
 *   - mismos diccionarios externos es.txt / en.txt
 *
 * Para que la moderación sea idéntica a backend, debes colocar los diccionarios en:
 * app/src/main/assets/data/profanity/ldnoobwv2/es.txt
 * app/src/main/assets/data/profanity/ldnoobwv2/en.txt
 */
public final class AppInputValidator {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private static final boolean TEXT_MODERATION_ENABLED = true;
    private static final boolean TEXT_MODERATION_FAIL_OPEN = false;
    private static final String TEXT_MODERATION_DICTIONARY_DIR = "data/profanity/ldnoobwv2";
    private static final String TEXT_MODERATION_DICTIONARY_LANGS = "es,en";
    private static final String TEXT_MODERATION_RESERVED_USERNAME_TOKENS =
            "admin,administrator,administrador,support,soporte,moderator,moderador,staff,official,oficial," +
            "root,owner,system,sistema,help,ayuda,info,contact,contacto";
    private static final String TEXT_MODERATION_IGNORE_DICTIONARY_TOKENS =
            "blog,contact,conversation,file,files,filter,footer,footer navigation,github,insights,issues," +
            "navigation,open,pricing,privacy,projects,pull requests,security,skip to content,terms,training";

    private static final int USERNAME_MIN_TERM_LEN = 4;
    private static final int REAL_NAME_MIN_TERM_LEN = 4;

    private static final Pattern MULTISPACE_RE = Pattern.compile("\\s+");
    private static final Pattern REAL_NAME_TOKEN_SPLIT_RE = Pattern.compile("[^a-z]+");

    private static final Set<String> HIGH_RISK_RESERVED_TOKENS = Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.asList(
                    "admin",
                    "administrator",
                    "administrador",
                    "support",
                    "soporte",
                    "moderator",
                    "moderador",
                    "staff",
                    "official",
                    "oficial",
                    "root",
                    "owner",
                    "system",
                    "sistema"
            ))
    );

    @Nullable
    private static volatile DictionaryCache cachedDictionary;

    private AppInputValidator() {}

    public static final class ValidationResult<T> {
        @Nullable private final T value;
        @Nullable private final String errorMessage;

        private ValidationResult(@Nullable T value, @Nullable String errorMessage) {
            this.value = value;
            this.errorMessage = errorMessage;
        }

        /**
         * Crea un resultado válido conservando el valor ya normalizado.
         *
         * @param value valor validado o normalizado; puede ser {@code null} cuando el campo es opcional.
         * @param <T> tipo del valor validado.
         * @return resultado correcto sin mensaje de error.
         */
        @NonNull
        public static <T> ValidationResult<T> ok(@Nullable T value) {
            return new ValidationResult<>(value, null);
        }

        /**
         * Crea un resultado inválido con el mensaje ya listo para mostrarse en UI.
         *
         * @param message texto de error localizado.
         * @param <T> tipo esperado por el consumidor del resultado.
         * @return resultado inválido sin valor asociado.
         */
        @NonNull
        public static <T> ValidationResult<T> error(@NonNull String message) {
            return new ValidationResult<>(null, message);
        }

        /**
         * Indica si la validación terminó sin errores.
         *
         * @return {@code true} cuando no existe mensaje de error; {@code false} en caso contrario.
         */
        public boolean isValid() {
            return !StringUtils.hasText(errorMessage);
        }

        /**
         * Devuelve el valor resultante de la validación.
         *
         * @return valor normalizado o {@code null} si el resultado es inválido o el campo es opcional.
         */
        @Nullable
        public T getValue() {
            return value;
        }

        /**
         * Devuelve el mensaje de error asociado a la validación.
         *
         * @return texto localizado del fallo o {@code null} si el resultado es válido.
         */
        @Nullable
        public String getErrorMessage() {
            return errorMessage;
        }
    }

    private enum ModerationField {
        USERNAME,
        REAL_NAME
    }

    private static final class DictionaryCache {
        @NonNull final Set<String> singleTerms;
        @NonNull final Set<String> phraseTerms;
        @NonNull final Set<String> usernameTerms;

        DictionaryCache(@NonNull Set<String> singleTerms,
                        @NonNull Set<String> phraseTerms,
                        @NonNull Set<String> usernameTerms) {
            this.singleTerms = Collections.unmodifiableSet(singleTerms);
            this.phraseTerms = Collections.unmodifiableSet(phraseTerms);
            this.usernameTerms = Collections.unmodifiableSet(usernameTerms);
        }
    }

    private static final class DictionaryLoadException extends Exception {
        DictionaryLoadException(@NonNull String message, @Nullable Throwable cause) {
            super(message, cause);
        }

        DictionaryLoadException() {
            super("No hay idiomas configurados para moderación");
        }
    }

    /**
     * Valida el identificador usado en el login, ya sea usuario o correo.
     *
     * @param context contexto usado para resolver mensajes de error.
     * @param rawValue texto introducido por el usuario.
     * @return {@link ValidationResult} con el identificador trimado o un error si está vacío.
     */
    @NonNull
    public static ValidationResult<String> validateLoginIdentifier(
            @NonNull Context context,
            @Nullable String rawValue
    ) {
        String value = safeTrim(rawValue);
        if (!StringUtils.hasText(value)) {
            return ValidationResult.error(context.getString(R.string.login_error_identificador_vacio));
        }
        return ValidationResult.ok(value);
    }

    /**
     * Valida la contraseña del formulario de acceso sin aplicar todavía reglas de complejidad.
     *
     * @param context contexto usado para obtener textos localizados.
     * @param rawValue contraseña introducida por el usuario.
     * @return {@link ValidationResult} con la contraseña trimada o un error si falta.
     */
    @NonNull
    public static ValidationResult<String> validateLoginPassword(
            @NonNull Context context,
            @Nullable String rawValue
    ) {
        String value = safeTrim(rawValue);
        if (!StringUtils.hasText(value)) {
            return ValidationResult.error(context.getString(R.string.login_error_password_vacio));
        }
        return ValidationResult.ok(value);
    }

    /**
     * Valida el nombre de usuario con las mismas restricciones funcionales y de moderación que backend.
     *
     * @param context contexto para localizar errores.
     * @param rawValue valor original del campo.
     * @param required indica si el campo es obligatorio en este flujo.
     * @return {@link ValidationResult} con el username normalizado o el error correspondiente.
     */
    @NonNull
    public static ValidationResult<String> validateUsername(
            @NonNull Context context,
            @Nullable String rawValue,
            boolean required
    ) {
        String value = safeTrim(rawValue);
        if (!StringUtils.hasText(value)) {
            return required
                    ? ValidationResult.error(messageForCode(context, "USERNAME_REQUIRED", R.string.registro_error_usuario_vacio))
                    : ValidationResult.ok("");
        }

        if (value.length() < 5) {
            return ValidationResult.error(messageForCode(context, "USERNAME_TOO_SHORT", R.string.registro_error_usuario_corto));
        }
        if (value.length() > 50) {
            return ValidationResult.error(messageForCode(context, "USERNAME_TOO_LONG", R.string.validation_username_too_long));
        }
        if (!value.matches("^[a-zA-Z0-9]+$")) {
            return ValidationResult.error(messageForCode(context, "USERNAME_INVALID_FORMAT", R.string.registro_error_usuario_formato));
        }

        ValidationResult<String> moderation = validateTextModeration(context, value, ModerationField.USERNAME);
        if (!moderation.isValid()) {
            return moderation;
        }

        return ValidationResult.ok(value);
    }

    /**
     * Valida el nombre real comprobando longitud, caracteres permitidos y moderación semántica.
     *
     * @param context contexto para mensajes localizados.
     * @param rawValue nombre recibido desde UI.
     * @param required indica si el nombre debe existir obligatoriamente.
     * @return {@link ValidationResult} con el valor saneado o un mensaje de error.
     */
    @NonNull
    public static ValidationResult<String> validateRealName(
            @NonNull Context context,
            @Nullable String rawValue,
            boolean required
    ) {
        String value = safeTrim(rawValue);
        if (!StringUtils.hasText(value)) {
            if (required) {
                return ValidationResult.error(messageForCode(context, "REAL_NAME_TOO_SHORT", R.string.validation_real_name_too_short));
            }
            return ValidationResult.ok("");
        }

        if (value.length() < 3) {
            return ValidationResult.error(messageForCode(context, "REAL_NAME_TOO_SHORT", R.string.validation_real_name_too_short));
        }
        if (value.length() > 80) {
            return ValidationResult.error(messageForCode(context, "REAL_NAME_TOO_LONG", R.string.validation_real_name_too_long));
        }
        if (!value.matches("^[a-zA-ZáéíóúÁÉÍÓÚñÑüÜ\\s'-]+$")) {
            return ValidationResult.error(messageForCode(context, "REAL_NAME_INVALID_CHARACTERS", R.string.validation_real_name_invalid_characters));
        }

        ValidationResult<String> moderation = validateTextModeration(context, value, ModerationField.REAL_NAME);
        if (!moderation.isValid()) {
            return moderation;
        }

        return ValidationResult.ok(value);
    }

    /**
     * Valida una dirección de correo aplicando trim, minúsculas y formato básico.
     *
     * @param context contexto para localizar mensajes.
     * @param rawValue correo introducido.
     * @param required indica si el campo admite vacío.
     * @return {@link ValidationResult} con el email normalizado o un error de validación.
     */
    @NonNull
    public static ValidationResult<String> validateEmail(
            @NonNull Context context,
            @Nullable String rawValue,
            boolean required
    ) {
        String value = safeTrim(rawValue).toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(value)) {
            if (!required) return ValidationResult.ok("");
            return ValidationResult.error(messageForCode(context, "EMAIL_REQUIRED", R.string.registro_error_correo_vacio));
        }
        if (!EMAIL_PATTERN.matcher(value).matches()) {
            return ValidationResult.error(messageForCode(context, "EMAIL_FORMAT_INVALID", R.string.registro_error_correo_formato));
        }
        return ValidationResult.ok(value);
    }

    /**
     * Valida la contraseña según longitud real, límite de bytes UTF-8 y requisitos mínimos de seguridad.
     *
     * @param context contexto usado para resolver textos localizados.
     * @param rawValue contraseña original.
     * @param required indica si el campo puede quedar vacío.
     * @param useNewPasswordRequiredCode selecciona el código de error del flujo de recuperación.
     * @return {@link ValidationResult} con la contraseña original trimada o un error descriptivo.
     */
    @NonNull
    public static ValidationResult<String> validatePassword(
            @NonNull Context context,
            @Nullable String rawValue,
            boolean required,
            boolean useNewPasswordRequiredCode
    ) {
        String value = safeTrim(rawValue);
        if (!StringUtils.hasText(value)) {
            if (!required) return ValidationResult.ok("");
            String code = useNewPasswordRequiredCode ? "NEW_PASSWORD_REQUIRED" : "PASSWORD_REQUIRED";
            int fallback = useNewPasswordRequiredCode
                    ? R.string.forgot_error_password_vacia
                    : R.string.registro_error_password_vacio;
            return ValidationResult.error(messageForCode(context, code, fallback));
        }
        int codePointLength = value.codePointCount(0, value.length());
        if (codePointLength < 8) {
            int fallback = useNewPasswordRequiredCode
                    ? R.string.forgot_error_password_corta
                    : R.string.registro_error_password_corta;
            return ValidationResult.error(messageForCode(context, "PASSWORD_TOO_SHORT", fallback));
        }
        if (value.getBytes(StandardCharsets.UTF_8).length > 72) {
            return ValidationResult.error(messageForCode(context, "PASSWORD_TOO_LONG_BYTES", R.string.validation_password_too_long_bytes));
        }
        if (!hasUppercase(value)) {
            return ValidationResult.error(messageForCode(context, "PASSWORD_MISSING_UPPERCASE", R.string.validation_password_missing_uppercase));
        }
        if (!hasDigit(value)) {
            return ValidationResult.error(messageForCode(context, "PASSWORD_MISSING_NUMBER", R.string.validation_password_missing_number));
        }
        return ValidationResult.ok(value);
    }

    /**
     * Comprueba que la confirmación coincide exactamente con la contraseña indicada.
     *
     * @param context contexto para generar el texto de error.
     * @param password contraseña base.
     * @param confirmation valor escrito en el campo de confirmación.
     * @param mismatchFallbackResId recurso a mostrar si ambos textos no coinciden.
     * @return {@link ValidationResult} válido cuando ambos campos son equivalentes tras el trim.
     */
    @NonNull
    public static ValidationResult<String> validatePasswordConfirmation(
            @NonNull Context context,
            @Nullable String password,
            @Nullable String confirmation,
            int mismatchFallbackResId
    ) {
        String confirmValue = safeTrim(confirmation);
        if (!confirmValue.equals(safeTrim(password))) {
            return ValidationResult.error(context.getString(mismatchFallbackResId));
        }
        return ValidationResult.ok(confirmValue);
    }

    /**
     * Valida una fecha de nacimiento recibida como texto ISO.
     *
     * @param context contexto para localizar errores.
     * @param rawValue fecha en formato ISO-8601.
     * @param required indica si el campo puede quedar vacío.
     * @return {@link ValidationResult} con la fecha normalizada o un error si es inválida.
     */
    @NonNull
    public static ValidationResult<String> validateBirthDate(
            @NonNull Context context,
            @Nullable String rawValue,
            boolean required
    ) {
        String value = safeTrim(rawValue);
        if (!StringUtils.hasText(value)) {
            if (!required) return ValidationResult.ok("");
            return ValidationResult.error(messageForCode(context, "BIRTH_DATE_REQUIRED", R.string.registro_error_fecha_vacia));
        }

        final LocalDate dateValue;
        try {
            dateValue = LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            return ValidationResult.error(messageForCode(context, "BIRTH_DATE_INVALID", R.string.registro_error_fecha_vacia));
        }

        return validateBirthDate(context, dateValue);
    }

    /**
     * Valida una fecha de nacimiento ya parseada comprobando mayoría de edad y fechas futuras.
     *
     * @param context contexto para mensajes localizados.
     * @param value fecha de nacimiento a comprobar.
     * @return {@link ValidationResult} válido cuando cumple las restricciones de edad.
     */
    @NonNull
    public static ValidationResult<String> validateBirthDate(
            @NonNull Context context,
            @NonNull LocalDate value
    ) {
        LocalDate today = LocalDate.now();
        if (value.isAfter(today)) {
            return ValidationResult.error(messageForCode(context, "BIRTH_DATE_IN_FUTURE", R.string.profile_error_birthdate_min_age));
        }

        LocalDate minAdultDate = today.minusYears(18);
        if (value.isAfter(minAdultDate)) {
            return ValidationResult.error(messageForCode(context, "AGE_RESTRICTION_NOT_MET", R.string.profile_error_birthdate_min_age));
        }
        return ValidationResult.ok(value.toString());
    }

    /**
     * Valida el código numérico de recuperación de contraseña.
     *
     * @param context contexto para localizar mensajes.
     * @param rawValue código introducido por el usuario.
     * @return {@link ValidationResult} con el código o un error si no tiene seis dígitos numéricos.
     */
    @NonNull
    public static ValidationResult<String> validateRecoveryCode(
            @NonNull Context context,
            @Nullable String rawValue
    ) {
        String value = safeTrim(rawValue);
        if (!StringUtils.hasText(value)) {
            return ValidationResult.error(messageForCode(context, "CODE_REQUIRED", R.string.forgot_error_codigo_vacio));
        }
        if (value.length() != 6) {
            return ValidationResult.error(messageForCode(context, "CODE_INVALID_LENGTH", R.string.validation_recovery_code_invalid_length));
        }
        if (!value.matches("^\\d{6}$")) {
            return ValidationResult.error(messageForCode(context, "CODE_MUST_BE_NUMERIC", R.string.validation_recovery_code_must_be_numeric));
        }
        return ValidationResult.ok(value);
    }

    /**
     * Valida la altura del perfil dentro del rango permitido por cliente y backend.
     *
     * @param context contexto usado para resolver errores.
     * @param rawValue altura en centímetros.
     * @return {@link ValidationResult} con la altura o un error si queda fuera de rango.
     */
    @NonNull
    public static ValidationResult<Integer> validateHeight(
            @NonNull Context context,
            @Nullable Integer rawValue
    ) {
        if (rawValue == null) return ValidationResult.ok(null);
        if (rawValue < 50 || rawValue > 300) {
            return ValidationResult.error(messageForCode(context, "HEIGHT_OUT_OF_RANGE", R.string.validation_height_out_of_range));
        }
        return ValidationResult.ok(rawValue);
    }

    /**
     * Valida el peso del perfil dentro del rango permitido.
     *
     * @param context contexto para mensajes localizados.
     * @param rawValue peso en kilogramos.
     * @return {@link ValidationResult} con el peso o un error si queda fuera de rango.
     */
    @NonNull
    public static ValidationResult<Double> validateWeight(
            @NonNull Context context,
            @Nullable Double rawValue
    ) {
        if (rawValue == null) return ValidationResult.ok(null);
        if (rawValue < 20.0 || rawValue > 300.0) {
            return ValidationResult.error(messageForCode(context, "WEIGHT_OUT_OF_RANGE", R.string.validation_weight_out_of_range));
        }
        return ValidationResult.ok(rawValue);
    }

    /**
     * Compara dos textos tras aplicar el mismo trim defensivo usado por el resto de validaciones.
     *
     * @param left texto izquierdo.
     * @param right texto derecho.
     * @return {@code true} si ambos valores son equivalentes después del saneado básico.
     */
    public static boolean sameText(@Nullable String left, @Nullable String right) {
        String safeLeft = safeTrim(left);
        String safeRight = safeTrim(right);
        return safeLeft.equals(safeRight);
    }

    /**
     * Ejecuta la moderación textual específica del campo validado.
     *
     * @param context contexto para acceder a assets y mensajes localizados.
     * @param value texto ya validado sintácticamente.
     * @param field tipo de campo moderado para elegir reglas y mensajes.
     * @return {@link ValidationResult} válido o un error de moderación.
     */
    @NonNull
    private static ValidationResult<String> validateTextModeration(
            @NonNull Context context,
            @NonNull String value,
            @NonNull ModerationField field
    ) {
        if (!TEXT_MODERATION_ENABLED) {
            return ValidationResult.ok(value);
        }

        try {
            if (field == ModerationField.USERNAME) {
                String reservedMatch = matchReservedUsername(value);
                if (StringUtils.hasText(reservedMatch)) {
                    return ValidationResult.error(messageForCode(
                            context,
                            "USERNAME_INAPPROPRIATE_OR_NOT_ALLOWED",
                            R.string.validation_username_inappropriate_or_not_allowed
                    ));
                }

                String dictionaryMatch = matchUsernameDictionary(context, value);
                if (StringUtils.hasText(dictionaryMatch)) {
                    return ValidationResult.error(messageForCode(
                            context,
                            "USERNAME_INAPPROPRIATE_OR_NOT_ALLOWED",
                            R.string.validation_username_inappropriate_or_not_allowed
                    ));
                }

                return ValidationResult.ok(value);
            }

            String dictionaryMatch = matchRealNameDictionary(context, value);
            if (StringUtils.hasText(dictionaryMatch)) {
                return ValidationResult.error(messageForCode(
                        context,
                        "REAL_NAME_INAPPROPRIATE_OR_NOT_ALLOWED",
                        R.string.validation_real_name_inappropriate_or_not_allowed
                ));
            }

            return ValidationResult.ok(value);
        } catch (DictionaryLoadException e) {
            if (TEXT_MODERATION_FAIL_OPEN) {
                return ValidationResult.ok(value);
            }
            return ValidationResult.error(messageForCode(
                    context,
                    "CONTENT_VALIDATION_UNAVAILABLE",
                    R.string.validation_content_unavailable
            ));
        }
    }

    /**
     * Busca coincidencias con tokens reservados o de alto riesgo en un nombre de usuario.
     *
     * @param text username original o parcial.
     * @return token conflictivo detectado o {@code null} si no hay coincidencias.
     */
    @Nullable
    private static String matchReservedUsername(@Nullable String text) {
        String username = normalizeUsername(text);
        if (!StringUtils.hasText(username)) return null;

        for (String token : reservedTokens()) {
            if (!StringUtils.hasText(token)) continue;

            if (username.equals(token)) {
                return token;
            }
            if (username.startsWith(token) || username.endsWith(token)) {
                return token;
            }
            if (HIGH_RISK_RESERVED_TOKENS.contains(token) && username.contains(token)) {
                return token;
            }
        }

        return null;
    }

    /**
     * Busca coincidencias del username en los diccionarios cargados desde assets.
     *
     * @param context contexto para cargar el diccionario compartido.
     * @param text username a revisar.
     * @return término conflictivo encontrado o {@code null} si el valor es aceptable.
     * @throws DictionaryLoadException si no se puede cargar el diccionario de moderación.
     */
    @Nullable
    private static String matchUsernameDictionary(@NonNull Context context, @Nullable String text)
            throws DictionaryLoadException {
        String username = normalizeUsername(text);
        if (!StringUtils.hasText(username)) return null;

        DictionaryCache dictionary = loadDictionary(context);
        for (String term : dictionary.usernameTerms) {
            if (username.equals(term)) {
                return term;
            }

            int start = username.indexOf(term);
            while (start != -1) {
                int end = start + term.length();
                String left = username.substring(0, start);
                String right = username.substring(end);

                boolean leftOk = left.isEmpty() || isDigitsOnly(left);
                boolean rightOk = right.isEmpty() || isDigitsOnly(right);
                if (leftOk && rightOk) {
                    return term;
                }

                start = username.indexOf(term, start + 1);
            }
        }

        return null;
    }

    /**
     * Divide un nombre real normalizado en tokens alfabéticos para la moderación por palabras.
     *
     * @param text nombre a tokenizar.
     * @return array de tokens útiles; vacío si no hay contenido.
     */
    @NonNull
    private static String[] tokenizeRealName(@Nullable String text) {
        String normalized = normalizePhrase(text);
        if (!StringUtils.hasText(normalized)) return new String[0];
        return REAL_NAME_TOKEN_SPLIT_RE.split(normalized);
    }

    /**
     * Comprueba si el nombre real coincide con frases o términos prohibidos del diccionario.
     *
     * @param context contexto para cargar los assets de moderación.
     * @param text nombre real ya validado sintácticamente.
     * @return término o frase conflictiva, o {@code null} si no hay match.
     * @throws DictionaryLoadException si falla la carga del diccionario.
     */
    @Nullable
    private static String matchRealNameDictionary(@NonNull Context context, @Nullable String text)
            throws DictionaryLoadException {
        String normalized = normalizePhrase(text);
        if (!StringUtils.hasText(normalized)) return null;

        DictionaryCache dictionary = loadDictionary(context);

        if (dictionary.phraseTerms.contains(normalized)) {
            return normalized;
        }

        String[] tokens = tokenizeRealName(normalized);
        for (String token : tokens) {
            if (dictionary.singleTerms.contains(token)) {
                return token;
            }
        }

        return null;
    }

    /**
     * Carga y cachea los diccionarios de moderación configurados en assets.
     *
     * @param context contexto desde el que acceder a {@link AssetManager}.
     * @return caché inmutable con términos simples, frases y términos para username.
     * @throws DictionaryLoadException si falta algún fichero o la configuración es inválida.
     */
    @NonNull
    private static DictionaryCache loadDictionary(@NonNull Context context) throws DictionaryLoadException {
        DictionaryCache cached = cachedDictionary;
        if (cached != null) return cached;

        synchronized (AppInputValidator.class) {
            if (cachedDictionary != null) {
                return cachedDictionary;
            }

            AssetManager assetManager = context.getApplicationContext().getAssets();
            Set<String> ignoredPhrases = new HashSet<>();
            Set<String> ignoredUsernames = new HashSet<>();
            for (String token : parseCsv(TEXT_MODERATION_IGNORE_DICTIONARY_TOKENS)) {
                String normalizedPhrase = normalizePhrase(token);
                if (StringUtils.hasText(normalizedPhrase)) {
                    ignoredPhrases.add(normalizedPhrase);
                }
                String normalizedUsername = normalizeUsername(token);
                if (StringUtils.hasText(normalizedUsername)) {
                    ignoredUsernames.add(normalizedUsername);
                }
            }

            Set<String> singleTerms = new LinkedHashSet<>();
            Set<String> phraseTerms = new LinkedHashSet<>();
            Set<String> usernameTerms = new LinkedHashSet<>();

            String[] languages = parseCsv(TEXT_MODERATION_DICTIONARY_LANGS);
            if (languages.length == 0) {
                throw new DictionaryLoadException();
            }

            for (String lang : languages) {
                String filePath = TEXT_MODERATION_DICTIONARY_DIR + "/" + lang.toLowerCase(Locale.ROOT) + ".txt";
                try (InputStream inputStream = assetManager.open(filePath);
                     BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

                    String rawLine;
                    while ((rawLine = reader.readLine()) != null) {
                        String line = rawLine.trim();
                        if (line.isEmpty() || line.startsWith("#")) {
                            continue;
                        }

                        String term = normalizePhrase(line);
                        if (!StringUtils.hasText(term) || ignoredPhrases.contains(term)) {
                            continue;
                        }

                        if (term.contains(" ")) {
                            phraseTerms.add(term);
                        } else if (term.length() >= REAL_NAME_MIN_TERM_LEN) {
                            singleTerms.add(term);
                        }

                        String usernameTerm = normalizeUsername(line);
                        if (StringUtils.hasText(usernameTerm)
                                && usernameTerm.length() >= USERNAME_MIN_TERM_LEN
                                && !ignoredUsernames.contains(usernameTerm)) {
                            usernameTerms.add(usernameTerm);
                        }
                    }
                } catch (IOException e) {
                    throw new DictionaryLoadException("No existe el diccionario para '" + lang + "': " + filePath, e);
                }
            }

            cachedDictionary = new DictionaryCache(singleTerms, phraseTerms, usernameTerms);
            return cachedDictionary;
        }
    }

    /**
     * Aplica trim y compacta espacios para dejar una base común de comparación.
     *
     * @param text texto original.
     * @return texto saneado nunca nulo.
     */
    @NonNull
    private static String normalizeText(@Nullable String text) {
        if (text == null) return "";
        return safeTrim(text).replaceAll("\\s+", " ");
    }

    /**
     * Elimina diacríticos para comparar variantes acentuadas y no acentuadas como equivalentes.
     *
     * @param text texto ya normalizado.
     * @return cadena sin marcas diacríticas.
     */
    @NonNull
    private static String removeAccents(@NonNull String text) {
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFKD);
        return normalized.replaceAll("\\p{M}+", "");
    }

    /**
     * Normaliza un texto libre para comparaciones de frases del diccionario.
     *
     * @param text texto original.
     * @return frase en minúsculas, sin acentos y con espacios compactados.
     */
    @NonNull
    private static String normalizePhrase(@Nullable String text) {
        String normalized = normalizeText(text).toLowerCase(Locale.ROOT);
        normalized = removeAccents(normalized);
        normalized = MULTISPACE_RE.matcher(normalized).replaceAll(" ");
        return normalized.trim();
    }

    /**
     * Normaliza un username aplicando también sustituciones de leetspeak y eliminación de símbolos.
     *
     * @param text username original.
     * @return username canónico usado por la moderación de {@link ModerationField#USERNAME}.
     */
    @NonNull
    private static String normalizeUsername(@Nullable String text) {
        String normalized = normalizePhrase(text);
        normalized = normalized
                .replace('0', 'o')
                .replace('1', 'i')
                .replace('3', 'e')
                .replace('4', 'a')
                .replace('5', 's')
                .replace('7', 't')
                .replace('$', 's')
                .replace('@', 'a')
                .replace('!', 'i');

        StringBuilder builder = new StringBuilder(normalized.length());
        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            if (Character.isLetterOrDigit(ch)) {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    /**
     * Convierte una lista CSV en un array de tokens útiles.
     *
     * @param value cadena CSV opcional.
     * @return array sin vacíos ni espacios sobrantes.
     */
    @NonNull
    private static String[] parseCsv(@Nullable String value) {
        if (!StringUtils.hasText(value)) return new String[0];
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toArray(String[]::new);
    }

    /**
     * Genera el conjunto de tokens reservados de username ya normalizados.
     *
     * @return conjunto ordenado listo para las comprobaciones de reserva.
     */
    @NonNull
    private static Set<String> reservedTokens() {
        Set<String> result = new LinkedHashSet<>();
        for (String token : parseCsv(TEXT_MODERATION_RESERVED_USERNAME_TOKENS)) {
            String normalized = normalizeUsername(token);
            if (StringUtils.hasText(normalized)) {
                result.add(normalized);
            }
        }
        return result;
    }

    /**
     * Comprueba si una cadena está compuesta exclusivamente por dígitos.
     *
     * @param value texto a revisar.
     * @return {@code true} si todos sus caracteres son numéricos.
     */
    private static boolean isDigitsOnly(@NonNull String value) {
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Devuelve una cadena nunca nula tras aplicar trim defensivo.
     *
     * @param value texto opcional.
     * @return cadena recortada o vacía si el valor era {@code null}.
     */
    @NonNull
    private static String safeTrim(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * Busca al menos una letra mayúscula en la contraseña.
     *
     * @param value contraseña a revisar.
     * @return {@code true} si contiene alguna mayúscula.
     */
    private static boolean hasUppercase(@NonNull String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isUpperCase(value.charAt(i))) return true;
        }
        return false;
    }

    /**
     * Busca al menos un dígito en la contraseña.
     *
     * @param value contraseña a revisar.
     * @return {@code true} si contiene algún número.
     */
    private static boolean hasDigit(@NonNull String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isDigit(value.charAt(i))) return true;
        }
        return false;
    }


    /**
     * Resuelve el mensaje final de validación a partir del código de backend y un recurso de fallback.
     *
     * @param context contexto para localizar tanto backend como recurso local.
     * @param errorCode código semántico devuelto por backend.
     * @param fallbackResId recurso usado si no existe localización específica.
     * @return mensaje final listo para la UI.
     */
    @NonNull
    private static String messageForCode(
            @NonNull Context context,
            @NonNull String errorCode,
            int fallbackResId
    ) {
        String localized = BackendErrorLocalizer.localize(context, errorCode, null);
        return StringUtils.hasText(localized)
                ? localized
                : context.getString(fallbackResId);
    }
}
