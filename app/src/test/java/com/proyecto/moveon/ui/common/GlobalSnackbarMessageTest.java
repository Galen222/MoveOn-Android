package com.proyecto.moveon.ui.common;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Tests del modelo {@link GlobalSnackbarMessage}.
 */
public class GlobalSnackbarMessageTest {

    /**
     * Verifica que el constructor simple crea un mensaje sin acción secundaria.
     */
    @Test
    public void simpleConstructor_setsTypeAndMessageWithoutAction() {
        GlobalSnackbarMessage message = new GlobalSnackbarMessage(TopSnackbar.Type.SUCCESS, "Guardado");

        assertEquals(TopSnackbar.Type.SUCCESS, message.type);
        assertEquals("Guardado", message.message);
        assertNull(message.actionLabel);
        assertNull(message.action);
    }

    /**
     * Verifica que el constructor completo conserva etiqueta y acción.
     */
    @Test
    public void fullConstructor_setsActionFields() {
        Runnable action = () -> {
            // No-op: solo interesa conservar la referencia.
        };

        GlobalSnackbarMessage message = new GlobalSnackbarMessage(
                TopSnackbar.Type.ERROR,
                "Error",
                "Reintentar",
                action
        );

        assertEquals(TopSnackbar.Type.ERROR, message.type);
        assertEquals("Error", message.message);
        assertEquals("Reintentar", message.actionLabel);
        assertSame(action, message.action);
    }
    /**
     * Verifica que GlobalSnackbarMessage conserva sus campos cuando se construye sin acción.
     */
    @Test
    public void globalSnackbarMessage_simpleConstructorKeepsTypeAndMessage() {
        GlobalSnackbarMessage message = new GlobalSnackbarMessage(TopSnackbar.Type.SUCCESS, "guardado");

        assertEquals(TopSnackbar.Type.SUCCESS, message.type);
        assertEquals("guardado", message.message);
        assertNull(message.actionLabel);
        assertNull(message.action);
    }

    /**
     * Verifica que GlobalSnackbarMessage conserva la acción opcional y permite ejecutarla.
     */
    @Test
    public void globalSnackbarMessage_fullConstructorStoresActionPayload() {
        final int[] calls = {0};
        Runnable action = () -> calls[0]++;
        GlobalSnackbarMessage message = new GlobalSnackbarMessage(
                TopSnackbar.Type.ERROR,
                "fallo",
                "Reintentar",
                action
        );

        assertEquals(TopSnackbar.Type.ERROR, message.type);
        assertEquals("fallo", message.message);
        assertEquals("Reintentar", message.actionLabel);
        assertSame(action, message.action);

        Runnable storedAction = message.action;
        assertNotNull(storedAction);
        storedAction.run();
        assertEquals(1, calls[0]);
    }

    /**
     * Verifica que el enum visual de TopSnackbar mantiene los tipos esperados por la UI.
     */
    @Test
    public void topSnackbarType_containsExpectedVisualCategories() {
        assertArrayEquals(
                new TopSnackbar.Type[]{TopSnackbar.Type.SUCCESS, TopSnackbar.Type.WARNING, TopSnackbar.Type.ERROR},
                TopSnackbar.Type.values()
        );
    }
}
