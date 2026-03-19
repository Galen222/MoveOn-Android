package com.proyecto.moveon.workers;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.proyecto.moveon.app.ServiceLocator;
import com.proyecto.moveon.core.sync.GlobalSyncNotifier;
import com.proyecto.moveon.data.profile.PerfilRepository;
import com.proyecto.moveon.data.session.SecureSessionManager;

/**
 * Worker que sincroniza los patches de perfil pendientes con el backend.
 *
 * <p>FIX: Usa {@link ServiceLocator} para obtener el repositorio en lugar
 * de instanciarlo con {@code new}. Esto garantiza que se reutilice el
 * {@code UserPrefsRepository} singleton (como hace el resto de la app)
 * y permite sustituir el locator en tests vía {@code ServiceLocator.swap()}.</p>
 */
public class SyncPerfilWorker extends Worker {

    public SyncPerfilWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        String accountKey = SecureSessionManager
                .getInstance(getApplicationContext())
                .getAccountKey();

        if (accountKey == null) {
            return Result.success();
        }

        PerfilRepository repository = ServiceLocator
                .getInstance(getApplicationContext())
                .newPerfilRepository();

        PerfilRepository.SyncResult syncResult = repository.syncPendingNow(accountKey);
        repository.cancelOngoing();

        // Solo mostramos el aviso cuando realmente se vació una cola pendiente de perfil.
        if (!syncResult.shouldRetry && syncResult.completedPendingWork) {
            GlobalSyncNotifier.getInstance().notifySyncCompleted();
        }

        return syncResult.shouldRetry ? Result.retry() : Result.success();
    }
}
