
package com.proyecto.moveon.data.profile.sync;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.proyecto.moveon.R;
import com.proyecto.moveon.core.i18n.AppLanguageManager;
import com.proyecto.moveon.core.api.ApiError;
import com.proyecto.moveon.core.api.ApiResult;
import com.proyecto.moveon.data.local.entity.PerfilCacheEntity;
import com.proyecto.moveon.data.local.entity.PerfilPendingPatchEntity;
import com.proyecto.moveon.data.profile.PerfilRepository.SyncResult;
import com.proyecto.moveon.data.profile.PerfilRepository.UpdateResult;
import com.proyecto.moveon.data.profile.UserPrefsRepository;
import com.proyecto.moveon.data.profile.dto.ProfileInfoDto;
import com.proyecto.moveon.data.profile.local.PerfilLocalDataSource;
import com.proyecto.moveon.data.profile.remote.PerfilRemoteDataSource;
import com.proyecto.moveon.domain.profile.PerfilUsuario;
import com.proyecto.moveon.utils.StringUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Lógica de sincronización del perfil extraída de {@code PerfilRepository}.
 *
 * <p>Contiene la lógica de sync, merge, patch optimista y helpers de caché.
 * La lógica de foto (upload, promote, revert, merge de estado fotográfico)
 * se delega a {@link PhotoSyncHelper}.</p>
 *
 * <p>Todos los métodos públicos son <b>blocking</b> — el Repository los invoca
 * dentro de {@code io.execute()} o desde el Worker.</p>
 */
public final class PerfilSyncManager implements PhotoSyncHelper.SyncManagerBridge {

    /**
     * Máximo de reintentos para patches pendientes.
     * Tras alcanzar este límite, el patch se marca como FAILED y deja de
     * aparecer en {@code getPending()} (el DAO solo lee state = 'PENDING').
     * Esto evita que un error retryable repetido (ej. backend caído durante
     * horas) mantenga la cola bloqueada indefinidamente.
     */
    private static final int MAX_PATCH_ATTEMPTS = 5;

    private static final String STATE_PENDING = "PENDING";
    private static final String STATE_FAILED  = "FAILED";

    private final Context appContext;
    private final PerfilLocalDataSource local;
    private final PerfilRemoteDataSource remote;
    private final UserPrefsRepository userPrefsRepository;
    private final PhotoSyncHelper photoHelper;
    private final Gson gson = new Gson();

