package com.proyecto.moveon.ui.home.tracking;

import static org.junit.Assert.*;

import com.google.android.gms.maps.model.LatLng;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
/**
 * Pruebas para validar el comportamiento de tracking.
 */
public class TrackingStateTest {

    // ── Factory / estado inicial ────────────────────────────────────────────

    /**
     * Verifica el escenario cubierto por {@link #idle_returnsDefaultValues()}.
     */
    @Test
    public void idle_returnsDefaultValues() {
        TrackingState state = TrackingState.idle();

        assertEquals(TrackingState.Status.IDLE, state.getStatus());
        assertEquals(TrackingState.ActivityType.WALKING, state.getActivityType());
        assertEquals(0L, state.getElapsedSeconds());
        assertEquals(0, state.getDistanceMeters());
        assertEquals(0, state.getCalories());
        assertNull(state.getPace());
        assertTrue(state.getRoutePoints().isEmpty());
        assertNull(state.getEncodedPolyline());
    }

    // ── Status helpers ──────────────────────────────────────────────────────

    /**
     * Verifica el escenario cubierto por {@link #isIdle_trueOnlyWhenIdle()}.
     */
    @Test
    public void isIdle_trueOnlyWhenIdle() {
        TrackingState idle = TrackingState.idle();
        assertTrue(idle.isIdle());
        assertFalse(idle.isRunning());
        assertFalse(idle.isPaused());
        assertFalse(idle.isFinished());
        assertFalse(idle.isActive());
    }

    /**
     * Verifica el escenario cubierto por {@link #isRunning_trueWhenRunning()}.
     */
    @Test
    public void isRunning_trueWhenRunning() {
        TrackingState running = TrackingState.idle().toBuilder()
                .status(TrackingState.Status.RUNNING)
                .build();

        assertFalse(running.isIdle());
        assertTrue(running.isRunning());
        assertTrue(running.isActive());
    }

    /**
     * Verifica el escenario cubierto por {@link #isPaused_trueWhenPaused()}.
     */
    @Test
    public void isPaused_trueWhenPaused() {
        TrackingState paused = TrackingState.idle().toBuilder()
                .status(TrackingState.Status.PAUSED)
                .build();

        assertTrue(paused.isPaused());
        assertTrue(paused.isActive());
    }

    /**
     * Verifica el escenario cubierto por {@link #isFinished_trueWhenFinished()}.
     */
    @Test
    public void isFinished_trueWhenFinished() {
        TrackingState finished = TrackingState.idle().toBuilder()
                .status(TrackingState.Status.FINISHED)
                .build();

        assertTrue(finished.isFinished());
        assertFalse(finished.isActive());
    }

    // ── Builder ─────────────────────────────────────────────────────────────

    /**
     * Verifica el escenario cubierto por {@link #builder_setsAllFields()}.
     */
    @Test
    public void builder_setsAllFields() {
        List<LatLng> points = Arrays.asList(
                new LatLng(40.0, -3.0),
                new LatLng(40.1, -3.1)
        );

        TrackingState state = new TrackingState.Builder()
                .status(TrackingState.Status.RUNNING)
                .activityType(TrackingState.ActivityType.RUNNING_ACTIVITY)
                .elapsedSeconds(340)
                .movingSeconds(300)
                .stoppedSeconds(40)
                .autoPausedSeconds(25)
                .distanceMeters(1500)
                .preciseDistanceMeters(1500.4)
                .calories(120)
                .pace("5:30")
                .routePoints(points)
                .encodedPolyline("abc123")
                .build();

        assertEquals(TrackingState.Status.RUNNING, state.getStatus());
        assertEquals(TrackingState.ActivityType.RUNNING_ACTIVITY, state.getActivityType());
        assertEquals(340L, state.getElapsedSeconds());
        assertEquals(1500, state.getDistanceMeters());
        assertEquals(1500.4, state.getPreciseDistanceMeters(), 0.0001);
        assertEquals(40L, state.getStoppedSeconds());
        assertEquals(25L, state.getAutoPausedSeconds());
        assertEquals(340L, state.getEffectiveElapsedSeconds());
        assertEquals(120, state.getCalories());
        assertEquals("5:30", state.getPace());
        assertEquals(2, state.getRoutePoints().size());
        assertEquals("abc123", state.getEncodedPolyline());
    }

