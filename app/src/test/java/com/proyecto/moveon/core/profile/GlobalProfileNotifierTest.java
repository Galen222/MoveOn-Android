package com.proyecto.moveon.core.profile;

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
 * Tests unitarios para {@link GlobalProfileNotifier}.
 *
 * <p>Validan que el canal global de perfil emite mensajes con el tipo correcto,
 * conserva la acción opcional y deduplica ráfagas inmediatas.</p>
 */
public class GlobalProfileNotifierTest {

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
        GlobalProfileNotifier a = GlobalProfileNotifier.getInstance();
        GlobalProfileNotifier b = GlobalProfileNotifier.getInstance();

        assertSame(a, b);
    }

    @Test
    public void getMessageEvent_isNotNull() {
        assertNotNull(GlobalProfileNotifier.getInstance().getMessageEvent());
    }

    @Test
    public void notifySuccess_emitsSuccessMessage() {
        GlobalProfileNotifier notifier = GlobalProfileNotifier.getInstance();
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
            notifier.notifySuccess("Perfil actualizado");

            assertEquals(1, consumed.size());
            assertEquals(TopSnackbar.Type.SUCCESS, consumed.get(0).type);
            assertEquals("Perfil actualizado", consumed.get(0).message.toString());
            assertNull(consumed.get(0).actionLabel);
            assertNull(consumed.get(0).action);
        } finally {
            notifier.getMessageEvent().removeObserver(observer);
        }
    }

    @Test
    public void notifyError_withAction_emitsErrorMessageAndPreservesAction() {
        GlobalProfileNotifier notifier = GlobalProfileNotifier.getInstance();
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
            notifier.notifyError("No se pudo actualizar", "Reintentar", action);

            assertEquals(1, consumed.size());
            GlobalSnackbarMessage message = consumed.get(0);
            assertEquals(TopSnackbar.Type.ERROR, message.type);
            assertEquals("No se pudo actualizar", message.message.toString());
            assertEquals("Reintentar", message.actionLabel);
            assertNotNull(message.action);

            // Comprobamos que el callback queda realmente transportado dentro del evento.
            message.action.run();
            assertTrue(actionCalled.get());
        } finally {
            notifier.getMessageEvent().removeObserver(observer);
        }
    }

    @Test
    public void notifyMessage_withinDebounce_emitsOnlyOnce() {
        GlobalProfileNotifier notifier = GlobalProfileNotifier.getInstance();
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
            notifier.notifyWarning("Campo actualizado");
            notifier.notifyWarning("Campo actualizado otra vez");

            assertEquals(1, consumed.size());
            assertEquals(TopSnackbar.Type.WARNING, consumed.get(0).type);
            assertEquals("Campo actualizado", consumed.get(0).message.toString());
        } finally {
            notifier.getMessageEvent().removeObserver(observer);
        }
    }

    @Test
    public void notifyMessage_afterResettingClock_emitsAgain() throws Exception {
        GlobalProfileNotifier notifier = GlobalProfileNotifier.getInstance();
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
            notifier.notifySuccess("Primer aviso");

            // Simulamos el paso del tiempo sin sleeps para reabrir la ventana de emisión.
            setLastDispatchMs(notifier, 0L);
            notifier.notifyError("Segundo aviso");

            assertEquals(2, consumed.size());
            assertEquals(TopSnackbar.Type.SUCCESS, consumed.get(0).type);
            assertEquals("Primer aviso", consumed.get(0).message.toString());
            assertEquals(TopSnackbar.Type.ERROR, consumed.get(1).type);
            assertEquals("Segundo aviso", consumed.get(1).message.toString());
        } finally {
            notifier.getMessageEvent().removeObserver(observer);
        }
    }

    private static void resetSingleton() throws Exception {
        Field instanceField = GlobalProfileNotifier.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);
    }

    private static void setLastDispatchMs(GlobalProfileNotifier notifier, long value) throws Exception {
        Field lastDispatchField = GlobalProfileNotifier.class.getDeclaredField("lastDispatchMs");
        lastDispatchField.setAccessible(true);
        lastDispatchField.setLong(notifier, value);
    }
}
