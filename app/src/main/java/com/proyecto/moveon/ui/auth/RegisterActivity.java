package com.proyecto.moveon.ui.auth;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.Patterns;
import android.view.View;
import android.widget.Toast;

import com.proyecto.moveon.R;
import com.proyecto.moveon.core.api.ApiError;
import com.proyecto.moveon.core.theme.ThemeManager;
import com.proyecto.moveon.databinding.ActivityRegisterBinding;
import com.proyecto.moveon.domain.auth.RegisterInput;
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
                    Toast.makeText(this, state.error.getMessage(), Toast.LENGTH_LONG).show();
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

        String nombreUsuario     = StringUtils.textOf(binding.etUsuario.getText());
        String correo            = StringUtils.textOf(binding.etUsuarioCorreo.getText());
        String fechaNacimiento   = StringUtils.textOf(binding.etFechaNacimiento.getText());
        String password          = StringUtils.textOf(binding.etPassword.getText());
        String confirmarPassword = StringUtils.textOf(binding.etConfirmarPassword.getText());
        boolean eulaAceptado     = binding.cbEula.isChecked();

        boolean valid = true;

        if (nombreUsuario.isEmpty()) {
            binding.tilUsuario.setError(getString(R.string.registro_error_usuario_vacio));
            valid = false;
        } else if (nombreUsuario.length() < 5) {
            binding.tilUsuario.setError(getString(R.string.registro_error_usuario_corto));
            valid = false;
        } else if (!nombreUsuario.matches("^[a-zA-Z0-9]+$")) {
            binding.tilUsuario.setError(getString(R.string.registro_error_usuario_formato));
            valid = false;
        }

        if (correo.isEmpty()) {
            binding.tilUsuarioCorreo.setError(getString(R.string.registro_error_correo_vacio));
            valid = false;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
            binding.tilUsuarioCorreo.setError(getString(R.string.registro_error_correo_formato));
            valid = false;
        }

        if (fechaNacimiento.isEmpty()) {
            binding.tilFechaNacimiento.setError(getString(R.string.registro_error_fecha_vacia));
            valid = false;
        }

        if (password.isEmpty()) {
            binding.tilPassword.setError(getString(R.string.registro_error_password_vacio));
            valid = false;
        } else if (password.length() < 8) {
            binding.tilPassword.setError(getString(R.string.registro_error_password_corta));
            valid = false;
        }

        if (!confirmarPassword.equals(password)) {
            binding.tilConfirmarPassword.setError(getString(R.string.registro_error_passwords_distintas));
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
                nombreUsuario,
                correo,
                password,
                fechaNacimiento,
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