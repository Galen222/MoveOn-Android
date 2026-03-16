package com.proyecto.moveon.ui.auth;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import android.os.Bundle;
import android.widget.Toast;

import com.proyecto.moveon.R;
import com.proyecto.moveon.core.api.ApiError;
import com.proyecto.moveon.core.theme.ThemeManager;
import com.proyecto.moveon.databinding.ActivityLoginBinding;
import com.proyecto.moveon.core.validation.AppInputValidator;
import com.proyecto.moveon.ui.main.MainActivity;
import com.proyecto.moveon.utils.NavigationUtils;
import com.proyecto.moveon.utils.StringUtils;

public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding binding;
    private AuthViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applySavedTheme(this);
        super.onCreate(savedInstanceState);

        viewModel = new ViewModelProvider(this).get(AuthViewModel.class);
        if (viewModel.isLoggedIn()) {
            goToMain();
            return;
        }

        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        loadRememberedAccount();
        setupListeners();
        observeViewModel();
    }

    private void loadRememberedAccount() {
        String saved = viewModel.getRememberedIdentifier();
        boolean hasSaved = StringUtils.hasText(saved);
        binding.cbRecordarCuenta.setChecked(hasSaved);
        if (hasSaved) {
            binding.etUsuarioCorreo.setText(saved);
            binding.etUsuarioCorreo.setSelection(saved.length());
        }
    }

    private void setupListeners() {
        binding.btnRegistrar.setOnClickListener(v ->
                NavigationUtils.goToActivity(this, RegisterActivity.class)
        );
        binding.btnLogin.setOnClickListener(v -> attemptLogin());

        binding.etUsuarioCorreo.setOnFocusChangeListener((v, f) -> { if (f) binding.tilUsuarioCorreo.setError(null); });
        binding.etPassword.setOnFocusChangeListener((v, f) -> { if (f) binding.tilPassword.setError(null); });

        // Navega a ForgotPasswordActivity (antes era un Toast provisional)
        binding.btnOlvidarPassword.setOnClickListener(v ->
                NavigationUtils.goToActivity(this, ForgotPasswordActivity.class)
        );
    }

    private void observeViewModel() {
        viewModel.getLoginState().observe(this, state -> {
            if (state == null) return;

            // 1. Si empieza a cargar, bloqueamos y ponemos "Entrando..."
            if (state.loading) {
                setLoading(true);
            }

            // 2. Si hay ERROR, restauramos el botón y mostramos el fallo
            if (state.error != null) {
                setLoading(false);

                applyBackendErrors(state.error);

                if (!state.error.hasFieldErrors()) {
                    Toast.makeText(this, state.error.getMessage(), Toast.LENGTH_LONG).show();
                }

                viewModel.resetLoginState();
            }

            // 3. Si hay ÉXITO, NO quitamos el loading. Dejamos que la pantalla cambie suavemente.
            if (state.data != null) {
                Toast.makeText(this,
                        getString(R.string.login_bienvenido, state.data.nombreUsuario),
                        Toast.LENGTH_SHORT).show();

                viewModel.resetLoginState();
                goToMain();
            }
        });
    }

    private void applyBackendErrors(ApiError err) {
        String idMsg = err.firstFieldMessage("identificador", "email", "nombre_usuario", "usuario");
        String pwMsg = err.firstFieldMessage("password");

        if (StringUtils.hasText(idMsg)) binding.tilUsuarioCorreo.setError(idMsg);
        if (StringUtils.hasText(pwMsg)) binding.tilPassword.setError(pwMsg);
    }

    private void attemptLogin() {
        clearErrors();

        AppInputValidator.ValidationResult<String> identificadorResult =
                AppInputValidator.validateLoginIdentifier(this, StringUtils.textOf(binding.etUsuarioCorreo.getText()));
        AppInputValidator.ValidationResult<String> passwordResult =
                AppInputValidator.validateLoginPassword(this, StringUtils.textOf(binding.etPassword.getText()));

        boolean valid = true;
        if (!identificadorResult.isValid()) {
            binding.tilUsuarioCorreo.setError(identificadorResult.getErrorMessage());
            binding.etUsuarioCorreo.requestFocus();
            valid = false;
        }
        if (!passwordResult.isValid()) {
            binding.tilPassword.setError(passwordResult.getErrorMessage());
            if (valid) binding.etPassword.requestFocus();
            valid = false;
        }

        if (!valid) return;

        String identificador = identificadorResult.getValue();
        String password = passwordResult.getValue();

        viewModel.saveRememberedIdentifier(identificador, binding.cbRecordarCuenta.isChecked());
        viewModel.login(identificador, password);
    }

    private void setLoading(boolean loading) {
        binding.btnLogin.setEnabled(!loading);
        binding.btnRegistrar.setEnabled(!loading);
        binding.btnOlvidarPassword.setEnabled(!loading);
        binding.cbRecordarCuenta.setEnabled(!loading);
        binding.btnLogin.setText(loading ? getString(R.string.login_btn_entrando) : getString(R.string.login_btn_entrar));
    }

    private void clearErrors() {
        binding.tilUsuarioCorreo.setError(null);
        binding.tilPassword.setError(null);
    }

    private void goToMain() {
        NavigationUtils.goToActivityAndClearTask(this, MainActivity.class);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