    /**
     * Verifica el escenario cubierto por {@link #toBuilder_copiesAllFields()}.
     */
    @Test
    public void toBuilder_copiesAllFields() {
        TrackingState original = new TrackingState.Builder()
                .status(TrackingState.Status.RUNNING)
                .activityType(TrackingState.ActivityType.RUNNING_ACTIVITY)
                .elapsedSeconds(135)
                .movingSeconds(120)
                .stoppedSeconds(15)
                .autoPausedSeconds(9)
                .distanceMeters(500)
                .preciseDistanceMeters(500.8)
                .calories(40)
                .pace("6:00")
                .build();

        TrackingState copy = original.toBuilder().build();

        assertEquals(original.getStatus(), copy.getStatus());
        assertEquals(original.getActivityType(), copy.getActivityType());
        assertEquals(original.getElapsedSeconds(), copy.getElapsedSeconds());
        assertEquals(original.getDistanceMeters(), copy.getDistanceMeters());
        assertEquals(original.getPreciseDistanceMeters(), copy.getPreciseDistanceMeters(), 0.0001);
        assertEquals(original.getAutoPausedSeconds(), copy.getAutoPausedSeconds());
        assertEquals(original.getCalories(), copy.getCalories());
        assertEquals(original.getPace(), copy.getPace());
    }

    /**
     * Verifica el escenario cubierto por {@link #toBuilder_canModifySingleField()}.
     */
    @Test
    public void toBuilder_canModifySingleField() {
        TrackingState running = TrackingState.idle().toBuilder()
                .status(TrackingState.Status.RUNNING)
                .distanceMeters(1000)
                .build();

        TrackingState paused = running.toBuilder()
                .status(TrackingState.Status.PAUSED)
                .build();

        // Solo status cambia
        assertTrue(paused.isPaused());
        assertEquals(1000, paused.getDistanceMeters());
        assertEquals(TrackingState.ActivityType.WALKING, paused.getActivityType());
    }

    // ── Inmutabilidad de routePoints ────────────────────────────────────────

    /**
     * Verifica el escenario cubierto por {@link #routePoints_isUnmodifiable()}.
     */
    @Test(expected = UnsupportedOperationException.class)
    public void routePoints_isUnmodifiable() {
        TrackingState state = TrackingState.idle().toBuilder()
                .routePoints(List.of(new LatLng(40.0, -3.0)))
                .build();

        state.getRoutePoints().add(new LatLng(41.0, -3.5));
    }

    // ── Ciclo de vida completo ──────────────────────────────────────────────

    /**
     * Verifica el escenario cubierto por {@link #lifecycle_idle_running_paused_running_finished()}.
     */
    @Test
    public void lifecycle_idle_running_paused_running_finished() {
        TrackingState s1 = TrackingState.idle();
        assertTrue(s1.isIdle());

        TrackingState s2 = s1.toBuilder().status(TrackingState.Status.RUNNING).build();
        assertTrue(s2.isRunning());

        TrackingState s3 = s2.toBuilder().status(TrackingState.Status.PAUSED).build();
        assertTrue(s3.isPaused());

        TrackingState s4 = s3.toBuilder().status(TrackingState.Status.RUNNING).build();
        assertTrue(s4.isRunning());

        TrackingState s5 = s4.toBuilder().status(TrackingState.Status.FINISHED).build();
        assertTrue(s5.isFinished());
        assertFalse(s5.isActive());
    }
    /**
     * Verifica que el estado auto-pausado se considera pausado, activo y no pausado manualmente.
     */
    @Test
    public void autoPausedStatus_hasExpectedHelpers() {
        TrackingState state = TrackingState.idle().toBuilder()
                .status(TrackingState.Status.AUTO_PAUSED)
                .pauseReason(TrackingState.PauseReason.STATIONARY)
                .build();

        assertTrue(state.isPaused());
        assertTrue(state.isAutoPaused());
        assertFalse(state.isManuallyPaused());
        assertTrue(state.isActive());
        assertEquals(TrackingState.PauseReason.STATIONARY, state.getPauseReason());
    }

