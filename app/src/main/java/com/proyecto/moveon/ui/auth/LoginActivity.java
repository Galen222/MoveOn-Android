package com.proyecto.moveon.ui.auth;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

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

public class LoginActivity extends AppCompatActivity implements SocialAuthManager.Listener {

    private ActivityLoginBinding binding;
    private AuthViewModel viewModel;
    private SocialAuthManager socialAuthManager;

    @Nullable private SocialGoogleAccount pendingGoogleAccount;
    private boolean pendingSilentGoogleLogin;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(AppLanguageManager.wrapContext(newBase));
    }

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
        binding.btnRegistrar.setOnClickListener(v -> openRegisterWithFade(null));
        binding.btnLogin.setOnClickListener(v -> attemptLogin());
        binding.btnGoogleLogin.setOnClickListener(v -> {
            clearErrors();
            setLoading(true);
            pendingSilentGoogleLogin = false;
            socialAuthManager.signInWithGoogle();
        });

        binding.etUsuarioCorreo.setOnFocusChangeListener((v, f) -> { if (f) binding.tilUsuarioCorreo.setError(null); });
        binding.etPassword.setOnFocusChangeListener((v, f) -> { if (f) binding.tilPassword.setError(null); });

        binding.btnOlvidarPassword.setOnClickListener(v -> {
            NavigationUtils.goToActivity(this, ForgotPasswordActivity.class);
            applyFadeOpenTransition();
        });
    }

    private void observeViewModel() {
        viewModel.getLoginState().observe(this, state -> {
            if (state == null) return;

            if (state.loading) {
                setLoading(true);
            }

            if (state.error != null) {
                boolean silent = pendingSilentGoogleLogin;
                pendingSilentGoogleLogin = false;
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
                Toast.makeText(this,
                        getString(R.string.login_bienvenido, state.data.nombreUsuario),
                        Toast.LENGTH_SHORT).show();

                viewModel.resetLoginState();
                goToMain();
            }
        });
    }

    private boolean handleSocialNotRegistered(@NonNull ApiError error, boolean silent) {
        if (!"SOCIAL_ACCOUNT_NOT_REGISTERED".equals(error.getErrorCode())) {
            return false;
        }
        if (silent) {
            return true;
        }
        if (pendingGoogleAccount != null) {
            Intent intent = new Intent(this, RegisterActivity.class)
                    .putExtra(RegisterActivity.EXTRA_GOOGLE_ID_TOKEN, pendingGoogleAccount.idToken)
                    .putExtra(RegisterActivity.EXTRA_GOOGLE_DISPLAY_NAME, pendingGoogleAccount.displayName)
                    .putExtra(RegisterActivity.EXTRA_GOOGLE_AVATAR_URL, pendingGoogleAccount.avatarUrl)
                    .putExtra(RegisterActivity.EXTRA_GOOGLE_EMAIL, pendingGoogleAccount.email)
                    .putExtra(RegisterActivity.EXTRA_OPENED_FROM_LOGIN_SOCIAL, true);
            openRegisterWithFade(intent);
            TopSnackbar.warning(binding.getRoot(), getString(R.string.social_google_not_registered));
        } else {
            TopSnackbar.warning(binding.getRoot(), getString(R.string.social_google_not_registered));
        }
        return true;
    }


    /**
     * Abre la pantalla de registro con un fade real también en Android 10+.
     *
     * <p>Se evita delegar esta navegación en utilidades genéricas porque aquí sí queremos
     * forzar explícitamente la animación de entrada. Si no se hace así, algunos dispositivos
     * muestran la transición vertical por defecto del sistema.</p>
     *
     * @param customIntent intent ya preparado para registro social. Si es {@code null}, se abre
     *                     el registro estándar.
     */
    private void openRegisterWithFade(@Nullable Intent customIntent) {
        Intent intent = customIntent != null ? customIntent : new Intent(this, RegisterActivity.class);
        startActivity(intent);
        applyImmediateFadeTransition();
    }

    /**
     * Aplica el fade de navegación inmediatamente después de lanzar otra Activity.
     *
     * <p>En Android 14+ se usa la API nueva. En versiones anteriores se mantiene
     * el fallback clásico, pero encapsulado y suprimiendo solo el warning local de
     * deprecación para no ensuciar la compilación.</p>
     */
    @SuppressWarnings("deprecation")
    private void applyImmediateFadeTransition() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                    OVERRIDE_TRANSITION_OPEN,
                    android.R.anim.fade_in,
                    android.R.anim.fade_out
            );
        } else {
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        }
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
        binding.btnGoogleLogin.setEnabled(!loading);
        binding.tilUsuarioCorreo.setEnabled(!loading);
        binding.tilPassword.setEnabled(!loading);
        binding.btnLogin.setText(loading ? getString(R.string.login_btn_entrando) : getString(R.string.login_btn_entrar));
    }

    private void clearErrors() {
        binding.tilUsuarioCorreo.setError(null);
        binding.tilPassword.setError(null);
    }

    private void goToMain() {
        NavigationUtils.goToActivityAndClearTask(this, MainActivity.class);
        applyFadeOpenTransition();
    }

    private void applyFadeOpenTransition() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                    OVERRIDE_TRANSITION_OPEN,
                    android.R.anim.fade_in,
                    android.R.anim.fade_out
            );
        }
    }

    @Override
    public void onGoogleAccountReady(@NonNull SocialGoogleAccount account, boolean silent) {
        pendingGoogleAccount = account;
        pendingSilentGoogleLogin = silent;
        if (!silent) {
            setLoading(true);
        }
        viewModel.loginWithSocial(com.proyecto.moveon.domain.auth.SocialAuthProvider.GOOGLE, account.idToken);
    }

    @Override
    public void onSocialFlowError(@NonNull String message, boolean silent) {
        if (!silent) {
            setLoading(false);
            TopSnackbar.error(binding.getRoot(), message);
        }
    }

    @Override
    public void onSocialFlowCanceled() {
        setLoading(false);
        TopSnackbar.warning(binding.getRoot(), getString(R.string.social_auth_canceled));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
