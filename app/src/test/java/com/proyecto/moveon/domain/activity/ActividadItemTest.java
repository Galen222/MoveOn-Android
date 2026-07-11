package com.proyecto.moveon.domain.activity;

import static org.junit.Assert.*;

import com.proyecto.moveon.data.activities.ActivitySyncState;

import org.junit.Test;

/**
 * Tests del modelo inmutable {@link ActividadItem}.
 */
public class ActividadItemTest {

    /**
     * Verifica que el constructor conserva todos los campos públicos de dominio.
     */
    @Test
    public void constructor_preservesAllFields() {
        ActividadItem item = new ActividadItem(
                "local-1",
                42,
                "caminata",
                1_500,
                1_000,
                800,
                150,
                50,
                120,
                2_000,
                400,
                450,
                300,
                600,
                900,
                2,
                1,
                3,
                "poly",
                "https://example.test/map.png",
                "2026-04-25T10:00:00+00:00",
                ActivitySyncState.FAILED_CREATE,
                "timeout"
        );

        assertEquals("local-1", item.localId);
        assertEquals(Integer.valueOf(42), item.remoteId);
        assertEquals("caminata", item.tipo);
        assertEquals(1_500, item.distanciaMetros);
        assertEquals(1_000, item.duracionSegundos);
        assertEquals(800, item.duracionMovimientoSegundos);
        assertEquals(150, item.duracionParadoSegundos);
        assertEquals(50, item.duracionPausaManualSegundos);
        assertEquals(120, item.caloriasQuemadas);
        assertEquals(Integer.valueOf(2_000), item.pasos);
        assertEquals(400, item.ritmoMedioMovimientoSegKm);
        assertEquals(450, item.ritmoMedioTotalSegKm);
        assertEquals(300, item.ritmoMaximoSegKm);
        assertEquals(600, item.velocidadMediaKmhX100);
        assertEquals(900, item.velocidadMaxKmhX100);
        assertEquals(2, item.autoPausas);
        assertEquals(1, item.pausasManuales);
        assertEquals(3, item.alertasVelocidad);
        assertEquals("poly", item.rutaPolilinea);
        assertEquals("https://example.test/map.png", item.rutaMapaUrl);
        assertEquals("2026-04-25T10:00:00+00:00", item.fechaRutaIso);
        assertEquals(ActivitySyncState.FAILED_CREATE, item.syncState);
        assertEquals("timeout", item.lastError);
    }

    /**
     * Verifica que una actividad sincronizada no se marca como pendiente.
     */
    @Test
    public void isPendingSync_returnsFalseWhenSynced() {
        ActividadItem item = itemWithState(ActivitySyncState.SYNCED);

        assertFalse(item.isPendingSync());
    }

    /**
     * Verifica que cualquier estado distinto de {@code SYNCED} mantiene la actividad pendiente.
     */
    @Test
    public void isPendingSync_returnsTrueForPendingAndFailedStates() {
        assertTrue(itemWithState(ActivitySyncState.PENDING_CREATE).isPendingSync());
        assertTrue(itemWithState(ActivitySyncState.FAILED_CREATE).isPendingSync());
        assertTrue(itemWithState(ActivitySyncState.PENDING_DELETE).isPendingSync());
        assertTrue(itemWithState(ActivitySyncState.FAILED_DELETE).isPendingSync());
    }

    /**
     * Crea un ítem mínimo variando únicamente el estado de sincronización.
     */
    private static ActividadItem itemWithState(String syncState) {
        return new ActividadItem(
                "local", null, "carrera",
                100, 60, 50, 10, 0,
                5, null, 300, 320, 280, 700, 800,
                0, 0, 0, null, null,
                "2026-04-25T10:00:00+00:00",
                syncState,
                null
        );
    }
}