    /**
     * Verifica que el builder expone todos los campos de pausa, ritmos, velocidad y lifecycle.
     */
    @Test
    public void builder_setsRemainingFields() {
        LatLng location = new LatLng(40.0, -3.0);
        TrackingState.DiagnosticEvent event =
                new TrackingState.DiagnosticEvent(100L, "TYPE", "detail");

        TrackingState state = new TrackingState.Builder()
                .status(TrackingState.Status.PAUSED)
                .pauseReason(TrackingState.PauseReason.MANUAL)
                .activityType(TrackingState.ActivityType.RUNNING_ACTIVITY)
                .movingSeconds(11)
                .stoppedSeconds(22)
                .autoPausedSeconds(33)
                .manualPausedSeconds(44)
                .averageMovingPace("5:00")
                .averageElapsedPace("6:00")
                .maxPace("4:30")
                .maxSpeedKmhX100(1_600)
                .autoPauseCount(2)
                .manualPauseCount(3)
                .suspiciousSpeedEventCount(4)
                .currentLocation(location)
                .runningClassifiedSeconds(55)
                .walkingClassifiedSeconds(66)
                .sessionStartedAtEpochMs(77)
                .sessionFinishedAtEpochMs(88)
                .lastTimerTickAtEpochMs(99)
                .serviceCreatedAtEpochMs(111)
                .serviceDestroyedAtEpochMs(222)
                .serviceRestartCount(5)
                .diagnosticEvents(Collections.singletonList(event))
                .build();

        assertEquals(TrackingState.PauseReason.MANUAL, state.getPauseReason());
        assertEquals(11L, state.getMovingSeconds());
        assertEquals(22L, state.getStoppedSeconds());
        assertEquals(33L, state.getAutoPausedSeconds());
        assertEquals(44L, state.getManualPausedSeconds());
        assertEquals(33L, state.getEffectiveElapsedSeconds());
        assertEquals("5:00", state.getAverageMovingPace());
        assertEquals("6:00", state.getAverageElapsedPace());
        assertEquals("4:30", state.getMaxPace());
        assertEquals(1_600, state.getMaxSpeedKmhX100());
        assertEquals(2, state.getAutoPauseCount());
        assertEquals(3, state.getManualPauseCount());
        assertEquals(4, state.getSuspiciousSpeedEventCount());
        assertEquals(location, state.getCurrentLocation());
        assertEquals(55L, state.getRunningClassifiedSeconds());
        assertEquals(66L, state.getWalkingClassifiedSeconds());
        assertEquals(77L, state.getSessionStartedAtEpochMs());
        assertEquals(88L, state.getSessionFinishedAtEpochMs());
        assertEquals(99L, state.getLastTimerTickAtEpochMs());
        assertEquals(111L, state.getServiceCreatedAtEpochMs());
        assertEquals(222L, state.getServiceDestroyedAtEpochMs());
        assertEquals(5, state.getServiceRestartCount());
        assertEquals(1, state.getDiagnosticEvents().size());
        assertSame(event, state.getDiagnosticEvents().get(0));
    }

    /**
     * Verifica que la lista de ruta se copia defensivamente al construir el estado.
     */
    @Test
    public void routePoints_areDefensivelyCopied() {
        List<LatLng> points = new ArrayList<>();
        points.add(new LatLng(40.0, -3.0));

        TrackingState state = TrackingState.idle().toBuilder()
                .routePoints(points)
                .build();

        points.add(new LatLng(41.0, -4.0));

        assertEquals(1, state.getRoutePoints().size());
    }

    /**
     * Verifica que la lista de diagnósticos se copia defensivamente al construir el estado.
     */
    @Test
    public void diagnosticEvents_areDefensivelyCopied() {
        List<TrackingState.DiagnosticEvent> events = new ArrayList<>();
        events.add(new TrackingState.DiagnosticEvent(1L, "A", null));

        TrackingState state = TrackingState.idle().toBuilder()
                .diagnosticEvents(events)
                .build();

        events.add(new TrackingState.DiagnosticEvent(2L, "B", null));

        assertEquals(1, state.getDiagnosticEvents().size());
    }

    /**
     * Verifica que los eventos de diagnóstico conservan timestamp, tipo y detalle opcional.
     */
    @Test
    public void diagnosticEvent_preservesConstructorValues() {
        TrackingState.DiagnosticEvent event =
                new TrackingState.DiagnosticEvent(123L, "SPEED_ALERT", "too fast");

        assertEquals(123L, event.getAtEpochMs());
        assertEquals("SPEED_ALERT", event.getType());
        assertEquals("too fast", event.getDetail());
    }
    /**
     * Verifica los valores por defecto del estado idle y sus helpers booleanos.
     */
    @Test
    public void idle_hasDefaultMetricsAndOnlyIdleFlagEnabled() {
        TrackingState state = TrackingState.idle();

        assertEquals(TrackingState.Status.IDLE, state.getStatus());
        assertEquals(TrackingState.PauseReason.NONE, state.getPauseReason());
        assertEquals(TrackingState.ActivityType.WALKING, state.getActivityType());
        assertEquals(0L, state.getElapsedSeconds());
        assertEquals(0L, state.getMovingSeconds());
        assertEquals(0L, state.getStoppedSeconds());
        assertEquals(0L, state.getEffectiveElapsedSeconds());
        assertEquals(0, state.getDistanceMeters());
        assertEquals(0.0, state.getPreciseDistanceMeters(), 0.0001);
        assertTrue(state.isIdle());
        assertFalse(state.isRunning());
        assertFalse(state.isPaused());
        assertFalse(state.isActive());
        assertFalse(state.isFinished());
    }

