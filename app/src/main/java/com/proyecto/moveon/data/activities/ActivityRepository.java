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

import com.proyecto.moveon.BuildConfig;
import com.proyecto.moveon.R;
import com.proyecto.moveon.core.i18n.AppLanguageManager;
import com.proyecto.moveon.core.api.ApiError;
import com.proyecto.moveon.core.api.ApiErrorType;
import com.proyecto.moveon.core.api.ApiResult;
import com.proyecto.moveon.core.concurrency.MoveOnExecutors;
import com.proyecto.moveon.data.activities.dto.ActivityDiagnosticsRequestDto;
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
import com.proyecto.moveon.utils.StringUtils;
import com.proyecto.moveon.workers.SyncActividadesWorker;

import java.time.OffsetDateTime;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Coordinador delgado del dominio de actividades.
 *
 * <p>Encapsula lectura local, sincronización y acceso remoto para que la UI trate
 * actividades guardadas, pendientes o sincronizadas mediante una única fachada.</p>
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
    private static final String ENDPOINT_ACTIVITY_DIAGNOSTICS = "actividad/diagnostico";

    private final Context appContext;
    private final SecureSessionManager sessionManager;
    private final AuthenticatedApiClient apiClient;
    private final ActividadLocalDataSource local;
    private final ActividadRemoteDataSource remote;
    private final ActivitySyncManager syncManager;
    private final ExecutorService io = MoveOnExecutors.io();

    /**
     * Construye el repositorio de actividades resolviendo todas sus dependencias sobre el contexto de aplicación.
     *
     * @param context contexto desde el que inicializar base de datos, red y work manager.
     */
    public ActivityRepository(@NonNull Context context) {
        appContext = context.getApplicationContext();
        sessionManager = SecureSessionManager.getInstance(appContext);
        apiClient = new AuthenticatedApiClient(appContext);
        AppDatabase db = AppDatabase.getInstance(appContext);
        local = new ActividadLocalDataSource(db);
        remote = new ActividadRemoteDataSource(appContext);
        syncManager = new ActivitySyncManager(appContext, local, remote);
    }

    /**
     * Guarda una actividad primero en local y deja su sincronización remota en segundo plano.
     *
     * @param request payload con los datos recogidos por el tracking.
     * @param callback receptor del resultado inmediato del guardado local.
     */
    public void guardarActividad(
            @NonNull GuardarActividadRequestDto request,
            @NonNull Callback<GuardarActividadResponseDto> callback) {

        String accountKey = sessionManager.getAccountKey();
        if (accountKey == null) {
            callback.onResult(ApiResult.failure(
                    ApiError.local(AppLanguageManager.getString(appContext, R.string.error_no_sesion_activa))
            ));
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
            entity.duracionTotal = request.duracionTotal;
            entity.duracionMovimiento = request.duracionMovimiento;
            entity.duracionParado = request.duracionParado;
            entity.duracionPausaManual = request.duracionPausaManual;
            entity.caloriasQuemadas = request.caloriasQuemadas;
            entity.pasos = request.pasos;
            entity.ritmoMedioMovimiento = request.ritmoMedioMovimiento;
            entity.ritmoMedioTotal = request.ritmoMedioTotal;
            entity.ritmoMaximo = request.ritmoMaximo;
            entity.velocidadMediaKmhX100 = request.velocidadMediaKmhX100;
            entity.velocidadMaxKmhX100 = request.velocidadMaxKmhX100;
            entity.autoPausas = request.autoPausas;
            entity.pausasManuales = request.pausasManuales;
            entity.alertasVelocidad = request.alertasVelocidad;
            entity.rutaPolilinea = request.rutaPolilinea;
            entity.rutaMapaUrl = null;
            entity.fechaRuta = request.fechaRuta;
            entity.syncState = ActivitySyncState.PENDING_CREATE;
            entity.lastError = null;
            entity.createdAtMs = now;
            entity.updatedAtMs = now;

            local.save(entity);
            enqueueSync();

            GuardarActividadResponseDto dto = new GuardarActividadResponseDto();
            dto.id = 0;
            dto.tipo = entity.tipo;
            dto.distancia = entity.distancia;
            dto.duracionTotal = entity.duracionTotal;
            dto.duracionMovimiento = entity.duracionMovimiento;
            dto.duracionParado = entity.duracionParado;
            dto.duracionPausaManual = entity.duracionPausaManual;
            dto.caloriasQuemadas = entity.caloriasQuemadas;
            dto.pasos = entity.pasos;
            dto.ritmoMedioMovimiento = entity.ritmoMedioMovimiento;
            dto.ritmoMedioTotal = entity.ritmoMedioTotal;
            dto.ritmoMaximo = entity.ritmoMaximo;
            dto.velocidadMediaKmhX100 = entity.velocidadMediaKmhX100;
            dto.velocidadMaxKmhX100 = entity.velocidadMaxKmhX100;
            dto.autoPausas = entity.autoPausas;
            dto.pausasManuales = entity.pausasManuales;
            dto.alertasVelocidad = entity.alertasVelocidad;
            dto.rutaPolilinea = entity.rutaPolilinea;
            dto.rutaMapaUrl = entity.rutaMapaUrl;
            dto.fechaRuta = entity.fechaRuta;
            dto.nuevoTotalPuntos = 0;

            callback.onResult(ApiResult.success(dto));
        });
    }


    /**
     * Envía al backend un bloque automático de diagnóstico de tracking.
     *
     * <p>Es un flujo best-effort para builds internas: nunca debe romper el guardado
     * normal de la actividad ni mostrar errores al usuario final.</p>
     *
     * @param request bloque técnico generado por el módulo de tracking para análisis interno.
     */
    public void guardarActividadDiagnostico(@NonNull ActivityDiagnosticsRequestDto request) {
        if (!BuildConfig.ACTIVITY_DIAGNOSTICS_ENABLED) {
            return;
        }

        apiClient.postJson(
                ENDPOINT_ACTIVITY_DIAGNOSTICS,
                new com.google.gson.Gson().toJsonTree(request),
                json -> new JsonObject(),
                result -> {
                    // Flujo deliberadamente silencioso. El diagnóstico no debe alterar la UX.
                }
        );
    }

    /**
     * Recupera el bloque remoto de información básica del perfil asociado a la sesión.
     *
     * @param callback receptor del resultado parseado a {@link ProfileInfoDto}.
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

    /**
     * Observa las actividades visibles de una cuenta convirtiendo entidades locales a dominio.
     *
     * @param accountKey clave lógica de la cuenta.
     * @return flujo observable de {@link ActividadItem} listo para la UI.
     */
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

    /**
     * Fuerza una descarga remota de actividades y fusiona el snapshot en la base local.
     *
     * @param accountKey clave lógica de la cuenta.
     * @param callback callback opcional notificado al terminar con error o éxito.
     */
    public void refreshFromServer(@NonNull String accountKey, @Nullable SyncCallback callback) {
        remote.fetchAllActividades(result -> {
            if (!result.isSuccess() || result.data == null) {
                if (callback != null) {
                    callback.onComplete(result.error != null
                            ? result.error
                            : ApiError.local(AppLanguageManager.getString(appContext, R.string.error_cargando_actividades)));
                }
                return;
            }

            io.execute(() -> {
                syncManager.mergeRemoteSnapshot(accountKey, result.data);
                if (callback != null) callback.onComplete(null);
            });
        });
    }

    /**
     * Elimina una actividad ya sincronizada tanto en backend como en almacenamiento local.
     *
     * @param localId identificador local de la actividad a borrar.
     * @param callback receptor del resultado final de borrado.
     */
    public void borrarActividad(
            @NonNull String localId,
            @NonNull Callback<BorrarActividadResponseDto> callback) {

        io.execute(() -> {
            ActividadEntity entity = local.getByLocalId(localId);

            if (entity == null) {
                callback.onResult(ApiResult.failure(
                        ApiError.local(AppLanguageManager.getString(appContext, R.string.error_actividad_no_encontrada))));
                return;
            }

            if (entity.remoteId == null || !ActivitySyncState.SYNCED.equals(entity.syncState)) {
                callback.onResult(ApiResult.failure(
                        ApiError.typed(ApiErrorType.VALIDATION,
                                AppLanguageManager.getString(appContext, R.string.error_actividad_pendiente_sync))));
                return;
            }

            int remoteId = entity.remoteId;

            remote.deleteActividad(remoteId, result -> {
                if (!result.isSuccess()) {
                    callback.onResult(ApiResult.failure(
                            result.error != null
                                    ? result.error
                                    : ApiError.local(AppLanguageManager.getString(appContext, R.string.error_eliminando_actividad))));
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

    /**
     * Ejecuta inmediatamente la sincronización pendiente de actividades.
     *
     * @param accountKey clave lógica de la cuenta.
     * @return resumen del ciclo de sincronización offline.
     */
    @NonNull
    public SyncResult syncPendingNow(@NonNull String accountKey) {
        return syncManager.syncPendingNow(accountKey);
    }

    /**
     * Programa un {@link SyncActividadesWorker} único para vaciar la cola local cuando haya red.
     */
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

    /**
     * Cancela las llamadas remotas pendientes del repositorio.
     */
    public void cancelAll() {
        remote.cancelAll();
        apiClient.cancelAll();
    }

    /**
     * Convierte una entidad persistida al modelo de dominio usado por la UI.
     *
     * @param entity entidad almacenada en Room.
     * @return instancia de {@link ActividadItem} con el mismo estado de sincronización.
     */
    @NonNull
    private ActividadItem mapEntityToDomain(@NonNull ActividadEntity entity) {
        return new ActividadItem(
                entity.localId,
                entity.remoteId,
                entity.tipo,
                entity.distancia,
                entity.duracionTotal,
                entity.duracionMovimiento,
                entity.duracionParado,
                entity.duracionPausaManual,
                entity.caloriasQuemadas,
                entity.pasos,
                entity.ritmoMedioMovimiento,
                entity.ritmoMedioTotal,
                entity.ritmoMaximo,
                entity.velocidadMediaKmhX100,
                entity.velocidadMaxKmhX100,
                entity.autoPausas,
                entity.pausasManuales,
                entity.alertasVelocidad,
                entity.rutaPolilinea,
                entity.rutaMapaUrl,
                entity.fechaRuta,
                entity.syncState,
                entity.lastError
        );
    }

    /**
     * Valida reglas de negocio y coherencia temporal del payload antes de persistirlo localmente.
     *
     * @param request payload de guardado recibido desde tracking.
     * @return error de validación o {@code null} cuando el request es consistente.
     */
    @Nullable
    private ApiError validateRequest(@NonNull GuardarActividadRequestDto request) {
        if (!VALID_TIPOS.contains(request.tipo)) {
            return ApiError.typed(ApiErrorType.VALIDATION,
                    AppLanguageManager.getString(appContext, R.string.error_tipo_actividad_invalido));
        }
        if (request.distancia <= 0 || request.distancia > 300000) {
            return ApiError.typed(ApiErrorType.VALIDATION,
                    AppLanguageManager.getString(appContext, R.string.error_distancia_invalida));
        }
        if (request.duracionTotal <= 0 || request.duracionTotal > 86400) {
            return ApiError.typed(ApiErrorType.VALIDATION,
                    AppLanguageManager.getString(appContext, R.string.error_duracion_invalida));
        }
        if (request.duracionMovimiento <= 0 || request.duracionMovimiento > request.duracionTotal) {
            return ApiError.typed(ApiErrorType.VALIDATION,
                    AppLanguageManager.getString(appContext, R.string.error_tracking_moving_duration_invalid));
        }
        if (request.duracionParado < 0 || request.duracionParado > request.duracionTotal) {
            return ApiError.typed(ApiErrorType.VALIDATION,
                    AppLanguageManager.getString(appContext, R.string.error_tracking_stopped_duration_invalid));
        }
        if ((request.duracionMovimiento + request.duracionParado) != request.duracionTotal) {
            return ApiError.typed(ApiErrorType.VALIDATION,
                    AppLanguageManager.getString(appContext, R.string.error_tracking_total_duration_mismatch));
        }
        if (request.duracionPausaManual < 0 || request.duracionPausaManual > 86400) {
            return ApiError.typed(ApiErrorType.VALIDATION,
                    AppLanguageManager.getString(appContext, R.string.error_tracking_manual_pause_invalid));
        }
        if (request.caloriasQuemadas <= 0 || request.caloriasQuemadas > 10000) {
            return ApiError.typed(ApiErrorType.VALIDATION,
                    AppLanguageManager.getString(appContext, R.string.error_calorias_invalidas));
        }
        if (request.pasos != null && (request.pasos < 0 || request.pasos > 500000)) {
            return ApiError.typed(ApiErrorType.VALIDATION,
                    AppLanguageManager.getString(appContext, R.string.error_pasos_invalidos));
        }
        if (request.ritmoMedioMovimiento <= 0 || request.ritmoMedioMovimiento > 3600) {
            return ApiError.typed(ApiErrorType.VALIDATION,
                    AppLanguageManager.getString(appContext, R.string.error_tracking_average_pace_invalid));
        }
        if (request.ritmoMedioTotal <= 0 || request.ritmoMedioTotal > 3600) {
            return ApiError.typed(ApiErrorType.VALIDATION,
                    AppLanguageManager.getString(appContext, R.string.error_tracking_average_pace_invalid));
        }
        if (request.ritmoMaximo < 0 || request.ritmoMaximo > 3600) {
            return ApiError.typed(ApiErrorType.VALIDATION,
                    AppLanguageManager.getString(appContext, R.string.error_tracking_max_pace_invalid));
        }
        if (request.velocidadMediaKmhX100 <= 0 || request.velocidadMediaKmhX100 > 5000) {
            return ApiError.typed(ApiErrorType.VALIDATION,
                    AppLanguageManager.getString(appContext, R.string.error_tracking_average_speed_invalid));
        }
        if (request.velocidadMaxKmhX100 <= 0 || request.velocidadMaxKmhX100 > 10000
                || request.velocidadMaxKmhX100 < request.velocidadMediaKmhX100) {
            return ApiError.typed(ApiErrorType.VALIDATION,
                    AppLanguageManager.getString(appContext, R.string.error_tracking_max_speed_invalid));
        }
        if (request.autoPausas < 0 || request.autoPausas > 500
                || request.pausasManuales < 0 || request.pausasManuales > 500
                || request.alertasVelocidad < 0 || request.alertasVelocidad > 500) {
            return ApiError.typed(ApiErrorType.VALIDATION,
                    AppLanguageManager.getString(appContext, R.string.error_tracking_counter_invalid));
        }
        if (StringUtils.hasText(request.rutaPolilinea) && request.rutaPolilinea.trim().length() < 2) {
            return ApiError.typed(ApiErrorType.VALIDATION,
                    AppLanguageManager.getString(appContext, R.string.error_polilinea_invalida));
        }
        try {
            OffsetDateTime fecha = OffsetDateTime.parse(request.fechaRuta);
            if (fecha.isAfter(OffsetDateTime.now().plusMinutes(1))) {
                return ApiError.typed(ApiErrorType.VALIDATION,
                        AppLanguageManager.getString(appContext, R.string.error_fecha_futura));
            }
        } catch (Exception e) {
            return ApiError.typed(ApiErrorType.VALIDATION,
                    AppLanguageManager.getString(appContext, R.string.error_formato_fecha_invalido));
        }
        return null;
    }

    public static final class SyncResult {
        public final boolean retry;
        public final boolean completedPendingWork;

        private SyncResult(boolean retry, boolean completedPendingWork) {
            this.retry = retry;
            this.completedPendingWork = completedPendingWork;
        }

        /**
         * Devuelve un resultado correcto cuando el ciclo no encontró trabajo pendiente.
         *
         * @return resultado sin reintento y sin trabajo completado.
         */
        public static SyncResult successNoop() {
            return new SyncResult(false, false);
        }

        /**
         * Devuelve un resultado correcto cuando la cola pendiente quedó completada.
         *
         * @return resultado sin reintento y con trabajo completado.
         */
        public static SyncResult successCompleted() {
            return new SyncResult(false, true);
        }

        /**
         * Devuelve un resultado que solicita reintentar la sincronización más tarde.
         *
         * @return resultado marcado para reintento.
         */
        public static SyncResult retry() {
            return new SyncResult(true, false);
        }
    }
}
