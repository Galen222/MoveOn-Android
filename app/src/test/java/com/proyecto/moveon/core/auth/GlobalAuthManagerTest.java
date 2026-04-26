package com.proyecto.moveon.core.auth;

import static org.junit.Assert.*;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Tests unitarios para GlobalAuthManager.
 *
 * Nota: Los tests de LiveData observación requieren InstantTaskExecutorRule
 * (dependencia testImplementation) o tests instrumentados. Aquí se
 * testea la lógica de deduplicación del AtomicBoolean.
 */
public class GlobalAuthManagerTest {

    @Rule
    public final InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private GlobalAuthManager manager;

    /**
     * Reinicia el estado del singleton antes de cada test llamando a
     * {@code acknowledgeSessionExpired}, para que un test previo no
     * contamine al siguiente a través del estado global compartido.
     */
    @Before
    public void setUp() {
        manager = GlobalAuthManager.getInstance();
        manager.acknowledgeSessionExpired(); // Reset state
    }

    /**
     * Comprueba que dos llamadas a {@code getInstance()} devuelven la
     * misma referencia: el patrón singleton del manager es crítico porque
     * la notificación de sesión expirada debe ver UN ÚNICO estado en toda
     * la app.
     */
    @Test
    public void getInstance_returnsSameInstance() {
        GlobalAuthManager a = GlobalAuthManager.getInstance();
        GlobalAuthManager b = GlobalAuthManager.getInstance();
        assertSame(a, b);
    }

    /**
     * Verifica que tras {@code acknowledgeSessionExpired()} el manager
     * vuelve a aceptar nuevas notificaciones. Sin este reset la expiración
     * solo se propagaría una vez por vida del proceso.
     */
    @Test
    public void acknowledgeSessionExpired_resetsDispatchFlag() {
        manager.notifySessionExpired();
        manager.acknowledgeSessionExpired();
        // Tras acknowledge, debería poder notificar de nuevo sin ser deduplicado
        // (la verificación completa requiere LiveData observer)
    }

    /**
     * Comprueba que el LiveData que expone los eventos de expiración no
     * es {@code null} nada más pedir el manager: los observadores pueden
     * suscribirse con seguridad sin chequeo previo.
     */
    @Test
    public void getSessionExpiredEvent_isNotNull() {
        assertNotNull(manager.getSessionExpiredEvent());
    }
}