package com.proyecto.moveon.data.activities.sync;

import android.content.Context;

import androidx.annotation.NonNull;

import com.proyecto.moveon.R;
import com.proyecto.moveon.core.api.ApiError;
import com.proyecto.moveon.core.api.ApiErrorType;
import com.proyecto.moveon.core.api.ApiResult;
import com.proyecto.moveon.data.activities.ActivityRepository.SyncResult;
import com.proyecto.moveon.data.activities.ActivitySyncState;
import com.proyecto.moveon.data.activities.dto.ActividadResponseDto;
import com.proyecto.moveon.data.activities.local.ActividadLocalDataSource;
import com.proyecto.moveon.data.activities.remote.ActividadRemoteDataSource;
import com.proyecto.moveon.data.local.entity.ActividadEntity;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Lógica blocking de sincronización de actividades.
 *
 * <p>El servidor persiste y devuelve todos los campos de tracking enriquecido,
 * por lo que {@link #mapDtoIntoEntity} sobreescribe el registro local completo
 * con los datos autoritativos del servidor.</p>
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

    @NonNull
    public SyncResult syncPendingNow(@NonNull String accountKey) {
        List<ActividadEntity> creates = local.getPendingCreates(accountKey);
        boolean hadPendingCreates = creates != null && !creates.isEmpty();

        if (creates != null) {
            for (ActividadEntity entity : creates) {
                ApiResult<ActividadResponseDto> result =
                        remote.createActividadBlocking(
                                ActividadCreatePayload.fromEntity(entity).toJson());

                if (result.isSuccess() && result.data != null) {
                    mapDtoIntoEntity(entity, result.data);
                    entity.syncState = ActivitySyncState.SYNCED;
                    entity.lastError = null;
                    entity.updatedAtMs = System.currentTimeMillis();
                    local.save(entity);
                    continue;
                }

                ApiError error = result.error != null
                        ? result.error
                        : ApiError.local(appContext.getString(R.string.error_sincronizando_actividad));

                if (isRetryable(error)) {
                    entity.lastError = error.getMessage();
                    entity.updatedAtMs = System.currentTimeMillis();
                    local.save(entity);
                    return SyncResult.retry();
                }

                entity.syncState = ActivitySyncState.FAILED_CREATE;
                entity.lastError = error.getMessage();
                entity.updatedAtMs = System.currentTimeMillis();
                local.save(entity);
            }
        }

        ApiResult<List<ActividadResponseDto>> refreshResult = remote.fetchAllActividadesBlocking();
        if (refreshResult.isSuccess() && refreshResult.data != null) {
            mergeRemoteSnapshot(accountKey, refreshResult.data);
            return hadPendingCreates ? SyncResult.successCompleted() : SyncResult.successNoop();
        }

        if (refreshResult.error != null && isRetryable(refreshResult.error)) {
            return SyncResult.retry();
        }

        return hadPendingCreates ? SyncResult.successCompleted() : SyncResult.successNoop();
    }

    public void mergeRemoteSnapshot(@NonNull String accountKey,
                                    @NonNull List<ActividadResponseDto> remoteItems) {
        List<ActividadEntity> current = local.getAllNow(accountKey);
        Set<Integer> remoteIds = new HashSet<>();

        for (ActividadResponseDto dto : remoteItems) {
            remoteIds.add(dto.id);

            ActividadEntity existing = local.getByRemoteId(accountKey, dto.id);
            ActividadEntity entity = existing != null ? existing : new ActividadEntity();
            if (existing == null) {
                entity.localId = "remote_" + dto.id;
                entity.accountKey = accountKey;
                entity.createdAtMs = System.currentTimeMillis();
            }

            mapDtoIntoEntity(entity, dto);
            entity.syncState = ActivitySyncState.SYNCED;
            entity.lastError = null;
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

    /**
     * Sobreescribe {@code entity} con los datos autoritativos del servidor.
     *
     * <p>{@code tipo} y {@code fechaRuta} son {@code @NonNull} en Room pero
     * {@code @Nullable} en el DTO (el servidor siempre los devuelve, pero Gson
     * puede dejarlos null si el campo falta en la respuesta). En ese caso
     * se preserva el valor local para no romper la constraint de Room.</p>
     */
    private void mapDtoIntoEntity(@NonNull ActividadEntity entity,
                                  @NonNull ActividadResponseDto dto) {
        entity.remoteId              = dto.id;
        entity.tipo                  = dto.tipo != null ? dto.tipo : entity.tipo;
        entity.distancia             = dto.distancia;
        entity.duracionTotal         = dto.duracionTotal;
        entity.duracionMovimiento    = dto.duracionMovimiento;
        entity.duracionParado        = dto.duracionParado;
        entity.duracionPausaManual   = dto.duracionPausaManual;
        entity.caloriasQuemadas      = dto.caloriasQuemadas;
        entity.ritmoMedioMovimiento  = dto.ritmoMedioMovimiento;
        entity.ritmoMedioTotal       = dto.ritmoMedioTotal;
        entity.velocidadMediaKmhX100 = dto.velocidadMediaKmhX100;
        entity.velocidadMaxKmhX100   = dto.velocidadMaxKmhX100;
        entity.autoPausas            = dto.autoPausas;
        entity.pausasManuales        = dto.pausasManuales;
        entity.alertasVelocidad      = dto.alertasVelocidad;
        entity.rutaPolilinea         = dto.rutaPolilinea;
        entity.rutaMapaUrl           = dto.rutaMapaUrl;
        entity.fechaRuta             = dto.fechaRuta != null ? dto.fechaRuta : entity.fechaRuta;
    }

    public boolean isRetryable(@NonNull ApiError error) {
        ApiErrorType type = error.getType();
        return type == ApiErrorType.NETWORK
                || type == ApiErrorType.TIMEOUT
                || type == ApiErrorType.RATE_LIMIT
                || type == ApiErrorType.SERVER
                || type == ApiErrorType.CANCELED;
    }
}