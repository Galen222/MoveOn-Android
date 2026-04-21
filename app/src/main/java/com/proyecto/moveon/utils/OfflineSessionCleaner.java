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

    /**
     * Constructor privado: clase de utilidades estática, no se instancia.
     */
    private OfflineSessionCleaner() {}

    /**
     * Limpia credenciales y datos locales en background: cierra sesión,
     * cancela workers de sincronización y borra Room y la foto de perfil
     * en un executor desechable. Se usa desde la UI para no bloquear el
     * hilo principal al cerrar sesión.
     *
     * @param context contexto desde el que se resuelve el {@code applicationContext}.
     *
     * @see #clearSessionAndLocalDataBlocking(Context)
     */
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

    /**
     * Variante síncrona de {@link #clearSessionAndLocalDataAsync}. Sólo
     * debe llamarse desde un hilo IO (p. ej. un Worker que ya está fuera
     * del hilo principal) y cuando se necesita garantizar que todo esté
     * limpio antes de continuar.
     *
     * @param context contexto desde el que se resuelve el {@code applicationContext}.
     *
     * @see #clearSessionAndLocalDataAsync(Context)
     */
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

    /**
     * Intenta cerrar sesión de forma defensiva: si el {@link SecureSessionManager}
     * lanza cualquier excepción (p. ej. Keystore no disponible) se ignora
     * para que el borrado posterior continúe. Dejar credenciales colgando
     * sería peor que un logout silencioso.
     *
     * @param context contexto desde el que se obtiene el {@link SecureSessionManager} singleton.
     */
    private static void safeLogout(@NonNull Context context) {
        try {
            // Reutiliza el singleton para mantener un único gestor de sesión seguro.
            SecureSessionManager.getInstance(context).logout();
        } catch (Exception ignored) {
        }
    }

    /**
     * Cancela los trabajos únicos de WorkManager asociados a perfil y
     * actividades. Si WorkManager aún no está inicializado (p. ej. durante
     * un flujo de limpieza muy temprano) ignora la excepción para no
     * impedir el resto del cleanup.
     *
     * @param context contexto desde el que se obtiene el WorkManager.
     */
    private static void safeCancelWork(@NonNull Context context) {
        try {
            WorkManager wm = WorkManager.getInstance(context);
            wm.cancelUniqueWork(PerfilRepository.UNIQUE_SYNC_WORK_NAME);
            wm.cancelUniqueWork(ActivityRepository.UNIQUE_SYNC_WORK_NAME);
        } catch (Exception ignored) {
        }
    }
}
