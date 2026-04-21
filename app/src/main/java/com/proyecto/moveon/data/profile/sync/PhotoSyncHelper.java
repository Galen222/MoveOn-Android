
package com.proyecto.moveon.data.profile.sync;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.proyecto.moveon.R;
import com.proyecto.moveon.core.i18n.AppLanguageManager;
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
 * Lógica de sincronización de fotos de perfil separada de
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
        /**
         * Devuelve la entidad de caché actual o crea una vacía si todavía no existe.
         *
         * @param accountKey cuenta cuyos datos locales se necesitan.
         * @return caché de perfil utilizable para la sincronización.
         */
        @NonNull PerfilCacheEntity getOrCreateCache(@NonNull String accountKey);

        /**
         * Fusiona un snapshot remoto dentro de la caché local preservando, si procede, una foto pendiente.
         *
         * @param accountKey cuenta propietaria del perfil.
         * @param dto snapshot remoto recién obtenido del backend.
         * @param preferPendingPhoto indica si la foto local pendiente debe prevalecer sobre el snapshot.
         */
        void mergeRemoteSnapshot(@NonNull String accountKey,
                                 @NonNull ProfileInfoDto dto,
                                 boolean preferPendingPhoto);

        /**
         * Indica si siguen existiendo cambios de texto pendientes además del estado fotográfico.
         *
         * @param accountKey cuenta cuyo estado sucio se consulta.
         * @return {@code true} si aún quedan parches textuales sin sincronizar.
         */
        boolean hasPendingTextChanges(@NonNull String accountKey);
    }

    // ── Campos ──────────────────────────────────────────────────────────────

    private final Context appContext;
    private final PerfilLocalDataSource local;
    private final PerfilRemoteDataSource remote;
    private final SyncManagerBridge bridge;

    /**
     * Crea el helper responsable del ciclo de vida local y remoto de la foto de perfil.
     *
     * @param context contexto usado para operaciones de almacenamiento y cadenas localizadas.
     * @param local acceso al almacenamiento local del perfil.
     * @param remote acceso remoto al backend de perfil.
     * @param bridge puente hacia {@link PerfilSyncManager} para reusar caché y merge global.
     */
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
     * Guarda la foto localmente, intenta subirla al backend y deja el estado final preparado
     * como sincronizado, en cola o fallido según la respuesta remota.
     * <b>Blocking — llamar desde hilo IO.</b>
     *
     * @param accountKey cuenta dueña de la foto que se está actualizando.
     * @param sourceFile archivo original seleccionado por el usuario.
     * @return resultado de actualización alineado con {@link PerfilSyncManager} para que la UI decida cómo reaccionar.
     * @throws IOException si falla el guardado local de la foto pendiente o su promoción a foto actual.
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
                : ApiError.local(AppLanguageManager.getString(appContext, R.string.error_subiendo_foto));
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
     * @param accountKey cuenta cuya cola fotográfica se va a drenar.
     * @return {@code true} si el error es recuperable y conviene reintentar más tarde.
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
                : ApiError.local(AppLanguageManager.getString(appContext, R.string.error_subiendo_foto));
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
     *
     * @param entity entidad destino que se irá actualizando con el estado fotográfico resultante.
     * @param previous snapshot local previo, usado para reutilizar archivos y conservar pendientes cuando conviene.
     * @param dto snapshot remoto recién obtenido del backend.
     * @param preferPendingPhoto {@code true} si una foto pendiente local debe prevalecer sobre la versión remota.
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
                        entity.photoLastError        = AppLanguageManager.getString(appContext, 
                                R.string.error_actualizando_foto_local);
                    }
                }
            }
        }
    }

    // ── Utilidades públicas ──────────────────────────────────────────────────

    /**
     * Indica si la entidad tiene una foto pendiente de subida.
     *
     * @param entity caché cuyo estado fotográfico se quiere inspeccionar.
     * @return {@code true} cuando la foto sigue marcada como pendiente y existe una ruta local asociada.
     */
    public boolean hasPendingPhoto(@NonNull PerfilCacheEntity entity) {
        return STATE_PENDING.equals(entity.photoSyncState)
                && StringUtils.hasText(entity.pendingLocalPhotoPath);
    }

    /**
     * Inicializa el estado de foto en una entidad de caché nueva.
     *
     * @param entity entidad recién creada a la que se asignará un estado fotográfico limpio.
     */
    public void initDefaultPhotoState(@NonNull PerfilCacheEntity entity) {
        entity.localPhotoPath        = null;
        entity.pendingLocalPhotoPath = null;
        entity.photoSyncState        = STATE_SYNCED;
        entity.photoLastError        = null;
    }

    /**
     * Comprueba si un error de API es retryable (red, timeout, servidor, rate limit).
     *
     * @param error error producido durante la subida o descarga; puede ser {@code null} cuando faltan detalles.
     * @return {@code true} si merece la pena mantener la foto en cola para un reintento posterior.
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

    /**
     * Promociona la foto pendiente a foto actual cuando la subida fue bien pero no se pudo refrescar el snapshot remoto.
     *
     * @param accountKey cuenta cuya foto local debe consolidarse como sincronizada.
     */
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

    /**
     * Descarta la foto pendiente y deja el estado local marcado como fallo no recuperable.
     *
     * @param accountKey cuenta cuya foto pendiente debe revertirse.
     * @param errorMessage mensaje de error visible que se conservará en la caché.
     */
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