    /**
     * Crea el coordinador de sincronización de perfil con sus dependencias locales y remotas.
     *
     * @param context contexto de aplicación.
     * @param local datasource local de caché y cola pendiente.
     * @param remote datasource remoto del perfil.
     * @param userPrefsRepository repositorio usado para reflejar objetivos sincronizados en preferencias.
     */
    public PerfilSyncManager(@NonNull Context context,
                             @NonNull PerfilLocalDataSource local,
                             @NonNull PerfilRemoteDataSource remote,
                             @NonNull UserPrefsRepository userPrefsRepository) {
        this.appContext = context.getApplicationContext();
        this.local = local;
        this.remote = remote;
        this.userPrefsRepository = userPrefsRepository;
        this.photoHelper = new PhotoSyncHelper(appContext, local, remote, this);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SyncManagerBridge — usado por PhotoSyncHelper
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Recupera la caché de perfil existente o crea una vacía con valores por defecto.
     *
     * @param accountKey clave lógica de la cuenta.
     * @return entidad de caché disponible para operar.
     */
    @Override
    @NonNull
    public PerfilCacheEntity getOrCreateCache(@NonNull String accountKey) {
        PerfilCacheEntity current = local.getCacheNow(accountKey);
        return current != null ? current : createEmptyCache(accountKey);
    }

    /**
     * Integra un snapshot remoto del perfil preservando, si procede, el estado local pendiente de foto.
     *
     * @param accountKey clave lógica de la cuenta.
     * @param dto snapshot remoto del backend.
     * @param preferPendingPhoto {@code true} para priorizar una foto local pendiente sobre la remota.
     */
    @Override
    public void mergeRemoteSnapshot(@NonNull String accountKey,
                                    @NonNull ProfileInfoDto dto,
                                    boolean preferPendingPhoto) {
        mergeRemoteSnapshotInternal(accountKey, dto, preferPendingPhoto);
    }

    /**
     * Indica si quedan patches de texto pendientes de sincronizar para la cuenta.
     *
     * @param accountKey clave lógica de la cuenta.
     * @return {@code true} cuando la cola local de patches no está vacía.
     */
    @Override
    public boolean hasPendingTextChanges(@NonNull String accountKey) {
        return local.countPending(accountKey) > 0;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Retry helper centralizado
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Evalúa si un patch pendiente debe seguir en cola de reintentos.
     *
     * <p>Incrementa el contador de intentos y actualiza el mensaje de error. Si el error no es
     * retryable o se ha alcanzado {@link #MAX_PATCH_ATTEMPTS}, marca el patch como FAILED y
     * devuelve {@code false}. En caso contrario lo mantiene como PENDING y devuelve {@code true}.</p>
     *
     * @param op patch pendiente cuyo estado de reintento debe actualizarse.
     * @param error error que provocó el fallo del último intento.
     * @return {@code true} si el patch sigue pendiente y merece reintento.
     */
    private boolean shouldKeepRetrying(@NonNull PerfilPendingPatchEntity op,
                                       @NonNull ApiError error) {
        op.attempts += 1;
        op.lastError = error.getMessage();

        if (!PhotoSyncHelper.isRetryableError(error)) {
            op.state = STATE_FAILED;
            local.updatePatch(op);
            return false;
        }

        if (op.attempts >= MAX_PATCH_ATTEMPTS) {
            op.state = STATE_FAILED;
            local.updatePatch(op);
            return false;
        }

        op.state = STATE_PENDING;
        local.updatePatch(op);
        return true;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Patch + sync directo
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Aplica un patch optimistamente en Room, intenta sincronizar con el backend y gestiona el resultado.
     *
     * <p>Puede terminar como {@link UpdateResult#synced()}, {@link UpdateResult#queued()} o
     * {@link UpdateResult#failed(ApiError)} según la respuesta remota y la política de reintentos.</p>
     *
     * <p><b>Blocking:</b> llamar desde hilo IO.</p>
     *
     * @param accountKey clave lógica de la cuenta cuyo perfil debe actualizarse.
     * @param patchJson patch parcial del perfil ya validado por la capa superior.
     * @return resultado final del intento de actualización.
     */
    @NonNull
    public UpdateResult patchAndSync(@NonNull String accountKey,
                                     @NonNull JsonObject patchJson) {
        PerfilPendingPatchEntity op = new PerfilPendingPatchEntity();
        op.operationId = UUID.randomUUID().toString();
        op.accountKey  = accountKey;
        op.payloadJson = gson.toJson(patchJson);
        op.createdAtMs = System.currentTimeMillis();
        op.attempts    = 0;
        op.lastError   = null;
        op.state       = STATE_PENDING;
        local.enqueuePatch(op);

        PerfilCacheEntity current = getOrCreateCache(accountKey);
        applyPatchToCache(current, patchJson);
        current.dirty = true;
        local.saveCache(current);

        ApiResult<String> result = remote.patchPerfilBlocking(patchJson);
        if (result.isSuccess()) {
            local.deletePatch(op.operationId);
            ApiResult<ProfileInfoDto> fetchResult = remote.fetchPerfilBlocking();
            if (fetchResult.isSuccess() && fetchResult.data != null) {
                mergeRemoteSnapshotInternal(accountKey, fetchResult.data, false);
            } else {
                PerfilCacheEntity updated = local.getCacheNow(accountKey);
                if (updated != null) {
                    updated.dirty = hasPendingTextChanges(accountKey)
                            || photoHelper.hasPendingPhoto(updated);
                    updated.lastSyncedAtMs = System.currentTimeMillis();
                    local.saveCache(updated);
                }
            }
            return UpdateResult.synced();
        }

        // Centraliza la decisión de reintento para mantener un límite consistente.
        ApiError error = result.error != null
                ? result.error
                : ApiError.local(AppLanguageManager.getString(appContext, R.string.error_sincronizando_perfil));

        if (shouldKeepRetrying(op, error)) {
            return UpdateResult.queued();
        }

        // Fallo permanente: refrescar caché desde servidor para revertir optimismo.
        ApiResult<ProfileInfoDto> fetchResult = remote.fetchPerfilBlocking();
        if (fetchResult.isSuccess() && fetchResult.data != null) {
            mergeRemoteSnapshotInternal(accountKey, fetchResult.data, false);
        }

        return UpdateResult.failed(error);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Upload foto — delegado a PhotoSyncHelper
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Guarda la foto localmente, intenta subirla al backend y gestiona el resultado.
     *
     * <p><b>Blocking:</b> llamar desde hilo IO.</p>
     *
     * @param accountKey clave lógica de la cuenta propietaria de la foto.
     * @param sourceFile fichero local seleccionado por el usuario.
     * @return resultado final del flujo de subida y sincronización.
     * @throws IOException si la copia o lectura local de la foto falla antes de subirla.
     */
    @NonNull
    public UpdateResult uploadPhotoAndSync(@NonNull String accountKey,
                                           @NonNull File sourceFile) throws IOException {
        return photoHelper.uploadAndSync(accountKey, sourceFile);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Sync completo (Worker)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Sincroniza todos los patches pendientes, la foto y el refresh final del perfil.
     *
     * <p>Es el punto de entrada usado por {@code SyncPerfilWorker} para vaciar la cola offline y
     * dejar la caché consolidada tras aplicar texto, foto y snapshot remoto final.</p>
     *
     * <p><b>Blocking:</b> llamar desde hilo IO.</p>
     *
     * @param accountKey clave lógica de la cuenta que debe sincronizarse.
     * @return resultado agregado del ciclo completo de sincronización.
     */
    @NonNull
    public SyncResult syncAllPending(@NonNull String accountKey) {
        boolean retryNeeded = false;

        // Detectamos al inicio si realmente existía cola offline.
        // Esto evita mostrar el snackbar cuando el worker corre sin nada pendiente.
        boolean hadPendingText = local.countPending(accountKey) > 0;
        PerfilCacheEntity currentCache = local.getCacheNow(accountKey);
        boolean hadPendingPhoto = currentCache != null && photoHelper.hasPendingPhoto(currentCache);
        boolean hadPendingWork = hadPendingText || hadPendingPhoto;

        // 1. Sync patches de texto pendientes.
        List<PerfilPendingPatchEntity> ops = local.getPending(accountKey);
        if (ops != null) {
            for (PerfilPendingPatchEntity op : ops) {
                JsonObject patchJson = gson.fromJson(op.payloadJson, JsonObject.class);
                ApiResult<String> result = remote.patchPerfilBlocking(patchJson);

                if (result.isSuccess()) {
                    // Eliminamos el patch porque ya quedó confirmado en backend.
                    local.deletePatch(op.operationId);
                    continue;
                }

                // Centraliza la decisión de reintento para mantener un límite consistente.
                ApiError error = result.error != null
                        ? result.error
                        : ApiError.local(AppLanguageManager.getString(appContext, R.string.error_sincronizando_perfil));

                if (shouldKeepRetrying(op, error)) {
                    // Si un patch sigue siendo retryable detenemos el ciclo para reintentar luego.
                    retryNeeded = true;
                    break;
                }
                // Si shouldKeepRetrying devolvió false, el patch ya quedó FAILED y no bloquea la cola.
            }
        }

        // 2. Sync foto pendiente — delegado a PhotoSyncHelper.
        if (photoHelper.syncPendingIfNeeded(accountKey)) {
            retryNeeded = true;
        }

        // 3. Refresh general del perfil para dejar la caché consolidada.
        ApiResult<ProfileInfoDto> refreshResult = remote.fetchPerfilBlocking();
        if (refreshResult.isSuccess() && refreshResult.data != null) {
            mergeRemoteSnapshotInternal(accountKey, refreshResult.data, false);
        } else if (refreshResult.error != null
                && PhotoSyncHelper.isRetryableError(refreshResult.error)) {
            retryNeeded = true;
        }

        if (retryNeeded) {
            return SyncResult.retry();
        }

        return hadPendingWork ? SyncResult.successCompleted() : SyncResult.successNoop();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Merge remoto
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Reconstruye la caché local a partir del snapshot remoto reaplicando patches pendientes y estado de foto.
     *
     * @param accountKey clave lógica de la cuenta.
     * @param dto snapshot remoto recibido.
     * @param preferPendingPhoto {@code true} para mantener visible la foto local pendiente cuando corresponda.
     */
    private void mergeRemoteSnapshotInternal(@NonNull String accountKey,
                                             @NonNull ProfileInfoDto dto,
                                             boolean preferPendingPhoto) {
        PerfilCacheEntity previous = local.getCacheNow(accountKey);
        PerfilCacheEntity entity   = previous != null ? copyOf(previous) : createEmptyCache(accountKey);

        // ── Campos de texto ─────────────────────────────────────────────
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

        // ── Foto — delegado a PhotoSyncHelper ───────────────────────────
        photoHelper.mergePhotoState(entity, previous, dto, preferPendingPhoto);

        // ── Re-aplicar patches pendientes sobre la caché ────────────────
        List<PerfilPendingPatchEntity> pending = local.getPending(accountKey);
        if (pending != null) {
            for (PerfilPendingPatchEntity op : pending) {
                JsonObject patchJson = gson.fromJson(op.payloadJson, JsonObject.class);
                applyPatchToCache(entity, patchJson);
            }
        }

        // ── Dirty flag y timestamps ─────────────────────────────────────
        entity.dirty = (pending != null && !pending.isEmpty())
                || photoHelper.hasPendingPhoto(entity);
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

    // ══════════════════════════════════════════════════════════════════════════
    // Cache helpers
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Crea una caché base con los valores iniciales que espera la UI del perfil.
     *
     * @param accountKey clave lógica de la cuenta.
     * @return entidad nueva inicializada con defaults y estado fotográfico por defecto.
     */
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
        e.perfilVisible         = true;
        e.totalPuntos           = 0;
        e.totalCalorias         = 0L;
        e.objetivoSemanalMetros = 50_000L;
        e.objetivoMensualMetros = 150_000L;
        e.dirty                 = false;
        e.lastFetchedAtMs       = 0L;
        e.lastSyncedAtMs        = 0L;
        photoHelper.initDefaultPhotoState(e);
        return e;
    }

    /**
     * Realiza una copia superficial campo a campo de la caché del perfil.
     *
     * @param source entidad origen.
     * @return copia desacoplada apta para mutaciones locales.
     */
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

    // ══════════════════════════════════════════════════════════════════════════
    // Mapping
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Convierte la caché persistida al modelo de dominio consumido por la UI.
     *
     * @param entity entidad local ya resuelta.
     * @return instancia de {@link PerfilUsuario} lista para exponer.
     */
    @NonNull
    public PerfilUsuario mapEntityToDomain(@NonNull PerfilCacheEntity entity) {
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

    // ══════════════════════════════════════════════════════════════════════════
    // Patch helpers
    // ══════════════════════════════════════════════════════════════════════════


    /**
     * Aplica sobre la caché local solo los campos presentes en un patch JSON.
     *
     * @param cache entidad de caché a mutar.
     * @param patch payload parcial con los cambios del perfil.
     */
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

    /**
     * Lee una cadena opcional desde un elemento JSON respetando los valores nulos.
     *
     * @param element elemento JSON a interpretar.
     * @return cadena contenida o {@code null} cuando el elemento es {@code JsonNull}.
     */
    @Nullable
    private String readNullableString(@NonNull JsonElement element) {
        return element.isJsonNull() ? null : element.getAsString();
    }

    /**
     * Expone la heurística compartida para decidir si un error merece reintento.
     *
     * @param error error a evaluar.
     * @return {@code true} cuando {@link PhotoSyncHelper} considera el error recuperable.
     */
    public boolean isRetryable(@Nullable ApiError error) {
        return PhotoSyncHelper.isRetryableError(error);
    }
}

