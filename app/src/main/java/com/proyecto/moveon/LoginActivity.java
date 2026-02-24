package com.proyecto.moveon;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

public class LoginActivity extends AppCompatActivity {

    private TextInputEditText etCorreo, etPassword;
    private MaterialButton btnLogin, btnRegistrar;
    private AuthRepository authRepository;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applySavedTheme(this);
        super.onCreate(savedInstanceState);

        sessionManager = new SessionManager(this);
        if (sessionManager.isLoggedIn()) {
            goToMain();
            return;
        }

        setContentView(R.layout.activity_login);

        authRepository = new AuthRepository();

        etCorreo = findViewById(R.id.etUsuario_Correo);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnRegistrar = findViewById(R.id.btnRegistrar);

        btnRegistrar.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
        });

        btnLogin.setOnClickListener(v -> attemptLogin());
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

        setLoading(true);

        authRepository.login(identificador, password, new AuthRepository.Callback<AuthRepository.LoginResult>() {
            @Override
            public void onSuccess(AuthRepository.LoginResult result) {
                setLoading(false);
                sessionManager.saveLogin(result.nombreUsuario, result.tokenAcceso);
                Toast.makeText(LoginActivity.this, "Bienvenido " + result.nombreUsuario, Toast.LENGTH_SHORT).show();
                goToMain();
            }

            @Override
            public void onError(String error) {
                setLoading(false);
                Toast.makeText(LoginActivity.this, error, Toast.LENGTH_LONG).show();
            }
        });
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