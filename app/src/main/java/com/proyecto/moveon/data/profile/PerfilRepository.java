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

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.proyecto.moveon.R;
import com.proyecto.moveon.core.api.ApiError;
import com.proyecto.moveon.core.api.ApiErrorType;
import com.proyecto.moveon.core.api.ApiResult;
import com.proyecto.moveon.core.concurrency.MoveOnExecutors;
import com.proyecto.moveon.data.local.db.AppDatabase;
import com.proyecto.moveon.data.local.entity.PerfilCacheEntity;
import com.proyecto.moveon.data.local.entity.PerfilPendingPatchEntity;
import com.proyecto.moveon.data.profile.dto.ProfileInfoDto;
import com.proyecto.moveon.data.profile.UserPrefsRepository;
import com.proyecto.moveon.data.profile.local.PerfilLocalDataSource;
import com.proyecto.moveon.data.profile.local.ProfilePhotoStorage;
import com.proyecto.moveon.data.profile.remote.PerfilRemoteDataSource;
import com.proyecto.moveon.data.session.AuthRepository;
import com.proyecto.moveon.domain.profile.PerfilUsuario;
import com.proyecto.moveon.utils.StringUtils;
import com.proyecto.moveon.workers.SyncPerfilWorker;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public class PerfilRepository {

    public interface RefreshCallback {
        void onComplete(@Nullable ApiError error);
    }

    public interface UpdateCallback {
        void onComplete(@NonNull UpdateResult result);
    }

    public static final String UNIQUE_SYNC_WORK_NAME = "sync_perfil";

    private static final String PHOTO_STATE_SYNCED  = "SYNCED";
    private static final String PHOTO_STATE_PENDING = "PENDING_UPLOAD";
    private static final String PHOTO_STATE_FAILED  = "FAILED";

    private final Context appContext;
    private final PerfilLocalDataSource local;
    private final PerfilRemoteDataSource remote;
    private final UserPrefsRepository userPrefsRepository;
    private final ExecutorService io = MoveOnExecutors.io();
    private final Gson gson = new Gson();

    /**
     * Constructor por defecto: crea su propio UserPrefsRepository.
     * Usado por los Workers que necesitan instancias aisladas.
     */
    public PerfilRepository(@NonNull Context context) {
        this(context, new UserPrefsRepository(context));
    }

    /**
     * MEJ-01: Constructor con inyección de UserPrefsRepository.
     * Usado por ServiceLocator para compartir la instancia singleton
     * y evitar duplicados entre ViewModels.
     */
    public PerfilRepository(@NonNull Context context,
                            @NonNull UserPrefsRepository userPrefsRepository) {
        this.appContext = context.getApplicationContext();
        AppDatabase db = AppDatabase.getInstance(appContext);
        this.local = new PerfilLocalDataSource(db);
        this.remote = new PerfilRemoteDataSource(appContext);
        this.userPrefsRepository = userPrefsRepository;
    }

    public LiveData<PerfilUsuario> observePerfil(@NonNull String accountKey) {
        MediatorLiveData<PerfilUsuario> result = new MediatorLiveData<>();
        result.addSource(local.observeCache(accountKey), entity ->
                result.setValue(entity != null ? mapEntityToDomain(entity) : null));
        return result;
    }

    @Nullable
    public PerfilUsuario getCachedPerfilNow(@NonNull String accountKey) {
        PerfilCacheEntity entity = local.getCacheNow(accountKey);
        return entity != null ? mapEntityToDomain(entity) : null;
    }

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
                mergeRemoteSnapshot(accountKey, result.data, false);
                if (callback != null) callback.onComplete(null);
            });
        });
    }

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
            PerfilPendingPatchEntity op = new PerfilPendingPatchEntity();
            op.operationId = UUID.randomUUID().toString();
            op.accountKey  = accountKey;
            op.payloadJson = gson.toJson(patchJson);
            op.createdAtMs = System.currentTimeMillis();
            op.attempts    = 0;
            op.lastError   = null;
            op.state       = "PENDING";
            local.enqueuePatch(op);

            boolean applyOptimistically = shouldApplyPatchOptimistically(patchJson);

            if (applyOptimistically) {
                PerfilCacheEntity current = getOrCreateCache(accountKey);
                applyPatchToCache(current, patchJson);
                current.dirty = true;
                local.saveCache(current);
            }

            ApiResult<String> result = remote.patchPerfilBlocking(patchJson);
            if (result.isSuccess()) {
                local.deletePatch(op.operationId);
                ApiResult<ProfileInfoDto> fetchResult = remote.fetchPerfilBlocking();
                if (fetchResult.isSuccess() && fetchResult.data != null) {
                    mergeRemoteSnapshot(accountKey, fetchResult.data, false);
                } else {
                    PerfilCacheEntity updated = local.getCacheNow(accountKey);
                    if (updated != null) {
                        if (!applyOptimistically) {
                            applyPatchToCache(updated, patchJson);
                        }
                        updated.dirty = hasPendingTextChanges(accountKey)
                                || PHOTO_STATE_PENDING.equals(updated.photoSyncState);
                        updated.lastSyncedAtMs = System.currentTimeMillis();
                        local.saveCache(updated);
                    }
                }
                if (callback != null) callback.onComplete(UpdateResult.synced());
                return;
            }

            ApiError error = result.error != null
                    ? result.error
                    : ApiError.local(appContext.getString(R.string.error_sincronizando_perfil));
            op.attempts += 1;
            op.lastError  = error.getMessage();

            if (isRetryable(error)) {
                local.updatePatch(op);
                enqueueSync();
                if (callback != null) callback.onComplete(UpdateResult.queued());
                return;
            }

            op.state = "FAILED";
            local.updatePatch(op);

            ApiResult<ProfileInfoDto> fetchResult = remote.fetchPerfilBlocking();
            if (fetchResult.isSuccess() && fetchResult.data != null) {
                mergeRemoteSnapshot(accountKey, fetchResult.data, false);
            }

            if (callback != null) callback.onComplete(UpdateResult.failed(error));
        });
    }

    public void uploadPhotoLocalFirst(@NonNull String accountKey,
                                      @NonNull File sourceFile,
                                      @Nullable UpdateCallback callback) {
        io.execute(() -> {
            PerfilCacheEntity current = getOrCreateCache(accountKey);
            String oldPendingPath = current.pendingLocalPhotoPath;
            try {
                String pendingPath = ProfilePhotoStorage.savePendingPhoto(appContext, accountKey, sourceFile);
                if (StringUtils.hasText(oldPendingPath) && !oldPendingPath.equals(pendingPath)) {
                    ProfilePhotoStorage.deleteFileSilently(oldPendingPath);
                }
                current.pendingLocalPhotoPath = pendingPath;
                current.photoSyncState        = PHOTO_STATE_PENDING;
                current.photoLastError        = null;
                current.dirty                 = true;
                local.saveCache(current);

                ApiResult<String> uploadResult = remote.uploadPhotoBlocking(new File(pendingPath));
                if (uploadResult.isSuccess()) {
                    ApiResult<ProfileInfoDto> fetchResult = remote.fetchPerfilBlocking();
                    if (fetchResult.isSuccess() && fetchResult.data != null) {
                        mergeRemoteSnapshot(accountKey, fetchResult.data, true);
                    } else {
                        promotePendingWithoutRemote(accountKey);
                    }
                    if (callback != null) callback.onComplete(UpdateResult.synced());
                    return;
                }

                ApiError error = uploadResult.error != null
                        ? uploadResult.error
                        : ApiError.local(appContext.getString(R.string.error_subiendo_foto));
                if (isRetryable(error)) {
                    PerfilCacheEntity refreshed = getOrCreateCache(accountKey);
                    refreshed.photoSyncState = PHOTO_STATE_PENDING;
                    refreshed.photoLastError = error.getMessage();
                    refreshed.dirty          = true;
                    local.saveCache(refreshed);
                    enqueueSync();
                    if (callback != null) callback.onComplete(UpdateResult.queued());
                    return;
                }

                revertPendingPhoto(accountKey, error.getMessage());
                if (callback != null) callback.onComplete(UpdateResult.failed(error));
            } catch (IOException e) {
                if (callback != null) {
                    callback.onComplete(UpdateResult.failed(
                            ApiError.local(appContext.getString(R.string.error_guardando_foto_local))));
                }
            }
        });
    }

    @NonNull
    public SyncResult syncPendingNow(@NonNull String accountKey) {
        boolean retryNeeded = false;

        List<PerfilPendingPatchEntity> ops = local.getPending(accountKey);
        if (ops != null) {
            for (PerfilPendingPatchEntity op : ops) {
                JsonObject patchJson = gson.fromJson(op.payloadJson, JsonObject.class);
                ApiResult<String> result = remote.patchPerfilBlocking(patchJson);

                if (result.isSuccess()) {
                    local.deletePatch(op.operationId);
                    continue;
                }

                ApiError error = result.error != null
                        ? result.error
                        : ApiError.local(appContext.getString(R.string.error_sincronizando_perfil));
                op.attempts += 1;
                op.lastError  = error.getMessage();

                if (isRetryable(error)) {
                    local.updatePatch(op);
                    retryNeeded = true;
                    break;
                } else {
                    op.state = "FAILED";
                    local.updatePatch(op);
                }
            }
        }

        PerfilCacheEntity cache = local.getCacheNow(accountKey);
        if (cache != null
                && PHOTO_STATE_PENDING.equals(cache.photoSyncState)
                && StringUtils.hasText(cache.pendingLocalPhotoPath)
                && ProfilePhotoStorage.exists(cache.pendingLocalPhotoPath)) {
            ApiResult<String> uploadResult = remote.uploadPhotoBlocking(new File(cache.pendingLocalPhotoPath));
            if (uploadResult.isSuccess()) {
                ApiResult<ProfileInfoDto> fetchResult = remote.fetchPerfilBlocking();
                if (fetchResult.isSuccess() && fetchResult.data != null) {
                    mergeRemoteSnapshot(accountKey, fetchResult.data, true);
                } else {
                    promotePendingWithoutRemote(accountKey);
                }
            } else {
                ApiError error = uploadResult.error != null
                        ? uploadResult.error
                        : ApiError.local(appContext.getString(R.string.error_subiendo_foto));
                if (isRetryable(error)) {
                    cache.photoLastError = error.getMessage();
                    cache.dirty          = true;
                    local.saveCache(cache);
                    retryNeeded = true;
                } else {
                    revertPendingPhoto(accountKey, error.getMessage());
                }
            }
        }

        ApiResult<ProfileInfoDto> refreshResult = remote.fetchPerfilBlocking();
        if (refreshResult.isSuccess() && refreshResult.data != null) {
            mergeRemoteSnapshot(accountKey, refreshResult.data, false);
        } else if (refreshResult.error != null && isRetryable(refreshResult.error)) {
            retryNeeded = true;
        }

        return retryNeeded ? SyncResult.retry() : SyncResult.success();
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
                // FIX: REPLACE en vez de KEEP para que cada acción nueva reprograme
                // el Worker con backoff fresco. Con KEEP, un Worker dormido en backoff
                // exponencial (hasta 30 s) bloqueaba la sincronización de patches
                // nuevos hasta que el backoff anterior expirase.
                .enqueueUniqueWork(UNIQUE_SYNC_WORK_NAME, ExistingWorkPolicy.REPLACE, request);
    }

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

    private void mergeRemoteSnapshot(@NonNull String accountKey,
                                     @NonNull ProfileInfoDto dto,
                                     boolean preferPendingPhoto) {
        PerfilCacheEntity previous = local.getCacheNow(accountKey);
        PerfilCacheEntity entity   = previous != null ? copyOf(previous) : createEmptyCache(accountKey);
        int previousVersion        = previous != null ? previous.fotoVersion : -1;

        entity.accountKey      = accountKey;
        entity.nombreUsuario   = StringUtils.hasText(dto.nombreUsuario) ? dto.nombreUsuario : entity.nombreUsuario;
        entity.nombreReal      = dto.nombreReal;
        entity.email           = StringUtils.textOf(dto.email);
        entity.fechaNacimiento = StringUtils.textOf(dto.fechaNacimiento);
        entity.genero          = dto.genero;
        entity.altura          = dto.altura;
        entity.peso            = dto.peso;
        entity.provincia       = dto.provincia;
        entity.fotoPerfil      = dto.fotoPerfil;
        entity.fotoVersion     = dto.fotoVersion;
        entity.perfilVisible   = dto.perfilVisible;
        entity.totalPuntos     = dto.totalPuntos;
        entity.totalCalorias          = dto.totalCalorias;
        entity.objetivoSemanalMetros  = dto.objetivoSemanalMetros;
        entity.objetivoMensualMetros  = dto.objetivoMensualMetros;

        if (preferPendingPhoto && previous != null && StringUtils.hasText(previous.pendingLocalPhotoPath)) {
            try {
                String promoted = ProfilePhotoStorage.promotePendingToCurrent(
                        appContext, accountKey, previous.pendingLocalPhotoPath, Math.max(dto.fotoVersion, 1));
                if (StringUtils.hasText(previous.localPhotoPath) && !promoted.equals(previous.localPhotoPath)) {
                    ProfilePhotoStorage.deleteFileSilently(previous.localPhotoPath);
                }
                entity.localPhotoPath        = promoted;
                entity.pendingLocalPhotoPath = null;
                entity.photoSyncState        = PHOTO_STATE_SYNCED;
                entity.photoLastError        = null;
            } catch (IOException e) {
                entity.localPhotoPath        = previous.localPhotoPath;
                entity.pendingLocalPhotoPath = previous.pendingLocalPhotoPath;
                entity.photoSyncState        = previous.photoSyncState;
                entity.photoLastError        = previous.photoLastError;
            }
        } else if (!PHOTO_STATE_PENDING.equals(entity.photoSyncState)
                || !StringUtils.hasText(entity.pendingLocalPhotoPath)
                || !ProfilePhotoStorage.exists(entity.pendingLocalPhotoPath)) {
            if (!StringUtils.hasText(dto.fotoPerfil)) {
                if (StringUtils.hasText(entity.localPhotoPath)) {
                    ProfilePhotoStorage.deleteFileSilently(entity.localPhotoPath);
                }
                entity.localPhotoPath        = null;
                entity.pendingLocalPhotoPath = null;
                entity.photoSyncState        = PHOTO_STATE_SYNCED;
                entity.photoLastError        = null;
            } else {
                boolean canReuseLocal = previous != null
                        && previousVersion == dto.fotoVersion
                        && StringUtils.hasText(previous.localPhotoPath)
                        && ProfilePhotoStorage.exists(previous.localPhotoPath);
                if (canReuseLocal) {
                    entity.localPhotoPath        = previous.localPhotoPath;
                    entity.pendingLocalPhotoPath = null;
                    entity.photoSyncState        = PHOTO_STATE_SYNCED;
                    entity.photoLastError        = null;
                } else {
                    try {
                        String downloadedPath = ProfilePhotoStorage.downloadRemotePhoto(
                                appContext, accountKey, dto.fotoPerfil, Math.max(dto.fotoVersion, 1));
                        if (StringUtils.hasText(previous != null ? previous.localPhotoPath : null)
                                && !downloadedPath.equals(previous.localPhotoPath)) {
                            ProfilePhotoStorage.deleteFileSilently(previous.localPhotoPath);
                        }
                        entity.localPhotoPath        = downloadedPath;
                        entity.pendingLocalPhotoPath = null;
                        entity.photoSyncState        = PHOTO_STATE_SYNCED;
                        entity.photoLastError        = null;
                    } catch (IOException e) {
                        entity.localPhotoPath        = previous != null ? previous.localPhotoPath : null;
                        entity.pendingLocalPhotoPath = previous != null ? previous.pendingLocalPhotoPath : null;
                        entity.photoSyncState        = previous != null && StringUtils.hasText(previous.pendingLocalPhotoPath)
                                ? previous.photoSyncState : PHOTO_STATE_FAILED;
                        entity.photoLastError        = appContext.getString(R.string.error_actualizando_foto_local);
                    }
                }
            }
        }

        List<PerfilPendingPatchEntity> pending = local.getPending(accountKey);
        if (pending != null) {
            for (PerfilPendingPatchEntity op : pending) {
                JsonObject patchJson = gson.fromJson(op.payloadJson, JsonObject.class);
                applyPatchToCache(entity, patchJson);
            }
        }

        boolean hasPendingPhoto = PHOTO_STATE_PENDING.equals(entity.photoSyncState)
                && StringUtils.hasText(entity.pendingLocalPhotoPath);
        entity.dirty = (pending != null && !pending.isEmpty()) || hasPendingPhoto;
        entity.lastFetchedAtMs = System.currentTimeMillis();
        if (!entity.dirty) {
            entity.lastSyncedAtMs = entity.lastFetchedAtMs;
        }

        userPrefsRepository.syncFromServer(
                accountKey,
                entity.objetivoSemanalMetros,
                entity.objetivoMensualMetros
        );

        local.saveCache(entity);
    }

    private void promotePendingWithoutRemote(@NonNull String accountKey) {
        PerfilCacheEntity current = local.getCacheNow(accountKey);
        if (current == null || !StringUtils.hasText(current.pendingLocalPhotoPath)) return;
        try {
            String currentPath = ProfilePhotoStorage.promotePendingToCurrent(
                    appContext, accountKey, current.pendingLocalPhotoPath, Math.max(current.fotoVersion, 1));
            if (StringUtils.hasText(current.localPhotoPath) && !current.localPhotoPath.equals(currentPath)) {
                ProfilePhotoStorage.deleteFileSilently(current.localPhotoPath);
            }
            current.localPhotoPath        = currentPath;
            current.pendingLocalPhotoPath = null;
            current.photoSyncState        = PHOTO_STATE_SYNCED;
            current.photoLastError        = null;
            current.dirty                 = hasPendingTextChanges(accountKey);
            current.lastSyncedAtMs        = System.currentTimeMillis();
            local.saveCache(current);
        } catch (IOException ignored) {
        }
    }

    private void revertPendingPhoto(@NonNull String accountKey, @Nullable String errorMessage) {
        PerfilCacheEntity current = local.getCacheNow(accountKey);
        if (current == null) return;
        if (StringUtils.hasText(current.pendingLocalPhotoPath)) {
            ProfilePhotoStorage.deleteFileSilently(current.pendingLocalPhotoPath);
        }
        current.pendingLocalPhotoPath = null;
        current.photoSyncState        = PHOTO_STATE_FAILED;
        current.photoLastError        = errorMessage;
        current.dirty                 = hasPendingTextChanges(accountKey);
        local.saveCache(current);
    }

    @NonNull
    private PerfilCacheEntity getOrCreateCache(@NonNull String accountKey) {
        PerfilCacheEntity current = local.getCacheNow(accountKey);
        return current != null ? current : createEmptyCache(accountKey);
    }

    @NonNull
    private PerfilCacheEntity createEmptyCache(@NonNull String accountKey) {
        PerfilCacheEntity e = new PerfilCacheEntity();
        e.accountKey            = accountKey;
        e.nombreUsuario         = accountKey;
        e.nombreReal            = null;
        e.email                 = "";
        e.fechaNacimiento       = "";
        e.genero                = null;
        e.altura                = null;
        e.peso                  = null;
        e.provincia             = null;
        e.fotoPerfil            = null;
        e.fotoVersion           = 0;
        e.localPhotoPath        = null;
        e.pendingLocalPhotoPath = null;
        e.photoSyncState        = PHOTO_STATE_SYNCED;
        e.photoLastError        = null;
        e.perfilVisible         = true;
        e.totalPuntos           = 0;
        e.totalCalorias         = 0L;
        e.objetivoSemanalMetros = 50_000L;
        e.objetivoMensualMetros = 150_000L;
        e.dirty                 = false;
        e.lastFetchedAtMs       = 0L;
        e.lastSyncedAtMs        = 0L;
        return e;
    }

    @NonNull
    private PerfilCacheEntity copyOf(@NonNull PerfilCacheEntity source) {
        PerfilCacheEntity e = new PerfilCacheEntity();
        e.accountKey            = source.accountKey;
        e.nombreUsuario         = source.nombreUsuario;
        e.nombreReal            = source.nombreReal;
        e.email                 = source.email;
        e.fechaNacimiento       = source.fechaNacimiento;
        e.genero                = source.genero;
        e.altura                = source.altura;
        e.peso                  = source.peso;
        e.provincia             = source.provincia;
        e.fotoPerfil            = source.fotoPerfil;
        e.fotoVersion           = source.fotoVersion;
        e.localPhotoPath        = source.localPhotoPath;
        e.pendingLocalPhotoPath = source.pendingLocalPhotoPath;
        e.photoSyncState        = source.photoSyncState;
        e.photoLastError        = source.photoLastError;
        e.perfilVisible         = source.perfilVisible;
        e.totalPuntos           = source.totalPuntos;
        e.totalCalorias         = source.totalCalorias;
        e.objetivoSemanalMetros = source.objetivoSemanalMetros;
        e.objetivoMensualMetros = source.objetivoMensualMetros;
        e.dirty                 = source.dirty;
        e.lastFetchedAtMs       = source.lastFetchedAtMs;
        e.lastSyncedAtMs        = source.lastSyncedAtMs;
        return e;
    }

    @NonNull
    private PerfilUsuario mapEntityToDomain(@NonNull PerfilCacheEntity entity) {
        return new PerfilUsuario(
                entity.nombreUsuario,
                entity.email,
                entity.fechaNacimiento,
                entity.totalPuntos,
                entity.nombreReal,
                entity.genero,
                entity.altura,
                entity.peso,
                entity.provincia,
                entity.fotoPerfil,
                entity.fotoVersion,
                entity.localPhotoPath,
                entity.pendingLocalPhotoPath,
                entity.photoSyncState,
                entity.perfilVisible
        );
    }

    // FIX: Siempre aplicar optimistamente. Antes, nombre_real, email y
    // fecha_nacimiento NO se aplicaban al instante, lo que causaba que el
    // overlay bloqueara la UI 8-30 s esperando al backend.
    //
    // Es seguro porque:
    // 1. El cliente ya valida con AppInputValidator antes de llamar aquí.
    // 2. Si el servidor rechaza (email duplicado, 400/422), el path FAILED
    //    ejecuta fetchPerfilBlocking → mergeRemoteSnapshot que revierte el
    //    caché automáticamente vía Room LiveData.
    // 3. El usuario ve el valor nuevo ~200 ms y luego el error + reversión,
    //    que es infinitamente mejor que bloquear 30 s con overlay.
    private boolean shouldApplyPatchOptimistically(@NonNull JsonObject patch) {
        return true;
    }

    private void applyPatchToCache(@NonNull PerfilCacheEntity cache, @NonNull JsonObject patch) {
        if (patch.has("nombre_real")) {
            cache.nombreReal = readNullableString(patch.get("nombre_real"));
        }
        if (patch.has("email") && !patch.get("email").isJsonNull()) {
            cache.email = patch.get("email").getAsString();
        }
        if (patch.has("fecha_nacimiento") && !patch.get("fecha_nacimiento").isJsonNull()) {
            cache.fechaNacimiento = patch.get("fecha_nacimiento").getAsString();
        }
        if (patch.has("genero")) {
            cache.genero = readNullableString(patch.get("genero"));
        }
        if (patch.has("altura")) {
            cache.altura = patch.get("altura").isJsonNull() ? null : patch.get("altura").getAsInt();
        }
        if (patch.has("peso")) {
            cache.peso = patch.get("peso").isJsonNull() ? null : patch.get("peso").getAsDouble();
        }
        if (patch.has("provincia")) {
            cache.provincia = readNullableString(patch.get("provincia"));
        }
        if (patch.has("perfil_visible") && !patch.get("perfil_visible").isJsonNull()) {
            cache.perfilVisible = patch.get("perfil_visible").getAsBoolean();
        }
        if (patch.has("objetivo_semanal_metros") && !patch.get("objetivo_semanal_metros").isJsonNull()) {
            cache.objetivoSemanalMetros = patch.get("objetivo_semanal_metros").getAsLong();
        }
        if (patch.has("objetivo_mensual_metros") && !patch.get("objetivo_mensual_metros").isJsonNull()) {
            cache.objetivoMensualMetros = patch.get("objetivo_mensual_metros").getAsLong();
        }
    }

    @Nullable
    private String readNullableString(@NonNull JsonElement element) {
        return element.isJsonNull() ? null : element.getAsString();
    }

    private boolean hasPendingTextChanges(@NonNull String accountKey) {
        return local.countPending(accountKey) > 0;
    }

    private boolean isRetryable(@Nullable ApiError error) {
        if (error == null) return true;
        ApiErrorType type = error.getType();
        return type == ApiErrorType.NETWORK
                || type == ApiErrorType.TIMEOUT
                || type == ApiErrorType.SERVER
                || type == ApiErrorType.RATE_LIMIT
                || type == ApiErrorType.CANCELED;
    }

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

    public static final class SyncResult {
        public final boolean shouldRetry;

        private SyncResult(boolean shouldRetry) { this.shouldRetry = shouldRetry; }

        public static SyncResult success() { return new SyncResult(false); }
        public static SyncResult retry()   { return new SyncResult(true); }
    }
}
