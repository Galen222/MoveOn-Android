package com.proyecto.moveon.ui.auth;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.proyecto.moveon.R;
import com.proyecto.moveon.core.api.ApiError;
import com.proyecto.moveon.core.i18n.AppLanguageManager;
import com.proyecto.moveon.core.theme.ThemeManager;
import com.proyecto.moveon.core.validation.AppInputValidator;
import com.proyecto.moveon.databinding.ActivityLoginBinding;
import com.proyecto.moveon.ui.common.TopSnackbar;
import com.proyecto.moveon.ui.main.MainActivity;
import com.proyecto.moveon.utils.NavigationUtils;
import com.proyecto.moveon.utils.StringUtils;
/**
 * Pantalla de acceso clásico y social de la aplicación.
 *
 * <p>Orquesta la validación básica del formulario, el intercambio con {@link AuthViewModel}
 * y la integración de Google a través de {@link SocialAuthManager}.</p>
 */
public class LoginActivity extends AppCompatActivity implements SocialAuthManager.Listener {

    private ActivityLoginBinding binding;
    private AuthViewModel viewModel;
    private SocialAuthManager socialAuthManager;

    @Nullable private SocialGoogleAccount pendingGoogleAccount;
    private boolean pendingSilentGoogleLogin;

    /**
     * Envuelve el contexto base para aplicar el idioma seleccionado antes de inflar recursos.
     *
     * @param newBase contexto original de la actividad.
     */
    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(AppLanguageManager.wrapContext(newBase));
    }

    /**
     * Inicializa la pantalla de login, restaura la cuenta recordada y arranca el flujo social silencioso.
     *
     * @param savedInstanceState estado previo de la actividad, si existe.
     */
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

        socialAuthManager = new SocialAuthManager(this, this);

        loadRememberedAccount();
        setupListeners();
        observeViewModel();
        socialAuthManager.trySilentSignInWithGoogle(viewModel.shouldTrySilentGoogleSignIn());
    }

    /**
     * Recupera el identificador recordado para precargar el formulario y el estado del checkbox.
     */
    private void loadRememberedAccount() {
        String saved = viewModel.getRememberedIdentifier();
        boolean hasSaved = StringUtils.hasText(saved);
        binding.cbRecordarCuenta.setChecked(hasSaved);
        if (hasSaved) {
            binding.etUsuarioCorreo.setText(saved);
            binding.etUsuarioCorreo.setSelection(saved.length());
        }
    }

    /**
     * Conecta todas las acciones de la pantalla con su flujo correspondiente.
     */
    private void setupListeners() {
        binding.btnRegistrar.setOnClickListener(v -> startActivity(new Intent(this, RegisterActivity.class)));
        binding.btnLogin.setOnClickListener(v -> attemptLogin());
        binding.btnGoogleLogin.setOnClickListener(v -> {
            clearErrors();
            pendingGoogleAccount = null;
            pendingSilentGoogleLogin = false;
            setLoading(true);
            showGoogleLoading(
                    true,
                    false,
                    null,
                    R.string.social_google_loading_title,
                    R.string.social_google_loading_message_signin
            );
            socialAuthManager.signInWithGoogle();
        });

        binding.etUsuarioCorreo.setOnFocusChangeListener((v, f) -> { if (f) binding.tilUsuarioCorreo.setError(null); });
        binding.etPassword.setOnFocusChangeListener((v, f) -> { if (f) binding.tilPassword.setError(null); });

        binding.btnOlvidarPassword.setOnClickListener(
                v -> NavigationUtils.goToActivity(this, ForgotPasswordActivity.class)
        );
    }

    /**
     * Observa el estado del login para pintar carga, errores y navegación al home.
     */
    private void observeViewModel() {
        viewModel.getLoginState().observe(this, state -> {
            if (state == null) return;

            if (state.loading) {
                setLoading(true);
            }

            if (state.error != null) {
                boolean silent = pendingSilentGoogleLogin;
                pendingSilentGoogleLogin = false;
                showGoogleLoading(false, silent, null, 0, 0);
                setLoading(false);

                if (handleSocialNotRegistered(state.error, silent)) {
                    viewModel.resetLoginState();
                    return;
                }

                applyBackendErrors(state.error);
                if (!silent && !state.error.hasFieldErrors()) {
                    TopSnackbar.error(binding.getRoot(), state.error.getMessage());
                }
                viewModel.resetLoginState();
            }

            if (state.data != null) {
                pendingSilentGoogleLogin = false;
                showGoogleLoading(false, false, null, 0, 0);
                Toast.makeText(this,
                        getString(R.string.login_bienvenido, state.data.nombreUsuario),
                        Toast.LENGTH_SHORT).show();

                viewModel.resetLoginState();
                goToMain();
            }
        });
    }

    /**
     * Intercepta el caso en que Google devuelve una cuenta válida pero aún no registrada en backend.
     *
     * @param error error recibido del flujo social.
     * @param silent indica si el intento venía del login silencioso.
     * @return {@code true} si el error quedó consumido por esta pantalla.
     */
    private boolean handleSocialNotRegistered(@NonNull ApiError error, boolean silent) {
        if (!"SOCIAL_ACCOUNT_NOT_REGISTERED".equals(error.getErrorCode())) {
            return false;
        }
        pendingGoogleAccount = null;
        showGoogleLoading(false, silent, null, 0, 0);
        if (!silent) {
            TopSnackbar.warning(binding.getRoot(), getString(R.string.social_google_not_registered));
        }
        return true;
    }

    /**
     * Reparte los errores de backend entre los campos visibles del formulario.
     *
     * @param err error de API con posibles field errors.
     */
    private void applyBackendErrors(ApiError err) {
        String idMsg = err.firstFieldMessage("identificador", "email", "nombre_usuario", "usuario");
        String pwMsg = err.firstFieldMessage("password");

        if (StringUtils.hasText(idMsg)) binding.tilUsuarioCorreo.setError(idMsg);
        if (StringUtils.hasText(pwMsg)) binding.tilPassword.setError(pwMsg);
    }

    /**
     * Valida el formulario y dispara el login clásico con usuario o correo y contraseña.
     */
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

    /**
     * Habilita o bloquea los controles mientras hay una petición de autenticación en curso.
     *
     * @param loading {@code true} para mostrar estado ocupado.
     */
    private void setLoading(boolean loading) {
        binding.btnLogin.setEnabled(!loading);
        binding.btnRegistrar.setEnabled(!loading);
        binding.btnOlvidarPassword.setEnabled(!loading);
        binding.cbRecordarCuenta.setEnabled(!loading);
        binding.btnGoogleLogin.setEnabled(!loading);
        binding.tilUsuarioCorreo.setEnabled(!loading);
        binding.tilPassword.setEnabled(!loading);
        binding.btnLogin.setText(loading ? getString(R.string.login_btn_entrando) : getString(R.string.login_btn_entrar));
    }

    /**
     * Limpia los errores visibles del formulario antes de una nueva validación.
     */
    private void clearErrors() {
        binding.tilUsuarioCorreo.setError(null);
        binding.tilPassword.setError(null);
    }

    /**
     * Muestra u oculta el overlay de progreso del flujo de Google.
     *
     * @param visible indica si el overlay debe quedar visible.
     * @param silent señala si el intento es silencioso para ajustar la animación.
     * @param account cuenta resuelta para renderizar avatar, si existe.
     * @param titleRes recurso del título a mostrar.
     * @param messageRes recurso del mensaje descriptivo.
     */
    private void showGoogleLoading(boolean visible,
                                   boolean silent,
                                   @Nullable SocialGoogleAccount account,
                                   @StringRes int titleRes,
                                   @StringRes int messageRes) {
        if (visible) {
            if (titleRes != 0) binding.tvGoogleLoadingTitle.setText(titleRes);
            if (messageRes != 0) binding.tvGoogleLoadingMessage.setText(messageRes);
            renderLoadingAvatar(binding.ivGoogleLoadingAvatar, account);
            binding.overlayGoogleLoading.setVisibility(View.VISIBLE);
            binding.overlayGoogleLoading.setAlpha(0f);
            binding.cardGoogleLoading.setScaleX(0.96f);
            binding.cardGoogleLoading.setScaleY(0.96f);
            binding.overlayGoogleLoading.animate().alpha(1f).setDuration(silent ? 180 : 220).start();
            binding.cardGoogleLoading.animate().scaleX(1f).scaleY(1f).setDuration(silent ? 180 : 220).start();
            return;
        }
        if (binding.overlayGoogleLoading.getVisibility() != View.VISIBLE) {
            return;
        }
        binding.overlayGoogleLoading.animate().alpha(0f).setDuration(160).withEndAction(() -> {
            binding.overlayGoogleLoading.setVisibility(View.GONE);
            binding.overlayGoogleLoading.setAlpha(1f);
        }).start();
    }

    /**
     * Renderiza el avatar de la cuenta de Google o un icono por defecto en el overlay.
     *
     * @param imageView vista destino.
     * @param account cuenta seleccionada, opcional.
     */
    private void renderLoadingAvatar(@NonNull ImageView imageView, @Nullable SocialGoogleAccount account) {
        String avatarUrl = account != null ? account.avatarUrl : null;
        if (StringUtils.hasText(avatarUrl)) {
            Glide.with(this)
                    .load(avatarUrl)
                    .placeholder(R.drawable.ic_google)
                    .error(R.drawable.ic_google)
                    .circleCrop()
                    .into(imageView);
            return;
        }
        imageView.setImageResource(R.drawable.ic_google);
    }

    /**
     * Navega a {@link MainActivity} limpiando la pila de navegación del login.
     */
    private void goToMain() {
        NavigationUtils.goToActivityAndClearTask(this, MainActivity.class);
    }

    /**
     * Recibe la cuenta resuelta por Google y delega el intercambio de token al {@link AuthViewModel}.
     *
     * @param account cuenta autenticada por Google.
     * @param silent indica si el flujo venía de un intento silencioso.
     */
    @Override
    public void onGoogleAccountReady(@NonNull SocialGoogleAccount account, boolean silent) {
        pendingGoogleAccount = account;
        pendingSilentGoogleLogin = silent;
        if (!silent) {
            setLoading(true);
            showGoogleLoading(
                    true,
                    false,
                    account,
                    R.string.social_google_loading_title,
                    R.string.social_google_loading_message_signin
            );
        }
        viewModel.loginWithSocial(com.proyecto.moveon.domain.auth.SocialAuthProvider.GOOGLE, account.idToken);
    }

    /**
     * Muestra el error del proveedor social y restablece la pantalla si el fallo era visible para el usuario.
     *
     * @param message mensaje de error a mostrar.
     * @param silent indica si el fallo procede de un intento silencioso.
     */
    @Override
    public void onSocialFlowError(@NonNull String message, boolean silent) {
        showGoogleLoading(false, silent, null, 0, 0);
        if (!silent) {
            setLoading(false);
            TopSnackbar.error(binding.getRoot(), message);
        }
    }

    /**
     * Restaura la pantalla cuando el usuario cancela el flujo social manualmente.
     */
    @Override
    public void onSocialFlowCanceled() {
        showGoogleLoading(false, false, null, 0, 0);
        setLoading(false);
        TopSnackbar.warning(binding.getRoot(), getString(R.string.social_auth_canceled));
    }

    /**
     * Libera el binding al destruir la actividad.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}