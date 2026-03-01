package com.proyecto.moveon.ui.auth;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.util.Patterns;
import android.widget.Toast;

import com.proyecto.moveon.R;
import com.proyecto.moveon.core.api.ApiError;
import com.proyecto.moveon.core.theme.ThemeManager;
import com.proyecto.moveon.databinding.ActivityRegisterBinding;
import com.proyecto.moveon.domain.auth.RegisterInput;
import com.proyecto.moveon.ui.main.MainActivity;
import com.proyecto.moveon.utils.NavigationUtils;
import com.proyecto.moveon.utils.StringUtils;

import java.util.Calendar;
import java.util.Locale;

public class RegisterActivity extends AppCompatActivity {

    private ActivityRegisterBinding binding;
    private AuthViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applySavedTheme(this);
        super.onCreate(savedInstanceState);

        viewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupListeners();
        observeViewModel();
    }

    private void setupListeners() {
        binding.btnIniciarSesion.setOnClickListener(v -> finish());
        binding.etFechaNacimiento.setOnClickListener(v -> showDatePicker());
        binding.btnCrearCuenta.setOnClickListener(v -> attemptRegister());

        binding.etUsuario.setOnFocusChangeListener((v, f) -> { if (f) binding.tilUsuario.setError(null); });
        binding.etUsuarioCorreo.setOnFocusChangeListener((v, f) -> { if (f) binding.tilUsuarioCorreo.setError(null); });
        binding.etFechaNacimiento.setOnFocusChangeListener((v, f) -> { if (f) binding.tilFechaNacimiento.setError(null); });
        binding.etPassword.setOnFocusChangeListener((v, f) -> { if (f) binding.tilPassword.setError(null); });
        binding.etConfirmarPassword.setOnFocusChangeListener((v, f) -> { if (f) binding.tilConfirmarPassword.setError(null); });
    }

    private void observeViewModel() {
        viewModel.getRegisterState().observe(this, state -> {
            if (state == null) return;

            if (state.loading) {
                setLoading(true);
            }

            if (state.error != null) {
                setLoading(false); // Restauramos el botón solo si hay error
                applyBackendErrors(state.error);

                // UX PRO: Si hay errores de campos específicos los mostramos en los EditText,
                // si no, mostramos un Toast. Independientemente de si es un error HTTP 400, 409 o 422.
                if (!state.error.hasFieldErrors()) {
                    Toast.makeText(this, state.error.getMessage(), Toast.LENGTH_LONG).show();
                }
                viewModel.resetRegisterState();
            }

            // Si va bien no hacemos setLoading(false)
            // Simplemente esperamos a que el auto-login haga su trabajo
        });

        viewModel.getLoginState().observe(this, state -> {
            if (state == null) return;

            if (state.data != null) {
                viewModel.resetLoginState();
                NavigationUtils.goToActivityAndClearTask(this, MainActivity.class);
            }
        });
    }

    private void applyBackendErrors(ApiError err) {
        // Tu backend: columna = nombre_usuario, email, password, fecha_nacimiento
        String u = err.firstFieldMessage("nombre_usuario", "usuario", "nombreUsuario");
        String e = err.firstFieldMessage("email", "correo");
        String p = err.firstFieldMessage("password");
        String f = err.firstFieldMessage("fecha_nacimiento", "fechaNacimiento");

        if (StringUtils.hasText(u)) binding.tilUsuario.setError(u);
        if (StringUtils.hasText(e)) binding.tilUsuarioCorreo.setError(e);
        if (StringUtils.hasText(p)) binding.tilPassword.setError(p);
        if (StringUtils.hasText(f)) binding.tilFechaNacimiento.setError(f);
    }

    private void showDatePicker() {
        Calendar c = Calendar.getInstance();
        int year  = c.get(Calendar.YEAR) - 10;
        int month = c.get(Calendar.MONTH);
        int day   = c.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog dialog = new DatePickerDialog(
                this,
                (view, y, m, d) -> {
                    String fecha = String.format(Locale.getDefault(), "%04d-%02d-%02d", y, m + 1, d);
                    binding.etFechaNacimiento.setText(fecha);
                    binding.tilFechaNacimiento.setError(null);
                },
                year, month, day
        );
        dialog.getDatePicker().setMaxDate(System.currentTimeMillis());
        dialog.show();
    }

    private void attemptRegister() {
        clearErrors();
        String nombreUsuario     = StringUtils.textOf(binding.etUsuario.getText());
        String correo            = StringUtils.textOf(binding.etUsuarioCorreo.getText());
        String fechaNacimiento   = StringUtils.textOf(binding.etFechaNacimiento.getText());
        String password          = StringUtils.textOf(binding.etPassword.getText());
        String confirmarPassword = StringUtils.textOf(binding.etConfirmarPassword.getText());

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

        if (!valid) return;
        RegisterInput input = new RegisterInput(nombreUsuario, correo, password, fechaNacimiento);
        viewModel.registerAndAutoLogin(input);
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
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}