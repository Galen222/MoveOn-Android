package com.proyecto.moveon.ui.auth;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointBackward;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.proyecto.moveon.R;
import com.proyecto.moveon.core.api.ApiError;
import com.proyecto.moveon.core.i18n.AppLanguageManager;
import com.proyecto.moveon.core.theme.ThemeManager;
import com.proyecto.moveon.core.validation.AppInputValidator;
import com.proyecto.moveon.databinding.ActivityRegisterBinding;
import com.proyecto.moveon.domain.auth.RegisterInput;
import com.proyecto.moveon.domain.auth.SocialAuthProvider;
import com.proyecto.moveon.domain.auth.SocialRegisterInput;
import com.proyecto.moveon.ui.common.TopSnackbar;
import com.proyecto.moveon.ui.main.MainActivity;
import com.proyecto.moveon.utils.NavigationUtils;
import com.proyecto.moveon.utils.StringUtils;

import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
/**
 * Pantalla de alta clásica y finalización del registro social.
 *
 * <p>Combina validación local de formulario, aceptación de textos legales y la continuación
 * del flujo de Google cuando el backend todavía necesita completar datos obligatorios.</p>
 */
public class RegisterActivity extends AppCompatActivity implements SocialAuthManager.Listener {

    public static final String EXTRA_GOOGLE_ID_TOKEN = "extra_google_id_token";
    public static final String EXTRA_GOOGLE_DISPLAY_NAME = "extra_google_display_name";
    public static final String EXTRA_GOOGLE_AVATAR_URL = "extra_google_avatar_url";
    public static final String EXTRA_GOOGLE_EMAIL = "extra_google_email";

    private static final String EULA_VERSION = "1.0";
    private static final int MIN_AGE_YEARS = 18;
    private static final LocalDate DEFAULT_BIRTH_DATE = LocalDate.of(2000, 1, 1);

    private ActivityRegisterBinding binding;
    private AuthViewModel viewModel;
    private SocialAuthManager socialAuthManager;

    @Nullable private SocialGoogleAccount pendingGoogleAccount;

