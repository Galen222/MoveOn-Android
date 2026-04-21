package com.proyecto.moveon.workers;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.proyecto.moveon.R;
import com.proyecto.moveon.app.ServiceLocator;
import com.proyecto.moveon.core.i18n.AppLanguageManager;
import com.proyecto.moveon.core.sync.GlobalSyncNotifier;
import com.proyecto.moveon.data.activities.ActivityRepository;
import com.proyecto.moveon.data.session.SecureSessionManager;

/**
 * Worker que sincroniza las actividades pendientes de crear con el backend.
 *
 * <p>Usa {@link ServiceLocator} para obtener el repositorio en lugar
 * de instanciarlo con {@code new}. Así unifica la creación de dependencias
 * con el resto de la app (ViewModels, MoveOnApp) y permite sustituir
 * el ServiceLocator en tests instrumentados vía {@code ServiceLocator.swap()}.</p>
 */
public class SyncActividadesWorker extends Worker {

    /**
     * Constructor requerido por WorkManager. Simplemente delega en la
     * implementación base; la lógica real vive en {@link #doWork()}.
     *
     * @param context contexto inyectado por WorkManager.
     * @param params parámetros de la ejecución (reintentos, input data, tags…).
     */
    public SyncActividadesWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    /**
     * Sincroniza las actividades pendientes de subir. Si no hay sesión
     * ({@code accountKey == null}) devuelve {@link Result#success()} para
     * no mantener el Worker reprogramado indefinidamente antes del login.
     *
     * <p>Cuando hay sesión, obtiene una instancia propia de
     * {@link ActivityRepository} (para poder cancelar sin afectar a la UI)
     * y le pide que empuje las actividades locales en estado pendiente.</p>
     *
     * @return {@link Result#success()} al terminar, o {@link Result#retry()} si la sincronización falla por red y procede reintentar.
     */
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
            // El texto se resuelve aquí para que el notifier global transporte el mensaje final,
            // igual que ya hacen los notifiers de perfil y estadísticas.
            GlobalSyncNotifier.getInstance().notifySyncCompleted(
                    AppLanguageManager.getString(getApplicationContext(), R.string.sync_completed)
            );
        }

        return result.retry ? Result.retry() : Result.success();
    }
}
