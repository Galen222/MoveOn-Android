package com.proyecto.moveon.data.profile.sync;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.proyecto.moveon.R;
import com.proyecto.moveon.core.api.ApiError;
import com.proyecto.moveon.core.api.ApiErrorType;
import com.proyecto.moveon.core.api.ApiResult;
import com.proyecto.moveon.data.local.entity.PerfilCacheEntity;
import com.proyecto.moveon.data.profile.PerfilRepository.UpdateResult;
import com.proyecto.moveon.data.profile.dto.ProfileInfoDto;
import com.proyecto.moveon.data.profile.local.PerfilLocalDataSource;
import com.proyecto.moveon.data.profile.local.ProfilePhotoStorage;
import com.proyecto.moveon.data.profile.remote.PerfilRemoteDataSource;
import com.proyecto.moveon.utils.StringUtils;

import java.io.File;
import java.io.IOException;

/**
 * MEJ-07: Lógica de sincronización de fotos de perfil extraída de
 * {@link PerfilSyncManager}.
 *
 * <p>Concentra todo el ciclo de vida de la foto (pending → upload → promote/revert),
 * el merge de estado fotográfico desde snapshots remotos, y las operaciones de
 * archivo local (save, promote, delete). Todos los métodos públicos son
 * <b>blocking</b> — se invocan desde hilo IO.</p>
 *
 * <p>Se comunica con {@link PerfilSyncManager} a través de {@link SyncManagerBridge}
 * para acceder a la caché compartida y al merge completo de snapshots.</p>
 */
public final class PhotoSyncHelper {

    // ── Constantes de estado de foto ────────────────────────────────────────

    public static final String STATE_SYNCED  = "SYNCED";
    public static final String STATE_PENDING = "PENDING_UPLOAD";
    public static final String STATE_FAILED  = "FAILED";

    // ── Bridge con PerfilSyncManager ────────────────────────────────────────

    /**
     * Interfaz que PerfilSyncManager implementa para que PhotoSyncHelper
     * pueda acceder a la caché y al merge sin dependencia circular.
     */
    public interface SyncManagerBridge {
        @NonNull PerfilCacheEntity getOrCreateCache(@NonNull String accountKey);
        void mergeRemoteSnapshot(@NonNull String accountKey,
                                 @NonNull ProfileInfoDto dto,
                                 boolean preferPendingPhoto);
        boolean hasPendingTextChanges(@NonNull String accountKey);
    }

    // ── Campos ──────────────────────────────────────────────────────────────

    private final Context appContext;
    private final PerfilLocalDataSource local;
    private final PerfilRemoteDataSource remote;
    private final SyncManagerBridge bridge;

    public PhotoSyncHelper(@NonNull Context context,
                           @NonNull PerfilLocalDataSource local,
                           @NonNull PerfilRemoteDataSource remote,
                           @NonNull SyncManagerBridge bridge) {
        this.appContext = context.getApplicationContext();
        this.local = local;
        this.remote = remote;
        this.bridge = bridge;
    }

    // ── Upload foto + sync ──────────────────────────────────────────────────

    /**
     * Guarda la foto localmente, intenta subirla al backend, y gestiona el
     * resultado (synced / queued / failed).
     * <b>Blocking — llamar desde hilo IO.</b>
     */
    @NonNull
    public UpdateResult uploadAndSync(@NonNull String accountKey,
                                      @NonNull File sourceFile) throws IOException {
        PerfilCacheEntity current = bridge.getOrCreateCache(accountKey);
        String oldPendingPath = current.pendingLocalPhotoPath;

        String pendingPath = ProfilePhotoStorage.savePendingPhoto(appContext, accountKey, sourceFile);
        if (StringUtils.hasText(oldPendingPath) && !oldPendingPath.equals(pendingPath)) {
            ProfilePhotoStorage.deleteFileSilently(oldPendingPath);
        }
        current.pendingLocalPhotoPath = pendingPath;
        current.photoSyncState        = STATE_PENDING;
        current.photoLastError        = null;
        current.dirty                 = true;
        local.saveCache(current);

        ApiResult<String> uploadResult = remote.uploadPhotoBlocking(new File(pendingPath));
        if (uploadResult.isSuccess()) {
            ApiResult<ProfileInfoDto> fetchResult = remote.fetchPerfilBlocking();
            if (fetchResult.isSuccess() && fetchResult.data != null) {
                bridge.mergeRemoteSnapshot(accountKey, fetchResult.data, true);
            } else {
                promotePendingWithoutRemote(accountKey);
            }
            return UpdateResult.synced();
        }

        ApiError error = uploadResult.error != null
                ? uploadResult.error
                : ApiError.local(appContext.getString(R.string.error_subiendo_foto));
        if (isRetryableError(error)) {
            PerfilCacheEntity refreshed = bridge.getOrCreateCache(accountKey);
            refreshed.photoSyncState = STATE_PENDING;
            refreshed.photoLastError = error.getMessage();
            refreshed.dirty          = true;
            local.saveCache(refreshed);
            return UpdateResult.queued();
        }

        revertPendingPhoto(accountKey, error.getMessage());
        return UpdateResult.failed(error);
    }

