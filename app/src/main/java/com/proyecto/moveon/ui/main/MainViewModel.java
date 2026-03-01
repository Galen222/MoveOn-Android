package com.proyecto.moveon.ui.main;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.proyecto.moveon.R;
import com.proyecto.moveon.core.api.ApiError;
import com.proyecto.moveon.core.api.ApiErrorType;
import com.proyecto.moveon.core.api.ApiResult;
import com.proyecto.moveon.data.session.AuthRepository;
import com.proyecto.moveon.data.session.SecureSessionManager;
import com.proyecto.moveon.domain.auth.LoginSession;
import com.proyecto.moveon.ui.common.Event;
import com.proyecto.moveon.utils.StringUtils;

public class MainViewModel extends AndroidViewModel {

    private final AuthRepository authRepository;
    private final SecureSessionManager sessionManager;

    private final MutableLiveData<Event<String>> sessionExpiredEvent = new MutableLiveData<>();
    private boolean silentRefreshAttempted = false;

    public MainViewModel(@NonNull Application application) {
        super(application);
        authRepository = new AuthRepository(application);
        sessionManager = new SecureSessionManager(application);
    }

    public LiveData<Event<String>> getSessionExpiredEvent() { return sessionExpiredEvent; }

    public boolean isLoggedIn() { return sessionManager.isLoggedIn(); }

    public void trySilentRefreshAtStartup() {
        if (silentRefreshAttempted) return;
        silentRefreshAttempted = true;

        String refreshToken = sessionManager.getRefreshToken();
        if (!StringUtils.hasText(refreshToken)) return;

        authRepository.refreshSession(refreshToken, new AuthRepository.Callback<LoginSession>() {
            @Override
            public void onResult(ApiResult<LoginSession> result) {
                if (result.isSuccess()) {
                    LoginSession s = result.data;
                    if (s == null) return;

                    String username = StringUtils.hasText(s.nombreUsuario)
                            ? s.nombreUsuario
                            : StringUtils.textOf(sessionManager.getUsername());

                    sessionManager.saveLogin(username, s.tokenAcceso, s.refreshToken);
                    return;
                }

                ApiError error = result.error != null ? result.error : ApiError.local(getApplication().getString(R.string.vm_error_generico));

                // Offline-first: si es red/timeout => NO expulses
                if (error.getType() == ApiErrorType.UNAUTHORIZED) {
                    sessionExpiredEvent.postValue(new Event<>(error.getMessage()));
                }
            }
        });
    }
}