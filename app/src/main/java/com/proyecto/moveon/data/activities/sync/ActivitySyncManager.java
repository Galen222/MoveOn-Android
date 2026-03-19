package com.proyecto.moveon.data.activities.sync;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.proyecto.moveon.R;
import com.proyecto.moveon.core.api.ApiError;
import com.proyecto.moveon.core.api.ApiErrorType;
import com.proyecto.moveon.core.api.ApiResult;
import com.proyecto.moveon.data.activities.ActivitySyncState;
import com.proyecto.moveon.data.activities.ActivityRepository.SyncResult;
import com.proyecto.moveon.data.activities.dto.ActividadResponseDto;
import com.proyecto.moveon.data.activities.local.ActividadLocalDataSource;
import com.proyecto.moveon.data.activities.remote.ActividadRemoteDataSource;
import com.proyecto.moveon.data.local.entity.ActividadEntity;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * MEJ-07: Lógica de sincronización de actividades extraída de {@code ActivityRepository}.
 *
 * <p>Contiene: sync de pending creates, merge de snapshot remoto, mapping
 * DTO→Entity, y clasificación de errores retryable. Todos los métodos
 * públicos son <b>blocking</b> — el Repository los invoca desde
 * {@code io.execute()} o desde el Worker.</p>
 */
public final class ActivitySyncManager {

    private final Context appContext;
    private final ActividadLocalDataSource local;
    private final ActividadRemoteDataSource remote;

    public ActivitySyncManager(@NonNull Context context,
                               @NonNull ActividadLocalDataSource local,
                               @NonNull ActividadRemoteDataSource remote) {
        this.appContext = context.getApplicationContext();
        this.local = local;
        this.remote = remote;
    }

    // ── Sync completo (Worker) ───────────────────────────────────────────────

    /**
     * Sincroniza actividades pendientes de crear + refresh completo desde servidor.
     * Llamado por {@code SyncActividadesWorker}.
     * <b>Blocking — llamar desde hilo IO.</b>
     */
    @NonNull
    public SyncResult syncPendingNow(@NonNull String accountKey) {
        // Capturamos si al arrancar este ciclo había trabajo offline real.
        // Así evitamos disparar el snackbar cuando el worker corre "en vacío".
        List<ActividadEntity> creates = local.getPendingCreates(accountKey);
        boolean hadPendingCreates = creates != null && !creates.isEmpty();

        if (creates != null) {
            for (ActividadEntity entity : creates) {
                ApiResult<ActividadResponseDto> result =
                        remote.createActividadBlocking(ActividadCreatePayload.fromEntity(entity).toJson());

                if (result.isSuccess() && result.data != null) {
                    ActividadResponseDto dto = result.data;
                    entity.remoteId         = dto.id;
                    entity.tipo             = dto.tipo;
                    entity.distancia        = dto.distancia;
                    entity.duracion         = dto.duracion;
                    entity.caloriasQuemadas = dto.caloriasQuemadas;
                    entity.rutaPolilinea    = dto.rutaPolilinea;
                    entity.rutaMapaUrl      = dto.rutaMapaUrl;
                    entity.fechaRuta        = dto.fechaRuta;
                    entity.syncState        = ActivitySyncState.SYNCED;
                    entity.lastError        = null;
                    entity.updatedAtMs      = System.currentTimeMillis();
                    local.save(entity);
                    continue;
                }

                ApiError error = result.error != null
                        ? result.error
                        : ApiError.local(appContext.getString(R.string.error_sincronizando_actividad));

                if (isRetryable(error)) {
                    // Dejamos la entidad pendiente para que WorkManager la reintente.
                    entity.lastError   = error.getMessage();
                    entity.updatedAtMs = System.currentTimeMillis();
                    local.save(entity);
                    return SyncResult.retry();
                }

                // Si el error es permanente marcamos FAILED_CREATE para sacar el elemento de la cola.
                entity.syncState   = ActivitySyncState.FAILED_CREATE;
                entity.lastError   = error.getMessage();
                entity.updatedAtMs = System.currentTimeMillis();
                local.save(entity);
            }
        }

        // Tras vaciar la cola local, refrescamos snapshot remoto para dejar Room coherente.
        ApiResult<List<ActividadResponseDto>> refreshResult = remote.fetchAllActividadesBlocking();
        if (refreshResult.isSuccess() && refreshResult.data != null) {
            mergeRemoteSnapshot(accountKey, refreshResult.data);
            return hadPendingCreates ? SyncResult.successCompleted() : SyncResult.successNoop();
        }

        if (refreshResult.error != null && isRetryable(refreshResult.error)) {
            return SyncResult.retry();
        }

        // Si el refresh falla de forma no retryable pero ya no queda cola local, tratamos el ciclo
        // como completado para no dejar el trabajo pendiente bloqueado.
        return hadPendingCreates ? SyncResult.successCompleted() : SyncResult.successNoop();
    }

    // ── Merge remoto ─────────────────────────────────────────────────────────

    /**
     * Fusiona la lista de actividades del servidor con Room.
     * - Actualiza existentes, inserta nuevas, borra las SYNCED que ya no existen en remoto.
     */
    public void mergeRemoteSnapshot(@NonNull String accountKey,
                                    @NonNull List<ActividadResponseDto> remoteItems) {
        List<ActividadEntity> current = local.getAllNow(accountKey);
        Set<Integer> remoteIds = new HashSet<>();

        for (ActividadResponseDto dto : remoteItems) {
            remoteIds.add(dto.id);

            ActividadEntity existing = local.getByRemoteId(accountKey, dto.id);
            ActividadEntity entity   = existing != null ? existing : new ActividadEntity();
            if (existing == null) {
                entity.localId     = "remote_" + dto.id;
                entity.accountKey  = accountKey;
                entity.createdAtMs = System.currentTimeMillis();
            }

            mapDtoIntoEntity(entity, dto);
            entity.syncState   = ActivitySyncState.SYNCED;
            entity.lastError   = null;
            entity.updatedAtMs = System.currentTimeMillis();
            local.save(entity);
        }

        for (ActividadEntity entity : current) {
            if (entity.remoteId == null) continue;
            if (!ActivitySyncState.SYNCED.equals(entity.syncState)) continue;
            if (!remoteIds.contains(entity.remoteId)) {
                local.deleteByLocalId(entity.localId);
            }
        }
    }

    // ── Mapping ──────────────────────────────────────────────────────────────

    private void mapDtoIntoEntity(@NonNull ActividadEntity entity, @NonNull ActividadResponseDto dto) {
        entity.remoteId         = dto.id;
        entity.tipo             = dto.tipo;
        entity.distancia        = dto.distancia;
        entity.duracion         = dto.duracion;
        entity.caloriasQuemadas = dto.caloriasQuemadas;
        entity.rutaPolilinea    = dto.rutaPolilinea;
        entity.rutaMapaUrl      = dto.rutaMapaUrl;
        entity.fechaRuta        = dto.fechaRuta;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    public boolean isRetryable(@NonNull ApiError error) {
        ApiErrorType type = error.getType();
        return type == ApiErrorType.NETWORK
                || type == ApiErrorType.TIMEOUT
                || type == ApiErrorType.RATE_LIMIT
                || type == ApiErrorType.SERVER
                || type == ApiErrorType.CANCELED;
    }
}