    // ── Sync foto pendiente (desde Worker) ──────────────────────────────────

    /**
     * Si hay una foto pendiente de subida, intenta subirla.
     * Llamado por {@link PerfilSyncManager#syncAllPending}.
     *
     * @return {@code true} si se necesita reintentar.
     */
    public boolean syncPendingIfNeeded(@NonNull String accountKey) {
        PerfilCacheEntity cache = local.getCacheNow(accountKey);
        if (cache == null
                || !STATE_PENDING.equals(cache.photoSyncState)
                || !StringUtils.hasText(cache.pendingLocalPhotoPath)
                || !ProfilePhotoStorage.exists(cache.pendingLocalPhotoPath)) {
            return false;
        }

        ApiResult<String> uploadResult = remote.uploadPhotoBlocking(
                new File(cache.pendingLocalPhotoPath));
        if (uploadResult.isSuccess()) {
            ApiResult<ProfileInfoDto> fetchResult = remote.fetchPerfilBlocking();
            if (fetchResult.isSuccess() && fetchResult.data != null) {
                bridge.mergeRemoteSnapshot(accountKey, fetchResult.data, true);
            } else {
                promotePendingWithoutRemote(accountKey);
            }
            return false;
        }

        ApiError error = uploadResult.error != null
                ? uploadResult.error
                : ApiError.local(appContext.getString(R.string.error_subiendo_foto));
        if (isRetryableError(error)) {
            cache.photoLastError = error.getMessage();
            cache.dirty          = true;
            local.saveCache(cache);
            return true;
        }

        revertPendingPhoto(accountKey, error.getMessage());
        return false;
    }

    // ── Merge de estado fotográfico ─────────────────────────────────────────

    /**
     * Aplica la lógica de foto al hacer merge de un snapshot remoto.
     * Muta {@code entity} in-place. Llamado por
     * {@link PerfilSyncManager#mergeRemoteSnapshot}.
     */
    public void mergePhotoState(@NonNull PerfilCacheEntity entity,
                                @Nullable PerfilCacheEntity previous,
                                @NonNull ProfileInfoDto dto,
                                boolean preferPendingPhoto) {
        int previousVersion = previous != null ? previous.fotoVersion : -1;

        if (preferPendingPhoto && previous != null
                && StringUtils.hasText(previous.pendingLocalPhotoPath)) {
            try {
                String promoted = ProfilePhotoStorage.promotePendingToCurrent(
                        appContext, entity.accountKey, previous.pendingLocalPhotoPath,
                        Math.max(dto.fotoVersion, 1));
                if (StringUtils.hasText(previous.localPhotoPath)
                        && !promoted.equals(previous.localPhotoPath)) {
                    ProfilePhotoStorage.deleteFileSilently(previous.localPhotoPath);
                }
                entity.localPhotoPath        = promoted;
                entity.pendingLocalPhotoPath = null;
                entity.photoSyncState        = STATE_SYNCED;
                entity.photoLastError        = null;
            } catch (IOException e) {
                entity.localPhotoPath        = previous.localPhotoPath;
                entity.pendingLocalPhotoPath = previous.pendingLocalPhotoPath;
                entity.photoSyncState        = previous.photoSyncState;
                entity.photoLastError        = previous.photoLastError;
            }
        } else if (!STATE_PENDING.equals(entity.photoSyncState)
                || !StringUtils.hasText(entity.pendingLocalPhotoPath)
                || !ProfilePhotoStorage.exists(entity.pendingLocalPhotoPath)) {
            if (!StringUtils.hasText(dto.fotoPerfil)) {
                if (StringUtils.hasText(entity.localPhotoPath)) {
                    ProfilePhotoStorage.deleteFileSilently(entity.localPhotoPath);
                }
                entity.localPhotoPath        = null;
                entity.pendingLocalPhotoPath = null;
                entity.photoSyncState        = STATE_SYNCED;
                entity.photoLastError        = null;
            } else {
                boolean canReuseLocal = previous != null
                        && previousVersion == dto.fotoVersion
                        && StringUtils.hasText(previous.localPhotoPath)
                        && ProfilePhotoStorage.exists(previous.localPhotoPath);
                if (canReuseLocal) {
                    entity.localPhotoPath        = previous.localPhotoPath;
                    entity.pendingLocalPhotoPath = null;
                    entity.photoSyncState        = STATE_SYNCED;
                    entity.photoLastError        = null;
                } else {
                    try {
                        String downloadedPath = ProfilePhotoStorage.downloadRemotePhoto(
                                appContext, entity.accountKey, dto.fotoPerfil,
                                Math.max(dto.fotoVersion, 1));
                        if (StringUtils.hasText(previous != null ? previous.localPhotoPath : null)
                                && !downloadedPath.equals(previous.localPhotoPath)) {
                            ProfilePhotoStorage.deleteFileSilently(previous.localPhotoPath);
                        }
                        entity.localPhotoPath        = downloadedPath;
                        entity.pendingLocalPhotoPath = null;
                        entity.photoSyncState        = STATE_SYNCED;
                        entity.photoLastError        = null;
                    } catch (IOException e) {
                        entity.localPhotoPath        = previous != null ? previous.localPhotoPath : null;
                        entity.pendingLocalPhotoPath = previous != null ? previous.pendingLocalPhotoPath : null;
                        entity.photoSyncState        = previous != null
                                && StringUtils.hasText(previous.pendingLocalPhotoPath)
                                ? previous.photoSyncState : STATE_FAILED;
                        entity.photoLastError        = appContext.getString(
                                R.string.error_actualizando_foto_local);
                    }
                }
            }
        }
    }

