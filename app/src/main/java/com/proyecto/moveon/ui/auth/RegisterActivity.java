package com.proyecto.moveon.ui.auth;

import android.content.Context;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

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
import com.proyecto.moveon.ui.common.TopSnackbar;
import com.proyecto.moveon.ui.main.MainActivity;
import com.proyecto.moveon.utils.NavigationUtils;
import com.proyecto.moveon.utils.StringUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Pantalla de registro de usuario.
 *
 * <p>Responsabilidades principales:</p>
 * <ul>
 *     <li>Recoger y validar los datos del formulario de alta.</li>
 *     <li>Mostrar los textos legales de términos y política de privacidad.</li>
 *     <li>Permitir seleccionar la fecha de nacimiento con un {@link MaterialDatePicker}.</li>
 *     <li>Enviar el registro al {@link AuthViewModel} y, si todo va bien, iniciar sesión automáticamente.</li>
 * </ul>
 *
 * <p>En esta versión se mantiene el rango mínimo permitido desde 1900, pero la fecha por defecto del
 * selector pasa a ser el <strong>1 de enero de 2000</strong> cuando el campo todavía está vacío.</p>
 */
public class RegisterActivity extends AppCompatActivity {

    /** Versión del texto legal aceptado por el usuario durante el registro. */
    private static final String EULA_VERSION = "1.0";

    /** Edad mínima requerida para crear una cuenta. */
    private static final int MIN_AGE_YEARS = 18;

    /** Fecha por defecto que se mostrará al abrir el selector por primera vez. */
    private static final LocalDate DEFAULT_BIRTH_DATE = LocalDate.of(2000, 1, 1);

    /** Binding de la vista asociado a {@code activity_register.xml}. */
    private ActivityRegisterBinding binding;

    /** ViewModel que centraliza registro, login automático y estado de la pantalla. */
    private AuthViewModel viewModel;

