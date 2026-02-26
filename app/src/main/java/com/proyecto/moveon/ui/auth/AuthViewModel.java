package com.proyecto.moveon.ui.auth;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.proyecto.moveon.data.session.AuthRepository;
import com.proyecto.moveon.data.session.SecureSessionManager;
import com.proyecto.moveon.ui.common.UiState;

public class AuthViewModel extends AndroidViewModel {

    private final AuthRepository authRepository;
    private final SecureSessionManager sessionManager;

    private final MutableLiveData<UiState<AuthRepository.LoginResult>> loginState = new MutableLiveData<>();
    private final MutableLiveData<UiState<String>> registerState = new MutableLiveData<>();

    public AuthViewModel(@NonNull Application app) {
        super(app);
        authRepository = new AuthRepository(app);
        sessionManager = new SecureSessionManager(app);
    }

    public LiveData<UiState<AuthRepository.LoginResult>> getLoginState() { return loginState; }
    public LiveData<UiState<String>> getRegisterState() { return registerState; }

    public void login(String identificador, String password) {
        loginState.setValue(UiState.loading());

        authRepository.login(identificador, password, new AuthRepository.Callback<AuthRepository.LoginResult>() {
            @Override
            public void onSuccess(AuthRepository.LoginResult result) {
                // guarda sesión aquí, no en Activity
                sessionManager.saveLogin(result.nombreUsuario, result.tokenAcceso, result.refreshToken);
                loginState.postValue(UiState.success(result));
            }

            @Override
            public void onError(String error) {
                loginState.postValue(UiState.error(error));
            }
        });
    }

    public void registerAndAutoLogin(AuthRepository.RegisterRequest req) {
        registerState.setValue(UiState.loading());

        authRepository.register(req, new AuthRepository.Callback<String>() {
            @Override
            public void onSuccess(String msg) {
                // Registro OK -> autologin
                authRepository.login(req.email, req.password, new AuthRepository.Callback<AuthRepository.LoginResult>() {
                    @Override
                    public void onSuccess(AuthRepository.LoginResult result) {
                        sessionManager.saveLogin(result.nombreUsuario, result.tokenAcceso, result.refreshToken);
                        registerState.postValue(UiState.success(msg));
                        // también puedes postear loginState si quieres
                        loginState.postValue(UiState.success(result));
                    }

                    @Override
                    public void onError(String error) {
                        registerState.postValue(UiState.error("Cuenta creada, pero no se pudo iniciar sesión: " + error));
                    }
                });
            }

            @Override
            public void onError(String error) {
                registerState.postValue(UiState.error(error));
            }
        });
    }

    @Override
    protected void onCleared() {
        // Cancela llamadas en curso al destruirse el VM
        authRepository.cancelAll();
        super.onCleared();
    }
}