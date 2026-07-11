package com.proyecto.moveon.ui.auth;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.proyecto.moveon.R;
import com.proyecto.moveon.core.i18n.AppLanguageManager;
import com.proyecto.moveon.app.ServiceLocator;
import com.proyecto.moveon.core.api.ApiError;
import com.proyecto.moveon.core.api.ApiResult;
import com.proyecto.moveon.core.settings.AppSettingsManager;
import com.proyecto.moveon.data.session.AuthRepository;
import com.proyecto.moveon.data.session.SecureSessionManager;
import com.proyecto.moveon.domain.auth.LoginSession;
import com.proyecto.moveon.domain.auth.RegisterInput;
import com.proyecto.moveon.domain.auth.SocialRegisterInput;
import com.proyecto.moveon.domain.auth.SocialAuthProvider;
import com.proyecto.moveon.ui.common.UiState;
/**
 * ViewModel que expone el estado y las acciones de auth.
 */
public class AuthViewModel extends AndroidViewModel {

    private final AuthRepository authRepository;
    private final SecureSessionManager sessionManager;

    private final MutableLiveData<UiState<LoginSession>> loginState    = new MutableLiveData<>();
    private final MutableLiveData<UiState<String>>       registerState = new MutableLiveData<>();

    /** Paso 1 de recuperación: solicitar envío del código al email. */
    private final MutableLiveData<UiState<String>> forgotState = new MutableLiveData<>();

    /** Paso 2 de recuperación: validar código y establecer nueva contraseña. */
    private final MutableLiveData<UiState<String>> resetState  = new MutableLiveData<>();

    /**
     * Crea el ViewModel resolviendo sus dependencias de autenticación y sesión.
     *
     * @param app aplicación usada para obtener singletons y recursos localizados.
     */
    public AuthViewModel(@NonNull Application app) {
        super(app);
        // La creación centralizada vía ServiceLocator mantiene las dependencias alineadas.
        authRepository = ServiceLocator.getInstance(app).newAuthRepository();
        // Reutiliza el singleton para mantener un único gestor de sesión seguro.
        sessionManager = SecureSessionManager.getInstance(app);
    }

    // ── Exposición de LiveData ────────────────────────────────────────────────

    /**
     * Expone el estado del login clásico o social.
     *
     * @return flujo observable con valores de {@link UiState} sobre {@link LoginSession}.
     */
    public LiveData<UiState<LoginSession>> getLoginState()    { return loginState; }
    /**
     * Expone el estado del registro.
     *
     * @return flujo observable con mensajes del resultado de registro.
     */
    public LiveData<UiState<String>>       getRegisterState() { return registerState; }
    /**
     * Expone el estado del paso de solicitud de recuperación.
     *
     * @return flujo observable asociado a {@link #solicitarRecuperacion(String)}.
     */
    public LiveData<UiState<String>>       getForgotState()   { return forgotState; }
    /**
     * Expone el estado del reseteo final de contraseña.
     *
     * @return flujo observable asociado a {@link #resetearPassword(String, String, String)}.
     */
    public LiveData<UiState<String>>       getResetState()    { return resetState; }

    // ── Reset de estados ─────────────────────────────────────────────────────

    /**
     * Limpia el último estado publicado de login.
     */
    public void resetLoginState()    { loginState.setValue(null); }
    /**
     * Limpia el último estado publicado de registro.
     */
    public void resetRegisterState() { registerState.setValue(null); }
    /**
     * Limpia el último estado publicado del envío de código de recuperación.
     */
    public void resetForgotState()   { forgotState.setValue(null); }
    /**
     * Limpia el último estado publicado del cambio de contraseña.
     */
    public void resetResetState()    { resetState.setValue(null); }

    // ── Sesión ───────────────────────────────────────────────────────────────

    /**
     * Indica si existe una sesión autenticada utilizable.
     *
     * @return {@code true} cuando {@link SecureSessionManager} conserva credenciales válidas.
     */
    public boolean isLoggedIn() { return sessionManager.isLoggedIn(); }

