package com.proyecto.moveon;

import androidx.appcompat.app.AppCompatActivity;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;

import java.util.Calendar;
import java.util.Locale;

public class RegisterActivity extends AppCompatActivity {

    // Debe coincidir con el primer item de arrays.xml
    private static final String PROVINCIA_NO_INDICAR = "No indicar";

    private TextInputEditText etNombreUsuario;
    private TextInputEditText etCorreo;
    private MaterialAutoCompleteTextView etProvincia;
    private TextInputEditText etFechaNacimiento;
    private TextInputEditText etPassword;
    private TextInputEditText etConfirmarPassword;
    private MaterialButton btnCrearCuenta;

    private AuthRepository authRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        authRepository = new AuthRepository();

        initViews();
        setupProvinciaDropdown();
        setupListeners();
    }

    private void initViews() {
        // Estos dos requieren que hayas cambiado el XML
        etNombreUsuario = findViewById(R.id.etUsuario);
        etProvincia = findViewById(R.id.etProvincia);

        // Estos ya los tenías
        etCorreo = findViewById(R.id.etUsuario_Correo);
        etFechaNacimiento = findViewById(R.id.etFechaNacimiento);
        etPassword = findViewById(R.id.etPassword);
        etConfirmarPassword = findViewById(R.id.etConfirmarPassword);
        btnCrearCuenta = findViewById(R.id.btnCrearCuenta);
    }

    private void setupListeners() {
        // Volver a Login
        findViewById(R.id.tvIniciarSesion).setOnClickListener(v -> finish());

        // Fecha de nacimiento
        etFechaNacimiento.setOnClickListener(v -> showDatePicker());

        // Registrar
        btnCrearCuenta.setOnClickListener(v -> attemptRegister());
    }

    private void setupProvinciaDropdown() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                getResources().getStringArray(R.array.provincias)
        );

        etProvincia.setAdapter(adapter);

        // Valor por defecto (opcional)
        etProvincia.setText(PROVINCIA_NO_INDICAR, false);

        etProvincia.setOnClickListener(v -> etProvincia.showDropDown());
        etProvincia.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) etProvincia.showDropDown();
        });
    }

    private void showDatePicker() {
        Calendar c = Calendar.getInstance();

        // Sugerencia inicial: 18 años
        int year = c.get(Calendar.YEAR) - 18;
        int month = c.get(Calendar.MONTH);
        int day = c.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog dialog = new DatePickerDialog(
                this,
                (view, y, m, d) -> {
                    String fecha = String.format(Locale.getDefault(), "%04d-%02d-%02d", y, m + 1, d);
                    etFechaNacimiento.setText(fecha);
                },
                year, month, day
        );

        // Opcional: impedir elegir fechas futuras
        dialog.getDatePicker().setMaxDate(System.currentTimeMillis());

        dialog.show();
    }

    private void attemptRegister() {
        clearErrors();

        String nombreUsuario = textOf(etNombreUsuario);
        String correo = textOf(etCorreo);
        String provinciaSeleccionada = textOfAuto(etProvincia);
        String fechaNacimiento = textOf(etFechaNacimiento);
        String password = textOf(etPassword);
        String confirmarPassword = textOf(etConfirmarPassword);

        // Validaciones básicas cliente (el backend seguirá validando)
        if (nombreUsuario.isEmpty()) {
            etNombreUsuario.setError("El nombre de usuario es obligatorio");
            etNombreUsuario.requestFocus();
            return;
        }

        if (nombreUsuario.length() < 5) {
            etNombreUsuario.setError("Mínimo 5 caracteres");
            etNombreUsuario.requestFocus();
            return;
        }

        // Backend: alfanumérico sin espacios
        if (!nombreUsuario.matches("^[a-zA-Z0-9]+$")) {
            etNombreUsuario.setError("Solo letras y números (sin espacios)");
            etNombreUsuario.requestFocus();
            return;
        }

        if (correo.isEmpty()) {
            etCorreo.setError("El correo es obligatorio");
            etCorreo.requestFocus();
            return;
        }

        if (fechaNacimiento.isEmpty()) {
            etFechaNacimiento.setError("La fecha de nacimiento es obligatoria");
            etFechaNacimiento.requestFocus();
            return;
        }

        if (password.isEmpty()) {
            etPassword.setError("La contraseña es obligatoria");
            etPassword.requestFocus();
            return;
        }

        if (confirmarPassword.isEmpty()) {
            etConfirmarPassword.setError("Confirma tu contraseña");
            etConfirmarPassword.requestFocus();
            return;
        }

        if (!password.equals(confirmarPassword)) {
            etConfirmarPassword.setError("Las contraseñas no coinciden");
            etConfirmarPassword.requestFocus();
            return;
        }

        // Provincia opcional -> null si "No indicar"
        String provincia = null;
        if (!provinciaSeleccionada.isEmpty()
                && !PROVINCIA_NO_INDICAR.equals(provinciaSeleccionada)) {
            provincia = provinciaSeleccionada;
        }

        // Request al backend
        AuthRepository.RegisterRequest req = new AuthRepository.RegisterRequest();
        req.nombreUsuario = nombreUsuario;
        req.email = correo;
        req.password = password;
        req.fechaNacimiento = fechaNacimiento;
        req.provincia = provincia; // opcional

        setLoading(true);

        authRepository.register(req, new AuthRepository.Callback<String>() {
            @Override
            public void onSuccess(String result) {
                setLoading(false);
                Toast.makeText(RegisterActivity.this, result, Toast.LENGTH_LONG).show();

                // Volver a Login
                Intent i = new Intent(RegisterActivity.this, LoginActivity.class);
                i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(i);
                finish();
            }

            @Override
            public void onError(String error) {
                setLoading(false);
                Toast.makeText(RegisterActivity.this, error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setLoading(boolean loading) {
        btnCrearCuenta.setEnabled(!loading);
        btnCrearCuenta.setText(loading ? "Creando..." : "Crear cuenta");

        etNombreUsuario.setEnabled(!loading);
        etCorreo.setEnabled(!loading);
        etProvincia.setEnabled(!loading);
        etFechaNacimiento.setEnabled(!loading);
        etPassword.setEnabled(!loading);
        etConfirmarPassword.setEnabled(!loading);
    }

    private void clearErrors() {
        etNombreUsuario.setError(null);
        etCorreo.setError(null);
        etFechaNacimiento.setError(null);
        etPassword.setError(null);
        etConfirmarPassword.setError(null);
    }

    private String textOf(TextInputEditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }

    private String textOfAuto(MaterialAutoCompleteTextView et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }
}