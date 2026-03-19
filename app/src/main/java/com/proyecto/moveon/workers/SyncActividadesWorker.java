package com.proyecto.moveon.workers;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.proyecto.moveon.app.ServiceLocator;
import com.proyecto.moveon.core.sync.GlobalSyncNotifier;
import com.proyecto.moveon.data.activities.ActivityRepository;
import com.proyecto.moveon.data.session.SecureSessionManager;

/**
 * Worker que sincroniza las actividades pendientes de crear con el backend.
 *
 * <p>FIX: Usa {@link ServiceLocator} para obtener el repositorio en lugar
 * de instanciarlo con {@code new}. Esto unifica la creación de dependencias
 * con el resto de la app (ViewModels, MoveOnApp) y permite sustituir
 * el ServiceLocator en tests instrumentados vía {@code ServiceLocator.swap()}.</p>
 */
public class SyncActividadesWorker extends Worker {

    public SyncActividadesWorker(@NonNull Context context, @NonNull WorkerParameters params) {
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

        ActivityRepository repository = ServiceLocator
                .getInstance(getApplicationContext())
                .newActivityRepository();

        ActivityRepository.SyncResult result = repository.syncPendingNow(accountKey);
        repository.cancelAll();

        // Solo avisamos a la UI si la cola offline de verdad quedó completada en esta ejecución.
        if (!result.retry && result.completedPendingWork) {
            GlobalSyncNotifier.getInstance().notifySyncCompleted();
        }

        return result.retry ? Result.retry() : Result.success();
    }
}
