
package com.proyecto.moveon.ui.auth;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.proyecto.moveon.R;
import com.proyecto.moveon.core.i18n.AppLanguageManager;
import com.proyecto.moveon.app.ServiceLocator;
import com.proyecto.moveon.core.api.ApiError;
import com.proyecto.moveon.core.api.ApiResult;
import com.proyecto.moveon.data.session.AuthRepository;
import com.proyecto.moveon.data.session.SecureSessionManager;
import com.proyecto.moveon.domain.auth.LoginSession;
import com.proyecto.moveon.domain.auth.RegisterInput;
import com.proyecto.moveon.domain.auth.SocialRegisterInput;
import com.proyecto.moveon.ui.common.UiState;

public class AuthViewModel extends AndroidViewModel {

    private final AuthRepository authRepository;
    private final SecureSessionManager sessionManager;

    private final MutableLiveData<UiState<LoginSession>> loginState    = new MutableLiveData<>();
    private final MutableLiveData<UiState<String>>       registerState = new MutableLiveData<>();

    /** Paso 1 de recuperación: solicitar envío del código al email. */
    private final MutableLiveData<UiState<String>> forgotState = new MutableLiveData<>();

    /** Paso 2 de recuperación: validar código y establecer nueva contraseña. */
    private final MutableLiveData<UiState<String>> resetState  = new MutableLiveData<>();

    public AuthViewModel(@NonNull Application app) {
        super(app);
        // MEJ-01: Creación centralizada vía ServiceLocator.
        authRepository = ServiceLocator.getInstance(app).newAuthRepository();
        // BUG-08: Singleton en lugar de new para evitar múltiples instancias.
        sessionManager = SecureSessionManager.getInstance(app);
    }

    // ── Exposición de LiveData ────────────────────────────────────────────────

    public LiveData<UiState<LoginSession>> getLoginState()    { return loginState; }
    public LiveData<UiState<String>>       getRegisterState() { return registerState; }
    public LiveData<UiState<String>>       getForgotState()   { return forgotState; }
    public LiveData<UiState<String>>       getResetState()    { return resetState; }

    // ── Reset de estados ─────────────────────────────────────────────────────

    public void resetLoginState()    { loginState.setValue(null); }
    public void resetRegisterState() { registerState.setValue(null); }
    public void resetForgotState()   { forgotState.setValue(null); }
    public void resetResetState()    { resetState.setValue(null); }

    // ── Sesión ───────────────────────────────────────────────────────────────

    public boolean isLoggedIn() { return sessionManager.isLoggedIn(); }

    public String getRememberedIdentifier() { return sessionManager.getRememberedIdentifier(); }

    public void saveRememberedIdentifier(String identifier, boolean remember) {
        if (remember) sessionManager.saveRememberedIdentifier(identifier);
        else          sessionManager.saveRememberedIdentifier(null);
    }

    // ── Login ────────────────────────────────────────────────────────────────

    public void login(String identificador, String password) {
        loginState.setValue(UiState.loading());

        authRepository.login(identificador, password, result -> {
            if (result.isSuccess()) {
                handleLoginSuccess(result, loginState);
            } else {
                loginState.postValue(UiState.error(errorOrDefault(result)));
            }
        });
    }

    public void loginWithSocial(String provider, String token) {
        loginState.setValue(UiState.loading());

        authRepository.loginSocial(provider, token, result -> {
            if (result.isSuccess()) {
                handleLoginSuccess(result, loginState);
            } else {
                loginState.postValue(UiState.error(errorOrDefault(result)));
            }
        });
    }

    // ── Registro + auto-login ────────────────────────────────────────────────

    // MEJ-03: Callback hell eliminado extrayendo cada nivel a un método con
    // nombre descriptivo. Antes: register(callback → login(callback → ...)).

    public void registerAndAutoLogin(RegisterInput input) {
        registerState.setValue(UiState.loading());
        authRepository.register(input, regResult -> handleRegisterResult(input, regResult));
    }

    public void registerWithSocial(SocialRegisterInput input) {
        registerState.setValue(UiState.loading());
        authRepository.registerSocial(input, result -> handleSocialRegisterResult(result));
    }

