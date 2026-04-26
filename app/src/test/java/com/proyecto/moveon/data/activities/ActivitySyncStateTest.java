package com.proyecto.moveon.data.activities;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Tests para las reglas de negocio de {@link ActivitySyncState}.
 */
public class ActivitySyncStateTest {

    /**
     * Verifica que el estado sincronizado es el único que no requiere reintento.
     */
    @Test
    public void isPending_returnsFalseOnlyForSynced() {
        assertFalse(ActivitySyncState.isPending(ActivitySyncState.SYNCED));
    }

    /**
     * Verifica que los estados pendientes o fallidos siguen requiriendo sincronización.
     */
    @Test
    public void isPending_returnsTrueForPendingAndFailedStates() {
        assertTrue(ActivitySyncState.isPending(ActivitySyncState.PENDING_CREATE));
        assertTrue(ActivitySyncState.isPending(ActivitySyncState.FAILED_CREATE));
        assertTrue(ActivitySyncState.isPending(ActivitySyncState.PENDING_DELETE));
        assertTrue(ActivitySyncState.isPending(ActivitySyncState.FAILED_DELETE));
    }

    /**
     * Verifica que un estado desconocido se trata como pendiente para favorecer el reintento seguro.
     */
    @Test
    public void isPending_returnsTrueForUnknownState() {
        assertTrue(ActivitySyncState.isPending("UNKNOWN"));
    }
}