    // ── Utilidades públicas ──────────────────────────────────────────────────

    /**
     * Indica si la entidad tiene una foto pendiente de subida.
     */
    public boolean hasPendingPhoto(@NonNull PerfilCacheEntity entity) {
        return STATE_PENDING.equals(entity.photoSyncState)
                && StringUtils.hasText(entity.pendingLocalPhotoPath);
    }

    /**
     * Inicializa el estado de foto en una entidad de caché nueva.
     */
    public void initDefaultPhotoState(@NonNull PerfilCacheEntity entity) {
        entity.localPhotoPath        = null;
        entity.pendingLocalPhotoPath = null;
        entity.photoSyncState        = STATE_SYNCED;
        entity.photoLastError        = null;
    }

    /**
     * Comprueba si un error de API es retryable (red, timeout, servidor, rate limit).
     */
    public static boolean isRetryableError(@Nullable ApiError error) {
        if (error == null) return true;
        ApiErrorType type = error.getType();
        return type == ApiErrorType.NETWORK
                || type == ApiErrorType.TIMEOUT
                || type == ApiErrorType.SERVER
                || type == ApiErrorType.RATE_LIMIT
                || type == ApiErrorType.CANCELED;
    }

    // ── Helpers privados ────────────────────────────────────────────────────

    private void promotePendingWithoutRemote(@NonNull String accountKey) {
        PerfilCacheEntity current = local.getCacheNow(accountKey);
        if (current == null || !StringUtils.hasText(current.pendingLocalPhotoPath)) return;
        try {
            String currentPath = ProfilePhotoStorage.promotePendingToCurrent(
                    appContext, accountKey, current.pendingLocalPhotoPath,
                    Math.max(current.fotoVersion, 1));
            if (StringUtils.hasText(current.localPhotoPath)
                    && !current.localPhotoPath.equals(currentPath)) {
                ProfilePhotoStorage.deleteFileSilently(current.localPhotoPath);
            }
            current.localPhotoPath        = currentPath;
            current.pendingLocalPhotoPath = null;
            current.photoSyncState        = STATE_SYNCED;
            current.photoLastError        = null;
            current.dirty                 = bridge.hasPendingTextChanges(accountKey);
            current.lastSyncedAtMs        = System.currentTimeMillis();
            local.saveCache(current);
        } catch (IOException ignored) {
        }
    }

    private void revertPendingPhoto(@NonNull String accountKey,
                                    @Nullable String errorMessage) {
        PerfilCacheEntity current = local.getCacheNow(accountKey);
        if (current == null) return;
        if (StringUtils.hasText(current.pendingLocalPhotoPath)) {
            ProfilePhotoStorage.deleteFileSilently(current.pendingLocalPhotoPath);
        }
        current.pendingLocalPhotoPath = null;
        current.photoSyncState        = STATE_FAILED;
        current.photoLastError        = errorMessage;
        current.dirty                 = bridge.hasPendingTextChanges(accountKey);
        local.saveCache(current);
    }
}
