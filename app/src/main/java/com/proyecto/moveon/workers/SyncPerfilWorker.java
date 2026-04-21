package com.proyecto.moveon.workers;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.proyecto.moveon.R;
import com.proyecto.moveon.app.ServiceLocator;
import com.proyecto.moveon.core.i18n.AppLanguageManager;
import com.proyecto.moveon.core.sync.GlobalSyncNotifier;
import com.proyecto.moveon.data.profile.PerfilRepository;
import com.proyecto.moveon.data.session.SecureSessionManager;

/**
 * Worker que sincroniza los patches de perfil pendientes con el backend.
 *
 * <p>Usa {@link ServiceLocator} para obtener el repositorio en lugar
 * de instanciarlo con {@code new}. Así reutiliza el
 * {@code UserPrefsRepository} singleton, mantiene la construcción de
 * dependencias alineada con el resto de la app y permite sustituir el
 * locator en tests vía {@code ServiceLocator.swap()}.</p>
 */
public class SyncPerfilWorker extends Worker {

    /**
     * Constructor requerido por WorkManager. Simplemente delega en la
     * implementación base; la lógica real vive en {@link #doWork()}.
     *
     * @param context contexto inyectado por WorkManager.
     * @param params parámetros de la ejecución (reintentos, input data, tags…).
     */
    public SyncPerfilWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    /**
     * Sincroniza los cambios locales del perfil con el backend. Si aún no
     * hay sesión ({@code accountKey == null}) termina con
     * {@link Result#success()} para no dejar el Worker reprogramado.
     *
     * <p>Con sesión activa, usa una instancia propia de
     * {@link PerfilRepository} para enviar los PATCH pendientes (nombre,
     * foto, preferencias) que hubieran quedado encolados offline.</p>
     *
     * @return {@link Result#success()} al terminar, o {@link Result#retry()} si la subida falla y merece un reintento.
     */
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
            // El texto se resuelve aquí para mantener el mismo contrato homogéneo del resto
            // de notifiers globales: todos transportan un GlobalSnackbarMessage listo para pintar.
            GlobalSyncNotifier.getInstance().notifySyncCompleted(
                    AppLanguageManager.getString(getApplicationContext(), R.string.sync_completed)
            );
        }

        return syncResult.shouldRetry ? Result.retry() : Result.success();
    }
}
