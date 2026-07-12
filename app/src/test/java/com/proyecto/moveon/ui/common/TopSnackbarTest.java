package com.proyecto.moveon.ui.common;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Tests de la resolución del margen superior del snackbar centralizado.
 */
public class TopSnackbarTest {

    /**
     * La barra de estado se incorpora cuando no existe compensación manual.
     */
    @Test
    public void resolveTopMargin_usesAutomaticSystemInset() {
        assertEquals(96, TopSnackbar.resolveTopMarginPx(72, 0));
    }

    /**
     * Un offset de diálogo mayor conserva su alineación específica.
     */
    @Test
    public void resolveTopMargin_keepsLargerExplicitOffset() {
        assertEquals(104, TopSnackbar.resolveTopMarginPx(72, 80));
    }

    /**
     * No suma dos veces el mismo espacio seguro cuando ambos offsets se solapan.
     */
    @Test
    public void resolveTopMargin_doesNotDoubleCountOverlappingOffsets() {
        assertEquals(96, TopSnackbar.resolveTopMarginPx(72, 40));
    }
}
