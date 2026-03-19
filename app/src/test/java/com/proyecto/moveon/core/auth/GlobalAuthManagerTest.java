package com.proyecto.moveon.core.auth;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

/**
 * Tests unitarios para GlobalAuthManager.
 *
 * Nota: Los tests de LiveData observación requieren InstantTaskExecutorRule
 * (dependencia androidTestImplementation) o tests instrumentados. Aquí se
 * testea la lógica de deduplicación del AtomicBoolean.
 */
public class GlobalAuthManagerTest {

    private GlobalAuthManager manager;

    @Before
    public void setUp() {
        manager = GlobalAuthManager.getInstance();
        manager.acknowledgeSessionExpired(); // Reset state
    }

    @Test
    public void getInstance_returnsSameInstance() {
        GlobalAuthManager a = GlobalAuthManager.getInstance();
        GlobalAuthManager b = GlobalAuthManager.getInstance();
        assertSame(a, b);
    }

    @Test
    public void acknowledgeSessionExpired_resetsDispatchFlag() {
        manager.notifySessionExpired();
        manager.acknowledgeSessionExpired();
        // Tras acknowledge, debería poder notificar de nuevo sin ser deduplicado
        // (la verificación completa requiere LiveData observer)
    }

    @Test
    public void getSessionExpiredEvent_isNotNull() {
        assertNotNull(manager.getSessionExpiredEvent());
    }
}