    /**
     * Recupera el identificador recordado para precargar el formulario de acceso.
     *
     * @return email o username persistido, o {@code null} si no hay dato recordado.
     */
    public String getRememberedIdentifier() { return sessionManager.getRememberedIdentifier(); }

    /**
     * Devuelve {@code true} solo cuando la última sesión recuperable pertenece a Google.
     *
     * <p>Esto evita lanzar comprobaciones silenciosas de Google en instalaciones o sesiones
     * que nunca se autenticaron con ese provider.</p>
     *
     * @return {@code true} cuando compensa intentar una recuperación silenciosa con Google.
     */
    public boolean shouldTrySilentGoogleSignIn() {
        return !sessionManager.hasRecoverableSession()
                && SocialAuthProvider.GOOGLE.equals(sessionManager.getAuthProvider())
                && AppSettingsManager.isGoogleSilentSignInEnabled(getApplication());
    }

    /**
     * Guarda o elimina el identificador recordado según la preferencia del usuario.
     *
     * @param identifier valor introducido en el formulario de acceso.
     * @param remember {@code true} para persistirlo y {@code false} para limpiarlo.
     */
    public void saveRememberedIdentifier(String identifier, boolean remember) {
        if (remember) sessionManager.saveRememberedIdentifier(identifier);
        else          sessionManager.saveRememberedIdentifier(null);
    }

    // ── Login ────────────────────────────────────────────────────────────────

    /**
     * Ejecuta el login clásico con identificador y contraseña.
     *
     * @param identificador email o nombre de usuario enviado al backend.
     * @param password contraseña en claro introducida por el usuario.
     */
    public void login(String identificador, String password) {
        loginState.setValue(UiState.loading());

        authRepository.login(identificador, password, result -> {
            if (result.isSuccess()) {
                handleLoginSuccess(result, loginState, null);
            } else {
                loginState.postValue(UiState.error(errorOrDefault(result)));
            }
        });
    }

    /**
     * Ejecuta el login social intercambiando el token del proveedor por una sesión propia.
     *
     * @param provider identificador del proveedor social, por ejemplo {@link SocialAuthProvider#GOOGLE}.
     * @param token token emitido por el proveedor externo.
     */
    public void loginWithSocial(String provider, String token) {
        loginState.setValue(UiState.loading());

        authRepository.loginSocial(provider, token, result -> {
            if (result.isSuccess()) {
                handleLoginSuccess(result, loginState, provider);
            } else {
                loginState.postValue(UiState.error(errorOrDefault(result)));
            }
        });
    }

    // ── Registro + auto-login ────────────────────────────────────────────────

    // El flujo se divide en métodos con nombre descriptivo para mantener
    // legible la secuencia register → login → persistencia de sesión.

    /**
     * Lanza el registro clásico y, si tiene éxito, encadena automáticamente el login.
     *
     * @param input datos de alta necesarios para el registro.
     */
    public void registerAndAutoLogin(RegisterInput input) {
        registerState.setValue(UiState.loading());
        authRepository.register(input, regResult -> handleRegisterResult(input, regResult));
    }

    /**
     * Completa el registro social con los datos ya validados por el proveedor.
     *
     * @param input información necesaria para finalizar el alta social.
     */
    public void registerWithSocial(SocialRegisterInput input) {
        registerState.setValue(UiState.loading());
        authRepository.registerSocial(input, result -> handleSocialRegisterResult(result));
    }

    /**
     * Procesa la respuesta del registro clásico y decide si debe lanzar el auto-login.
     *
     * @param input datos originales del registro, reutilizados para el login posterior.
     * @param regResult resultado devuelto por {@link AuthRepository#register(RegisterInput, AuthRepository.Callback)}.
     */
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

