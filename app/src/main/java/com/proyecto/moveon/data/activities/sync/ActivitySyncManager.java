package com.proyecto.moveon.data.activities.sync;

import android.content.Context;

import androidx.annotation.NonNull;

import com.proyecto.moveon.R;
import com.proyecto.moveon.core.i18n.AppLanguageManager;
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

    /**
     * Construye el gestor a partir del par local/remoto que se le inyecta.
     * La separación permite testear con mocks sin tocar Room ni la red,
     * y elimina la dependencia directa con singletons.
     *
     * @param context cualquier contexto; internamente se usa el applicationContext para no fugar.
     * @param local data source de Room con las actividades y su estado de sincronización.
     * @param remote data source HTTP que habla con el backend de actividades.
     */
    public ActivitySyncManager(@NonNull Context context,
                               @NonNull ActividadLocalDataSource local,
                               @NonNull ActividadRemoteDataSource remote) {
        this.appContext = context.getApplicationContext();
        this.local = local;
        this.remote = remote;
    }

    /**
     * Empuja al backend todas las actividades locales en estado pendiente
     * del usuario indicado. Por cada actividad creada con éxito, actualiza
     * la entidad local con el {@code remoteId} y el estado {@code SYNCED}
     * devuelto por el servidor para que no se reintente en la siguiente
     * pasada.
     *
     * <p>El bucle es secuencial a propósito, no paralelo: así el orden de
     * llegada al backend respeta el orden cronológico local y evita saturar
     * al servidor con muchas subidas simultáneas.</p>
     *
     * @param accountKey clave de la cuenta sobre la que se sincroniza.
     * @return {@link SyncResult#retry()} si se detecta un error transitorio; en otro caso,
     * resultado de éxito distinguiendo entre sincronización efectiva y no-op.
     */
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
                        : ApiError.local(AppLanguageManager.getString(appContext, R.string.error_sincronizando_actividad));

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

    /**
     * Integra el listado remoto con el estado local: inserta o actualiza
     * las filas que existen en el servidor (manteniendo el {@code remoteId}
     * como clave) y deja intactas las locales en estado pendiente para no
     * perder trabajo que aún no se ha subido.
     *
     * @param accountKey clave de la cuenta a la que pertenecen las actividades.
     * @param remoteItems lista recibida del backend con el estado canónico de cada actividad.
     */
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
     *
     * @param entity entidad local que debe actualizarse con el estado canónico remoto.
     * @param dto dto devuelto por el backend con los campos persistidos y enriquecidos.
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
        entity.ritmoMaximo           = dto.ritmoMaximo;
        entity.velocidadMediaKmhX100 = dto.velocidadMediaKmhX100;
        entity.velocidadMaxKmhX100   = dto.velocidadMaxKmhX100;
        entity.autoPausas            = dto.autoPausas;
        entity.pausasManuales        = dto.pausasManuales;
        entity.alertasVelocidad      = dto.alertasVelocidad;
        entity.rutaPolilinea         = dto.rutaPolilinea;
        entity.rutaMapaUrl           = dto.rutaMapaUrl;
        entity.fechaRuta             = dto.fechaRuta != null ? dto.fechaRuta : entity.fechaRuta;
    }

    /**
     * Decide si un error devuelto al intentar sincronizar merece reintento:
     * problemas de red, timeouts, rate limit, errores 5xx y cancelaciones
     * son transitorios; el resto (400, 401, 409…) requieren intervención
     * y no se reintentan.
     *
     * @param error error producido en la subida al backend.
     * @return {@code true} si el Worker debe reencolar el intento más tarde.
     */
    public boolean isRetryable(@NonNull ApiError error) {
        ApiErrorType type = error.getType();
        return type == ApiErrorType.NETWORK
                || type == ApiErrorType.TIMEOUT
                || type == ApiErrorType.RATE_LIMIT
                || type == ApiErrorType.SERVER
                || type == ApiErrorType.CANCELED;
    }
}
