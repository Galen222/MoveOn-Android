package com.proyecto.moveon.core.validation;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Patterns;

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

    private static final Set<String> HIGH_RISK_RESERVED_TOKENS = unmodifiableSet(
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

        @NonNull
        public static <T> ValidationResult<T> ok(@Nullable T value) {
            return new ValidationResult<>(value, null);
        }

        @NonNull
        public static <T> ValidationResult<T> error(@NonNull String message) {
            return new ValidationResult<>(null, message);
        }

        public boolean isValid() {
            return !StringUtils.hasText(errorMessage);
        }

        @Nullable
        public T getValue() {
            return value;
        }

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

        DictionaryLoadException(@NonNull String message) {
            super(message);
        }
    }

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
        if (!Patterns.EMAIL_ADDRESS.matcher(value).matches()) {
            return ValidationResult.error(messageForCode(context, "EMAIL_FORMAT_INVALID", R.string.registro_error_correo_formato));
        }
        return ValidationResult.ok(value);
    }

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
        if (value.length() < 8) {
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

    public static boolean sameText(@Nullable String left, @Nullable String right) {
        String safeLeft = safeTrim(left);
        String safeRight = safeTrim(right);
        return safeLeft.equals(safeRight);
    }

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

    @NonNull
    private static String[] tokenizeRealName(@Nullable String text) {
        String normalized = normalizePhrase(text);
        if (!StringUtils.hasText(normalized)) return new String[0];
        return REAL_NAME_TOKEN_SPLIT_RE.split(normalized);
    }

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
                throw new DictionaryLoadException("No hay idiomas configurados para moderación");
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

    @NonNull
    private static String normalizeText(@Nullable String text) {
        if (text == null) return "";
        return safeTrim(text).replaceAll("\\s+", " ");
    }

    @NonNull
    private static String removeAccents(@NonNull String text) {
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFKD);
        return normalized.replaceAll("\\p{M}+", "");
    }

    @NonNull
    private static String normalizePhrase(@Nullable String text) {
        String normalized = normalizeText(text).toLowerCase(Locale.ROOT);
        normalized = removeAccents(normalized);
        normalized = MULTISPACE_RE.matcher(normalized).replaceAll(" ");
        return normalized.trim();
    }

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

    @NonNull
    private static String[] parseCsv(@Nullable String value) {
        if (!StringUtils.hasText(value)) return new String[0];
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toArray(String[]::new);
    }

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

    private static boolean isDigitsOnly(@NonNull String value) {
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    @NonNull
    private static String safeTrim(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean hasUppercase(@NonNull String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isUpperCase(value.charAt(i))) return true;
        }
        return false;
    }

    private static boolean hasDigit(@NonNull String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isDigit(value.charAt(i))) return true;
        }
        return false;
    }

    @NonNull
    private static Set<String> unmodifiableSet(@NonNull String... values) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(values)));
    }

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
