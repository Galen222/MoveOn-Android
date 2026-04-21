package com.proyecto.moveon.ui.main;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

import com.proyecto.moveon.core.auth.GlobalAuthManager;
import com.proyecto.moveon.data.session.SecureSessionManager;
import com.proyecto.moveon.data.session.SessionRefreshCoordinator;
/**
 * ViewModel que expone el estado y las acciones de main.
 */
public class MainViewModel extends AndroidViewModel {

    private final SecureSessionManager sessionManager;
    private final SessionRefreshCoordinator sessionRefreshCoordinator;

    public MainViewModel(@NonNull Application application) {
        super(application);
        sessionManager = SecureSessionManager.getInstance(application);
        sessionRefreshCoordinator = SessionRefreshCoordinator.getInstance(application);
    }

    /**
     * Devuelve {@code true} si NO queda ninguna forma recuperable de sesión.
     * Para una app offline-first, la presencia de refresh token sigue contando
     * como sesión recuperable aunque el access se renueve silenciosamente después.
     */
    public boolean isNotLoggedIn() {
        return !sessionManager.hasRecoverableSession();
    }

    public void ensureSessionFresh() {
        if (!sessionManager.hasRefreshToken()) return;
        if (!sessionRefreshCoordinator.shouldRefreshProactively()) return;

        sessionRefreshCoordinator.ensureFreshSessionAsync(outcome -> {
            if (outcome.isUnauthorized()) {
                GlobalAuthManager.getInstance().notifySessionExpired();
            }
        });
    }
}
