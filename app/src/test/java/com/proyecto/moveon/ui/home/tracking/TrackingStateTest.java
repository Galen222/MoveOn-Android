package com.proyecto.moveon.ui.home.tracking;

import static org.junit.Assert.*;

import com.google.android.gms.maps.model.LatLng;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class TrackingStateTest {

    // ── Factory / estado inicial ────────────────────────────────────────────

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

    @Test
    public void isIdle_trueOnlyWhenIdle() {
        TrackingState idle = TrackingState.idle();
        assertTrue(idle.isIdle());
        assertFalse(idle.isRunning());
        assertFalse(idle.isPaused());
        assertFalse(idle.isFinished());
        assertFalse(idle.isActive());
    }

    @Test
    public void isRunning_trueWhenRunning() {
        TrackingState running = TrackingState.idle().toBuilder()
                .status(TrackingState.Status.RUNNING)
                .build();

        assertFalse(running.isIdle());
        assertTrue(running.isRunning());
        assertTrue(running.isActive());
    }

    @Test
    public void isPaused_trueWhenPaused() {
        TrackingState paused = TrackingState.idle().toBuilder()
                .status(TrackingState.Status.PAUSED)
                .build();

        assertTrue(paused.isPaused());
        assertTrue(paused.isActive());
    }

    @Test
    public void isFinished_trueWhenFinished() {
        TrackingState finished = TrackingState.idle().toBuilder()
                .status(TrackingState.Status.FINISHED)
                .build();

        assertTrue(finished.isFinished());
        assertFalse(finished.isActive());
    }

    // ── Builder ─────────────────────────────────────────────────────────────

    @Test
    public void builder_setsAllFields() {
        List<LatLng> points = Arrays.asList(
                new LatLng(40.0, -3.0),
                new LatLng(40.1, -3.1)
        );

        TrackingState state = new TrackingState.Builder()
                .status(TrackingState.Status.RUNNING)
                .activityType(TrackingState.ActivityType.RUNNING_ACTIVITY)
                                .elapsedSeconds(300)
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
        assertEquals(300L, state.getElapsedSeconds());
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

    @Test
    public void toBuilder_copiesAllFields() {
        TrackingState original = new TrackingState.Builder()
                .status(TrackingState.Status.RUNNING)
                .activityType(TrackingState.ActivityType.RUNNING_ACTIVITY)
                                .elapsedSeconds(120)
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

    @Test(expected = UnsupportedOperationException.class)
    public void routePoints_isUnmodifiable() {
        TrackingState state = TrackingState.idle().toBuilder()
                .routePoints(Arrays.asList(new LatLng(40.0, -3.0)))
                .build();

        state.getRoutePoints().add(new LatLng(41.0, -3.5));
    }

    // ── Ciclo de vida completo ──────────────────────────────────────────────

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
}

