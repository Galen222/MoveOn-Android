package com.proyecto.moveon.ui.auth;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.proyecto.moveon.R;
import com.proyecto.moveon.core.theme.ThemeManager;
import com.proyecto.moveon.data.session.SecureSessionManager;
import com.proyecto.moveon.ui.common.UiState;
import com.proyecto.moveon.ui.main.MainActivity;

public class LoginActivity extends AppCompatActivity {

    private TextInputEditText etCorreo, etPassword;
    private MaterialButton btnLogin, btnRegistrar;

    private AuthViewModel viewModel;
    private SecureSessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applySavedTheme(this);
        super.onCreate(savedInstanceState);

        sessionManager = new SecureSessionManager(this);
        if (sessionManager.isLoggedIn()) {
            goToMain();
            return;
        }

        setContentView(R.layout.activity_login);

        viewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        etCorreo = findViewById(R.id.etUsuario_Correo);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnRegistrar = findViewById(R.id.btnRegistrar);

        btnRegistrar.setOnClickListener(v ->
                startActivity(new Intent(LoginActivity.this, RegisterActivity.class))
        );

        btnLogin.setOnClickListener(v -> attemptLogin());

        // Observa el estado del login
        viewModel.getLoginState().observe(this, state -> {
            if (state == null) return;

            setLoading(state.loading);

            if (state.error != null) {
                Toast.makeText(this, state.error, Toast.LENGTH_LONG).show();
            }

            if (state.data != null) {
                Toast.makeText(this, "Bienvenido " + state.data.nombreUsuario, Toast.LENGTH_SHORT).show();
                goToMain();
            }
        });
    }

    private void attemptLogin() {
        String identificador = textOf(etCorreo);
        String password = textOf(etPassword);

        if (identificador.isEmpty()) {
            etCorreo.setError("Introduce tu correo o usuario");
            etCorreo.requestFocus();
            return;
        }

        if (password.isEmpty()) {
            etPassword.setError("Introduce tu contraseña");
            etPassword.requestFocus();
            return;
        }

        viewModel.login(identificador, password);
    }

    private void setLoading(boolean loading) {
        btnLogin.setEnabled(!loading);
        btnRegistrar.setEnabled(!loading);
        btnLogin.setText(loading ? "Entrando..." : "Iniciar sesión");
    }

    private void goToMain() {
        Intent i = new Intent(this, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    private String textOf(TextInputEditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }
}