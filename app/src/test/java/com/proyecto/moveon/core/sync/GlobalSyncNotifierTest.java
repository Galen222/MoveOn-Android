package com.proyecto.moveon.core.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.Observer;

import com.proyecto.moveon.ui.common.Event;
import com.proyecto.moveon.ui.common.GlobalSnackbarMessage;
import com.proyecto.moveon.ui.common.TopSnackbar;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Tests unitarios para {@link GlobalSyncNotifier}.
 *
 * <p>Se valida el contrato homogéneo del singleton:</p>
 * <ul>
 *     <li>devuelve siempre la misma instancia,</li>
 *     <li>publica mensajes globales del mismo tipo que perfil y stats,</li>
 *     <li>marca la sincronización completada como {@code SUCCESS},</li>
 *     <li>y respeta la ventana de deduplicación temporal.</li>
 * </ul>
 *
 * <p>Se usa {@link InstantTaskExecutorRule} para que {@code LiveData#postValue(...)}
 * se ejecute de forma síncrona en tests JVM.</p>
 */
public class GlobalSyncNotifierTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Before
    public void setUp() throws Exception {
        resetSingleton();
    }

    @After
    public void tearDown() throws Exception {
        resetSingleton();
    }

    /**
     * Verifica el escenario cubierto por {@link #getInstance_returnsSameInstance()}.
     */
    @Test
    public void getInstance_returnsSameInstance() {
        GlobalSyncNotifier a = GlobalSyncNotifier.getInstance();
        GlobalSyncNotifier b = GlobalSyncNotifier.getInstance();

        assertSame(a, b);
    }

    /**
     * Verifica el escenario cubierto por {@link #getMessageEvent_isNotNull()}.
     */
    @Test
    public void getMessageEvent_isNotNull() {
        assertNotNull(GlobalSyncNotifier.getInstance().getMessageEvent());
    }

    /**
     * Verifica el escenario cubierto por {@link #notifySyncCompleted_emitsExpectedSuccessMessage()}.
     */
    @Test
    public void notifySyncCompleted_emitsExpectedSuccessMessage() {
        GlobalSyncNotifier notifier = GlobalSyncNotifier.getInstance();
        List<GlobalSnackbarMessage> consumed = new ArrayList<>();
        Observer<Event<GlobalSnackbarMessage>> observer = event -> {
            if (event == null) return;
            GlobalSnackbarMessage value = event.getContentIfNotHandled();
            if (value != null) {
                consumed.add(value);
            }
        };

        notifier.getMessageEvent().observeForever(observer);
        try {
            notifier.notifySyncCompleted("Sincronización completada");

            assertEquals(1, consumed.size());
            assertEquals(TopSnackbar.Type.SUCCESS, consumed.get(0).type);
            assertEquals("Sincronización completada", consumed.get(0).message.toString());
            assertNull(consumed.get(0).actionLabel);
            assertNull(consumed.get(0).action);
        } finally {
            notifier.getMessageEvent().removeObserver(observer);
        }
    }

    /**
     * Verifica el escenario cubierto por {@link #notifySyncCompleted_withinDebounce_emitsOnlyOnce()}.
     */
    @Test
    public void notifySyncCompleted_withinDebounce_emitsOnlyOnce() {
        GlobalSyncNotifier notifier = GlobalSyncNotifier.getInstance();
        List<GlobalSnackbarMessage> consumed = new ArrayList<>();
        Observer<Event<GlobalSnackbarMessage>> observer = event -> {
            if (event == null) return;
            GlobalSnackbarMessage value = event.getContentIfNotHandled();
            if (value != null) {
                consumed.add(value);
            }
        };

        notifier.getMessageEvent().observeForever(observer);
        try {
            notifier.notifySyncCompleted("Sincronización completada");
            notifier.notifySyncCompleted("Sincronización completada duplicada");

            // La segunda llamada cae dentro de la ventana de dedupe y no debe republicar.
            assertEquals(1, consumed.size());
            assertEquals(TopSnackbar.Type.SUCCESS, consumed.get(0).type);
            assertEquals("Sincronización completada", consumed.get(0).message.toString());
        } finally {
            notifier.getMessageEvent().removeObserver(observer);
        }
    }

    /**
     * Verifica el escenario cubierto por {@link #notifySyncCompleted_afterResettingClock_emitsAgain()}.
     */
    @Test
    public void notifySyncCompleted_afterResettingClock_emitsAgain() throws Exception {
        GlobalSyncNotifier notifier = GlobalSyncNotifier.getInstance();
        List<GlobalSnackbarMessage> consumed = new ArrayList<>();
        Observer<Event<GlobalSnackbarMessage>> observer = event -> {
            if (event == null) return;
            GlobalSnackbarMessage value = event.getContentIfNotHandled();
            if (value != null) {
                consumed.add(value);
            }
        };

        notifier.getMessageEvent().observeForever(observer);
        try {
            notifier.notifySyncCompleted("Primer aviso");

            // Forzamos el reloj interno hacia atrás para simular que ya pasó la ventana debounce
            // sin meter sleeps frágiles en la suite.
            setLastDispatchMs(notifier, 0L);
            notifier.notifySyncCompleted("Segundo aviso");

            assertEquals(2, consumed.size());
            assertEquals(TopSnackbar.Type.SUCCESS, consumed.get(0).type);
            assertEquals("Primer aviso", consumed.get(0).message.toString());
            assertEquals(TopSnackbar.Type.SUCCESS, consumed.get(1).type);
            assertEquals("Segundo aviso", consumed.get(1).message.toString());
        } finally {
            notifier.getMessageEvent().removeObserver(observer);
        }
    }

    private static void resetSingleton() throws Exception {
        Field instanceField = GlobalSyncNotifier.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);
    }

    private static void setLastDispatchMs(GlobalSyncNotifier notifier, long value) throws Exception {
        Field lastDispatchField = GlobalSyncNotifier.class.getDeclaredField("lastDispatchMs");
        lastDispatchField.setAccessible(true);
        lastDispatchField.setLong(notifier, value);
    }
}
