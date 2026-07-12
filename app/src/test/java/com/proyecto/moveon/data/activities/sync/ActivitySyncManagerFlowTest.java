package com.proyecto.moveon.data.activities.sync;

import static org.junit.Assert.*;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.test.core.app.ApplicationProvider;

import com.google.gson.JsonObject;
import com.proyecto.moveon.core.api.ApiError;
import com.proyecto.moveon.core.api.ApiErrorType;
import com.proyecto.moveon.core.api.ApiResult;
import com.proyecto.moveon.data.activities.ActivityRepository.SyncResult;
import com.proyecto.moveon.data.activities.ActivitySyncState;
import com.proyecto.moveon.data.activities.dto.ActividadResponseDto;
import com.proyecto.moveon.data.activities.local.ActividadLocalDataSource;
import com.proyecto.moveon.data.activities.remote.ActividadRemoteDataSource;
import com.proyecto.moveon.data.local.dao.ActividadDao;
import com.proyecto.moveon.data.local.entity.ActividadEntity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Tests de flujo completo de {@link ActivitySyncManager} con DAO y remoto fake.
 * <p>
 * Cubre la clase real, no una copia de sus condiciones, y evita Room/red real para que
 * los escenarios sean rápidos y deterministas en unit tests JVM.
 */
@RunWith(RobolectricTestRunner.class)
public class ActivitySyncManagerFlowTest {

    @Test
    public void syncPendingNow_successfulCreate_mapsDtoSavesSyncedAndReturnsCompleted() throws Exception {
        FakeActividadDao dao = new FakeActividadDao();
        ActividadEntity pending = actividad("local-1", null, ActivitySyncState.PENDING_CREATE);
        pending.tipo = "Correr";
        pending.fechaRuta = "2026-04-25T10:00:00Z";
        dao.pendingCreates = Collections.singletonList(pending);

        FakeRemoteDataSource remote = new FakeRemoteDataSource(appContext());
        remote.createResults.add(ApiResult.success(dto(
                77,
                "Caminar",
                12_345,
                "server_poly",
                "https://example.test/map.png",
                "2026-04-25T11:00:00Z"
        )));
        remote.fetchResult = ApiResult.success(Collections.emptyList());

        SyncResult result = manager(dao, remote).syncPendingNow("acc");

        assertFalse(result.retry);
        assertTrue(result.completedPendingWork);
        assertEquals("local-1", remote.lastCreateBody.get("client_local_id").getAsString());

        ActividadEntity saved = dao.saved.getFirst();
        assertEquals(Integer.valueOf(77), saved.remoteId);
        assertEquals("Caminar", saved.tipo);
        assertEquals(12_345, saved.distancia);
        assertEquals(222, saved.duracionTotal);
        assertEquals(200, saved.duracionMovimiento);
        assertEquals(12, saved.duracionParado);
        assertEquals(10, saved.duracionPausaManual);
        assertEquals(321, saved.caloriasQuemadas);
        assertEquals(Integer.valueOf(4_321), saved.pasos);
        assertEquals(345, saved.ritmoMedioMovimiento);
        assertEquals(360, saved.ritmoMedioTotal);
        assertEquals(330, saved.ritmoMaximo);
        assertEquals(1_050, saved.velocidadMediaKmhX100);
        assertEquals(1_450, saved.velocidadMaxKmhX100);
        assertEquals(2, saved.autoPausas);
        assertEquals(1, saved.pausasManuales);
        assertEquals(3, saved.alertasVelocidad);
        assertEquals("server_poly", saved.rutaPolilinea);
        assertEquals("https://example.test/map.png", saved.rutaMapaUrl);
        assertEquals("2026-04-25T11:00:00Z", saved.fechaRuta);
        assertEquals(ActivitySyncState.SYNCED, saved.syncState);
        assertNull(saved.lastError);
        assertTrue(saved.updatedAtMs > 0);
    }

    @Test
    public void syncPendingNow_retryableCreateError_savesLastErrorAndRequestsRetry() throws Exception {
        FakeActividadDao dao = new FakeActividadDao();
        ActividadEntity pending = actividad("local-2", null, ActivitySyncState.PENDING_CREATE);
        dao.pendingCreates = Collections.singletonList(pending);

        FakeRemoteDataSource remote = new FakeRemoteDataSource(appContext());
        remote.createResults.add(ApiResult.failure(ApiError.typed(ApiErrorType.NETWORK, "sin conexión")));

        SyncResult result = manager(dao, remote).syncPendingNow("acc");

        assertTrue(result.retry);
        assertFalse(result.completedPendingWork);
        assertEquals(1, dao.saved.size());
        assertSame(pending, dao.saved.getFirst());
        assertEquals(ActivitySyncState.PENDING_CREATE, pending.syncState);
        assertEquals("sin conexión", pending.lastError);
        assertTrue(pending.updatedAtMs > 0);
        assertEquals(0, remote.fetchCalls);
    }

