package com.proyecto.moveon.data.activities;

import static org.junit.Assert.*;

import com.proyecto.moveon.data.activities.dto.GuardarActividadRequestDto;
import com.proyecto.moveon.data.local.entity.ActividadEntity;
import com.proyecto.moveon.domain.activity.ActividadItem;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.OffsetDateTime;

/**
 * Tests de lógica pura privada de ActivityRepository que no necesita inicializar Android ni Room.
 */
public class ActivityRepositoryPrivatePureTest {

    /**
     * Verifica que las factorías de SyncResult representan correctamente noop, completado y reintento.
     */
    @Test
    public void syncResultFactories_exposeExpectedFlags() {
        ActivityRepository.SyncResult noop = ActivityRepository.SyncResult.successNoop();
        ActivityRepository.SyncResult completed = ActivityRepository.SyncResult.successCompleted();
        ActivityRepository.SyncResult retry = ActivityRepository.SyncResult.retry();

        assertFalse(noop.retry);
        assertFalse(noop.completedPendingWork);
        assertFalse(completed.retry);
        assertTrue(completed.completedPendingWork);
        assertTrue(retry.retry);
        assertFalse(retry.completedPendingWork);
    }

    /**
     * Verifica que mapEntityToDomain traslada todos los campos enriquecidos de Room al modelo de dominio.
     */
    @Test
    public void mapEntityToDomain_copiesAllActivityFields() throws Exception {
        ActividadEntity entity = sampleEntity();
        ActivityRepository repository = allocateRepository();

        ActividadItem item = invokeMapEntityToDomain(repository, entity);

        assertEquals("local-1", item.localId);
        assertEquals(Integer.valueOf(77), item.remoteId);
        assertEquals("Correr", item.tipo);
        assertEquals(5000, item.distanciaMetros);
        assertEquals(1800, item.duracionSegundos);
        assertEquals(1700, item.duracionMovimientoSegundos);
        assertEquals(80, item.duracionParadoSegundos);
        assertEquals(20, item.duracionPausaManualSegundos);
        assertEquals(350, item.caloriasQuemadas);
        assertEquals(Integer.valueOf(4_321), item.pasos);
        assertEquals(340, item.ritmoMedioMovimientoSegKm);
        assertEquals(360, item.ritmoMedioTotalSegKm);
        assertEquals(300, item.ritmoMaximoSegKm);
        assertEquals(1000, item.velocidadMediaKmhX100);
        assertEquals(1500, item.velocidadMaxKmhX100);
        assertEquals(2, item.autoPausas);
        assertEquals(1, item.pausasManuales);
        assertEquals(3, item.alertasVelocidad);
        assertEquals("poly", item.rutaPolilinea);
        assertEquals("map", item.rutaMapaUrl);
        assertEquals("2026-04-25T10:00:00Z", item.fechaRutaIso);
        assertEquals(ActivitySyncState.PENDING_CREATE, item.syncState);
        assertEquals("pendiente", item.lastError);
        assertTrue(item.isPendingSync());
    }

    /**
     * Verifica que mapEntityToDomain conserva nulls esperados en campos remotos opcionales.
     */
    @Test
    public void mapEntityToDomain_keepsOptionalRemoteFieldsNull() throws Exception {
        ActividadEntity entity = sampleEntity();
        entity.remoteId = null;
        entity.rutaPolilinea = null;
        entity.rutaMapaUrl = null;
        entity.lastError = null;
        entity.syncState = ActivitySyncState.SYNCED;
        ActivityRepository repository = allocateRepository();

        ActividadItem item = invokeMapEntityToDomain(repository, entity);

        assertNull(item.remoteId);
        assertNull(item.rutaPolilinea);
        assertNull(item.rutaMapaUrl);
        assertNull(item.lastError);
        assertEquals(ActivitySyncState.SYNCED, item.syncState);
        assertFalse(item.isPendingSync());
    }

    /**
     * Verifica que mapEntityToDomain marca como pendiente cualquier estado distinto de sincronizado.
     */
    @Test
    public void mapEntityToDomain_marksFailedAndDeleteStatesAsPending() throws Exception {
        ActivityRepository repository = allocateRepository();
        ActividadEntity failedCreate = sampleEntity();
        failedCreate.syncState = ActivitySyncState.FAILED_CREATE;
        ActividadEntity pendingDelete = sampleEntity();
        pendingDelete.syncState = ActivitySyncState.PENDING_DELETE;
        ActividadEntity failedDelete = sampleEntity();
        failedDelete.syncState = ActivitySyncState.FAILED_DELETE;

        assertTrue(invokeMapEntityToDomain(repository, failedCreate).isPendingSync());
        assertTrue(invokeMapEntityToDomain(repository, pendingDelete).isPendingSync());
        assertTrue(invokeMapEntityToDomain(repository, failedDelete).isPendingSync());
    }

    /**
     * Verifica que validateRequest acepta un payload coherente sin necesitar recursos Android.
     */
    @Test
    public void validateRequest_validPayloadReturnsNull() throws Exception {
        ActivityRepository repository = allocateRepository();
        GuardarActividadRequestDto request = validRequest("Correr");

        Object error = invokeValidateRequest(repository, request);

        assertNull(error);
    }

    /**
     * Verifica que validateRequest acepta los dos tipos canónicos permitidos por la app.
     */
    @Test
    public void validateRequest_acceptsWalkingAndRunningCanonicalTypes() throws Exception {
        ActivityRepository repository = allocateRepository();
        GuardarActividadRequestDto caminar = validRequest("Caminar");
        GuardarActividadRequestDto correr = validRequest("Correr");

        assertNull(invokeValidateRequest(repository, caminar));
        assertNull(invokeValidateRequest(repository, correr));
    }