    /**
     * Completa el flujo registro → login persistiendo la sesión o publicando el error final.
     *
     * @param registerMsg mensaje de éxito del registro que se conserva para la UI.
     * @param loginResult resultado del login lanzado tras registrar al usuario.
     */
    private void handleAutoLoginResult(String registerMsg, ApiResult<LoginSession> loginResult) {
        if (loginResult.isSuccess() && loginResult.data != null) {
            LoginSession s = loginResult.data;
            sessionManager.saveLoginWithProvider(s.nombreUsuario, s.tokenAcceso, s.refreshToken, null);
            registerState.postValue(UiState.success(registerMsg));
            loginState.postValue(UiState.success(s));
        } else {
            ApiError err = errorOrDefault(loginResult);
            registerState.postValue(UiState.error(ApiError.local(
                    getString(R.string.vm_error_login_post_registro, err.getMessage())
            )));
        }
    }

    /**
     * Procesa el resultado final del registro social persistiendo la sesión cuando procede.
     *
     * @param result resultado devuelto por el backend con la sesión creada.
     */
    private void handleSocialRegisterResult(@NonNull ApiResult<LoginSession> result) {
        if (result.isSuccess() && result.data != null) {
            LoginSession s = result.data;
            sessionManager.saveLoginWithProvider(s.nombreUsuario, s.tokenAcceso, s.refreshToken, SocialAuthProvider.GOOGLE);
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
     *
     * @param email correo al que debe enviarse el código de recuperación.
     */
    public void solicitarRecuperacion(String email) {
        forgotState.setValue(UiState.loading());

        String locale = AppLanguageManager.sanitizeSelectableMode(
                AppLanguageManager.getResolvedLanguageTag(getApplication())
        );

        authRepository.solicitarRecuperacion(email, locale, result -> {
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
     * Valida el código recibido por email y establece la nueva contraseña.
     * Resultado publicado en {@link #resetState}.
     *
     * @param email cuenta para la que se está reseteando la contraseña.
     * @param codigo código de recuperación recibido por el usuario.
     * @param nuevaPassword nueva contraseña en claro introducida en el formulario.
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

    // Centraliza la selección entre el error devuelto por la API y el fallback genérico.

    /**
     * Obtiene el error real del resultado o fabrica un fallback genérico localizado.
     *
     * @param result resultado cuyo error se quiere normalizar.
     * @return error efectivo listo para publicarse en la UI.
     */
    @NonNull
    private ApiError errorOrDefault(@NonNull ApiResult<?> result) {
        return result.error != null
                ? result.error
                : ApiError.local(getString(R.string.vm_error_generico));
    }

    /**
     * Persiste la sesión recibida y publica el estado de éxito sobre el destino indicado.
     *
     * @param result resultado que contiene la sesión autenticada.
     * @param target LiveData donde se publicará el éxito o el error de respuesta inválida.
     * @param authProvider proveedor usado en el login, o {@code null} para login clásico.
     */
    private void handleLoginSuccess(@NonNull ApiResult<LoginSession> result,
                                    @NonNull MutableLiveData<UiState<LoginSession>> target,
                                    @Nullable String authProvider) {
        LoginSession s = result.data;
        if (s != null) {
            sessionManager.saveLoginWithProvider(s.nombreUsuario, s.tokenAcceso, s.refreshToken, authProvider);
            target.postValue(UiState.success(s));
        } else {
            target.postValue(UiState.error(
                    ApiError.local(getString(R.string.vm_error_respuesta_invalida))));
        }
    }

    /**
     * Resuelve un recurso string usando el idioma efectivo de la aplicación.
     *
     * @param resId identificador del recurso.
     * @param args argumentos opcionales de formateo.
     * @return cadena localizada resuelta con {@link AppLanguageManager}.
     */
    @NonNull
    private String getString(int resId, Object... args) {
        if (args.length == 0) return AppLanguageManager.getString(getApplication(), resId);
        return AppLanguageManager.getString(getApplication(), resId, args);
    }

    // ────────────────────────────────────────────────────────────────────────

    /**
     * Cancela peticiones en curso antes de liberar el ViewModel.
     */
    @Override
    protected void onCleared() {
        authRepository.cancelAll();
    }
}