    @Test
    public void syncPendingNow_permanentCreateError_marksFailedAndStillRefreshesSnapshot() throws Exception {
        FakeActividadDao dao = new FakeActividadDao();
        ActividadEntity pending = actividad("local-3", null, ActivitySyncState.PENDING_CREATE);
        dao.pendingCreates = Collections.singletonList(pending);
        dao.allNow = Collections.emptyList();

        FakeRemoteDataSource remote = new FakeRemoteDataSource(appContext());
        remote.createResults.add(ApiResult.failure(ApiError.typed(ApiErrorType.VALIDATION, 422, "payload inválido")));
        remote.fetchResult = ApiResult.success(Collections.emptyList());

        SyncResult result = manager(dao, remote).syncPendingNow("acc");

        assertFalse(result.retry);
        assertTrue(result.completedPendingWork);
        assertEquals(ActivitySyncState.FAILED_CREATE, pending.syncState);
        assertEquals("payload inválido", pending.lastError);
        assertEquals(1, remote.fetchCalls);
    }

    @Test
    public void syncPendingNow_noPendingAndRetryableRefreshError_returnsRetry() throws Exception {
        FakeActividadDao dao = new FakeActividadDao();
        dao.pendingCreates = Collections.emptyList();

        FakeRemoteDataSource remote = new FakeRemoteDataSource(appContext());
        remote.fetchResult = ApiResult.failure(ApiError.typed(ApiErrorType.SERVER, 503, "servicio no disponible"));

        SyncResult result = manager(dao, remote).syncPendingNow("acc");

        assertTrue(result.retry);
        assertFalse(result.completedPendingWork);
        assertEquals(1, remote.fetchCalls);
        assertTrue(dao.saved.isEmpty());
    }

    @Test
    public void syncPendingNow_noPendingAndPermanentRefreshError_isNoopSuccess() throws Exception {
        FakeActividadDao dao = new FakeActividadDao();
        dao.pendingCreates = null;

        FakeRemoteDataSource remote = new FakeRemoteDataSource(appContext());
        remote.fetchResult = ApiResult.failure(ApiError.typed(ApiErrorType.VALIDATION, 400, "consulta inválida"));

        SyncResult result = manager(dao, remote).syncPendingNow("acc");

        assertFalse(result.retry);
        assertFalse(result.completedPendingWork);
        assertEquals(1, remote.fetchCalls);
    }

    @Test
    public void mergeRemoteSnapshot_insertsUpdatesPreservesNullableFallbacksAndDeletesMissingSyncedRows()
            throws Exception {
        FakeActividadDao dao = new FakeActividadDao();

        ActividadEntity staleSynced = actividad("local-stale", 10, ActivitySyncState.SYNCED);
        ActividadEntity localPending = actividad("local-pending", 11, ActivitySyncState.PENDING_CREATE);
        ActividadEntity existing = actividad("local-existing", 20, ActivitySyncState.SYNCED);
        existing.tipo = "Correr";
        existing.fechaRuta = "2026-04-20T10:00:00Z";
        existing.lastError = "error antiguo";

        dao.allNow = Arrays.asList(staleSynced, localPending, existing);
        dao.byRemoteId.put(20, existing);

        ActividadResponseDto updateExisting = dto(
                20,
                null,
                5_000,
                "poly-updated",
                null,
                null
        );
        ActividadResponseDto insertNew = dto(
                30,
                "Caminar",
                1_500,
                null,
                "https://example.test/new.png",
                "2026-04-21T08:30:00Z"
        );

        manager(dao, new FakeRemoteDataSource(appContext()))
                .mergeRemoteSnapshot("acc", Arrays.asList(updateExisting, insertNew));

        assertEquals(2, dao.saved.size());

        ActividadEntity updated = dao.saved.getFirst();
        assertSame(existing, updated);
        assertEquals(Integer.valueOf(20), updated.remoteId);
        assertEquals("Correr", updated.tipo);
        assertEquals("2026-04-20T10:00:00Z", updated.fechaRuta);
        assertEquals(5_000, updated.distancia);
        assertEquals("poly-updated", updated.rutaPolilinea);
        assertNull(updated.rutaMapaUrl);
        assertEquals(ActivitySyncState.SYNCED, updated.syncState);
        assertNull(updated.lastError);

        ActividadEntity inserted = dao.saved.get(1);
        assertEquals("remote_30", inserted.localId);
        assertEquals("acc", inserted.accountKey);
        assertEquals(Integer.valueOf(30), inserted.remoteId);
        assertEquals("Caminar", inserted.tipo);
        assertEquals("https://example.test/new.png", inserted.rutaMapaUrl);
        assertEquals(ActivitySyncState.SYNCED, inserted.syncState);
        assertTrue(inserted.createdAtMs > 0);
        assertTrue(inserted.updatedAtMs > 0);

        assertEquals(Collections.singletonList("local-stale"), dao.deletedLocalIds);
        assertFalse(dao.deletedLocalIds.contains("local-pending"));
    }

