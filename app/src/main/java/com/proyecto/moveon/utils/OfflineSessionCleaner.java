package com.proyecto.moveon.utils;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.WorkManager;

import com.proyecto.moveon.data.activities.ActivityRepository;
import com.proyecto.moveon.data.local.db.AppDatabase;
import com.proyecto.moveon.data.profile.PerfilRepository;
import com.proyecto.moveon.data.profile.local.ProfilePhotoStorage;
import com.proyecto.moveon.data.session.SecureSessionManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Limpia sesión y datos locales cuando la cuenta se cierra o se elimina.
 *
 * <p>Delegar el logout en {@link SecureSessionManager} garantiza que, además de los tokens,
 * también se reinicien el provider persistido y el silent sign-in de Google.</p>
 */
public final class OfflineSessionCleaner {

    private OfflineSessionCleaner() {}

    public static void clearSessionAndLocalDataAsync(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        safeLogout(appContext);
        safeCancelWork(appContext);

        // Warning: ExecutorService wrapped in try-with-resources para garantizar shutdown.
        // shutdown() no cancela tareas en curso, solo impide encolar nuevas — el comportamiento
        // deseado aquí: la tarea de borrado se completa normalmente.
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            executor.execute(() -> {
                try {
                    AppDatabase.getInstance(appContext).clearAllTables();
                } catch (Exception ignored) {
                }
                try {
                    ProfilePhotoStorage.deleteAll(appContext);
                } catch (Exception ignored) {
                }
            });
        }
    }

    public static void clearSessionAndLocalDataBlocking(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        safeLogout(appContext);
        safeCancelWork(appContext);
        try {
            AppDatabase.getInstance(appContext).clearAllTables();
        } catch (Exception ignored) {
        }
        try {
            ProfilePhotoStorage.deleteAll(appContext);
        } catch (Exception ignored) {
        }
    }

    private static void safeLogout(@NonNull Context context) {
        try {
            // Reutiliza el singleton para mantener un único gestor de sesión seguro.
            SecureSessionManager.getInstance(context).logout();
        } catch (Exception ignored) {
        }
    }

    private static void safeCancelWork(@NonNull Context context) {
        try {
            WorkManager wm = WorkManager.getInstance(context);
            wm.cancelUniqueWork(PerfilRepository.UNIQUE_SYNC_WORK_NAME);
            wm.cancelUniqueWork(ActivityRepository.UNIQUE_SYNC_WORK_NAME);
        } catch (Exception ignored) {
        }
    }
}
