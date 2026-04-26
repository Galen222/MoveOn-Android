package com.proyecto.moveon.core.settings;

import static org.junit.Assert.*;

import com.proyecto.moveon.data.activities.ActivitySyncState;
import com.proyecto.moveon.domain.activity.ActividadItem;
import com.proyecto.moveon.testutil.MemoryContext;
import com.proyecto.moveon.ui.home.tracking.TrackingState;

import org.junit.Test;

/**
 * Tests de selección de ritmo medio preferido en actividades cerradas y tracking en vivo.
 */
public class PaceDisplayUtilsTest {

    /**
     * Verifica que por defecto se usa el ritmo total de una actividad cerrada.
     */
    @Test
    public void getPreferredAveragePaceSeconds_usesTotalPaceByDefault() {
        MemoryContext context = new MemoryContext();
        ActividadItem item = activityWithPaces(315, 360);

        assertFalse(PaceDisplayUtils.shouldUseMovingPace(context));
        assertEquals(360, PaceDisplayUtils.getPreferredAveragePaceSeconds(context, item));
    }

    /**
     * Verifica que el ritmo en movimiento se prioriza cuando la preferencia está activa y hay dato válido.
     */
    @Test
    public void getPreferredAveragePaceSeconds_usesMovingPaceWhenConfiguredAndAvailable() {
        MemoryContext context = new MemoryContext();
        AppSettingsManager.setPaceDisplayMode(context, AppSettingsManager.PACE_DISPLAY_MOVING);
        ActividadItem item = activityWithPaces(315, 360);

        assertTrue(PaceDisplayUtils.shouldUseMovingPace(context));
        assertEquals(315, PaceDisplayUtils.getPreferredAveragePaceSeconds(context, item));
    }

    /**
     * Verifica que una actividad antigua sin ritmo en movimiento cae al ritmo total aunque la preferencia esté activa.
     */
    @Test
    public void getPreferredAveragePaceSeconds_fallsBackToTotalWhenMovingPaceIsMissing() {
        MemoryContext context = new MemoryContext();
        AppSettingsManager.setPaceDisplayMode(context, AppSettingsManager.PACE_DISPLAY_MOVING);
        ActividadItem item = activityWithPaces(0, 390);

        assertEquals(390, PaceDisplayUtils.getPreferredAveragePaceSeconds(context, item));
    }

    /**
     * Verifica que en tracking en vivo se devuelve el texto de ritmo en movimiento si tiene contenido útil.
     */
    @Test
    public void getPreferredAveragePaceText_usesMovingTextWhenConfiguredAndPresent() {
        MemoryContext context = new MemoryContext();
        AppSettingsManager.setPaceDisplayMode(context, AppSettingsManager.PACE_DISPLAY_MOVING);
        TrackingState state = TrackingState.idle().toBuilder()
                .averageMovingPace("5:10")
                .averageElapsedPace("5:40")
                .build();

        assertEquals("5:10", PaceDisplayUtils.getPreferredAveragePaceText(context, state));
    }

    /**
     * Verifica que en tracking en vivo se usa el ritmo total cuando el texto de movimiento está vacío.
     */
    @Test
    public void getPreferredAveragePaceText_fallsBackToElapsedTextWhenMovingTextIsBlank() {
        MemoryContext context = new MemoryContext();
        AppSettingsManager.setPaceDisplayMode(context, AppSettingsManager.PACE_DISPLAY_MOVING);
        TrackingState state = TrackingState.idle().toBuilder()
                .averageMovingPace("   ")
                .averageElapsedPace("6:00")
                .build();

        assertEquals("6:00", PaceDisplayUtils.getPreferredAveragePaceText(context, state));
    }

    /**
     * Verifica que se devuelve el texto en movimiento como último fallback si no hay ritmo total disponible.
     */
    @Test
    public void getPreferredAveragePaceText_returnsMovingTextWhenElapsedTextIsMissing() {
        MemoryContext context = new MemoryContext();
        TrackingState state = TrackingState.idle().toBuilder()
                .averageMovingPace("7:15")
                .averageElapsedPace(null)
                .build();

        assertEquals("7:15", PaceDisplayUtils.getPreferredAveragePaceText(context, state));
    }

    private static ActividadItem activityWithPaces(int movingPace, int totalPace) {
        return new ActividadItem(
                "local-pace",
                1,
                "Correr",
                5000,
                1800,
                1700,
                100,
                0,
                350,
                movingPace,
                totalPace,
                300,
                1000,
                1500,
                0,
                0,
                0,
                null,
                null,
                "2026-04-25T10:00:00Z",
                ActivitySyncState.SYNCED,
                null
        );
    }
}
