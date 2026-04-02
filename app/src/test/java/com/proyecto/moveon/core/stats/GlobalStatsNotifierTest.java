package com.proyecto.moveon.core.stats;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

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
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tests unitarios para {@link GlobalStatsNotifier}.
 *
 * <p>Validan que el canal global de estadísticas emite mensajes con el tipo correcto,
 * preserva las acciones opcionales y respeta la deduplicación temporal.</p>
 */
public class GlobalStatsNotifierTest {

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

    @Test
    public void getInstance_returnsSameInstance() {
        GlobalStatsNotifier a = GlobalStatsNotifier.getInstance();
        GlobalStatsNotifier b = GlobalStatsNotifier.getInstance();

        assertSame(a, b);
    }

    @Test
    public void getMessageEvent_isNotNull() {
        assertNotNull(GlobalStatsNotifier.getInstance().getMessageEvent());
    }

    @Test
    public void notifyWarning_emitsWarningMessage() {
        GlobalStatsNotifier notifier = GlobalStatsNotifier.getInstance();
        List<GlobalSnackbarMessage> consumed = new ArrayList<>();
        Observer<Event<GlobalSnackbarMessage>> observer = event -> {
            if (event == null) return;
            GlobalSnackbarMessage message = event.getContentIfNotHandled();
            if (message != null) {
                consumed.add(message);
            }
        };

        notifier.getMessageEvent().observeForever(observer);
        try {
            notifier.notifyWarning("No puedes borrar una actividad pendiente");

            assertEquals(1, consumed.size());
            assertEquals(TopSnackbar.Type.WARNING, consumed.get(0).type);
            assertEquals("No puedes borrar una actividad pendiente", consumed.get(0).message.toString());
            assertNull(consumed.get(0).actionLabel);
            assertNull(consumed.get(0).action);
        } finally {
            notifier.getMessageEvent().removeObserver(observer);
        }
    }

    @Test
    public void notifyError_withAction_emitsErrorMessageAndPreservesAction() {
        GlobalStatsNotifier notifier = GlobalStatsNotifier.getInstance();
        List<GlobalSnackbarMessage> consumed = new ArrayList<>();
        Observer<Event<GlobalSnackbarMessage>> observer = event -> {
            if (event == null) return;
            GlobalSnackbarMessage message = event.getContentIfNotHandled();
            if (message != null) {
                consumed.add(message);
            }
        };
        AtomicBoolean actionCalled = new AtomicBoolean(false);
        Runnable action = () -> actionCalled.set(true);

        notifier.getMessageEvent().observeForever(observer);
        try {
            notifier.notifyError("No se pudo abrir la preview", "Reintentar", action);

            assertEquals(1, consumed.size());
            GlobalSnackbarMessage message = consumed.get(0);
            assertEquals(TopSnackbar.Type.ERROR, message.type);
            assertEquals("No se pudo abrir la preview", message.message.toString());
            assertEquals("Reintentar", message.actionLabel);
            assertNotNull(message.action);

            message.action.run();
            assertTrue(actionCalled.get());
        } finally {
            notifier.getMessageEvent().removeObserver(observer);
        }
    }

    @Test
    public void notifyMessage_withinDebounce_emitsOnlyOnce() {
        GlobalStatsNotifier notifier = GlobalStatsNotifier.getInstance();
        List<GlobalSnackbarMessage> consumed = new ArrayList<>();
        Observer<Event<GlobalSnackbarMessage>> observer = event -> {
            if (event == null) return;
            GlobalSnackbarMessage message = event.getContentIfNotHandled();
            if (message != null) {
                consumed.add(message);
            }
        };

        notifier.getMessageEvent().observeForever(observer);
        try {
            notifier.notifySuccess("Actividad borrada");
            notifier.notifyError("No debería verse este segundo aviso");

            // El segundo aviso entra en la ventana de debounce y no debe reemplazar al primero.
            assertEquals(1, consumed.size());
            assertEquals(TopSnackbar.Type.SUCCESS, consumed.get(0).type);
            assertEquals("Actividad borrada", consumed.get(0).message.toString());
        } finally {
            notifier.getMessageEvent().removeObserver(observer);
        }
    }

    @Test
    public void notifyMessage_afterResettingClock_emitsAgain() throws Exception {
        GlobalStatsNotifier notifier = GlobalStatsNotifier.getInstance();
        List<GlobalSnackbarMessage> consumed = new ArrayList<>();
        Observer<Event<GlobalSnackbarMessage>> observer = event -> {
            if (event == null) return;
            GlobalSnackbarMessage message = event.getContentIfNotHandled();
            if (message != null) {
                consumed.add(message);
            }
        };

        notifier.getMessageEvent().observeForever(observer);
        try {
            notifier.notifySuccess("Primer aviso stats");

            // Forzamos un nuevo hueco temporal para permitir otra emisión válida.
            setLastDispatchMs(notifier, 0L);
            notifier.notifyWarning("Segundo aviso stats");

            assertEquals(2, consumed.size());
            assertEquals(TopSnackbar.Type.SUCCESS, consumed.get(0).type);
            assertEquals("Primer aviso stats", consumed.get(0).message.toString());
            assertEquals(TopSnackbar.Type.WARNING, consumed.get(1).type);
            assertEquals("Segundo aviso stats", consumed.get(1).message.toString());
        } finally {
            notifier.getMessageEvent().removeObserver(observer);
        }
    }

    private static void resetSingleton() throws Exception {
        Field instanceField = GlobalStatsNotifier.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);
    }

    private static void setLastDispatchMs(GlobalStatsNotifier notifier, long value) throws Exception {
        Field lastDispatchField = GlobalStatsNotifier.class.getDeclaredField("lastDispatchMs");
        lastDispatchField.setAccessible(true);
        lastDispatchField.setLong(notifier, value);
    }
}
