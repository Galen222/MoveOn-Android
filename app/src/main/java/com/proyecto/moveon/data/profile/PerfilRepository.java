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
 * MEJ-07: Coordinador delgado del perfil.
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

    public PerfilRepository(@NonNull Context context) {
        this(context, new UserPrefsRepository(context));
    }

    public PerfilRepository(@NonNull Context context,
                            @NonNull UserPrefsRepository userPrefsRepository) {
        this.appContext = context.getApplicationContext();
        AppDatabase db = AppDatabase.getInstance(appContext);
        this.local = new PerfilLocalDataSource(db);
        this.remote = new PerfilRemoteDataSource(appContext);
        this.syncManager = new PerfilSyncManager(appContext, local, remote, userPrefsRepository);
    }

    // ── Observación ──────────────────────────────────────────────────────────

    public LiveData<PerfilUsuario> observePerfil(@NonNull String accountKey) {
        MediatorLiveData<PerfilUsuario> result = new MediatorLiveData<>();
        result.addSource(local.observeCache(accountKey), entity ->
                result.setValue(entity != null ? syncManager.mapEntityToDomain(entity) : null));
        return result;
    }

    @Nullable
    public PerfilUsuario getCachedPerfilNow(@NonNull String accountKey) {
        PerfilCacheEntity entity = local.getCacheNow(accountKey);
        return entity != null ? syncManager.mapEntityToDomain(entity) : null;
    }

    // ── Refresh ──────────────────────────────────────────────────────────────

    public void refreshPerfil(@NonNull String accountKey, @Nullable RefreshCallback callback) {
        remote.fetchPerfil(result -> {
            if (!result.isSuccess() || result.data == null) {
                if (callback != null) {
                    callback.onComplete(result.error != null
                            ? result.error
                            : ApiError.local(appContext.getString(R.string.error_cargando_perfil)));
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

    public void applyLocalPatchAndEnqueue(@NonNull String accountKey,
                                          @NonNull JsonObject patchJson,
                                          @Nullable UpdateCallback callback) {
        if (patchJson.isEmpty()) {
            if (callback != null) {
                callback.onComplete(UpdateResult.failed(
                        ApiError.local(appContext.getString(R.string.error_no_hay_cambios))));
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
                            ApiError.local(appContext.getString(R.string.error_guardando_foto_local))));
                }
            }
        });
    }

    // ── Sync (Worker) ────────────────────────────────────────────────────────

    @NonNull
    public SyncResult syncPendingNow(@NonNull String accountKey) {
        return syncManager.syncAllPending(accountKey);
    }

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

    public void eliminarCuenta(@NonNull AuthRepository.Callback<String> callback) {
        io.execute(() -> remote.eliminarCuenta(result -> {
            if (result.isSuccess()) {
                callback.onResult(ApiResult.success(result.data != null ? result.data : "OK"));
            } else {
                callback.onResult(ApiResult.failure(
                        result.error != null ? result.error
                                : ApiError.local(appContext.getString(R.string.vm_error_generico))));
            }
        }));
    }

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

        public static UpdateResult synced() { return new UpdateResult(STATUS_SYNCED, null); }
        public static UpdateResult queued() { return new UpdateResult(STATUS_QUEUED, null); }
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