    private void handleRegisterResult(RegisterInput input, ApiResult<String> regResult) {
        if (!regResult.isSuccess()) {
            registerState.postValue(UiState.error(errorOrDefault(regResult)));
            return;
        }

        String msg = regResult.data != null
                ? regResult.data
                : getString(R.string.vm_registro_completado);

        // Registro OK → auto-login
        authRepository.login(input.email, input.password,
                loginResult -> handleAutoLoginResult(msg, loginResult));
    }

    private void handleAutoLoginResult(String registerMsg, ApiResult<LoginSession> loginResult) {
        if (loginResult.isSuccess() && loginResult.data != null) {
            LoginSession s = loginResult.data;
            sessionManager.saveLogin(s.nombreUsuario, s.tokenAcceso, s.refreshToken);
            registerState.postValue(UiState.success(registerMsg));
            loginState.postValue(UiState.success(s));
        } else {
            ApiError err = errorOrDefault(loginResult);
            registerState.postValue(UiState.error(ApiError.local(
                    getString(R.string.vm_error_login_post_registro, err.getMessage())
            )));
        }
    }

    private void handleSocialRegisterResult(@NonNull ApiResult<LoginSession> result) {
        if (result.isSuccess() && result.data != null) {
            LoginSession s = result.data;
            sessionManager.saveLogin(s.nombreUsuario, s.tokenAcceso, s.refreshToken);
            registerState.postValue(UiState.success(getString(R.string.vm_registro_social_completado)));
            loginState.postValue(UiState.success(s));
        } else {
            registerState.postValue(UiState.error(errorOrDefault(result)));
        }
    }

    // ── Recuperación de contraseña — Paso 1 ─────────────────────────────────

    /**
     * Solicita al backend el envío de un código de recuperación al email indicado.
     * Resultado publicado en {@link #forgotState}.
     */
    public void solicitarRecuperacion(String email) {
        forgotState.setValue(UiState.loading());

        authRepository.solicitarRecuperacion(email, result -> {
            if (result.isSuccess()) {
                forgotState.postValue(UiState.success(result.data != null
                        ? result.data
                        : getString(R.string.repo_recuperacion_enviada)));
            } else {
                forgotState.postValue(UiState.error(errorOrDefault(result)));
            }
        });
    }

    // ── Recuperación de contraseña — Paso 2 ─────────────────────────────────

    /**
     * Se valida el código recibido por email y establece la nueva contraseña.
     * Resultado publicado en {@link #resetState}.
     */
    public void resetearPassword(String email, String codigo, String nuevaPassword) {
        resetState.setValue(UiState.loading());

        authRepository.resetearPassword(email, codigo, nuevaPassword, result -> {
            if (result.isSuccess()) {
                resetState.postValue(UiState.success(result.data != null
                        ? result.data
                        : getString(R.string.repo_password_reseteada)));
            } else {
                resetState.postValue(UiState.error(errorOrDefault(result)));
            }
        });
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    // MEJ-03: Extraído patrón repetitivo de "result.error ?? error genérico".

    @NonNull
    private ApiError errorOrDefault(@NonNull ApiResult<?> result) {
        return result.error != null
                ? result.error
                : ApiError.local(getString(R.string.vm_error_generico));
    }

    private void handleLoginSuccess(@NonNull ApiResult<LoginSession> result,
                                    @NonNull MutableLiveData<UiState<LoginSession>> target) {
        LoginSession s = result.data;
        if (s != null) {
            sessionManager.saveLogin(s.nombreUsuario, s.tokenAcceso, s.refreshToken);
            target.postValue(UiState.success(s));
        } else {
            target.postValue(UiState.error(
                    ApiError.local(getString(R.string.vm_error_respuesta_invalida))));
        }
    }

    @NonNull
    private String getString(int resId, Object... args) {
        if (args.length == 0) return AppLanguageManager.getString(getApplication(), resId);
        return AppLanguageManager.getString(getApplication(), resId, args);
    }

    // ────────────────────────────────────────────────────────────────────────

    @Override
    protected void onCleared() {
        authRepository.cancelAll();
        super.onCleared();
    }
}

