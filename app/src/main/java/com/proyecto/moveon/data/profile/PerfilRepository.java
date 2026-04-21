
package com.proyecto.moveon.data.profile;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.google.gson.JsonObject;
import com.proyecto.moveon.R;
import com.proyecto.moveon.core.i18n.AppLanguageManager;
import com.proyecto.moveon.core.api.ApiError;
import com.proyecto.moveon.core.api.ApiResult;
import com.proyecto.moveon.core.concurrency.MoveOnExecutors;
import com.proyecto.moveon.data.local.db.AppDatabase;
import com.proyecto.moveon.data.local.entity.PerfilCacheEntity;
import com.proyecto.moveon.data.profile.dto.ProfileInfoDto;
import com.proyecto.moveon.data.profile.local.PerfilLocalDataSource;
import com.proyecto.moveon.data.profile.remote.PerfilRemoteDataSource;
import com.proyecto.moveon.data.profile.sync.PerfilSyncManager;
import com.proyecto.moveon.data.session.AuthRepository;
import com.proyecto.moveon.domain.profile.PerfilUsuario;
import com.proyecto.moveon.workers.SyncPerfilWorker;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Coordinador delgado del módulo de perfil.
 *
 * <p>Responsabilidades: exponer LiveData, validar inputs, despachar trabajo
 * al hilo IO, y programar WorkManager. Toda la lógica de sync, merge y
 * ciclo de vida de foto vive en {@link PerfilSyncManager}.</p>
 */
public class PerfilRepository {

    public interface RefreshCallback {
        void onComplete(@Nullable ApiError error);
    }

    public interface UpdateCallback {
        void onComplete(@NonNull UpdateResult result);
    }

    public static final String UNIQUE_SYNC_WORK_NAME = "sync_perfil";

    private final Context appContext;
    private final PerfilLocalDataSource local;
    private final PerfilRemoteDataSource remote;
    private final PerfilSyncManager syncManager;
    private final ExecutorService io = MoveOnExecutors.io();

    /**
     * Crea el repositorio usando el repositorio de preferencias por defecto.
     *
     * @param context contexto desde el que resolver base de datos y servicios de perfil.
     */
    public PerfilRepository(@NonNull Context context) {
        this(context, new UserPrefsRepository(context));
    }

    /**
     * Crea el repositorio de perfil con sus dependencias explícitas.
     *
     * @param context contexto desde el que resolver almacenamiento y red.
     * @param userPrefsRepository repositorio usado para sincronizar objetivos locales del usuario.
     */
    public PerfilRepository(@NonNull Context context,
                            @NonNull UserPrefsRepository userPrefsRepository) {
        this.appContext = context.getApplicationContext();
        AppDatabase db = AppDatabase.getInstance(appContext);
        this.local = new PerfilLocalDataSource(db);
        this.remote = new PerfilRemoteDataSource(appContext);
        this.syncManager = new PerfilSyncManager(appContext, local, remote, userPrefsRepository);
    }

    // ── Observación ──────────────────────────────────────────────────────────

    /**
     * Observa el perfil cacheado de una cuenta y lo transforma al modelo de dominio.
     *
     * @param accountKey clave lógica de la cuenta.
     * @return {@link LiveData} que emite el perfil visible por la UI.
     */
    public LiveData<PerfilUsuario> observePerfil(@NonNull String accountKey) {
        MediatorLiveData<PerfilUsuario> result = new MediatorLiveData<>();
        result.addSource(local.observeCache(accountKey), entity ->
                result.setValue(entity != null ? syncManager.mapEntityToDomain(entity) : null));
        return result;
    }

    /**
     * Devuelve de forma síncrona el perfil cacheado actual si existe.
     *
     * @param accountKey clave lógica de la cuenta.
     * @return perfil cacheado o {@code null} cuando todavía no hay snapshot local.
     */
    @Nullable
    public PerfilUsuario getCachedPerfilNow(@NonNull String accountKey) {
        PerfilCacheEntity entity = local.getCacheNow(accountKey);
        return entity != null ? syncManager.mapEntityToDomain(entity) : null;
    }

    // ── Refresh ──────────────────────────────────────────────────────────────

    /**
     * Fuerza una recarga remota del perfil y fusiona el snapshot resultante en la caché local.
     *
     * @param accountKey clave lógica de la cuenta.
     * @param callback callback opcional notificado al finalizar con error o éxito.
     */
    public void refreshPerfil(@NonNull String accountKey, @Nullable RefreshCallback callback) {
        remote.fetchPerfil(result -> {
            if (!result.isSuccess() || result.data == null) {
                if (callback != null) {
                    callback.onComplete(result.error != null
                            ? result.error
                            : ApiError.local(AppLanguageManager.getString(appContext, R.string.error_cargando_perfil)));
                }
                return;
            }
            io.execute(() -> {
                syncManager.mergeRemoteSnapshot(accountKey, result.data, false);
                if (callback != null) callback.onComplete(null);
            });
        });
    }

    // ── Patch ────────────────────────────────────────────────────────────────

    /**
     * Aplica un patch local al perfil, intenta sincronizarlo y encola un worker si queda pendiente.
     *
     * @param accountKey clave lógica de la cuenta.
     * @param patchJson payload parcial con los campos modificados.
     * @param callback callback opcional con el resultado final del intento.
     */
    public void applyLocalPatchAndEnqueue(@NonNull String accountKey,
                                          @NonNull JsonObject patchJson,
                                          @Nullable UpdateCallback callback) {
        if (patchJson.isEmpty()) {
            if (callback != null) {
                callback.onComplete(UpdateResult.failed(
                        ApiError.local(AppLanguageManager.getString(appContext, R.string.error_no_hay_cambios))));
            }
            return;
        }

        io.execute(() -> {
            UpdateResult result = syncManager.patchAndSync(accountKey, patchJson);
            if (UpdateResult.STATUS_QUEUED.equals(result.status)) {
                enqueueSync();
            }
            if (callback != null) callback.onComplete(result);
        });
    }

