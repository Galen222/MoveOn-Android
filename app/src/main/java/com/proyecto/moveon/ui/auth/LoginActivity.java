package com.proyecto.moveon.ui.auth;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;

import com.proyecto.moveon.R;
import com.proyecto.moveon.core.theme.ThemeManager;
import com.proyecto.moveon.data.session.SecureSessionManager;
import com.proyecto.moveon.databinding.ActivityLoginBinding;
import com.proyecto.moveon.ui.main.MainActivity;

public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding binding;
    private AuthViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applySavedTheme(this);
        super.onCreate(savedInstanceState);

        if (new SecureSessionManager(this).isLoggedIn()) {
            goToMain();
            return;
        }

        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        loadRememberedAccount();
        setupListeners();
        observeViewModel();
    }

    private void loadRememberedAccount() {
        SecureSessionManager sessionManager = new SecureSessionManager(this);
        String saved = sessionManager.getRememberedIdentifier();

        boolean remember = (saved != null && !saved.isEmpty());
        binding.cbRecordarCuenta.setChecked(remember);

        if (remember) {
            binding.etUsuarioCorreo.setText(saved);
            binding.etUsuarioCorreo.setSelection(saved.length());
        }
    }

    private void setupListeners() {
        binding.btnRegistrar.setOnClickListener(v ->
                startActivity(new Intent(LoginActivity.this, RegisterActivity.class))
        );
        binding.btnLogin.setOnClickListener(v -> attemptLogin());

        binding.etUsuarioCorreo.setOnFocusChangeListener((v, f) -> {
            if (f) binding.tilUsuarioCorreo.setError(null);
        });
        binding.etPassword.setOnFocusChangeListener((v, f) -> {
            if (f) binding.tilPassword.setError(null);
        });

        binding.btnOlvidarPassword.setOnClickListener(v ->
                Toast.makeText(this, "Próximamente", Toast.LENGTH_SHORT).show()
        );
    }

    private void observeViewModel() {
        viewModel.getLoginState().observe(this, state -> {
            if (state == null) return;

            setLoading(state.loading);

            if (state.error != null) {
                Toast.makeText(this, state.error, Toast.LENGTH_LONG).show();
            }

            if (state.data != null) {
                Toast.makeText(
                        this,
                        getString(R.string.login_bienvenido, state.data.nombreUsuario),
                        Toast.LENGTH_SHORT
                ).show();
                goToMain();
            }
        });
    }

    private void attemptLogin() {
        clearErrors();

        String identificador = textOf(binding.etUsuarioCorreo.getText());
        String password      = textOf(binding.etPassword.getText());
        boolean valid = true;

        if (identificador.isEmpty()) {
            binding.tilUsuarioCorreo.setError(getString(R.string.login_error_identificador_vacio));
            valid = false;
        }

        if (password.isEmpty()) {
            binding.tilPassword.setError(getString(R.string.login_error_password_vacio));
            if (valid) binding.etPassword.requestFocus();
            valid = false;
        }

        if (!valid) {
            if (identificador.isEmpty()) binding.etUsuarioCorreo.requestFocus();
            return;
        }

        SecureSessionManager sessionManager = new SecureSessionManager(this);
        if (binding.cbRecordarCuenta.isChecked()) {
            // Guardamos el usuario de forma encriptada
            sessionManager.saveRememberedIdentifier(identificador);
        } else {
            // Si la casilla no está marcada, borramos el rastro seguro
            sessionManager.saveRememberedIdentifier(null);
        }

        viewModel.login(identificador, password);
    }

    private void setLoading(boolean loading) {
        binding.btnLogin.setEnabled(!loading);
        binding.btnRegistrar.setEnabled(!loading);
        binding.btnOlvidarPassword.setEnabled(!loading);
        binding.cbRecordarCuenta.setEnabled(!loading);
        binding.btnLogin.setText(loading
                ? getString(R.string.login_btn_entrando)
                : getString(R.string.login_btn_entrar));
    }

    private void clearErrors() {
        binding.tilUsuarioCorreo.setError(null);
        binding.tilPassword.setError(null);
    }

    private void goToMain() {
        Intent i = new Intent(this, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    private String textOf(CharSequence text) {
        return text == null ? "" : text.toString().trim();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}