    /**
     * Reenvuelve el contexto base con el idioma activo antes de que Android
     * infle recursos de la pantalla.
     * 
     * @param newBase contexto original recibido por la {@link AppCompatActivity}.
     */
    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(AppLanguageManager.wrapContext(newBase));
    }

    /**
     * Inicializa la pantalla de registro, aplica el tema persistido y conecta
     * el flujo clásico y el social con {@link AuthViewModel}.
     * 
     * <p>También restaura una posible cuenta de Google pendiente enviada desde
     * {@link LoginActivity} para continuar el alta sin repetir el sign-in.</p>
     * 
     * @param savedInstanceState estado restaurado por Android, o {@code null}.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applySavedTheme(this);
        super.onCreate(savedInstanceState);

        viewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        socialAuthManager = new SocialAuthManager(this, this);

        setupEulaCheckbox();
        setupListeners();
        observeViewModel();
        restorePendingGoogleAccountFromExtras();
        renderSocialMode(null, false);
    }

    /**
     * Reconstruye una cuenta de Google pendiente a partir de los extras del
     * {@link android.content.Intent} cuando la pantalla se abre para completar
     * un registro social interrumpido.
     */
    private void restorePendingGoogleAccountFromExtras() {
        String idToken = getIntent().getStringExtra(EXTRA_GOOGLE_ID_TOKEN);
        if (!StringUtils.hasText(idToken)) return;

        pendingGoogleAccount = new SocialGoogleAccount(
                idToken,
                getIntent().getStringExtra(EXTRA_GOOGLE_EMAIL),
                getIntent().getStringExtra(EXTRA_GOOGLE_DISPLAY_NAME),
                getIntent().getStringExtra(EXTRA_GOOGLE_AVATAR_URL)
        );
        applyPendingGoogleAccount(pendingGoogleAccount);
        TopSnackbar.warning(binding.getRoot(), getString(R.string.social_google_complete_profile));
    }

    /**
     * Prepara el texto legal con spans clicables y sincroniza los mensajes de
     * error del consentimiento con la interacción del usuario.
     */
    private void setupEulaCheckbox() {
        String terminos = getString(R.string.registro_eula_link_terminos);
        String politica = getString(R.string.registro_eula_link_politica);
        String plantilla = getString(R.string.registro_eula_texto, terminos, politica);

        SpannableString spannable = new SpannableString(plantilla);

        int startTerminos = plantilla.indexOf(terminos);
        int endTerminos = startTerminos + terminos.length();
        spannable.setSpan(buildLinkSpan(true), startTerminos, endTerminos, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        int startPolitica = plantilla.indexOf(politica);
        int endPolitica = startPolitica + politica.length();
        spannable.setSpan(buildLinkSpan(false), startPolitica, endPolitica, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        binding.tvEulaText.setText(spannable);
        binding.tvEulaText.setMovementMethod(LinkMovementMethod.getInstance());
        binding.tvEulaText.setHighlightColor(getColor(android.R.color.transparent));

        binding.cbEula.setOnCheckedChangeListener((buttonView, checked) -> {
            if (checked) binding.tvEulaError.setVisibility(View.GONE);
        });

        binding.etUsuario.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) {
                if (pendingGoogleAccount != null) {
                    binding.tilUsuario.setHelperText(getString(R.string.social_google_username_helper));
                }
            }
        });
    }

    /**
     * Crea el span clicable que abre el diálogo legal correspondiente dentro
     * del texto del consentimiento.
     * 
     * @param isTerminos {@code true} para abrir términos y condiciones;
     * {@code false} para abrir la política.
     * @return span configurado con color, subrayado y acción de apertura.
     */
    private ClickableSpan buildLinkSpan(boolean isTerminos) {
        return new ClickableSpan() {
            /**
             * Abre el diálogo legal asociado al tramo pulsado dentro del texto de consentimiento.
             *
             * @param widget vista de texto que recibió el clic sobre el span.
             */
            @Override
            public void onClick(@NonNull View widget) {
                if (isTerminos) showTerminosDialog();
                else showPoliticaDialog();
            }

            /**
             * Aplica el estilo visual del enlace legal para mantener color corporativo y subrayado.
             *
             * @param ds objeto de pintura que define cómo debe renderizarse el span.
             */
            @Override
            public void updateDrawState(@NonNull TextPaint ds) {
                super.updateDrawState(ds);
                ds.setColor(getColor(R.color.greenPrimary));
                ds.setUnderlineText(true);
            }
        };
    }

    /**
     * Enlaza todos los controles interactivos de la pantalla, incluyendo el
     * alta clásica, el flujo social y la limpieza de errores al recuperar foco.
     */
    private void setupListeners() {
        binding.btnIniciarSesion.setOnClickListener(v -> finish());
        binding.etFechaNacimiento.setOnClickListener(v -> showDatePicker());
        binding.btnCrearCuenta.setOnClickListener(v -> {
            if (pendingGoogleAccount != null) {
                attemptCompleteGoogleRegister();
            } else {
                attemptRegister();
            }
        });
        binding.btnGoogleRegister.setOnClickListener(v -> attemptGoogleRegister());

        binding.etUsuario.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) return;
            binding.tilUsuario.setError(null);
            if (pendingGoogleAccount != null) {
                binding.tilUsuario.setHelperText(getString(R.string.social_google_username_helper));
            }
        });
        binding.etUsuarioCorreo.setOnFocusChangeListener((v, hasFocus) -> { if (hasFocus) binding.tilUsuarioCorreo.setError(null); });
        binding.etFechaNacimiento.setOnFocusChangeListener((v, hasFocus) -> { if (hasFocus) binding.tilFechaNacimiento.setError(null); });
        binding.etPassword.setOnFocusChangeListener((v, hasFocus) -> { if (hasFocus) binding.tilPassword.setError(null); });
        binding.etConfirmarPassword.setOnFocusChangeListener((v, hasFocus) -> { if (hasFocus) binding.tilConfirmarPassword.setError(null); });
    }

    /**
     * Observa los estados expuestos por {@link AuthViewModel} para reflejar en
     * la UI los loaders, errores de backend y la navegación tras el auto-login.
     */
    private void observeViewModel() {
        viewModel.getRegisterState().observe(this, state -> {
            if (state == null) return;

            if (state.loading) {
                setLoading(true);
                return;
            }

            if (state.error != null) {
                showGoogleLoading(false, false, null, 0, 0);
                setLoading(false);

                if (handleRegisterConflict(state.error)) {
                    viewModel.resetRegisterState();
                    return;
                }

                applyBackendErrors(state.error);

                if (!state.error.hasFieldErrors()) {
                    TopSnackbar.error(binding.getRoot(), state.error.getMessage());
                }
                viewModel.resetRegisterState();
            }
        });

        viewModel.getLoginState().observe(this, state -> {
            if (state == null) return;

            if (state.data != null) {
                showGoogleLoading(false, false, null, 0, 0);
                Toast.makeText(this, getString(R.string.login_bienvenido, state.data.nombreUsuario), Toast.LENGTH_SHORT).show();
                viewModel.resetLoginState();

                NavigationUtils.goToActivityAndClearTask(this, MainActivity.class);
            }
        });
    }



    /**
     * Abre el diálogo de términos y condiciones usando los recursos localizados.
     */
    private void showTerminosDialog() {
        showLegalDialog(getString(R.string.registro_eula_titulo_terminos), getString(R.string.registro_eula_contenido_terminos));
    }

    /**
     * Abre el diálogo de política de privacidad usando los recursos localizados.
     */
    private void showPoliticaDialog() {
        showLegalDialog(getString(R.string.registro_eula_titulo_politica), getString(R.string.registro_eula_contenido_politica));
    }

    /**
     * Muestra un diálogo legal reutilizable y, si el usuario acepta desde él,
     * marca automáticamente el consentimiento en el checkbox principal.
     * 
     * @param title título del documento legal mostrado.
     * @param content contenido completo que se pinta en el mensaje del diálogo.
     */
    private void showLegalDialog(String title, String content) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(content)
                .setPositiveButton(R.string.registro_eula_btn_aceptar, (dialog, which) -> {
                    binding.cbEula.setChecked(true);
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.registro_eula_btn_cerrar, (dialog, which) -> dialog.dismiss())
                .show();
    }

    /**
     * Abre un {@link MaterialDatePicker} limitado a fechas válidas para el
     * registro y reutiliza, si existe, la fecha ya escrita por el usuario.
     */
    private void showDatePicker() {
        LocalDate maxAllowedDate = LocalDate.now(ZoneOffset.UTC).minusYears(MIN_AGE_YEARS);
        long maxAllowedMillis = maxAllowedDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        long minMillis = LocalDate.of(1900, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();

        CalendarConstraints constraints = new CalendarConstraints.Builder()
                .setStart(minMillis)
                .setEnd(maxAllowedMillis)
                .setValidator(DateValidatorPointBackward.before(maxAllowedMillis))
                .build();

        MaterialDatePicker.Builder<Long> builder = MaterialDatePicker.Builder.datePicker()
                .setTitleText(R.string.registro_hint_fecha)
                .setCalendarConstraints(constraints);

        String currentText = StringUtils.textOf(binding.etFechaNacimiento.getText());
        if (!currentText.isEmpty()) {
            try {
                LocalDate parsed = LocalDate.parse(currentText);
                long millis = parsed.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
                builder.setSelection(millis);
            } catch (Exception ignored) {
                builder.setSelection(getDefaultBirthDateSelectionMillis(maxAllowedDate));
            }
        } else {
            builder.setSelection(getDefaultBirthDateSelectionMillis(maxAllowedDate));
        }

        MaterialDatePicker<Long> picker = builder.build();
        picker.addOnPositiveButtonClickListener(selection -> {
            LocalDate selectedDate = Instant.ofEpochMilli(selection).atZone(ZoneOffset.UTC).toLocalDate();
            binding.etFechaNacimiento.setText(selectedDate.toString());
            binding.tilFechaNacimiento.setError(null);
        });
        picker.show(getSupportFragmentManager(), "registro_date_picker");
    }

    /**
     * Calcula la selección inicial segura del date picker respetando la edad
     * mínima exigida por la pantalla.
     * 
     * @param maxAllowedDate fecha máxima permitida tras aplicar la mayoría de edad.
     * @return instante en milisegundos UTC que se usará como selección inicial.
     */
    private long getDefaultBirthDateSelectionMillis(@NonNull LocalDate maxAllowedDate) {
        LocalDate safeDefaultDate = DEFAULT_BIRTH_DATE.isAfter(maxAllowedDate) ? maxAllowedDate : DEFAULT_BIRTH_DATE;
        return safeDefaultDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
    }

    /**
     * Valida todos los campos del registro clásico, pinta los errores inline y,
     * si todo es correcto, construye el {@link RegisterInput} para registrar y
     * lanzar el auto-login posterior.
     */
    private void attemptRegister() {
        clearErrors();

        AppInputValidator.ValidationResult<String> nombreUsuarioResult = AppInputValidator.validateUsername(this, StringUtils.textOf(binding.etUsuario.getText()), true);
        AppInputValidator.ValidationResult<String> correoResult = AppInputValidator.validateEmail(this, StringUtils.textOf(binding.etUsuarioCorreo.getText()), true);
        AppInputValidator.ValidationResult<String> fechaNacimientoResult = AppInputValidator.validateBirthDate(this, StringUtils.textOf(binding.etFechaNacimiento.getText()), true);
        AppInputValidator.ValidationResult<String> passwordResult = AppInputValidator.validatePassword(this, StringUtils.textOf(binding.etPassword.getText()), true, false);
        AppInputValidator.ValidationResult<String> confirmarPasswordResult = AppInputValidator.validatePasswordConfirmation(this, passwordResult.getValue(), StringUtils.textOf(binding.etConfirmarPassword.getText()), R.string.registro_error_passwords_distintas);

        boolean eulaAceptado = binding.cbEula.isChecked();
        boolean valid = true;

        if (!nombreUsuarioResult.isValid()) { binding.tilUsuario.setError(nombreUsuarioResult.getErrorMessage()); valid = false; }
        if (!correoResult.isValid()) { binding.tilUsuarioCorreo.setError(correoResult.getErrorMessage()); valid = false; }
        if (!fechaNacimientoResult.isValid()) { binding.tilFechaNacimiento.setError(fechaNacimientoResult.getErrorMessage()); valid = false; }
        if (!passwordResult.isValid()) { binding.tilPassword.setError(passwordResult.getErrorMessage()); valid = false; }
        if (!confirmarPasswordResult.isValid()) { binding.tilConfirmarPassword.setError(confirmarPasswordResult.getErrorMessage()); valid = false; }
        if (!eulaAceptado) { binding.tvEulaError.setVisibility(View.VISIBLE); valid = false; }
        if (!valid) return;

        String fechaAceptacion = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC).format(Instant.now());
        RegisterInput input = new RegisterInput(
                nombreUsuarioResult.getValue(),
                correoResult.getValue(),
                passwordResult.getValue(),
                fechaNacimientoResult.getValue(),
                true,
                fechaAceptacion,
                EULA_VERSION
        );
        viewModel.registerAndAutoLogin(input);
    }

    /**
     * Inicia el flujo de autenticación con Google y muestra la overlay específica
     * mientras se resuelve la cuenta externa.
     */
    private void attemptGoogleRegister() {
        clearErrors();
        setLoading(true);
        showGoogleLoading(true, false, pendingGoogleAccount,
                R.string.social_google_loading_title,
                pendingGoogleAccount == null
                        ? R.string.social_google_loading_message_register
                        : R.string.social_google_loading_message_register_switch);
        socialAuthManager.signInWithGoogle();
    }

    /**
     * Completa el alta después del sign-in con Google usando únicamente los
     * campos que siguen siendo responsabilidad del usuario dentro de la app.
     */
    private void attemptCompleteGoogleRegister() {
        clearErrors();
        if (pendingGoogleAccount == null) {
            TopSnackbar.error(binding.getRoot(), getString(R.string.social_google_generic_error));
            return;
        }
        SocialRegisterInput input = buildGoogleRegisterInput(pendingGoogleAccount.idToken);
        if (input == null) {
            TopSnackbar.warning(binding.getRoot(), getString(R.string.social_google_complete_profile));
            return;
        }
        setLoading(true);
        showGoogleLoading(true, false, pendingGoogleAccount,
                R.string.social_google_loading_title,
                R.string.social_google_loading_message_finish);
        viewModel.registerWithSocial(input);
    }

    /**
     * Valida los campos que siguen pendientes en el registro social antes de
     * enviar el alta definitiva al backend.
     * 
     * @return {@code true} si usuario, fecha y aceptación legal son válidos.
     */
    private boolean validatePendingSocialFields() {
        AppInputValidator.ValidationResult<String> nombreUsuarioResult = AppInputValidator.validateUsername(this, StringUtils.textOf(binding.etUsuario.getText()), true);
        AppInputValidator.ValidationResult<String> fechaNacimientoResult = AppInputValidator.validateBirthDate(this, StringUtils.textOf(binding.etFechaNacimiento.getText()), true);
        boolean valid = true;
        if (!nombreUsuarioResult.isValid()) { binding.tilUsuario.setError(nombreUsuarioResult.getErrorMessage()); valid = false; }
        if (!fechaNacimientoResult.isValid()) { binding.tilFechaNacimiento.setError(fechaNacimientoResult.getErrorMessage()); valid = false; }
        if (!binding.cbEula.isChecked()) { binding.tvEulaError.setVisibility(View.VISIBLE); valid = false; }
        return valid;
    }

    /**
     * Construye el payload final del registro social usando los datos locales y
     * el token del proveedor ya resuelto.
     *
     * @param token token de identidad emitido por el proveedor.
     * @return input listo para backend, o {@code null} si aún fallan validaciones locales.
     */
    @Nullable
    private SocialRegisterInput buildGoogleRegisterInput(@NonNull String token) {
        if (!validatePendingSocialFields()) return null;
        String fechaAceptacion = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC).format(Instant.now());
        return new SocialRegisterInput(
                SocialAuthProvider.GOOGLE,
                token,
                StringUtils.textOf(binding.etUsuario.getText()),
                StringUtils.textOf(binding.etFechaNacimiento.getText()),
                true,
                fechaAceptacion,
                EULA_VERSION
        );
    }

    /**
     * Vuelca en la UI la cuenta de Google recuperada, activa el modo social y
     * propone un nombre de usuario inicial con feedback visible.
     *
     * @param account cuenta externa ya autenticada.
     */
    private void applyPendingGoogleAccount(@NonNull SocialGoogleAccount account) {
        pendingGoogleAccount = account;
        renderSocialMode(account, true);
        suggestUsernameFromGoogle(account);
        showGoogleLoading(false, false, null, 0, 0);
        setLoading(false);
    }

    /**
     * Interpreta conflictos de negocio devueltos por el backend para decidir si
     * la pantalla debe mostrar un tratamiento específico en lugar del error genérico.
     * 
     * @param error error devuelto por la operación de registro.
     * @return {@code true} si el conflicto ya se ha tratado de forma específica.
     */
    private boolean handleRegisterConflict(@NonNull ApiError error) {
        boolean socialFlow = pendingGoogleAccount != null;

        SocialRegisterConflictResolver.Resolution resolution =
                SocialRegisterConflictResolver.resolve(error.getErrorCode(), socialFlow);

        if (resolution == SocialRegisterConflictResolver.Resolution.SHOW_USERNAME_TAKEN
                || isUsernameAlreadyInUse(error)) {
            showUsernameAlreadyInUseFeedback();
            return true;
        }

        if (resolution == SocialRegisterConflictResolver.Resolution.SHOW_EMAIL_ALREADY_REGISTERED
                || isEmailAlreadyInUse(error)) {
            showEmailAlreadyInUseFeedback(socialFlow);
            return true;
        }

        return false;
    }

    /**
     * Muestra el conflicto de nombre de usuario en el campo y en el canal de
     * feedback superior para forzar al usuario a elegir otro identificador.
     */
    private void showUsernameAlreadyInUseFeedback() {
        binding.tilUsuario.setHelperText(null);
        binding.tilUsuario.setError(getString(R.string.backend_error_username_already_in_use));
        binding.etUsuario.requestFocus();
        TopSnackbar.error(binding.getRoot(), getString(R.string.registro_username_already_exists_choose_another));
    }

    /**
     * Presenta el conflicto de email adaptando el feedback al flujo actual.
     * 
     * @param socialFlow {@code true} si el conflicto llegó durante el alta social.
     */
    private void showEmailAlreadyInUseFeedback(boolean socialFlow) {
        if (!socialFlow) {
            binding.tilUsuarioCorreo.setError(getString(R.string.backend_error_email_already_in_use));
            binding.etUsuarioCorreo.requestFocus();
        }
        TopSnackbar.error(binding.getRoot(), getString(R.string.social_google_email_already_registered));
    }

    /**
     * Comprueba si el error recibido describe un conflicto de nombre de usuario
     * buscando tanto códigos como mensajes localizados y errores por campo.
     * 
     * @param error error a inspeccionar.
     * @return {@code true} si corresponde a un username ya ocupado.
     */
    private boolean isUsernameAlreadyInUse(@NonNull ApiError error) {
        if ("USERNAME_ALREADY_IN_USE".equals(error.getErrorCode())) {
            return true;
        }
        String usernameFieldMessage = error.firstFieldMessage("nombre_usuario", "usuario", "nombreUsuario");
        return matchesLocalizedConflict(usernameFieldMessage, R.string.backend_error_username_already_in_use)
                || matchesLocalizedConflict(error.getMessage(), R.string.backend_error_username_already_in_use);
    }

    /**
     * Comprueba si el error recibido describe un conflicto de email teniendo en
     * cuenta mensajes globales y errores por campo.
     * 
     * @param error error a inspeccionar.
     * @return {@code true} si el backend indica que el correo ya existe.
     */
    private boolean isEmailAlreadyInUse(@NonNull ApiError error) {
        if ("EMAIL_ALREADY_IN_USE".equals(error.getErrorCode())) {
            return true;
        }
        String emailFieldMessage = error.firstFieldMessage("email", "correo");
        return matchesLocalizedConflict(emailFieldMessage, R.string.backend_error_email_already_in_use)
                || matchesLocalizedConflict(error.getMessage(), R.string.backend_error_email_already_in_use);
    }

    /**
     * Compara un mensaje real con la traducción esperada tras normalizar ambos
     * textos para soportar diferencias de acentos, espacios o puntuación.
     * 
     * @param actualMessage mensaje real recibido del backend.
     * @param expectedRes recurso string que actúa como referencia localizable.
     * @return {@code true} si ambos mensajes representan el mismo conflicto.
     */
    private boolean matchesLocalizedConflict(@Nullable String actualMessage, @StringRes int expectedRes) {
        return normalizeForComparison(actualMessage)
                .equals(normalizeForComparison(getString(expectedRes)));
    }

    /**
     * Normaliza un texto para comparaciones laxas eliminando diacríticos,
     * símbolos y diferencias de mayúsculas.
     *
     * @param value texto original, puede ser {@code null}.
     * @return representación estable apta para comparaciones internas.
     */
    @NonNull
    private String normalizeForComparison(@Nullable String value) {
        if (!StringUtils.hasText(value)) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replaceAll("[^A-Za-z0-9]", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    /**
     * Alterna la pantalla entre registro clásico y finalización de registro
     * social ocultando o mostrando los campos que corresponden.
     * 
     * @param account cuenta social actualmente seleccionada, o {@code null}.
     * @param enabled {@code true} si la UI debe pasar al modo social.
     */
    private void renderSocialMode(@Nullable SocialGoogleAccount account, boolean enabled) {
        binding.cardGoogleSummary.setVisibility(enabled ? View.VISIBLE : View.GONE);
        binding.tilUsuarioCorreo.setVisibility(enabled ? View.GONE : View.VISIBLE);
        binding.tilPassword.setVisibility(enabled ? View.GONE : View.VISIBLE);
        binding.tilConfirmarPassword.setVisibility(enabled ? View.GONE : View.VISIBLE);
        binding.btnCrearCuenta.setText(enabled
                ? getString(R.string.social_google_finish_register)
                : getString(R.string.registro_btn_crear));
        binding.btnGoogleRegister.setText(enabled
                ? getString(R.string.social_google_change_account)
                : getString(R.string.social_google_sign_up));
        binding.tvSubtitle.setText(enabled
                ? getString(R.string.social_google_profile_ready)
                : getString(R.string.registro_subtitulo));
        binding.tilUsuario.setHelperText(enabled ? getString(R.string.social_google_username_helper) : null);

        if (enabled) {
            binding.tvGoogleSummaryName.setText(StringUtils.hasText(account != null ? account.displayName : null)
                    ? account.displayName
                    : getString(R.string.social_google_account_detected));
            binding.tvGoogleSummaryEmail.setText(StringUtils.hasText(account != null ? account.email : null)
                    ? account.email
                    : getString(R.string.social_google_continue));
            renderLoadingAvatar(binding.ivGoogleSummaryAvatar, account);
        }
    }

    /**
     * Genera y aplica una sugerencia de nombre de usuario basada en el perfil
     * de Google para reducir fricción durante el alta.
     *
     * @param account cuenta desde la que se toma el nombre visible.
     */
    private void suggestUsernameFromGoogle(@Nullable SocialGoogleAccount account) {
        if (account == null) return;
        String suggested = buildSuggestedUsername(account.displayName);
        binding.etUsuario.setText(suggested);
        binding.etUsuario.setSelection(suggested.length());
        binding.tilUsuario.setError(null);
        binding.tilUsuario.setHelperText(getString(R.string.social_google_username_helper));
        TopSnackbar.successLong(binding.getRoot(), getString(R.string.social_google_username_prefilled));
    }

    /**
     * Construye una sugerencia de nombre de usuario combinando una base limpia
     * derivada del nombre visible con un sufijo aleatorio.
     *
     * <p>El resultado se usa para precargar el campo de usuario tras integrar una cuenta de
     * {@link SocialGoogleAccount}, minimizando la fricción del alta.</p>
     *
     * @param displayName nombre mostrado por Google, o {@code null}.
     * @return sugerencia final lista para precargar el input de usuario.
     */
    @NonNull
    private String buildSuggestedUsername(@Nullable String displayName) {
        String base = normalizeUsernameBase(displayName);
        if (base.length() > 46) {
            base = base.substring(0, 46);
        }
        int digits = ThreadLocalRandom.current().nextInt(1000, 10000);
        return base + digits;
    }

    /**
     * Reduce el nombre visible a una base compatible con las reglas del nombre
     * de usuario eliminando espacios, símbolos y acentos.
     *
     * @param displayName nombre original obtenido del proveedor.
     * @return base normalizada con una longitud mínima segura.
     */
    @NonNull
    private String normalizeUsernameBase(@Nullable String displayName) {
        String raw = StringUtils.hasText(displayName) ? displayName : "moveon";
        String normalized = Normalizer.normalize(raw, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replaceAll("[^A-Za-z0-9]", "")
                .toLowerCase(Locale.ROOT)
                .trim();
        if (normalized.length() < 5) {
            normalized = (normalized + "moveon");
        }
        if (normalized.length() < 5) {
            normalized = "moveon";
        }
        return normalized;
    }

    /**
     * Reparte los errores de backend entre los campos visibles de la pantalla
     * para que el usuario vea exactamente qué dato debe corregir.
     * 
     * @param err error con posibles mensajes por campo.
     */
    private void applyBackendErrors(ApiError err) {
        String usuarioError = err.firstFieldMessage("nombre_usuario", "usuario", "nombreUsuario");
        String emailError = err.firstFieldMessage("email", "correo");
        String passwordError = err.firstFieldMessage("password");
        String fechaError = err.firstFieldMessage("fecha_nacimiento", "fechaNacimiento");

        if (StringUtils.hasText(usuarioError)) binding.tilUsuario.setError(usuarioError);
        if (StringUtils.hasText(emailError)) binding.tilUsuarioCorreo.setError(emailError);
        if (StringUtils.hasText(passwordError)) binding.tilPassword.setError(passwordError);
        if (StringUtils.hasText(fechaError)) binding.tilFechaNacimiento.setError(fechaError);
    }

    /**
     * Habilita o bloquea los controles de la pantalla mientras hay una petición
     * en vuelo y actualiza el texto principal del CTA según el flujo actual.
     * 
     * @param loading {@code true} si debe bloquearse la interacción.
     */
    private void setLoading(boolean loading) {
        binding.btnCrearCuenta.setEnabled(!loading);
        binding.btnGoogleRegister.setEnabled(!loading);
        binding.btnIniciarSesion.setEnabled(!loading);
        binding.tilUsuario.setEnabled(!loading);
        binding.tilUsuarioCorreo.setEnabled(!loading);
        binding.tilFechaNacimiento.setEnabled(!loading);
        binding.tilPassword.setEnabled(!loading);
        binding.tilConfirmarPassword.setEnabled(!loading);
        binding.cbEula.setEnabled(!loading);
        binding.btnCrearCuenta.setText(loading
                ? getString(R.string.registro_btn_creando)
                : (pendingGoogleAccount != null
                    ? getString(R.string.social_google_finish_register)
                    : getString(R.string.registro_btn_crear)));
    }

    /**
     * Limpia todos los errores inline y el aviso de EULA antes de iniciar una
     * nueva validación o un nuevo intento de envío.
     */
    private void clearErrors() {
        binding.tilUsuario.setError(null);
        binding.tilUsuarioCorreo.setError(null);
        binding.tilFechaNacimiento.setError(null);
        binding.tilPassword.setError(null);
        binding.tilConfirmarPassword.setError(null);
        binding.tvEulaError.setVisibility(View.GONE);
    }

    /**
     * Muestra u oculta la overlay de progreso del flujo de Google.
     *
     * @param visible {@code true} para hacer visible la overlay.
     * @param silent {@code true} cuando la animación debe ser más discreta por tratarse de un flujo silencioso.
     * @param account cuenta seleccionada para renderizar avatar, si existe.
     * @param titleRes recurso de texto para el título mostrado en la tarjeta.
     * @param messageRes recurso de texto para el mensaje descriptivo.
     */
    private void showGoogleLoading(boolean visible,
                                   boolean silent,
                                   @Nullable SocialGoogleAccount account,
                                   @StringRes int titleRes,
                                   @StringRes int messageRes) {
        if (visible) {
            if (titleRes != 0) binding.tvGoogleLoadingTitle.setText(titleRes);
            if (messageRes != 0) binding.tvGoogleLoadingMessage.setText(messageRes);
            renderLoadingAvatar(binding.ivGoogleLoadingAvatar, account);
            binding.overlayGoogleLoading.setVisibility(View.VISIBLE);
            binding.overlayGoogleLoading.setAlpha(0f);
            binding.cardGoogleLoading.setScaleX(0.96f);
            binding.cardGoogleLoading.setScaleY(0.96f);
            binding.overlayGoogleLoading.animate().alpha(1f).setDuration(silent ? 180 : 220).start();
            binding.cardGoogleLoading.animate().scaleX(1f).scaleY(1f).setDuration(silent ? 180 : 220).start();
            return;
        }
        if (binding.overlayGoogleLoading.getVisibility() != View.VISIBLE) {
            return;
        }
        binding.overlayGoogleLoading.animate().alpha(0f).setDuration(160).withEndAction(() -> {
            binding.overlayGoogleLoading.setVisibility(View.GONE);
            binding.overlayGoogleLoading.setAlpha(1f);
        }).start();
    }

    /**
     * Pinta el avatar de Google en las overlays de carga o, si no existe URL,
     * deja el icono por defecto del proveedor.
     * 
     * @param imageView destino en el que se renderiza el avatar.
     * @param account cuenta social de la que se toma la foto, o {@code null}.
     */
    private void renderLoadingAvatar(@NonNull ImageView imageView, @Nullable SocialGoogleAccount account) {
        String avatarUrl = account != null ? account.avatarUrl : null;
        if (StringUtils.hasText(avatarUrl)) {
            Glide.with(this)
                    .load(avatarUrl)
                    .placeholder(R.drawable.ic_google)
                    .error(R.drawable.ic_google)
                    .circleCrop()
                    .into(imageView);
            return;
        }
        imageView.setImageResource(R.drawable.ic_google);
    }

    /**
     * Callback del {@link SocialAuthManager} cuando Google devuelve una cuenta válida.
     * 
     * @param account cuenta autenticada y lista para completar el registro.
     * @param silent indica si el flujo se realizó en modo silencioso.
     */
    @Override
    public void onGoogleAccountReady(@NonNull SocialGoogleAccount account, boolean silent) {
        applyPendingGoogleAccount(account);
    }

    /**
     * Callback de error del flujo social. Cierra la overlay y muestra el fallo
     * salvo en reintentos silenciosos.
     * 
     * @param message mensaje visible del error.
     * @param silent {@code true} cuando el fallo no debe notificarse al usuario.
     */
    @Override
    public void onSocialFlowError(@NonNull String message, boolean silent) {
        showGoogleLoading(false, silent, null, 0, 0);
        setLoading(false);
        if (!silent) TopSnackbar.error(binding.getRoot(), message);
    }

    /**
     * Callback invocado cuando el usuario cancela explícitamente el flujo de
     * autenticación social desde el proveedor externo.
     */
    @Override
    public void onSocialFlowCanceled() {
        showGoogleLoading(false, false, null, 0, 0);
        setLoading(false);
    }

    /**
     * Libera la referencia al binding para evitar fugas cuando la actividad se
     * destruye definitivamente.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