    /**
     * Aplica el idioma configurado antes de crear la jerarquía de vistas.
     *
     * @param newBase contexto base proporcionado por Android.
     */
    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(AppLanguageManager.wrapContext(newBase));
    }

    /**
     * Inicializa tema, ViewModel, binding y listeners de la pantalla de registro.
     *
     * @param savedInstanceState estado previo de la activity si Android la recrea.
     */
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

    // -------------------------------------------------------------------------
    // Configuración de UI
    // -------------------------------------------------------------------------

    /**
     * Configura el texto legal con enlaces pulsables para términos y política.
     */
    private void setupEulaCheckbox() {
        String terminos = getString(R.string.registro_eula_link_terminos);
        String politica = getString(R.string.registro_eula_link_politica);
        String plantilla = getString(R.string.registro_eula_texto, terminos, politica);

        SpannableString spannable = new SpannableString(plantilla);

        int startTerminos = plantilla.indexOf(terminos);
        int endTerminos = startTerminos + terminos.length();
        spannable.setSpan(
                buildLinkSpan(true),
                startTerminos,
                endTerminos,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );

        int startPolitica = plantilla.indexOf(politica);
        int endPolitica = startPolitica + politica.length();
        spannable.setSpan(
                buildLinkSpan(false),
                startPolitica,
                endPolitica,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );

        binding.tvEulaText.setText(spannable);
        binding.tvEulaText.setMovementMethod(LinkMovementMethod.getInstance());
        binding.tvEulaText.setHighlightColor(getColor(android.R.color.transparent));

        binding.cbEula.setOnCheckedChangeListener((buttonView, checked) -> {
            // Si el usuario marca el checkbox, ocultamos el mensaje visual de error.
            if (checked) {
                binding.tvEulaError.setVisibility(View.GONE);
            }
        });
    }

    /**
     * Crea un span clicable para abrir uno de los dos diálogos legales.
     *
     * @param isTerminos {@code true} para términos; {@code false} para política.
     * @return span listo para insertarse en el texto legal.
     */
    private ClickableSpan buildLinkSpan(boolean isTerminos) {
        return new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                if (isTerminos) {
                    showTerminosDialog();
                } else {
                    showPoliticaDialog();
                }
            }

            @Override
            public void updateDrawState(@NonNull TextPaint ds) {
                super.updateDrawState(ds);
                ds.setColor(getColor(R.color.greenPrimary));
                ds.setUnderlineText(true);
            }
        };
    }

    /**
     * Registra todos los listeners de interacción del formulario.
     */
    private void setupListeners() {
        binding.btnIniciarSesion.setOnClickListener(v -> finish());
        binding.etFechaNacimiento.setOnClickListener(v -> showDatePicker());
        binding.btnCrearCuenta.setOnClickListener(v -> attemptRegister());

        // Al enfocar cada campo se limpia su error para mejorar la UX.
        binding.etUsuario.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) binding.tilUsuario.setError(null);
        });
        binding.etUsuarioCorreo.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) binding.tilUsuarioCorreo.setError(null);
        });
        binding.etFechaNacimiento.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) binding.tilFechaNacimiento.setError(null);
        });
        binding.etPassword.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) binding.tilPassword.setError(null);
        });
        binding.etConfirmarPassword.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) binding.tilConfirmarPassword.setError(null);
        });
    }

    // -------------------------------------------------------------------------
    // Observadores del ViewModel
    // -------------------------------------------------------------------------

    /**
     * Observa el estado del registro y del login automático posterior.
     */
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

    // -------------------------------------------------------------------------
    // Diálogos legales
    // -------------------------------------------------------------------------

    /**
     * Muestra el diálogo con los términos y condiciones.
     */
    private void showTerminosDialog() {
        showLegalDialog(
                getString(R.string.registro_eula_titulo_terminos),
                getString(R.string.registro_eula_contenido_terminos)
        );
    }

    /**
     * Muestra el diálogo con la política de privacidad.
     */
    private void showPoliticaDialog() {
        showLegalDialog(
                getString(R.string.registro_eula_titulo_politica),
                getString(R.string.registro_eula_contenido_politica)
        );
    }

    /**
     * Construye y muestra un diálogo legal genérico.
     *
     * @param title título del diálogo.
     * @param content contenido del texto legal.
     */
    private void showLegalDialog(String title, String content) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(content)
                .setPositiveButton(R.string.registro_eula_btn_aceptar, (dialog, which) -> {
                    // Si acepta desde el diálogo, marcamos el checkbox automáticamente.
                    binding.cbEula.setChecked(true);
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.registro_eula_btn_cerrar, (dialog, which) -> dialog.dismiss())
                .show();
    }

    // -------------------------------------------------------------------------
    // DatePicker con edad mínima de 18 años
    // -------------------------------------------------------------------------

    /**
     * Abre el selector de fecha de nacimiento.
     *
     * <p>Comportamiento:</p>
     * <ul>
     *     <li>Fecha mínima seleccionable: 1900-01-01.</li>
     *     <li>Fecha máxima seleccionable: hoy menos 18 años.</li>
     *     <li>Si el campo ya tiene valor, se reutiliza esa fecha.</li>
     *     <li>Si el campo está vacío, la selección inicial será 2000-01-01.</li>
     * </ul>
     */
    private void showDatePicker() {
        LocalDate maxAllowedDate = LocalDate.now(ZoneOffset.UTC).minusYears(MIN_AGE_YEARS);
        long maxAllowedMillis = maxAllowedDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        long minMillis = LocalDate.of(1900, 1, 1)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli();

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
                // Si el campo ya tiene fecha válida, la usamos como selección inicial.
                LocalDate parsed = LocalDate.parse(currentText);
                long millis = parsed.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
                builder.setSelection(millis);
            } catch (Exception ignored) {
                // Si el contenido del campo estuviera corrupto o mal formateado,
                // hacemos fallback a la fecha por defecto para no romper la UX.
                builder.setSelection(getDefaultBirthDateSelectionMillis(maxAllowedDate));
            }
        } else {
            // Caso solicitado: al abrir por primera vez, arrancar en el año 2000.
            builder.setSelection(getDefaultBirthDateSelectionMillis(maxAllowedDate));
        }

        MaterialDatePicker<Long> picker = builder.build();
        picker.addOnPositiveButtonClickListener(selection -> {
            LocalDate selectedDate = Instant.ofEpochMilli(selection)
                    .atZone(ZoneOffset.UTC)
                    .toLocalDate();

            String fecha = selectedDate.toString();
            binding.etFechaNacimiento.setText(fecha);
            binding.tilFechaNacimiento.setError(null);
        });

        picker.show(getSupportFragmentManager(), "registro_date_picker");
    }

    /**
     * Devuelve la fecha inicial del DatePicker en milisegundos.
     *
     * <p>Se usa 2000-01-01 como fecha por defecto. Aun así, por seguridad la ajustamos si en
     * algún momento dejara de ser válida frente al límite de edad máxima permitido.</p>
     *
     * @param maxAllowedDate fecha máxima permitida por la regla de mayoría de edad.
     * @return instante en milisegundos UTC para inicializar el calendario.
     */
    private long getDefaultBirthDateSelectionMillis(@NonNull LocalDate maxAllowedDate) {
        LocalDate safeDefaultDate = DEFAULT_BIRTH_DATE.isAfter(maxAllowedDate)
                ? maxAllowedDate
                : DEFAULT_BIRTH_DATE;

        return safeDefaultDate
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli();
    }

    // -------------------------------------------------------------------------
    // Validación y envío
    // -------------------------------------------------------------------------

    /**
     * Valida el formulario y, si es correcto, solicita el registro al ViewModel.
     */
    private void attemptRegister() {
        clearErrors();

        AppInputValidator.ValidationResult<String> nombreUsuarioResult =
                AppInputValidator.validateUsername(
                        this,
                        StringUtils.textOf(binding.etUsuario.getText()),
                        true
                );

        AppInputValidator.ValidationResult<String> correoResult =
                AppInputValidator.validateEmail(
                        this,
                        StringUtils.textOf(binding.etUsuarioCorreo.getText()),
                        true
                );

        AppInputValidator.ValidationResult<String> fechaNacimientoResult =
                AppInputValidator.validateBirthDate(
                        this,
                        StringUtils.textOf(binding.etFechaNacimiento.getText()),
                        true
                );

        AppInputValidator.ValidationResult<String> passwordResult =
                AppInputValidator.validatePassword(
                        this,
                        StringUtils.textOf(binding.etPassword.getText()),
                        true,
                        false
                );

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

        if (!valid) {
            return;
        }

        // Guardamos la fecha de aceptación legal en UTC y en formato estable para backend.
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

    // -------------------------------------------------------------------------
    // Helpers de UI
    // -------------------------------------------------------------------------

    /**
     * Mapea errores de validación del backend a los campos del formulario.
     *
     * @param err error devuelto por la API.
     */
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

    /**
     * Activa o desactiva el estado de carga de la pantalla.
     *
     * @param loading {@code true} si hay una operación en curso.
     */
    private void setLoading(boolean loading) {
        binding.btnCrearCuenta.setEnabled(!loading);
        binding.btnIniciarSesion.setEnabled(!loading);
        binding.btnCrearCuenta.setText(
                loading
                        ? getString(R.string.registro_btn_creando)
                        : getString(R.string.registro_btn_crear)
        );
    }

    /**
     * Limpia todos los errores visibles del formulario.
     */
    private void clearErrors() {
        binding.tilUsuario.setError(null);
        binding.tilUsuarioCorreo.setError(null);
        binding.tilFechaNacimiento.setError(null);
        binding.tilPassword.setError(null);
        binding.tilConfirmarPassword.setError(null);
        binding.tvEulaError.setVisibility(View.GONE);
    }

    /**
     * Libera la referencia al binding para evitar fugas de memoria.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
