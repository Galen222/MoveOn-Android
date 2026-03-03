package com.proyecto.moveon.ui.auth;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.proyecto.moveon.R;
import com.proyecto.moveon.core.api.ApiError;
import com.proyecto.moveon.data.session.AuthRepository;
import com.proyecto.moveon.data.session.SecureSessionManager;
import com.proyecto.moveon.domain.auth.LoginSession;
import com.proyecto.moveon.domain.auth.RegisterInput;
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
        authRepository = new AuthRepository(app);
        sessionManager = new SecureSessionManager(app);
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
                LoginSession s = result.data;
                if (s != null) {
                    sessionManager.saveLogin(s.nombreUsuario, s.tokenAcceso, s.refreshToken);
                    loginState.postValue(UiState.success(s));
                } else {
                    loginState.postValue(UiState.error(
                            ApiError.local(getApplication().getString(R.string.vm_error_respuesta_invalida))));
                }
            } else {
                loginState.postValue(UiState.error(result.error != null
                        ? result.error
                        : ApiError.local(getApplication().getString(R.string.vm_error_generico))));
            }
        });
    }

    // ── Registro + auto-login ────────────────────────────────────────────────

    public void registerAndAutoLogin(RegisterInput input) {
        registerState.setValue(UiState.loading());

        authRepository.register(input, regResult -> {
            if (!regResult.isSuccess()) {
                registerState.postValue(UiState.error(regResult.error != null
                        ? regResult.error
                        : ApiError.local(getApplication().getString(R.string.vm_error_generico))));
                return;
            }

            String msg = regResult.data != null
                    ? regResult.data
                    : getApplication().getString(R.string.vm_registro_completado);

            // Registro OK → auto-login
            authRepository.login(input.email, input.password, loginResult -> {
                if (loginResult.isSuccess() && loginResult.data != null) {
                    LoginSession s = loginResult.data;
                    sessionManager.saveLogin(s.nombreUsuario, s.tokenAcceso, s.refreshToken);
                    registerState.postValue(UiState.success(msg));
                    loginState.postValue(UiState.success(s));
                } else {
                    ApiError err = loginResult.error != null
                            ? loginResult.error
                            : ApiError.local(getApplication().getString(R.string.vm_error_generico));
                    registerState.postValue(UiState.error(ApiError.local(
                            getApplication().getString(R.string.vm_error_login_post_registro, err.getMessage())
                    )));
                }
            });
        });
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
                        : getApplication().getString(R.string.repo_recuperacion_enviada)));
            } else {
                forgotState.postValue(UiState.error(result.error != null
                        ? result.error
                        : ApiError.local(getApplication().getString(R.string.vm_error_generico))));
            }
        });
    }

    // ── Recuperación de contraseña — Paso 2 ─────────────────────────────────

    /**
     * Valida el código recibido por email y establece la nueva contraseña.
     * Resultado publicado en {@link #resetState}.
     */
    public void resetearPassword(String email, String codigo, String nuevaPassword) {
        resetState.setValue(UiState.loading());

        authRepository.resetearPassword(email, codigo, nuevaPassword, result -> {
            if (result.isSuccess()) {
                resetState.postValue(UiState.success(result.data != null
                        ? result.data
                        : getApplication().getString(R.string.repo_password_reseteada)));
            } else {
                resetState.postValue(UiState.error(result.error != null
                        ? result.error
                        : ApiError.local(getApplication().getString(R.string.vm_error_generico))));
            }
        });
    }

    // ────────────────────────────────────────────────────────────────────────

    @Override
    protected void onCleared() {
        authRepository.cancelAll();
        super.onCleared();
    }
}