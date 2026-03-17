package com.proyecto.moveon.ui.auth;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import android.app.DatePickerDialog;
import android.content.Context;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.View;
import android.widget.Toast;

import com.proyecto.moveon.R;
import com.proyecto.moveon.core.api.ApiError;
import com.proyecto.moveon.core.i18n.AppLanguageManager;
import com.proyecto.moveon.core.theme.ThemeManager;
import com.proyecto.moveon.databinding.ActivityRegisterBinding;
import com.proyecto.moveon.core.validation.AppInputValidator;
import com.proyecto.moveon.domain.auth.RegisterInput;
import com.proyecto.moveon.ui.common.TopSnackbar;
import com.proyecto.moveon.ui.main.MainActivity;
import com.proyecto.moveon.utils.NavigationUtils;
import com.proyecto.moveon.utils.StringUtils;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Locale;

public class RegisterActivity extends AppCompatActivity {

    private static final String EULA_VERSION = "1.0";
    private static final int MIN_AGE_YEARS = 18;

    private ActivityRegisterBinding binding;
    private AuthViewModel viewModel;

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

        setupEulaCheckbox();
        setupListeners();
        observeViewModel();
    }

    // ── UI setup ──────────────────────────────────────────────────────────────

    private void setupEulaCheckbox() {
        String terminos  = getString(R.string.registro_eula_link_terminos);
        String politica  = getString(R.string.registro_eula_link_politica);
        String plantilla = getString(R.string.registro_eula_texto, terminos, politica);

        SpannableString spannable = new SpannableString(plantilla);

        int startTerminos = plantilla.indexOf(terminos);
        int endTerminos   = startTerminos + terminos.length();
        spannable.setSpan(buildLinkSpan(true), startTerminos, endTerminos,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        int startPolitica = plantilla.indexOf(politica);
        int endPolitica   = startPolitica + politica.length();
        spannable.setSpan(buildLinkSpan(false), startPolitica, endPolitica,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        binding.tvEulaText.setText(spannable);
        binding.tvEulaText.setMovementMethod(LinkMovementMethod.getInstance());
        binding.tvEulaText.setHighlightColor(getColor(android.R.color.transparent));

        binding.cbEula.setOnCheckedChangeListener((btn, checked) -> {
            if (checked) binding.tvEulaError.setVisibility(View.GONE);
        });
    }

    private ClickableSpan buildLinkSpan(boolean isTerminos) {
        return new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                if (isTerminos) showTerminosDialog();
                else            showPoliticaDialog();
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
        binding.btnCrearCuenta.setOnClickListener(v -> attemptRegister());

        binding.etUsuario.setOnFocusChangeListener(
                (v, f) -> { if (f) binding.tilUsuario.setError(null); });
        binding.etUsuarioCorreo.setOnFocusChangeListener(
                (v, f) -> { if (f) binding.tilUsuarioCorreo.setError(null); });
        binding.etFechaNacimiento.setOnFocusChangeListener(
                (v, f) -> { if (f) binding.tilFechaNacimiento.setError(null); });
        binding.etPassword.setOnFocusChangeListener(
                (v, f) -> { if (f) binding.tilPassword.setError(null); });
        binding.etConfirmarPassword.setOnFocusChangeListener(
                (v, f) -> { if (f) binding.tilConfirmarPassword.setError(null); });
    }

    // ── Observers ─────────────────────────────────────────────────────────────

    private void observeViewModel() {
        viewModel.getRegisterState().observe(this, state -> {
            if (state == null) return;

            if (state.loading) {
                setLoading(true);
                return;
            }

            if (state.error != null) {
                setLoading(false);
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
                Toast.makeText(
                        this,
                        getString(R.string.login_bienvenido, state.data.nombreUsuario),
                        Toast.LENGTH_SHORT
                ).show();

                viewModel.resetLoginState();
                NavigationUtils.goToActivityAndClearTask(this, MainActivity.class);
            }
        });
    }

    // ── Diálogos legales ──────────────────────────────────────────────────────

    private void showTerminosDialog() {
        showLegalDialog(
                getString(R.string.registro_eula_titulo_terminos),
                getString(R.string.registro_eula_contenido_terminos)
        );
    }

    private void showPoliticaDialog() {
        showLegalDialog(
                getString(R.string.registro_eula_titulo_politica),
                getString(R.string.registro_eula_contenido_politica)
        );
    }

    private void showLegalDialog(String title, String content) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(content)
                .setPositiveButton(R.string.registro_eula_btn_aceptar, (d, w) -> {
                    binding.cbEula.setChecked(true);
                    d.dismiss();
                })
                .setNegativeButton(R.string.registro_eula_btn_cerrar, (d, w) -> d.dismiss())
                .show();
    }

    // ── DatePicker +18 ────────────────────────────────────────────────────────

    private void showDatePicker() {
        Calendar maxDate = Calendar.getInstance();
        maxDate.add(Calendar.YEAR, -MIN_AGE_YEARS);

        int year  = maxDate.get(Calendar.YEAR);
        int month = maxDate.get(Calendar.MONTH);
        int day   = maxDate.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog dialog = new DatePickerDialog(
                this,
                (view, y, m, d) -> {
                    String fecha = String.format(Locale.getDefault(), "%04d-%02d-%02d", y, m + 1, d);
                    binding.etFechaNacimiento.setText(fecha);
                    binding.tilFechaNacimiento.setError(null);
                },
                year, month, day
        );

        dialog.getDatePicker().setMaxDate(maxDate.getTimeInMillis());
        dialog.show();
    }

    // ── Validación y envío ────────────────────────────────────────────────────

    private void attemptRegister() {
        clearErrors();

        AppInputValidator.ValidationResult<String> nombreUsuarioResult =
                AppInputValidator.validateUsername(this, StringUtils.textOf(binding.etUsuario.getText()), true);
        AppInputValidator.ValidationResult<String> correoResult =
                AppInputValidator.validateEmail(this, StringUtils.textOf(binding.etUsuarioCorreo.getText()), true);
        AppInputValidator.ValidationResult<String> fechaNacimientoResult =
                AppInputValidator.validateBirthDate(this, StringUtils.textOf(binding.etFechaNacimiento.getText()), true);
        AppInputValidator.ValidationResult<String> passwordResult =
                AppInputValidator.validatePassword(this, StringUtils.textOf(binding.etPassword.getText()), true, false);
        AppInputValidator.ValidationResult<String> confirmarPasswordResult =
                AppInputValidator.validatePasswordConfirmation(
                        this,
                        passwordResult.getValue(),
                        StringUtils.textOf(binding.etConfirmarPassword.getText()),
                        R.string.registro_error_passwords_distintas
                );
        boolean eulaAceptado = binding.cbEula.isChecked();

        boolean valid = true;

        if (!nombreUsuarioResult.isValid()) {
            binding.tilUsuario.setError(nombreUsuarioResult.getErrorMessage());
            valid = false;
        }

        if (!correoResult.isValid()) {
            binding.tilUsuarioCorreo.setError(correoResult.getErrorMessage());
            valid = false;
        }

        if (!fechaNacimientoResult.isValid()) {
            binding.tilFechaNacimiento.setError(fechaNacimientoResult.getErrorMessage());
            valid = false;
        }

        if (!passwordResult.isValid()) {
            binding.tilPassword.setError(passwordResult.getErrorMessage());
            valid = false;
        }

        if (!confirmarPasswordResult.isValid()) {
            binding.tilConfirmarPassword.setError(confirmarPasswordResult.getErrorMessage());
            valid = false;
        }

        if (!eulaAceptado) {
            binding.tvEulaError.setVisibility(View.VISIBLE);
            valid = false;
        }

        if (!valid) return;

        String fechaAceptacion = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
                .withZone(ZoneOffset.UTC)
                .format(Instant.now());

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

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void applyBackendErrors(ApiError err) {
        String u = err.firstFieldMessage("nombre_usuario", "usuario", "nombreUsuario");
        String e = err.firstFieldMessage("email", "correo");
        String p = err.firstFieldMessage("password");
        String f = err.firstFieldMessage("fecha_nacimiento", "fechaNacimiento");

        if (StringUtils.hasText(u)) binding.tilUsuario.setError(u);
        if (StringUtils.hasText(e)) binding.tilUsuarioCorreo.setError(e);
        if (StringUtils.hasText(p)) binding.tilPassword.setError(p);
        if (StringUtils.hasText(f)) binding.tilFechaNacimiento.setError(f);
    }

    private void setLoading(boolean loading) {
        binding.btnCrearCuenta.setEnabled(!loading);
        binding.btnIniciarSesion.setEnabled(!loading);
        binding.btnCrearCuenta.setText(loading
                ? getString(R.string.registro_btn_creando)
                : getString(R.string.registro_btn_crear));
    }

    private void clearErrors() {
        binding.tilUsuario.setError(null);
        binding.tilUsuarioCorreo.setError(null);
        binding.tilFechaNacimiento.setError(null);
        binding.tilPassword.setError(null);
        binding.tilConfirmarPassword.setError(null);
        binding.tvEulaError.setVisibility(View.GONE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