    @Test
    public void isRetryable_matchesExpectedTransientErrorTypes() throws Exception {
        ActivitySyncManager manager = manager(new FakeActividadDao(), new FakeRemoteDataSource(appContext()));

        assertTrue(manager.isRetryable(ApiError.typed(ApiErrorType.NETWORK, "network")));
        assertTrue(manager.isRetryable(ApiError.typed(ApiErrorType.TIMEOUT, "timeout")));
        assertTrue(manager.isRetryable(ApiError.typed(ApiErrorType.RATE_LIMIT, "rate")));
        assertTrue(manager.isRetryable(ApiError.typed(ApiErrorType.SERVER, "server")));
        assertTrue(manager.isRetryable(ApiError.typed(ApiErrorType.CANCELED, "canceled")));

        assertFalse(manager.isRetryable(ApiError.typed(ApiErrorType.UNAUTHORIZED, "unauthorized")));
        assertFalse(manager.isRetryable(ApiError.typed(ApiErrorType.FORBIDDEN, "forbidden")));
        assertFalse(manager.isRetryable(ApiError.typed(ApiErrorType.NOT_FOUND, "not found")));
        assertFalse(manager.isRetryable(ApiError.typed(ApiErrorType.CONFLICT, "conflict")));
        assertFalse(manager.isRetryable(ApiError.typed(ApiErrorType.VALIDATION, "validation")));
        assertFalse(manager.isRetryable(ApiError.typed(ApiErrorType.PAYLOAD_TOO_LARGE, "large")));
        assertFalse(manager.isRetryable(ApiError.typed(ApiErrorType.PARSE, "parse")));
        assertFalse(manager.isRetryable(ApiError.local("unknown")));
    }

    private static Context appContext() {
        return ApplicationProvider.getApplicationContext();
    }

    private static ActivitySyncManager manager(FakeActividadDao dao,
                                               FakeRemoteDataSource remote) throws Exception {
        return new ActivitySyncManager(appContext(), actividadDataSourceWith(dao), remote);
    }

    private static ActividadLocalDataSource actividadDataSourceWith(FakeActividadDao dao) throws Exception {
        ActividadLocalDataSource dataSource = allocateLocalDataSource();
        setDao(dataSource, dao);
        return dataSource;
    }

    private static ActividadLocalDataSource allocateLocalDataSource() throws Exception {
        Field field = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
        field.setAccessible(true);
        Object unsafe = java.util.Objects.requireNonNull(field.get(null), "Unsafe no disponible");
        Method method = unsafe.getClass().getMethod("allocateInstance", Class.class);
        return (ActividadLocalDataSource) method.invoke(unsafe, ActividadLocalDataSource.class);
    }

    private static void setDao(ActividadLocalDataSource target, ActividadDao value) throws Exception {
        Field field = ActividadLocalDataSource.class.getDeclaredField("dao");
        field.setAccessible(true);
        field.set(target, value);
    }

    private static ActividadEntity actividad(String localId, Integer remoteId, String syncState) {
        ActividadEntity entity = new ActividadEntity();
        entity.localId = localId;
        entity.accountKey = "acc";
        entity.remoteId = remoteId;
        entity.tipo = "Correr";
        entity.distancia = 100;
        entity.duracionTotal = 60;
        entity.duracionMovimiento = 50;
        entity.duracionParado = 5;
        entity.duracionPausaManual = 5;
        entity.caloriasQuemadas = 20;
        entity.pasos = null;
        entity.ritmoMedioMovimiento = 400;
        entity.ritmoMedioTotal = 420;
        entity.ritmoMaximo = 350;
        entity.velocidadMediaKmhX100 = 900;
        entity.velocidadMaxKmhX100 = 1_200;
        entity.autoPausas = 1;
        entity.pausasManuales = 1;
        entity.alertasVelocidad = 0;
        entity.rutaPolilinea = "poly-local";
        entity.rutaMapaUrl = "map-local";
        entity.fechaRuta = "2026-04-25T10:00:00Z";
        entity.syncState = syncState;
        entity.lastError = null;
        entity.createdAtMs = 1L;
        entity.updatedAtMs = 1L;
        return entity;
    }

