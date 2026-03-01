package com.proyecto.moveon.ui.profile;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.proyecto.moveon.core.api.ApiError;
import com.proyecto.moveon.core.api.ApiResult;
import com.proyecto.moveon.data.session.AuthRepository;
import com.proyecto.moveon.data.session.SecureSessionManager;
import com.proyecto.moveon.ui.common.UiState;
import com.proyecto.moveon.utils.StringUtils;

public class ProfileViewModel extends AndroidViewModel {

    private final AuthRepository authRepository;
    private final SecureSessionManager sessionManager;
    private final MutableLiveData<UiState<String>> logoutState = new MutableLiveData<>();

    public ProfileViewModel(@NonNull Application application) {
        super(application);
        authRepository = new AuthRepository(application);
        sessionManager = new SecureSessionManager(application);
    }

    public LiveData<UiState<String>> getLogoutState() { return logoutState; }

    public String getUsername() {
        String username = sessionManager.getUsername();
        return StringUtils.hasText(username) ? username : null;
    }

    public void logout() {
        String refreshToken = sessionManager.getRefreshToken();

        if (!StringUtils.hasText(refreshToken)) {
            performLocalLogout();
            logoutState.setValue(UiState.success("Local"));
            return;
        }

        logoutState.setValue(UiState.loading());

        authRepository.logout(refreshToken, new AuthRepository.Callback<String>() {
            @Override
            public void onResult(ApiResult<String> result) {
                // Siempre limpiamos local
                performLocalLogout();

                if (result.isSuccess()) {
                    logoutState.postValue(UiState.success(result.data != null ? result.data : "OK"));
                } else {
                    logoutState.postValue(UiState.error(result.error != null ? result.error : ApiError.local("Error")));
                }
            }
        });
    }

    private void performLocalLogout() {
        sessionManager.logout();
    }

    @Override
    protected void onCleared() {
        authRepository.cancelAll();
        super.onCleared();
    }
}