    /**
     * Verifica que validateRequest acepta los límites inferiores válidos de métricas y contadores.
     */
    @Test
    public void validateRequest_acceptsMinimumValidBoundaries() throws Exception {
        ActivityRepository repository = allocateRepository();
        GuardarActividadRequestDto request = request("Caminar", 1, 1, 1, 0, 0, 1, 1, 1, 0, 1, 1, 0, 0, 0, null, pastDate());

        assertNull(invokeValidateRequest(repository, request));
    }

    /**
     * Verifica que validateRequest acepta los límites superiores válidos de métricas y contadores.
     */
    @Test
    public void validateRequest_acceptsMaximumValidBoundaries() throws Exception {
        ActivityRepository repository = allocateRepository();
        GuardarActividadRequestDto request = request("Correr", 300000, 86400, 86400, 0, 86400, 10000, 3600, 3600, 3600, 5000, 10000, 500, 500, 500, "xy", pastDate());

        assertNull(invokeValidateRequest(repository, request));
    }

    /**
     * Verifica que validateRequest acepta polilíneas nulas, en blanco o con longitud mínima válida.
     */
    @Test
    public void validateRequest_acceptsNullableBlankAndMinimumPolylineValues() throws Exception {
        ActivityRepository repository = allocateRepository();
        GuardarActividadRequestDto nullPolyline = request("Correr", 5000, 1800, 1700, 100, 20, 350, 340, 360, 300, 1000, 1500, 1, 1, 0, null, pastDate());
        GuardarActividadRequestDto blankPolyline = request("Correr", 5000, 1800, 1700, 100, 20, 350, 340, 360, 300, 1000, 1500, 1, 1, 0, "   ", pastDate());
        GuardarActividadRequestDto twoCharsPolyline = request("Correr", 5000, 1800, 1700, 100, 20, 350, 340, 360, 300, 1000, 1500, 1, 1, 0, "ab", pastDate());

        assertNull(invokeValidateRequest(repository, nullPolyline));
        assertNull(invokeValidateRequest(repository, blankPolyline));
        assertNull(invokeValidateRequest(repository, twoCharsPolyline));
    }

    private static ActividadEntity sampleEntity() {
        ActividadEntity entity = new ActividadEntity();
        entity.localId = "local-1";
        entity.accountKey = "account";
        entity.remoteId = 77;
        entity.tipo = "Correr";
        entity.distancia = 5000;
        entity.duracionTotal = 1800;
        entity.duracionMovimiento = 1700;
        entity.duracionParado = 80;
        entity.duracionPausaManual = 20;
        entity.caloriasQuemadas = 350;
        entity.pasos = 4_321;
        entity.ritmoMedioMovimiento = 340;
        entity.ritmoMedioTotal = 360;
        entity.ritmoMaximo = 300;
        entity.velocidadMediaKmhX100 = 1000;
        entity.velocidadMaxKmhX100 = 1500;
        entity.autoPausas = 2;
        entity.pausasManuales = 1;
        entity.alertasVelocidad = 3;
        entity.rutaPolilinea = "poly";
        entity.rutaMapaUrl = "map";
        entity.fechaRuta = "2026-04-25T10:00:00Z";
        entity.syncState = ActivitySyncState.PENDING_CREATE;
        entity.lastError = "pendiente";
        return entity;
    }

    private static GuardarActividadRequestDto validRequest(String tipo) {
        return request(tipo, 5000, 1800, 1700, 100, 20, 350, 340, 360, 300, 1000, 1500, 1, 1, 0, "poly", pastDate());
    }

    private static GuardarActividadRequestDto request(
            String tipo,
            int distancia,
            int duracionTotal,
            int duracionMovimiento,
            int duracionParado,
            int duracionPausaManual,
            int caloriasQuemadas,
            int ritmoMedioMovimiento,
            int ritmoMedioTotal,
            int ritmoMaximo,
            int velocidadMediaKmhX100,
            int velocidadMaxKmhX100,
            int autoPausas,
            int pausasManuales,
            int alertasVelocidad,
            String rutaPolilinea,
            String fechaRuta) {
        return new GuardarActividadRequestDto(
                tipo,
                distancia,
                duracionTotal,
                duracionMovimiento,
                duracionParado,
                duracionPausaManual,
                caloriasQuemadas,
                null,
                ritmoMedioMovimiento,
                ritmoMedioTotal,
                ritmoMaximo,
                velocidadMediaKmhX100,
                velocidadMaxKmhX100,
                autoPausas,
                pausasManuales,
                alertasVelocidad,
                rutaPolilinea,
                fechaRuta
        );
    }

    private static String pastDate() {
        return OffsetDateTime.now().minusMinutes(5).toString();
    }

    private static ActivityRepository allocateRepository() throws Exception {
        Field field = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
        field.setAccessible(true);
        Object unsafe = java.util.Objects.requireNonNull(field.get(null), "Unsafe no disponible");
        Method method = unsafe.getClass().getMethod("allocateInstance", Class.class);
        return (ActivityRepository) method.invoke(unsafe, ActivityRepository.class);
    }

    private static ActividadItem invokeMapEntityToDomain(ActivityRepository repository, ActividadEntity entity) throws Exception {
        Method method = ActivityRepository.class.getDeclaredMethod("mapEntityToDomain", ActividadEntity.class);
        method.setAccessible(true);
        return (ActividadItem) method.invoke(repository, entity);
    }

    private static Object invokeValidateRequest(ActivityRepository repository, GuardarActividadRequestDto request) throws Exception {
        Method method = ActivityRepository.class.getDeclaredMethod("validateRequest", GuardarActividadRequestDto.class);
        method.setAccessible(true);
        return method.invoke(repository, request);
    }
}