    private static ActividadResponseDto dto(int id,
                                            String tipo,
                                            int distancia,
                                            String rutaPolilinea,
                                            String rutaMapaUrl,
                                            String fechaRuta) {
        ActividadResponseDto dto = new ActividadResponseDto();
        dto.id = id;
        dto.tipo = tipo;
        dto.distancia = distancia;
        dto.duracionTotal = 222;
        dto.duracionMovimiento = 200;
        dto.duracionParado = 12;
        dto.duracionPausaManual = 10;
        dto.caloriasQuemadas = 321;
        dto.pasos = 4_321;
        dto.ritmoMedioMovimiento = 345;
        dto.ritmoMedioTotal = 360;
        dto.ritmoMaximo = 330;
        dto.velocidadMediaKmhX100 = 1_050;
        dto.velocidadMaxKmhX100 = 1_450;
        dto.autoPausas = 2;
        dto.pausasManuales = 1;
        dto.alertasVelocidad = 3;
        dto.rutaPolilinea = rutaPolilinea;
        dto.rutaMapaUrl = rutaMapaUrl;
        dto.fechaRuta = fechaRuta;
        dto.nuevoTotalPuntos = 99;
        return dto;
    }

    private static final class FakeRemoteDataSource extends ActividadRemoteDataSource {
        final ArrayDeque<ApiResult<ActividadResponseDto>> createResults = new ArrayDeque<>();
        ApiResult<List<ActividadResponseDto>> fetchResult = ApiResult.success(Collections.emptyList());
        JsonObject lastCreateBody;
        int fetchCalls;

        FakeRemoteDataSource(@NonNull Context context) {
            super(context);
        }

        @NonNull
        @Override
        public ApiResult<ActividadResponseDto> createActividadBlocking(@NonNull JsonObject body) {
            lastCreateBody = body;
            return createResults.remove();
        }

        @NonNull
        @Override
        public ApiResult<List<ActividadResponseDto>> fetchAllActividadesBlocking() {
            fetchCalls++;
            return fetchResult;
        }
    }

    private static final class FakeActividadDao implements ActividadDao {
        final MutableLiveData<List<ActividadEntity>> liveData = new MutableLiveData<>();
        final List<ActividadEntity> saved = new ArrayList<>();
        final List<String> deletedLocalIds = new ArrayList<>();
        final java.util.Map<Integer, ActividadEntity> byRemoteId = new java.util.HashMap<>();
        List<ActividadEntity> allNow = new ArrayList<>();
        List<ActividadEntity> pendingCreates = new ArrayList<>();
        final List<ActividadEntity> pendingDeletes = new ArrayList<>();
        ActividadEntity byLocalId;
        int countVisible;

        @Override
        public LiveData<List<ActividadEntity>> observeVisible(String accountKey) {
            return liveData;
        }

        @Override
        public int countVisibleNow(String accountKey) {
            return countVisible;
        }

        @Override
        public List<ActividadEntity> getAllNow(String accountKey) {
            return allNow;
        }

        @Override
        public List<ActividadEntity> getPendingCreates(String accountKey) {
            return pendingCreates;
        }

        @Override
        public List<ActividadEntity> getPendingDeletes(String accountKey) {
            return pendingDeletes;
        }

        @Override
        public ActividadEntity getByLocalId(String localId) {
            return byLocalId;
        }

        @Override
        public ActividadEntity getByRemoteId(String accountKey, int remoteId) {
            return byRemoteId.get(remoteId);
        }

        @Override
        public void upsert(ActividadEntity entity) {
            saved.add(entity);
        }

        @Override
        public void upsertAll(List<ActividadEntity> items) {
            saved.addAll(items);
        }

        @Override
        public void deleteByLocalId(String localId) {
            deletedLocalIds.add(localId);
        }

        @Override
        public void deleteByAccount(String accountKey) {
            allNow = Collections.emptyList();
        }
    }
}
