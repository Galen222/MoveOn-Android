package com.proyecto.moveon.ui.main;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.proyecto.moveon.data.session.AuthRepository;
import com.proyecto.moveon.data.session.SecureSessionManager;
import com.proyecto.moveon.ui.common.Event;
import com.proyecto.moveon.ui.common.UiState;

import java.util.Locale;

public class MainViewModel extends AndroidViewModel {

    private final AuthRepository authRepository;
    private final SecureSessionManager sessionManager;

    private final MutableLiveData<UiState<Void>> silentRefreshState = new MutableLiveData<>();
    private final MutableLiveData<Event<String>> sessionExpiredEvent = new MutableLiveData<>();

    private boolean silentRefreshAttempted = false;

    public MainViewModel(@NonNull Application application) {
        super(application);
        authRepository = new AuthRepository(application);
        sessionManager = new SecureSessionManager(application);
    }

    public LiveData<UiState<Void>> getSilentRefreshState() {
        return silentRefreshState;
    }

    public LiveData<Event<String>> getSessionExpiredEvent() {
        return sessionExpiredEvent;
    }

    public void trySilentRefreshAtStartup() {
        if (silentRefreshAttempted) return;
        silentRefreshAttempted = true;

        String refreshToken = sessionManager.getRefreshToken();
        if (!hasText(refreshToken)) return;

        silentRefreshState.setValue(UiState.loading());

        authRepository.refreshSession(refreshToken, new AuthRepository.Callback<AuthRepository.LoginResult>() {
            @Override
            public void onSuccess(AuthRepository.LoginResult result) {
                String username = result.nombreUsuario;
                if (!hasText(username)) username = sessionManager.getUsername();
                if (username == null) username = "";

                sessionManager.saveLogin(username, result.tokenAcceso, result.refreshToken);
                silentRefreshState.postValue(UiState.success(null));
            }

            @Override
            public void onError(String error) {
                String msg = (error == null || error.trim().isEmpty()) ? "No se pudo renovar la sesión" : error;

                // Offline-first: si es red/timeout, NO expulsamos
                if (looksLikeInvalidRefresh(msg)) {
                    sessionExpiredEvent.postValue(new Event<>(msg));
                    return;
                }

                silentRefreshState.postValue(UiState.error(msg));
            }
        });
    }

    private boolean looksLikeInvalidRefresh(String error) {
        String e = error.toLowerCase(Locale.ROOT);

        return e.contains("refresh token inválido")
                || e.contains("refresh token invalido")
                || e.contains("refresh token expirado")
                || e.contains("refresh token reutilizado")
                || e.contains("no hay refresh token")
                || e.contains("error http 401")
                || e.contains("401");
    }

    private boolean hasText(String s) {
        return s != null && !s.trim().isEmpty();
    }

    @Override
    protected void onCleared() {
        authRepository.cancelAll();
        super.onCleared();
    }
}