    // ── Foto ─────────────────────────────────────────────────────────────────

    /**
     * Guarda la foto localmente antes de intentar subirla y encola sincronización si queda pendiente.
     *
     * @param accountKey clave lógica de la cuenta.
     * @param sourceFile archivo original seleccionado por el usuario.
     * @param callback callback opcional con el resultado del flujo.
     */
    public void uploadPhotoLocalFirst(@NonNull String accountKey,
                                      @NonNull File sourceFile,
                                      @Nullable UpdateCallback callback) {
        io.execute(() -> {
            try {
                UpdateResult result = syncManager.uploadPhotoAndSync(accountKey, sourceFile);
                if (UpdateResult.STATUS_QUEUED.equals(result.status)) {
                    enqueueSync();
                }
                if (callback != null) callback.onComplete(result);
            } catch (IOException e) {
                if (callback != null) {
                    callback.onComplete(UpdateResult.failed(
                            ApiError.local(AppLanguageManager.getString(appContext, R.string.error_guardando_foto_local))));
                }
            }
        });
    }

    // ── Sync (Worker) ────────────────────────────────────────────────────────

    /**
     * Ejecuta inmediatamente la sincronización offline pendiente del perfil.
     *
     * @param accountKey clave lógica de la cuenta.
     * @return resultado resumido del ciclo de sync.
     */
    @NonNull
    public SyncResult syncPendingNow(@NonNull String accountKey) {
        return syncManager.syncAllPending(accountKey);
    }

    /**
     * Programa un {@link SyncPerfilWorker} único con red requerida y backoff exponencial.
     */
    public void enqueueSync() {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(SyncPerfilWorker.class)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build();

        WorkManager.getInstance(appContext)
                .enqueueUniqueWork(UNIQUE_SYNC_WORK_NAME, ExistingWorkPolicy.REPLACE, request);
    }

    // ── Eliminar cuenta ──────────────────────────────────────────────────────

    /**
     * Solicita la eliminación remota de la cuenta y normaliza el resultado para la capa superior.
     *
     * @param callback receptor del estado final de la operación.
     */
    public void eliminarCuenta(@NonNull AuthRepository.Callback<String> callback) {
        io.execute(() -> remote.eliminarCuenta(result -> {
            if (result.isSuccess()) {
                callback.onResult(ApiResult.success(result.data != null ? result.data : "OK"));
            } else {
                callback.onResult(ApiResult.failure(
                        result.error != null ? result.error
                                : ApiError.local(AppLanguageManager.getString(appContext, R.string.vm_error_generico))));
            }
        }));
    }

    /**
     * Cancela peticiones remotas del módulo de perfil que sigan en curso.
     */
    public void cancelOngoing() {
        remote.cancelAll();
    }

    // ── Inner classes ────────────────────────────────────────────────────────

    public static final class UpdateResult {
        public static final String STATUS_SYNCED = "SYNCED";
        public static final String STATUS_QUEUED = "QUEUED";
        public static final String STATUS_FAILED = "FAILED";

        @NonNull  public final String   status;
        @Nullable public final ApiError error;

        private UpdateResult(@NonNull String status, @Nullable ApiError error) {
            this.status = status;
            this.error  = error;
        }

        /**
         * Crea un resultado ya sincronizado con éxito.
         *
         * @return resultado con estado {@link #STATUS_SYNCED}.
         */
        public static UpdateResult synced() { return new UpdateResult(STATUS_SYNCED, null); }
        /**
         * Crea un resultado que deja trabajo pendiente en cola.
         *
         * @return resultado con estado {@link #STATUS_QUEUED}.
         */
        public static UpdateResult queued() { return new UpdateResult(STATUS_QUEUED, null); }
        /**
         * Crea un resultado fallido con su error asociado.
         *
         * @param error error final de la operación.
         * @return resultado con estado {@link #STATUS_FAILED}.
         */
        public static UpdateResult failed(@NonNull ApiError error) { return new UpdateResult(STATUS_FAILED, error); }
    }

    /**
     * Resultado del ciclo de sincronización offline del perfil.
     *
     * <p>Además del típico flag de reintento, este resultado informa de si realmente se ha
     * completado trabajo pendiente (patches de texto o foto pendiente). Así la UI puede enseñar
     * el snackbar solo cuando la cola offline se ha vaciado de verdad.</p>
     */
    public static final class SyncResult {
        /** Indica si WorkManager debe reintentar más tarde. */
        public final boolean shouldRetry;

        /** Indica si esta ejecución completó trabajo pendiente real. */
        public final boolean completedPendingWork;

        private SyncResult(boolean shouldRetry, boolean completedPendingWork) {
            this.shouldRetry = shouldRetry;
            this.completedPendingWork = completedPendingWork;
        }

        /** Devuelve éxito cuando el worker se ejecutó pero no había cola pendiente. */
        public static SyncResult successNoop() {
            return new SyncResult(false, false);
        }

        /** Devuelve éxito cuando sí había cola pendiente y ya quedó completada. */
        public static SyncResult successCompleted() {
            return new SyncResult(false, true);
        }

        /** Devuelve reintento cuando parte de la cola sigue pendiente. */
        public static SyncResult retry() {
            return new SyncResult(true, false);
        }
    }
}

