package com.proyecto.moveon.data.session;

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Helper para centralizar refresh token:
 * - deduplica refresh concurrentes (si 5 peticiones fallan a la vez con 401, hace 1 solo refresh)
 * - actualiza SecureSessionManager
 * - clasifica "sesión expirada" vs "error temporal"
 */
public class SessionRefreshHelper {

    public interface Callback {
        void onSuccess(); // tokens renovados y guardados
        void onSessionExpired(String message); // refresh inválido/expirado/reutilizado -> logout recomendado
        void onError(String message); // red/timeout/servidor
    }

    private static final Object REFRESH_LOCK = new Object();
    private static boolean refreshInProgress = false;
    private static final List<Callback> pendingCallbacks = new ArrayList<>();

    private final Context appContext;
    private final SecureSessionManager sessionManager;
    private final AuthRepository authRepository;

    public SessionRefreshHelper(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        this.sessionManager = new SecureSessionManager(appContext);
        this.authRepository = new AuthRepository(appContext);
    }

    /**
     * Fuerza renovación usando refresh token guardado.
     */
    public void refreshIfNeeded(@NonNull Callback callback) {
        final String refreshToken = sessionManager.getRefreshToken();

        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            callback.onSessionExpired("No hay refresh token. Inicia sesión de nuevo.");
            return;
        }

        boolean shouldStartRefresh = false;

        synchronized (REFRESH_LOCK) {
            pendingCallbacks.add(callback);

            if (!refreshInProgress) {
                refreshInProgress = true;
                shouldStartRefresh = true;
            }
        }

        if (!shouldStartRefresh) {
            return; // ya hay un refresh en curso; este callback quedará en cola
        }

        authRepository.refreshSession(refreshToken, new AuthRepository.Callback<AuthRepository.LoginResult>() {
            @Override
            public void onSuccess(AuthRepository.LoginResult result) {
                // Mantiene username actual si backend lo devuelve vacío
                sessionManager.updateTokens(result.tokenAcceso, result.refreshToken);
                dispatchSuccess();
            }

            @Override
            public void onError(String error) {
                String safeError = (error == null || error.trim().isEmpty())
                        ? "No se pudo renovar la sesión"
                        : error;

                if (isRefreshTokenInvalidOrExpired(safeError)) {
                    // Seguridad: si el refresh ya no es válido, limpiamos sesión local
                    sessionManager.logout();
                    dispatchSessionExpired(safeError);
                } else {
                    dispatchError(safeError);
                }
            }
        });
    }

    private boolean isRefreshTokenInvalidOrExpired(String error) {
        String e = error.toLowerCase(Locale.ROOT);

        // Basado en mensajes actuales de tu backend:
        // inválido / inválido o reutilizado / reutilizado / expirado / etc.
        return e.contains("refresh token inválido")
                || e.contains("refresh token invalido")
                || e.contains("refresh token reutilizado")
                || e.contains("refresh token expirado")
                || e.contains("no hay refresh token");
    }

    private void dispatchSuccess() {
        List<Callback> callbacks;
        synchronized (REFRESH_LOCK) {
            refreshInProgress = false;
            callbacks = new ArrayList<>(pendingCallbacks);
            pendingCallbacks.clear();
        }

        for (Callback cb : callbacks) {
            cb.onSuccess();
        }
    }

    private void dispatchSessionExpired(String message) {
        List<Callback> callbacks;
        synchronized (REFRESH_LOCK) {
            refreshInProgress = false;
            callbacks = new ArrayList<>(pendingCallbacks);
            pendingCallbacks.clear();
        }

        for (Callback cb : callbacks) {
            cb.onSessionExpired(message);
        }
    }

    private void dispatchError(String message) {
        List<Callback> callbacks;
        synchronized (REFRESH_LOCK) {
            refreshInProgress = false;
            callbacks = new ArrayList<>(pendingCallbacks);
            pendingCallbacks.clear();
        }

        for (Callback cb : callbacks) {
            cb.onError(message);
        }
    }
}