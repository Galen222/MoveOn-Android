package com.proyecto.moveon.ui.main;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

import com.proyecto.moveon.core.auth.GlobalAuthManager;
import com.proyecto.moveon.data.session.SecureSessionManager;
import com.proyecto.moveon.data.session.SessionRefreshCoordinator;

/**
 * ViewModel que expone el estado y las acciones de main.
 *
 * <p>Actúa como puente entre {@link SecureSessionManager} y {@link SessionRefreshCoordinator}
 * para que {@code MainActivity} pueda comprobar si la sesión es recuperable y refrescarla de
 * forma proactiva sin conocer detalles del almacenamiento seguro.</p>
 */
public class MainViewModel extends AndroidViewModel {

    private final SecureSessionManager sessionManager;
    private final SessionRefreshCoordinator sessionRefreshCoordinator;

    /**
     * Inicializa el ViewModel de la actividad principal: toma el
     * gestor seguro de sesión y el coordinador de refresco para poder
     * mantener la sesión viva mientras el usuario navega.
     *
     * @param application application desde la que se obtienen los singletons de sesión.
     */
    public MainViewModel(@NonNull Application application) {
        super(application);
        sessionManager = SecureSessionManager.getInstance(application);
        sessionRefreshCoordinator = SessionRefreshCoordinator.getInstance(application);
    }

    /**
     * Devuelve {@code true} si NO queda ninguna forma recuperable de sesión.
     *
     * <p>Para una app offline-first, la presencia de refresh token sigue contando como sesión
     * recuperable aunque el access se renueve silenciosamente después.</p>
     *
     * @return {@code true} cuando el usuario ya no puede restaurar la sesión actual.
     * @see SecureSessionManager#hasRecoverableSession()
     */
    public boolean isNotLoggedIn() {
        return !sessionManager.hasRecoverableSession();
    }

    /**
     * Comprueba de forma proactiva que el access token sigue vigente y, si está cerca de caducar,
     * lanza un refresco en background.
     *
     * <p>Si el backend responde 401 (refresh token revocado o expirado), notifica a
     * {@link GlobalAuthManager} para cerrar sesión y volver a login.</p>
     *
     * <p>No hace nada si no hay refresh token guardado ({@link SecureSessionManager#hasRefreshToken()}
     * devuelve {@code false}) o si el coordinador considera con
     * {@link SessionRefreshCoordinator#shouldRefreshProactively()} que aún no es momento de refrescar.</p>
     *
     * @see SessionRefreshCoordinator#ensureFreshSessionAsync(SessionRefreshCoordinator.Callback)
     * @see GlobalAuthManager#notifySessionExpired()
     */
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
