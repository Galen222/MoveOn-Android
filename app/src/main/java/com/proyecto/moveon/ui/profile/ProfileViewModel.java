package com.proyecto.moveon.ui.profile;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.proyecto.moveon.data.session.AuthRepository;
import com.proyecto.moveon.data.session.SecureSessionManager;
import com.proyecto.moveon.ui.common.UiState;
import com.proyecto.moveon.utils.StringUtils;

public class ProfileViewModel extends AndroidViewModel {

    private final AuthRepository authRepository;
    private final SecureSessionManager sessionManager;
    // Aquí está tu UiState estandarizado para observar el proceso de logout
    private final MutableLiveData<UiState<String>> logoutState = new MutableLiveData<>();

    public ProfileViewModel(@NonNull Application application) {
        super(application);
        authRepository = new AuthRepository(application);
        sessionManager = new SecureSessionManager(application);
    }

    public LiveData<UiState<String>> getLogoutState() {
        return logoutState;
    }

    // Obtenemos los datos desde aquí para que la Vista no tenga que hablar con SecureSessionManager
    public String getUsername() {
        String username = sessionManager.getUsername();
        return StringUtils.hasText(username) ? username : null;
    }

    public void logout() {
        String refreshToken = sessionManager.getRefreshToken();
        // 1. Si no hay token de refresh, cerramos sesión localmente y emitimos éxito.
        if (!StringUtils.hasText(refreshToken)) {
            performLocalLogout();
            logoutState.setValue(UiState.success("Local"));
            return;
        }

        // 2. Avisamos a la vista de que empezamos a cargar ("Saliendo...")
        logoutState.setValue(UiState.loading());
        // 3. Llamamos a la API
        authRepository.logout(refreshToken, new AuthRepository.Callback<String>() {
            @Override
            public void onSuccess(String result) {
                performLocalLogout();
                logoutState.postValue(UiState.success(result));
            }

            @Override
            public void onError(String error) {
                performLocalLogout();
                logoutState.postValue(UiState.error(error));
            }
        });
    }

    private void performLocalLogout() {
        sessionManager.logout();
    }

    @Override
    protected void onCleared() {
        // Mágia anti-crashes: si el ViewModel muere, cancela la red
        authRepository.cancelAll();
        super.onCleared();
    }
}