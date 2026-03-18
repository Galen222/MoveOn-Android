package com.proyecto.moveon.data.activities;

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

import com.proyecto.moveon.core.api.ApiError;
import com.proyecto.moveon.core.api.ApiErrorType;
import com.proyecto.moveon.core.api.ApiResult;
import com.proyecto.moveon.core.concurrency.MoveOnExecutors;
import com.proyecto.moveon.data.activities.dto.ActividadResponseDto;
import com.proyecto.moveon.data.activities.dto.BorrarActividadResponseDto;
import com.proyecto.moveon.data.activities.dto.GuardarActividadRequestDto;
import com.proyecto.moveon.data.activities.dto.GuardarActividadResponseDto;
import com.proyecto.moveon.data.activities.local.ActividadLocalDataSource;
import com.proyecto.moveon.data.activities.remote.ActividadRemoteDataSource;
import com.proyecto.moveon.data.activities.sync.ActivitySyncManager;
import com.proyecto.moveon.data.local.db.AppDatabase;
import com.proyecto.moveon.data.local.entity.ActividadEntity;
import com.proyecto.moveon.data.profile.dto.ProfileInfoDto;
import com.proyecto.moveon.data.remote.AuthenticatedApiClient;
import com.proyecto.moveon.data.session.SecureSessionManager;
import com.proyecto.moveon.domain.activity.ActividadItem;
import com.proyecto.moveon.R;
import com.proyecto.moveon.utils.StringUtils;
import com.proyecto.moveon.workers.SyncActividadesWorker;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * MEJ-07: Coordinador delgado de actividades.
 *
 * <p>Responsabilidades: exponer LiveData, validar inputs, despachar trabajo
 * al hilo IO, y programar WorkManager. La lógica de sync y merge vive
 * en {@link ActivitySyncManager}.</p>
 */
public final class ActivityRepository {

    public interface Callback<T> {
        void onResult(ApiResult<T> result);
    }

    public interface SyncCallback {
        void onComplete(@Nullable ApiError error);
    }

    public static final String UNIQUE_SYNC_WORK_NAME = "sync_actividades";
    private static final Set<String> VALID_TIPOS = new HashSet<>();

    static {
        VALID_TIPOS.add("Caminar");
        VALID_TIPOS.add("Correr");
    }

    private static final String ENDPOINT_PERFIL_INFO = "perfil/informacion";

    private final Context appContext;
    private final SecureSessionManager sessionManager;
    private final AuthenticatedApiClient apiClient;
    private final ActividadLocalDataSource local;
    private final ActividadRemoteDataSource remote;
    private final ActivitySyncManager syncManager;
    private final ExecutorService io = MoveOnExecutors.io();

    public ActivityRepository(@NonNull Context context) {
        this.appContext     = context.getApplicationContext();
        this.sessionManager = SecureSessionManager.getInstance(appContext);
        this.apiClient      = new AuthenticatedApiClient(appContext);
        AppDatabase db      = AppDatabase.getInstance(appContext);
        this.local          = new ActividadLocalDataSource(db);
        this.remote         = new ActividadRemoteDataSource(appContext);
        this.syncManager    = new ActivitySyncManager(appContext, local, remote);
    }

    // ── Guardar ───────────────────────────────────────────────────────────────

    public void guardarActividad(
            @NonNull GuardarActividadRequestDto request,
            @NonNull Callback<GuardarActividadResponseDto> callback) {

        String accountKey = sessionManager.getAccountKey();
        if (accountKey == null) {
            callback.onResult(ApiResult.failure(ApiError.local(appContext.getString(R.string.error_no_sesion_activa))));
            return;
        }

        ApiError validation = validateRequest(request);
        if (validation != null) {
            callback.onResult(ApiResult.failure(validation));
            return;
        }

        io.execute(() -> {
            long now = System.currentTimeMillis();

            ActividadEntity entity = new ActividadEntity();
            entity.localId           = UUID.randomUUID().toString();
            entity.accountKey        = accountKey;
            entity.remoteId          = null;
            entity.tipo              = request.tipo;
            entity.distancia         = request.distancia;
            entity.duracion          = request.duracion;
            entity.caloriasQuemadas  = request.caloriasQuemadas;
            entity.rutaPolilinea     = request.rutaPolilinea;
            entity.rutaMapaUrl       = null;
            entity.fechaRuta         = request.fechaRuta;
            entity.syncState         = ActivitySyncState.PENDING_CREATE;
            entity.lastError         = null;
            entity.createdAtMs       = now;
            entity.updatedAtMs       = now;

            local.save(entity);
            enqueueSync();

            GuardarActividadResponseDto dto = new GuardarActividadResponseDto();
            dto.id               = 0;
            dto.tipo             = entity.tipo;
            dto.distancia        = entity.distancia;
            dto.duracion         = entity.duracion;
            dto.caloriasQuemadas = entity.caloriasQuemadas;
            dto.rutaPolilinea    = entity.rutaPolilinea;
            dto.rutaMapaUrl      = entity.rutaMapaUrl;
            dto.fechaRuta        = entity.fechaRuta;
            dto.nuevoTotalPuntos = 0;

            callback.onResult(ApiResult.success(dto));
        });
    }

    // ── Perfil ────────────────────────────────────────────────────────────────

    public void obtenerPerfil(@NonNull Callback<ProfileInfoDto> callback) {
        apiClient.get(
                ENDPOINT_PERFIL_INFO,
                json -> {
                    if (json == null || !json.isJsonObject()) return null;
                    return new com.google.gson.Gson().fromJson(json, ProfileInfoDto.class);
                },
                callback::onResult
        );
    }

