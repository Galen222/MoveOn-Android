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

public class RegisterActivity extends AppCompatActivity implements SocialAuthManager.Listener {

    public static final String EXTRA_GOOGLE_ID_TOKEN = "extra_google_id_token";
    public static final String EXTRA_GOOGLE_DISPLAY_NAME = "extra_google_display_name";
    public static final String EXTRA_GOOGLE_AVATAR_URL = "extra_google_avatar_url";
    public static final String EXTRA_GOOGLE_EMAIL = "extra_google_email";
    public static final String EXTRA_OPENED_FROM_LOGIN_SOCIAL = "extra_opened_from_login_social";

    private static final String EULA_VERSION = "1.0";
    private static final int MIN_AGE_YEARS = 18;
    private static final LocalDate DEFAULT_BIRTH_DATE = LocalDate.of(2000, 1, 1);

    private ActivityRegisterBinding binding;
    private AuthViewModel viewModel;
    private SocialAuthManager socialAuthManager;

    @Nullable private SocialGoogleAccount pendingGoogleAccount;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(AppLanguageManager.wrapContext(newBase));
    }

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

    private void restorePendingGoogleAccountFromExtras() {
        String idToken = getIntent().getStringExtra(EXTRA_GOOGLE_ID_TOKEN);
        if (!StringUtils.hasText(idToken)) return;

        pendingGoogleAccount = new SocialGoogleAccount(
                idToken,
                getIntent().getStringExtra(EXTRA_GOOGLE_EMAIL),
                getIntent().getStringExtra(EXTRA_GOOGLE_DISPLAY_NAME),
                getIntent().getStringExtra(EXTRA_GOOGLE_AVATAR_URL)
        );
        applyPendingGoogleAccount(pendingGoogleAccount, true);
        TopSnackbar.warning(binding.getRoot(), getString(R.string.social_google_complete_profile));
    }

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

    private ClickableSpan buildLinkSpan(boolean isTerminos) {
        return new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                if (isTerminos) showTerminosDialog();
                else showPoliticaDialog();
            }

            @Override
            public void updateDrawState(@NonNull TextPaint ds) {
                super.updateDrawState(ds);
                ds.setColor(getColor(R.color.greenPrimary));
                ds.setUnderlineText(true);
            }
        };
    }

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
        binding.btnGoogleRegister.setOnClickListener(v -> attemptSocialRegister(SocialAuthProvider.GOOGLE));

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



    private void showTerminosDialog() {
        showLegalDialog(getString(R.string.registro_eula_titulo_terminos), getString(R.string.registro_eula_contenido_terminos));
    }

    private void showPoliticaDialog() {
        showLegalDialog(getString(R.string.registro_eula_titulo_politica), getString(R.string.registro_eula_contenido_politica));
    }

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

    private long getDefaultBirthDateSelectionMillis(@NonNull LocalDate maxAllowedDate) {
        LocalDate safeDefaultDate = DEFAULT_BIRTH_DATE.isAfter(maxAllowedDate) ? maxAllowedDate : DEFAULT_BIRTH_DATE;
        return safeDefaultDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
    }

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

    private void attemptSocialRegister(@NonNull String provider) {
        clearErrors();
        if (!SocialAuthProvider.GOOGLE.equals(provider)) {
            TopSnackbar.error(binding.getRoot(), getString(R.string.social_google_generic_error));
            return;
        }
        setLoading(true);
        showGoogleLoading(true, false, pendingGoogleAccount,
                R.string.social_google_loading_title,
                pendingGoogleAccount == null
                        ? R.string.social_google_loading_message_register
                        : R.string.social_google_loading_message_register_switch);
        socialAuthManager.signInWithGoogle();
    }

    private void attemptCompleteGoogleRegister() {
        clearErrors();
        if (pendingGoogleAccount == null) {
            TopSnackbar.error(binding.getRoot(), getString(R.string.social_google_generic_error));
            return;
        }
        SocialRegisterInput input = buildSocialRegisterInput(SocialAuthProvider.GOOGLE, pendingGoogleAccount.idToken);
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

    private boolean validatePendingSocialFields() {
        AppInputValidator.ValidationResult<String> nombreUsuarioResult = AppInputValidator.validateUsername(this, StringUtils.textOf(binding.etUsuario.getText()), true);
        AppInputValidator.ValidationResult<String> fechaNacimientoResult = AppInputValidator.validateBirthDate(this, StringUtils.textOf(binding.etFechaNacimiento.getText()), true);
        boolean valid = true;
        if (!nombreUsuarioResult.isValid()) { binding.tilUsuario.setError(nombreUsuarioResult.getErrorMessage()); valid = false; }
        if (!fechaNacimientoResult.isValid()) { binding.tilFechaNacimiento.setError(fechaNacimientoResult.getErrorMessage()); valid = false; }
        if (!binding.cbEula.isChecked()) { binding.tvEulaError.setVisibility(View.VISIBLE); valid = false; }
        return valid;
    }

    @Nullable
    private SocialRegisterInput buildSocialRegisterInput(@NonNull String provider, @NonNull String token) {
        if (!validatePendingSocialFields()) return null;
        String fechaAceptacion = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC).format(Instant.now());
        return new SocialRegisterInput(
                provider,
                token,
                StringUtils.textOf(binding.etUsuario.getText()),
                StringUtils.textOf(binding.etFechaNacimiento.getText()),
                true,
                fechaAceptacion,
                EULA_VERSION
        );
    }

    private void applyPendingGoogleAccount(@NonNull SocialGoogleAccount account, boolean announce) {
        pendingGoogleAccount = account;
        renderSocialMode(account, true);
        suggestUsernameFromGoogle(account, announce);
        showGoogleLoading(false, false, null, 0, 0);
        setLoading(false);
    }

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

    private void showUsernameAlreadyInUseFeedback() {
        binding.tilUsuario.setHelperText(null);
        binding.tilUsuario.setError(getString(R.string.backend_error_username_already_in_use));
        binding.etUsuario.requestFocus();
        TopSnackbar.error(binding.getRoot(), getString(R.string.registro_username_already_exists_choose_another));
    }

    private void showEmailAlreadyInUseFeedback(boolean socialFlow) {
        if (!socialFlow) {
            binding.tilUsuarioCorreo.setError(getString(R.string.backend_error_email_already_in_use));
            binding.etUsuarioCorreo.requestFocus();
        }
        TopSnackbar.error(binding.getRoot(), getString(R.string.social_google_email_already_registered));
    }

    private boolean isUsernameAlreadyInUse(@NonNull ApiError error) {
        if ("USERNAME_ALREADY_IN_USE".equals(error.getErrorCode())) {
            return true;
        }
        String usernameFieldMessage = error.firstFieldMessage("nombre_usuario", "usuario", "nombreUsuario");
        return matchesLocalizedConflict(usernameFieldMessage, R.string.backend_error_username_already_in_use)
                || matchesLocalizedConflict(error.getMessage(), R.string.backend_error_username_already_in_use);
    }

    private boolean isEmailAlreadyInUse(@NonNull ApiError error) {
        if ("EMAIL_ALREADY_IN_USE".equals(error.getErrorCode())) {
            return true;
        }
        String emailFieldMessage = error.firstFieldMessage("email", "correo");
        return matchesLocalizedConflict(emailFieldMessage, R.string.backend_error_email_already_in_use)
                || matchesLocalizedConflict(error.getMessage(), R.string.backend_error_email_already_in_use);
    }

    private boolean matchesLocalizedConflict(@Nullable String actualMessage, @StringRes int expectedRes) {
        return normalizeForComparison(actualMessage)
                .equals(normalizeForComparison(getString(expectedRes)));
    }

    @NonNull
    private String normalizeForComparison(@Nullable String value) {
        if (!StringUtils.hasText(value)) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replaceAll("[^A-Za-z0-9]", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }

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

    private void suggestUsernameFromGoogle(@Nullable SocialGoogleAccount account, boolean announce) {
        if (account == null) return;
        String suggested = buildSuggestedUsername(account.displayName);
        binding.etUsuario.setText(suggested);
        binding.etUsuario.setSelection(suggested.length());
        binding.tilUsuario.setError(null);
        binding.tilUsuario.setHelperText(getString(R.string.social_google_username_helper));
        if (announce) {
            TopSnackbar.successLong(binding.getRoot(), getString(R.string.social_google_username_prefilled));
        }
    }

    @NonNull
    private String buildSuggestedUsername(@Nullable String displayName) {
        String base = normalizeUsernameBase(displayName);
        if (base.length() > 46) {
            base = base.substring(0, 46);
        }
        int digits = ThreadLocalRandom.current().nextInt(1000, 10000);
        return base + digits;
    }

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

    private void clearErrors() {
        binding.tilUsuario.setError(null);
        binding.tilUsuarioCorreo.setError(null);
        binding.tilFechaNacimiento.setError(null);
        binding.tilPassword.setError(null);
        binding.tilConfirmarPassword.setError(null);
        binding.tvEulaError.setVisibility(View.GONE);
    }

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

    @Override
    public void onGoogleAccountReady(@NonNull SocialGoogleAccount account, boolean silent) {
        applyPendingGoogleAccount(account, true);
    }

    @Override
    public void onSocialFlowError(@NonNull String message, boolean silent) {
        showGoogleLoading(false, silent, null, 0, 0);
        setLoading(false);
        if (!silent) TopSnackbar.error(binding.getRoot(), message);
    }

    @Override
    public void onSocialFlowCanceled() {
        showGoogleLoading(false, false, null, 0, 0);
        setLoading(false);
        TopSnackbar.warning(binding.getRoot(), getString(R.string.social_auth_canceled));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