    /**
     * Verifica que cada estado principal activa únicamente los helpers booleanos esperados.
     */
    @Test
    public void statusHelpers_classifyRunningPausedAutoPausedAndFinished() {
        TrackingState running = new TrackingState.Builder()
                .status(TrackingState.Status.RUNNING)
                .build();
        TrackingState paused = new TrackingState.Builder()
                .status(TrackingState.Status.PAUSED)
                .pauseReason(TrackingState.PauseReason.MANUAL)
                .build();
        TrackingState autoPaused = new TrackingState.Builder()
                .status(TrackingState.Status.AUTO_PAUSED)
                .pauseReason(TrackingState.PauseReason.STATIONARY)
                .build();
        TrackingState finished = new TrackingState.Builder()
                .status(TrackingState.Status.FINISHED)
                .build();

        assertTrue(running.isRunning());
        assertTrue(running.isActive());
        assertFalse(running.isPaused());

        assertTrue(paused.isPaused());
        assertTrue(paused.isManuallyPaused());
        assertTrue(paused.isActive());
        assertFalse(paused.isAutoPaused());

        assertTrue(autoPaused.isPaused());
        assertTrue(autoPaused.isAutoPaused());
        assertTrue(autoPaused.isActive());
        assertFalse(autoPaused.isManuallyPaused());

        assertTrue(finished.isFinished());
        assertFalse(finished.isActive());
    }

    /**
     * Verifica que el builder copia todas las métricas numéricas y textuales al snapshot final.
     */
    @Test
    public void builder_populatesAllScalarMetricsAndTextualFields() {
        TrackingState state = new TrackingState.Builder()
                .status(TrackingState.Status.RUNNING)
                .pauseReason(TrackingState.PauseReason.NONE)
                .activityType(TrackingState.ActivityType.RUNNING_ACTIVITY)
                .elapsedSeconds(600)
                .movingSeconds(540)
                .stoppedSeconds(60)
                .autoPausedSeconds(20)
                .manualPausedSeconds(10)
                .distanceMeters(1500)
                .preciseDistanceMeters(1500.75)
                .calories(123)
                .pace("4'30\"")
                .averageMovingPace("5'00\"")
                .averageElapsedPace("5'30\"")
                .maxPace("4'00\"")
                .maxSpeedKmhX100(1800)
                .autoPauseCount(2)
                .manualPauseCount(1)
                .suspiciousSpeedEventCount(3)
                .encodedPolyline("abc")
                .runningClassifiedSeconds(400)
                .walkingClassifiedSeconds(140)
                .sessionStartedAtEpochMs(1000)
                .sessionFinishedAtEpochMs(2000)
                .lastTimerTickAtEpochMs(1500)
                .serviceCreatedAtEpochMs(900)
                .serviceDestroyedAtEpochMs(2100)
                .serviceRestartCount(4)
                .build();

        assertEquals(TrackingState.Status.RUNNING, state.getStatus());
        assertEquals(TrackingState.PauseReason.NONE, state.getPauseReason());
        assertEquals(TrackingState.ActivityType.RUNNING_ACTIVITY, state.getActivityType());
        assertEquals(600L, state.getElapsedSeconds());
        assertEquals(540L, state.getMovingSeconds());
        assertEquals(60L, state.getStoppedSeconds());
        assertEquals(600L, state.getEffectiveElapsedSeconds());
        assertEquals(20L, state.getAutoPausedSeconds());
        assertEquals(10L, state.getManualPausedSeconds());
        assertEquals(1500, state.getDistanceMeters());
        assertEquals(1500.75, state.getPreciseDistanceMeters(), 0.0001);
        assertEquals(123, state.getCalories());
        assertEquals("4'30\"", state.getPace());
        assertEquals("5'00\"", state.getAverageMovingPace());
        assertEquals("5'30\"", state.getAverageElapsedPace());
        assertEquals("4'00\"", state.getMaxPace());
        assertEquals(1800, state.getMaxSpeedKmhX100());
        assertEquals(2, state.getAutoPauseCount());
        assertEquals(1, state.getManualPauseCount());
        assertEquals(3, state.getSuspiciousSpeedEventCount());
        assertEquals("abc", state.getEncodedPolyline());
        assertEquals(400L, state.getRunningClassifiedSeconds());
        assertEquals(140L, state.getWalkingClassifiedSeconds());
        assertEquals(1000L, state.getSessionStartedAtEpochMs());
        assertEquals(2000L, state.getSessionFinishedAtEpochMs());
        assertEquals(1500L, state.getLastTimerTickAtEpochMs());
        assertEquals(900L, state.getServiceCreatedAtEpochMs());
        assertEquals(2100L, state.getServiceDestroyedAtEpochMs());
        assertEquals(4, state.getServiceRestartCount());
    }