    // ── Observar / Refresh ────────────────────────────────────────────────────

    public LiveData<List<ActividadItem>> observeActividades(@NonNull String accountKey) {
        MediatorLiveData<List<ActividadItem>> result = new MediatorLiveData<>();
        result.addSource(local.observeVisible(accountKey), list -> {
            List<ActividadItem> items = new ArrayList<>();
            if (list != null) {
                for (ActividadEntity entity : list) {
                    items.add(mapEntityToDomain(entity));
                }
            }
            result.setValue(items);
        });
        return result;
    }

    public void refreshFromServer(@NonNull String accountKey, @Nullable SyncCallback callback) {
        remote.fetchAllActividades(result -> {
            if (!result.isSuccess() || result.data == null) {
                if (callback != null) {
                    callback.onComplete(result.error != null
                            ? result.error
                            : ApiError.local(appContext.getString(R.string.error_cargando_actividades)));
                }
                return;
            }

            io.execute(() -> {
                syncManager.mergeRemoteSnapshot(accountKey, result.data);
                if (callback != null) callback.onComplete(null);
            });
        });
    }

    // ── Borrar ────────────────────────────────────────────────────────────────

    public void borrarActividad(
            @NonNull String localId,
            @NonNull Callback<BorrarActividadResponseDto> callback) {

        io.execute(() -> {
            ActividadEntity entity = local.getByLocalId(localId);

            if (entity == null) {
                callback.onResult(ApiResult.failure(
                        ApiError.local(appContext.getString(R.string.error_actividad_no_encontrada))));
                return;
            }

            if (entity.remoteId == null || !ActivitySyncState.SYNCED.equals(entity.syncState)) {
                callback.onResult(ApiResult.failure(
                        ApiError.typed(ApiErrorType.VALIDATION,
                                appContext.getString(R.string.error_actividad_pendiente_sync))));
                return;
            }

            int remoteId = entity.remoteId;

            remote.deleteActividad(remoteId, result -> {
                if (!result.isSuccess()) {
                    callback.onResult(ApiResult.failure(
                            result.error != null
                                    ? result.error
                                    : ApiError.local(appContext.getString(R.string.error_eliminando_actividad))));
                    return;
                }

                io.execute(() -> {
                    local.deleteByLocalId(localId);
                    BorrarActividadResponseDto responseDto = result.data;
                    if (responseDto == null) {
                        responseDto = new BorrarActividadResponseDto();
                        responseDto.estatus = "success";
                    }
                    callback.onResult(ApiResult.success(responseDto));
                });
            });
        });
    }

    // ── Sync (Worker) ────────────────────────────────────────────────────────

    @NonNull
    public SyncResult syncPendingNow(@NonNull String accountKey) {
        return syncManager.syncPendingNow(accountKey);
    }

    public void enqueueSync() {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(SyncActividadesWorker.class)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build();

        WorkManager.getInstance(appContext)
                .enqueueUniqueWork(UNIQUE_SYNC_WORK_NAME, ExistingWorkPolicy.REPLACE, request);
    }

    public void cancelAll() {
        remote.cancelAll();
        apiClient.cancelAll();
    }

    // ── Privados ──────────────────────────────────────────────────────────────

    @NonNull
    private ActividadItem mapEntityToDomain(@NonNull ActividadEntity entity) {
        return new ActividadItem(
                entity.localId,
                entity.remoteId,
                entity.tipo,
                entity.distancia,
                entity.duracion,
                entity.caloriasQuemadas,
                entity.rutaPolilinea,
                entity.rutaMapaUrl,
                entity.fechaRuta,
                entity.syncState,
                entity.lastError
        );
    }

    @Nullable
    private ApiError validateRequest(@NonNull GuardarActividadRequestDto request) {
        if (!VALID_TIPOS.contains(request.tipo)) {
            return ApiError.typed(ApiErrorType.VALIDATION,
                    appContext.getString(R.string.error_tipo_actividad_invalido));
        }
        if (request.distancia <= 0 || request.distancia > 300000) {
            return ApiError.typed(ApiErrorType.VALIDATION,
                    appContext.getString(R.string.error_distancia_invalida));
        }
        if (request.duracion <= 0 || request.duracion > 86400) {
            return ApiError.typed(ApiErrorType.VALIDATION,
                    appContext.getString(R.string.error_duracion_invalida));
        }
        if (request.caloriasQuemadas <= 0 || request.caloriasQuemadas > 10000) {
            return ApiError.typed(ApiErrorType.VALIDATION,
                    appContext.getString(R.string.error_calorias_invalidas));
        }
        if (StringUtils.hasText(request.rutaPolilinea) && request.rutaPolilinea.trim().length() < 2) {
            return ApiError.typed(ApiErrorType.VALIDATION,
                    appContext.getString(R.string.error_polilinea_invalida));
        }
        try {
            OffsetDateTime fecha = OffsetDateTime.parse(request.fechaRuta);
            if (fecha.isAfter(OffsetDateTime.now().plusMinutes(1))) {
                return ApiError.typed(ApiErrorType.VALIDATION,
                        appContext.getString(R.string.error_fecha_futura));
            }
        } catch (Exception e) {
            return ApiError.typed(ApiErrorType.VALIDATION,
                    appContext.getString(R.string.error_formato_fecha_invalido));
        }
        return null;
    }

    // ── Inner classes ─────────────────────────────────────────────────────────

    public static final class SyncResult {
        public final boolean retry;

        private SyncResult(boolean retry) { this.retry = retry; }

        public static SyncResult success() { return new SyncResult(false); }
        public static SyncResult retry()   { return new SyncResult(true); }
    }
}
