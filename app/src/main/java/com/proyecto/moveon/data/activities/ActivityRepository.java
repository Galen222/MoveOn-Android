package com.proyecto.moveon.data.activities;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.proyecto.moveon.core.api.ApiError;
import com.proyecto.moveon.core.api.ApiErrorType;
import com.proyecto.moveon.core.api.ApiResult;
import com.proyecto.moveon.data.activities.dto.ActividadResponseDto;
import com.proyecto.moveon.data.activities.dto.BorrarActividadResponseDto;
import com.proyecto.moveon.data.activities.dto.GuardarActividadRequestDto;
import com.proyecto.moveon.data.activities.dto.GuardarActividadResponseDto;
import com.proyecto.moveon.data.activities.local.ActividadLocalDataSource;
import com.proyecto.moveon.data.activities.remote.ActividadRemoteDataSource;
import com.proyecto.moveon.data.activities.sync.ActividadCreatePayload;
import com.proyecto.moveon.data.local.db.AppDatabase;
import com.proyecto.moveon.data.local.entity.ActividadEntity;
import com.proyecto.moveon.data.profile.dto.ProfileInfoDto;
import com.proyecto.moveon.data.remote.AuthenticatedApiClient;
import com.proyecto.moveon.data.session.SecureSessionManager;
import com.proyecto.moveon.domain.activity.ActividadItem;
import com.proyecto.moveon.utils.StringUtils;
import com.proyecto.moveon.workers.SyncActividadesWorker;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Repositorio offline-first de actividades.
 * - guardarActividad(...) escribe primero en Room y luego encola sincronización.
 * - obtenerPerfil(...) mantiene el comportamiento actual por red para leer el peso.
 * - observeActividades()/refreshFromServer() permiten alimentar Stats desde local.
 * - borrarActividad(...) elimina en remoto y luego en local si el servidor confirma.
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
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    public ActivityRepository(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        this.sessionManager = new SecureSessionManager(appContext);
        this.apiClient = new AuthenticatedApiClient(appContext);
        AppDatabase db = AppDatabase.getInstance(appContext);
        this.local = new ActividadLocalDataSource(db);
        this.remote = new ActividadRemoteDataSource(appContext);
    }

    /**
     * Devuelve la accountKey del usuario autenticado.
     */
    @Nullable
    public String getCurrentAccountKey() {
        return sessionManager.getAccountKey();
    }

    // ── Guardar ───────────────────────────────────────────────────────────────

    /**
     * Mantiene la misma firma que usa TrackingViewModel,
     * Guarda primero en local y responde éxito inmediato si la validación es correcta.
     */
    public void guardarActividad(
            @NonNull GuardarActividadRequestDto request,
            @NonNull Callback<GuardarActividadResponseDto> callback) {

        String accountKey = sessionManager.getAccountKey();
        if (accountKey == null) {
            callback.onResult(ApiResult.failure(ApiError.local("No hay sesión activa")));
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
            entity.localId = UUID.randomUUID().toString();
            entity.accountKey = accountKey;
            entity.remoteId = null;
            entity.tipo = request.tipo;
            entity.distancia = request.distancia;
            entity.duracion = request.duracion;
            entity.caloriasQuemadas = request.caloriasQuemadas;
            entity.rutaPolilinea = request.rutaPolilinea;
            entity.rutaMapaUrl = null;
            entity.fechaRuta = request.fechaRuta;
            entity.syncState = "PENDING_CREATE";
            entity.lastError = null;
            entity.createdAtMs = now;
            entity.updatedAtMs = now;

            local.save(entity);
            enqueueSync();

            GuardarActividadResponseDto dto = new GuardarActividadResponseDto();
            dto.id = 0;
            dto.tipo = entity.tipo;
            dto.distancia = entity.distancia;
            dto.duracion = entity.duracion;
            dto.caloriasQuemadas = entity.caloriasQuemadas;
            dto.rutaPolilinea = entity.rutaPolilinea;
            dto.rutaMapaUrl = entity.rutaMapaUrl;
            dto.fechaRuta = entity.fechaRuta;
            dto.nuevoTotalPuntos = 0;

            callback.onResult(ApiResult.success(dto));
        });
    }

    // ── Perfil ────────────────────────────────────────────────────────────────

    /**
     * Mantiene el contrato actual para leer el peso desde TrackingViewModel.
     * Aquí se deja por red directa para no bloquear el hilo principal con consultas Room síncronas.
     */
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
        return Transformations.map(local.observeVisible(accountKey), list -> {
            List<ActividadItem> items = new ArrayList<>();
            if (list == null) return items;
            for (ActividadEntity entity : list) {
                items.add(mapEntityToDomain(entity));
            }
            return items;
        });
    }

    public void refreshFromServer(@NonNull String accountKey, @Nullable SyncCallback callback) {
        remote.fetchAllActividades(result -> {
            if (!result.isSuccess() || result.data == null) {
                if (callback != null) {
                    callback.onComplete(result.error != null
                            ? result.error
                            : ApiError.local("Error cargando actividades"));
                }
                return;
            }

            io.execute(() -> {
                mergeRemoteSnapshot(accountKey, result.data);
                if (callback != null) callback.onComplete(null);
            });
        });
    }

    // ── Borrar ────────────────────────────────────────────────────────────────

    /**
     * Elimina una actividad en el servidor y, si confirma, la borra en local.
     * Solo permite borrar actividades ya sincronizadas (remoteId != null, syncState == SYNCED).
     * Las actividades PENDING_CREATE no se pueden borrar desde Stats — deben sincronizarse primero.
     *
     * @param localId  identificador local de la actividad en Room
     * @param callback resultado con {@link BorrarActividadResponseDto} o error
     */
    public void borrarActividad(
            @NonNull String localId,
            @NonNull Callback<BorrarActividadResponseDto> callback) {

        io.execute(() -> {
            ActividadEntity entity = local.getByLocalId(localId);

            if (entity == null) {
                callback.onResult(ApiResult.failure(
                        ApiError.local("Actividad no encontrada")));
                return;
            }

            if (entity.remoteId == null || !"SYNCED".equals(entity.syncState)) {
                callback.onResult(ApiResult.failure(
                        ApiError.typed(ApiErrorType.VALIDATION,
                                "Actividad pendiente de sincronizar")));
                return;
            }

            int remoteId = entity.remoteId;

            remote.deleteActividad(remoteId, result -> {
                if (!result.isSuccess()) {
                    callback.onResult(ApiResult.failure(
                            result.error != null
                                    ? result.error
                                    : ApiError.local("Error al eliminar la actividad")));
                    return;
                }

                // El servidor confirmó el borrado — eliminamos en local
                io.execute(() -> {
                    local.deleteByLocalId(localId);

                    BorrarActividadResponseDto dto = new BorrarActividadResponseDto();
                    dto.estatus = "success";
                    dto.mensaje = result.data;
                    dto.nuevoTotalPuntos = 0; // El endpoint delete devuelve mensaje, no puntos directamente
                    callback.onResult(ApiResult.success(dto));
                });
            });
        });
    }

    // ── Sincronización ────────────────────────────────────────────────────────

    @NonNull
    public SyncResult syncPendingNow(@NonNull String accountKey) {
        List<ActividadEntity> creates = local.getPendingCreates(accountKey);
        for (ActividadEntity entity : creates) {
            ApiResult<ActividadResponseDto> result =
                    remote.createActividadBlocking(ActividadCreatePayload.fromEntity(entity).toJson());

            if (result.isSuccess() && result.data != null) {
                ActividadResponseDto dto = result.data;
                entity.remoteId = dto.id;
                entity.tipo = dto.tipo;
                entity.distancia = dto.distancia;
                entity.duracion = dto.duracion;
                entity.caloriasQuemadas = dto.caloriasQuemadas;
                entity.rutaPolilinea = dto.rutaPolilinea;
                entity.rutaMapaUrl = dto.rutaMapaUrl;
                entity.fechaRuta = dto.fechaRuta;
                entity.syncState = "SYNCED";
                entity.lastError = null;
                entity.updatedAtMs = System.currentTimeMillis();
                local.save(entity);
                continue;
            }

            ApiError error = result.error != null
                    ? result.error
                    : ApiError.local("Error sincronizando actividad");

            if (isRetryable(error)) {
                entity.lastError = error.getMessage();
                entity.updatedAtMs = System.currentTimeMillis();
                local.save(entity);
                return SyncResult.retry();
            }

            entity.syncState = "FAILED_CREATE";
            entity.lastError = error.getMessage();
            entity.updatedAtMs = System.currentTimeMillis();
            local.save(entity);
        }

        ApiResult<List<ActividadResponseDto>> refreshResult = remote.fetchAllActividadesBlocking();
        if (refreshResult.isSuccess() && refreshResult.data != null) {
            mergeRemoteSnapshot(accountKey, refreshResult.data);
            return SyncResult.success();
        }

        if (refreshResult.error != null && isRetryable(refreshResult.error)) {
            return SyncResult.retry();
        }

        return SyncResult.success();
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
                .enqueueUniqueWork(UNIQUE_SYNC_WORK_NAME, ExistingWorkPolicy.KEEP, request);
    }

    public void cancelAll() {
        remote.cancelAll();
        apiClient.cancelAll();
        io.shutdownNow();
    }

    // ── Privados ──────────────────────────────────────────────────────────────

    private void mergeRemoteSnapshot(@NonNull String accountKey,
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
            entity.syncState = "SYNCED";
            entity.lastError = null;
            entity.updatedAtMs = System.currentTimeMillis();
            local.save(entity);
        }

        for (ActividadEntity entity : current) {
            if (entity.remoteId == null) continue;
            if (!"SYNCED".equals(entity.syncState)) continue;
            if (!remoteIds.contains(entity.remoteId)) {
                local.deleteByLocalId(entity.localId);
            }
        }
    }

    private void mapDtoIntoEntity(@NonNull ActividadEntity entity, @NonNull ActividadResponseDto dto) {
        entity.remoteId = dto.id;
        entity.tipo = dto.tipo;
        entity.distancia = dto.distancia;
        entity.duracion = dto.duracion;
        entity.caloriasQuemadas = dto.caloriasQuemadas;
        entity.rutaPolilinea = dto.rutaPolilinea;
        entity.rutaMapaUrl = dto.rutaMapaUrl;
        entity.fechaRuta = dto.fechaRuta;
    }

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
            return ApiError.typed(ApiErrorType.VALIDATION, "El tipo de actividad no es válido");
        }
        if (request.distancia <= 0 || request.distancia > 300000) {
            return ApiError.typed(ApiErrorType.VALIDATION, "La distancia debe estar entre 1 y 300000 metros");
        }
        if (request.duracion <= 0 || request.duracion > 86400) {
            return ApiError.typed(ApiErrorType.VALIDATION, "La duración debe estar entre 1 y 86400 segundos");
        }
        if (request.caloriasQuemadas <= 0 || request.caloriasQuemadas > 10000) {
            return ApiError.typed(ApiErrorType.VALIDATION, "Las calorías deben estar entre 1 y 10000");
        }
        if (StringUtils.hasText(request.rutaPolilinea) && request.rutaPolilinea.trim().length() < 2) {
            return ApiError.typed(ApiErrorType.VALIDATION, "La ruta polilínea no es válida");
        }
        try {
            OffsetDateTime fecha = OffsetDateTime.parse(request.fechaRuta);
            if (fecha.isAfter(OffsetDateTime.now().plusMinutes(1))) {
                return ApiError.typed(ApiErrorType.VALIDATION, "La fecha de la actividad no puede estar en el futuro");
            }
        } catch (Exception e) {
            return ApiError.typed(ApiErrorType.VALIDATION, "El formato de fecha no es válido");
        }
        return null;
    }

    private boolean isRetryable(@NonNull ApiError error) {
        ApiErrorType type = error.getType();
        return type == ApiErrorType.NETWORK
                || type == ApiErrorType.TIMEOUT
                || type == ApiErrorType.RATE_LIMIT
                || type == ApiErrorType.SERVER
                || type == ApiErrorType.CANCELED;
    }

    // ── Inner classes ─────────────────────────────────────────────────────────

    public static final class SyncResult {
        public final boolean retry;

        private SyncResult(boolean retry) {
            this.retry = retry;
        }

        public static SyncResult success() {
            return new SyncResult(false);
        }

        public static SyncResult retry() {
            return new SyncResult(true);
        }
    }
}