    /**
     * Verifica que ruta, ubicación y diagnósticos se copian y se exponen como listas inmutables.
     */
    @Test
    public void builder_defensivelyCopiesRoutePointsAndDiagnosticEvents() {
        List<LatLng> route = new ArrayList<>();
        route.add(new LatLng(40.0, -3.0));
        List<TrackingState.DiagnosticEvent> diagnostics = new ArrayList<>();
        diagnostics.add(new TrackingState.DiagnosticEvent(1L, "start", "ok"));

        TrackingState state = new TrackingState.Builder()
                .routePoints(route)
                .currentLocation(new LatLng(41.0, -4.0))
                .diagnosticEvents(diagnostics)
                .build();

        route.add(new LatLng(42.0, -5.0));
        diagnostics.add(new TrackingState.DiagnosticEvent(2L, "late", null));

        assertEquals(1, state.getRoutePoints().size());
        assertEquals(40.0, state.getRoutePoints().get(0).latitude, 0.0001);
        assertEquals(41.0, state.getCurrentLocation().latitude, 0.0001);
        assertEquals(1, state.getDiagnosticEvents().size());
        assertEquals("start", state.getDiagnosticEvents().get(0).getType());

        assertListIsUnmodifiable(state.getRoutePoints());
        assertListIsUnmodifiable(state.getDiagnosticEvents());
    }

    /**
     * Verifica que toBuilder preserva el estado original y permite crear una variante independiente.
     */
    @Test
    public void toBuilder_preservesOriginalAndBuildsIndependentModifiedSnapshot() {
        TrackingState original = new TrackingState.Builder()
                .status(TrackingState.Status.RUNNING)
                .distanceMeters(1000)
                .calories(50)
                .routePoints(Arrays.asList(new LatLng(40.1, -3.1)))
                .build();

        TrackingState modified = original.toBuilder()
                .status(TrackingState.Status.FINISHED)
                .distanceMeters(2000)
                .calories(120)
                .build();

        assertEquals(TrackingState.Status.RUNNING, original.getStatus());
        assertEquals(1000, original.getDistanceMeters());
        assertEquals(50, original.getCalories());
        assertEquals(TrackingState.Status.FINISHED, modified.getStatus());
        assertEquals(2000, modified.getDistanceMeters());
        assertEquals(120, modified.getCalories());
        assertEquals(1, modified.getRoutePoints().size());
    }

    /**
     * Verifica que DiagnosticEvent conserva fecha, tipo y detalle opcional.
     */
    @Test
    public void diagnosticEvent_gettersReturnConstructorValues() {
        TrackingState.DiagnosticEvent withDetail = new TrackingState.DiagnosticEvent(123L, "gps", "weak");
        TrackingState.DiagnosticEvent withoutDetail = new TrackingState.DiagnosticEvent(456L, "tick", null);

        assertEquals(123L, withDetail.getAtEpochMs());
        assertEquals("gps", withDetail.getType());
        assertEquals("weak", withDetail.getDetail());
        assertEquals(456L, withoutDetail.getAtEpochMs());
        assertEquals("tick", withoutDetail.getType());
        assertNull(withoutDetail.getDetail());
    }

    private static <T> void assertListIsUnmodifiable(List<T> list) {
        try {
            list.add(null);
            fail("La lista debería ser inmutable");
        } catch (UnsupportedOperationException expected) {
            // Comportamiento esperado.
        }
    }
}