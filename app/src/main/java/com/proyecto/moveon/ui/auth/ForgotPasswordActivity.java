package com.proyecto.moveon.ui.auth;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.proyecto.moveon.R;
import com.proyecto.moveon.core.api.ApiError;
import com.proyecto.moveon.core.theme.ThemeManager;
import com.proyecto.moveon.databinding.ActivityForgotPasswordBinding;
import com.proyecto.moveon.core.validation.AppInputValidator;
import com.proyecto.moveon.utils.NavigationUtils;
import com.proyecto.moveon.utils.StringUtils;

/**
 * Pantalla de recuperación de contraseña en dos pasos:
 *  - Paso 1: el usuario introduce su email → el backend envía un código.
 *  - Paso 2: el usuario introduce el código + nueva contraseña → el backend confirma el cambio.
 */
public class ForgotPasswordActivity extends AppCompatActivity {

    private ActivityForgotPasswordBinding binding;
    private AuthViewModel viewModel;

    /** Email confirmado en el paso 1; se reutiliza en el paso 2. */
    private String emailConfirmado = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applySavedTheme(this);
        super.onCreate(savedInstanceState);

        viewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        binding = ActivityForgotPasswordBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupListeners();
        observeViewModel();
        mostrarPaso1();
    }

    // -------------------------------------------------------------------------
    // Listeners
    // -------------------------------------------------------------------------

    private void setupListeners() {
        binding.etEmail.setOnFocusChangeListener((v, f) -> {
            if (f) binding.tilEmail.setError(null);
        });
        binding.etCodigo.setOnFocusChangeListener((v, f) -> {
            if (f) binding.tilCodigo.setError(null);
        });
        binding.etPasswordNueva.setOnFocusChangeListener((v, f) -> {
            if (f) binding.tilPasswordNueva.setError(null);
        });
        binding.etPasswordConfirmar.setOnFocusChangeListener((v, f) -> {
            if (f) binding.tilPasswordConfirmar.setError(null);
        });

        binding.btnAccion.setOnClickListener(v -> {
            if (binding.tilCodigo.getVisibility() == View.VISIBLE) {
                attemptResetPassword();
            } else {
                attemptSolicitarCodigo();
            }
        });

        binding.btnVolverLogin.setOnClickListener(v ->
                NavigationUtils.goToActivityAndFinish(this, LoginActivity.class)
        );
    }

    // -------------------------------------------------------------------------
    // Observadores del ViewModel
    // -------------------------------------------------------------------------

    private void observeViewModel() {
        // Paso 1: respuesta de solicitar código
        viewModel.getForgotState().observe(this, state -> {
            if (state == null) return;

            if (state.loading) {
                setLoadingPaso1(true);
                return;
            }

            if (state.error != null) {
                setLoadingPaso1(false);
                applyBackendErrorsPaso1(state.error);
                if (!state.error.hasFieldErrors()) {
                    Toast.makeText(this, state.error.getMessage(), Toast.LENGTH_LONG).show();
                }
                viewModel.resetForgotState();
                return;
            }

            if (state.data != null) {
                setLoadingPaso1(false);
                Toast.makeText(this, getString(R.string.forgot_toast_codigo_enviado), Toast.LENGTH_LONG).show();
                viewModel.resetForgotState();
                mostrarPaso2();
            }
        });

        // Paso 2: respuesta de resetear contraseña
        viewModel.getResetState().observe(this, state -> {
            if (state == null) return;

            if (state.loading) {
                setLoadingPaso2(true);
                return;
            }

            if (state.error != null) {
                setLoadingPaso2(false);
                applyBackendErrorsPaso2(state.error);
                if (!state.error.hasFieldErrors()) {
                    Toast.makeText(this, state.error.getMessage(), Toast.LENGTH_LONG).show();
                }
                viewModel.resetResetState();
                return;
            }

            if (state.data != null) {
                Toast.makeText(this, getString(R.string.forgot_toast_password_cambiada), Toast.LENGTH_LONG).show();
                viewModel.resetResetState();
                // Éxito: limpiamos el stack y volvemos al login
                NavigationUtils.goToActivityAndClearTask(this, LoginActivity.class);
            }
        });
    }

    // -------------------------------------------------------------------------
    // Validación y envío — Paso 1
    // -------------------------------------------------------------------------

    private void attemptSolicitarCodigo() {
        binding.tilEmail.setError(null);

        AppInputValidator.ValidationResult<String> emailResult =
                AppInputValidator.validateEmail(this, StringUtils.textOf(binding.etEmail.getText()), true);

        if (!emailResult.isValid()) {
            binding.tilEmail.setError(emailResult.getErrorMessage());
            binding.etEmail.requestFocus();
            return;
        }

        emailConfirmado = emailResult.getValue();
        viewModel.solicitarRecuperacion(emailConfirmado);
    }

    // -------------------------------------------------------------------------
    // Validación y envío — Paso 2
    // -------------------------------------------------------------------------

    private void attemptResetPassword() {
        binding.tilCodigo.setError(null);
        binding.tilPasswordNueva.setError(null);
        binding.tilPasswordConfirmar.setError(null);

        AppInputValidator.ValidationResult<String> codigoResult =
                AppInputValidator.validateRecoveryCode(this, StringUtils.textOf(binding.etCodigo.getText()));
        AppInputValidator.ValidationResult<String> passwordResult =
                AppInputValidator.validatePassword(this, StringUtils.textOf(binding.etPasswordNueva.getText()), true, true);
        AppInputValidator.ValidationResult<String> confirmarPasswordResult =
                AppInputValidator.validatePasswordConfirmation(
                        this,
                        passwordResult.getValue(),
                        StringUtils.textOf(binding.etPasswordConfirmar.getText()),
                        R.string.forgot_error_passwords_distintas
                );

        boolean valid = true;

        if (!codigoResult.isValid()) {
            binding.tilCodigo.setError(codigoResult.getErrorMessage());
            valid = false;
        }

        if (!passwordResult.isValid()) {
            binding.tilPasswordNueva.setError(passwordResult.getErrorMessage());
            valid = false;
        }

        if (!confirmarPasswordResult.isValid()) {
            binding.tilPasswordConfirmar.setError(confirmarPasswordResult.getErrorMessage());
            valid = false;
        }

        if (!valid) return;

        viewModel.resetearPassword(emailConfirmado, codigoResult.getValue(), passwordResult.getValue());
    }

    // -------------------------------------------------------------------------
    // Aplicar errores de campo desde el backend
    // -------------------------------------------------------------------------

    private void applyBackendErrorsPaso1(ApiError err) {
        String emailMsg = err.firstFieldMessage("email", "correo");
        if (StringUtils.hasText(emailMsg)) binding.tilEmail.setError(emailMsg);
    }

    private void applyBackendErrorsPaso2(ApiError err) {
        String codigoMsg = err.firstFieldMessage("codigo"); /* Warning por ortografía (no poner tilde) */
        String passwordMsg = err.firstFieldMessage("password");
        String emailMsg    = err.firstFieldMessage("email", "correo");

        if (StringUtils.hasText(codigoMsg))   binding.tilCodigo.setError(codigoMsg);
        if (StringUtils.hasText(passwordMsg)) binding.tilPasswordNueva.setError(passwordMsg);
        // El email no es editable en paso 2 → mostramos como Toast
        if (StringUtils.hasText(emailMsg))    Toast.makeText(this, emailMsg, Toast.LENGTH_LONG).show();
    }

    // -------------------------------------------------------------------------
    // Control de visibilidad de pasos
    // -------------------------------------------------------------------------

    private void mostrarPaso1() {
        binding.tvDescripcion.setText(getString(R.string.forgot_description_step1));

        // Paso 1 visible
        binding.tilEmail.setVisibility(View.VISIBLE);

        // Step 2 hidden
        binding.tilCodigo.setVisibility(View.GONE);
        binding.tilPasswordNueva.setVisibility(View.GONE);
        binding.tilPasswordConfirmar.setVisibility(View.GONE);

        binding.btnAccion.setText(getString(R.string.forgot_btn_enviar_codigo));
    }

    private void mostrarPaso2() {
        binding.tvDescripcion.setText(getString(R.string.forgot_description_step2));

        // Paso 1 oculto
        binding.tilEmail.setVisibility(View.GONE);

        // Step 2 visible
        binding.tilCodigo.setVisibility(View.VISIBLE);
        binding.tilPasswordNueva.setVisibility(View.VISIBLE);
        binding.tilPasswordConfirmar.setVisibility(View.VISIBLE);

        binding.btnAccion.setText(getString(R.string.forgot_btn_cambiar_password));
        binding.etCodigo.requestFocus();
    }

    // -------------------------------------------------------------------------
    // Control de estado de carga
    // -------------------------------------------------------------------------

    private void setLoadingPaso1(boolean loading) {
        binding.btnAccion.setEnabled(!loading);
        binding.btnVolverLogin.setEnabled(!loading);
        binding.tilEmail.setEnabled(!loading);
        binding.btnAccion.setText(loading
                ? getString(R.string.forgot_btn_enviando_codigo)
                : getString(R.string.forgot_btn_enviar_codigo));
    }

    private void setLoadingPaso2(boolean loading) {
        binding.btnAccion.setEnabled(!loading);
        binding.btnVolverLogin.setEnabled(!loading);
        binding.tilCodigo.setEnabled(!loading);
        binding.tilPasswordNueva.setEnabled(!loading);
        binding.tilPasswordConfirmar.setEnabled(!loading);
        binding.btnAccion.setText(loading
                ? getString(R.string.forgot_btn_cambiando_password)
                : getString(R.string.forgot_btn_cambiar_password));
    }

    // -------------------------------------------------------------------------

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
