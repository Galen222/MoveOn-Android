package com.proyecto.moveon.ui.home.tracking;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.maps.model.LatLng;

import java.util.Collections;
import java.util.List;

/**
 * Estado inmutable del módulo de tracking.
 *
 * <p>Se amplía para distinguir pausa manual, auto-pausa por parada y
 * métricas correctas de movimiento. Los campos numéricos se almacenan en
 * unidades simples para facilitar persistencia offline y recomputación.</p>
 */
@Keep
@SuppressWarnings("unused")
public final class TrackingState {

    /**
     * Estado principal de la sesión.
     */
    public enum Status {
        IDLE,
        RUNNING,
        PAUSED,
        AUTO_PAUSED,
        FINISHED
    }

    /**
     * Tipo de actividad estimado en tiempo real.
     */
    public enum ActivityType {
        WALKING,
        RUNNING_ACTIVITY
    }

    /**
     * Motivo de la pausa actual, si existe.
     */
    public enum PauseReason {
        NONE,
        MANUAL,
        STATIONARY,
        SUSPICIOUS_SPEED
    }

    @NonNull private final Status status;
    @NonNull private final PauseReason pauseReason;
    @NonNull private final ActivityType activityType;
    private final long elapsedSeconds;
    private final long movingSeconds;
    private final long stoppedSeconds;
    private final long manualPausedSeconds;
    private final int distanceMeters;
    private final int calories;
    @Nullable private final String pace;
    @Nullable private final String averageMovingPace;
    @Nullable private final String averageElapsedPace;
    private final int maxSpeedKmhX100;
    private final int autoPauseCount;
    private final int manualPauseCount;
    private final int suspiciousSpeedEventCount;
    @NonNull private final List<LatLng> routePoints;
    @Nullable private final String encodedPolyline;

    private TrackingState(
            @NonNull Status status,
            @NonNull PauseReason pauseReason,
            @NonNull ActivityType activityType,
            long elapsedSeconds,
            long movingSeconds,
            long stoppedSeconds,
            long manualPausedSeconds,
            int distanceMeters,
            int calories,
            @Nullable String pace,
            @Nullable String averageMovingPace,
            @Nullable String averageElapsedPace,
            int maxSpeedKmhX100,
            int autoPauseCount,
            int manualPauseCount,
            int suspiciousSpeedEventCount,
            @NonNull List<LatLng> routePoints,
            @Nullable String encodedPolyline) {
        this.status = status;
        this.pauseReason = pauseReason;
        this.activityType = activityType;
        this.elapsedSeconds = elapsedSeconds;
        this.movingSeconds = movingSeconds;
        this.stoppedSeconds = stoppedSeconds;
        this.manualPausedSeconds = manualPausedSeconds;
        this.distanceMeters = distanceMeters;
        this.calories = calories;
        this.pace = pace;
        this.averageMovingPace = averageMovingPace;
        this.averageElapsedPace = averageElapsedPace;
        this.maxSpeedKmhX100 = maxSpeedKmhX100;
        this.autoPauseCount = autoPauseCount;
        this.manualPauseCount = manualPauseCount;
        this.suspiciousSpeedEventCount = suspiciousSpeedEventCount;
        this.routePoints = Collections.unmodifiableList(routePoints);
        this.encodedPolyline = encodedPolyline;
    }

    @NonNull
    public static TrackingState idle() {
        return new Builder().build();
    }

    @NonNull public Status getStatus() { return status; }
    @NonNull public PauseReason getPauseReason() { return pauseReason; }
    @NonNull public ActivityType getActivityType() { return activityType; }
    public long getElapsedSeconds() { return elapsedSeconds; }
    public long getMovingSeconds() { return movingSeconds; }
    public long getStoppedSeconds() { return stoppedSeconds; }
    public long getManualPausedSeconds() { return manualPausedSeconds; }
    public int getDistanceMeters() { return distanceMeters; }
    public int getCalories() { return calories; }
    @Nullable public String getPace() { return pace; }
    @Nullable public String getAverageMovingPace() { return averageMovingPace; }
    @Nullable public String getAverageElapsedPace() { return averageElapsedPace; }
    public int getMaxSpeedKmhX100() { return maxSpeedKmhX100; }
    public int getAutoPauseCount() { return autoPauseCount; }
    public int getManualPauseCount() { return manualPauseCount; }
    public int getSuspiciousSpeedEventCount() { return suspiciousSpeedEventCount; }
    @NonNull public List<LatLng> getRoutePoints() { return routePoints; }
    @Nullable public String getEncodedPolyline() { return encodedPolyline; }

    public boolean isIdle() { return status == Status.IDLE; }
    public boolean isRunning() { return status == Status.RUNNING; }
    public boolean isPaused() { return status == Status.PAUSED || status == Status.AUTO_PAUSED; }
    public boolean isManuallyPaused() { return status == Status.PAUSED; }
    public boolean isAutoPaused() { return status == Status.AUTO_PAUSED; }
    public boolean isFinished() { return status == Status.FINISHED; }
    public boolean isActive() {
        return status == Status.RUNNING || status == Status.PAUSED || status == Status.AUTO_PAUSED;
    }

    @NonNull
    public Builder toBuilder() {
        return new Builder(this);
    }

    /**
     * Builder mutable para construir snapshots inmutables.
     */
    public static final class Builder {

        @NonNull private Status status = Status.IDLE;
        @NonNull private PauseReason pauseReason = PauseReason.NONE;
        @NonNull private ActivityType activityType = ActivityType.WALKING;
        private long elapsedSeconds = 0L;
        private long movingSeconds = 0L;
        private long stoppedSeconds = 0L;
        private long manualPausedSeconds = 0L;
        private int distanceMeters = 0;
        private int calories = 0;
        @Nullable private String pace = null;
        @Nullable private String averageMovingPace = null;
        @Nullable private String averageElapsedPace = null;
        private int maxSpeedKmhX100 = 0;
        private int autoPauseCount = 0;
        private int manualPauseCount = 0;
        private int suspiciousSpeedEventCount = 0;
        @NonNull private List<LatLng> routePoints = Collections.emptyList();
        @Nullable private String encodedPolyline = null;

        public Builder() {
        }

        private Builder(@NonNull TrackingState source) {
            status = source.status;
            pauseReason = source.pauseReason;
            activityType = source.activityType;
            elapsedSeconds = source.elapsedSeconds;
            movingSeconds = source.movingSeconds;
            stoppedSeconds = source.stoppedSeconds;
            manualPausedSeconds = source.manualPausedSeconds;
            distanceMeters = source.distanceMeters;
            calories = source.calories;
            pace = source.pace;
            averageMovingPace = source.averageMovingPace;
            averageElapsedPace = source.averageElapsedPace;
            maxSpeedKmhX100 = source.maxSpeedKmhX100;
            autoPauseCount = source.autoPauseCount;
            manualPauseCount = source.manualPauseCount;
            suspiciousSpeedEventCount = source.suspiciousSpeedEventCount;
            routePoints = source.routePoints;
            encodedPolyline = source.encodedPolyline;
        }

        public Builder status(@NonNull Status value) { this.status = value; return this; }
        public Builder pauseReason(@NonNull PauseReason value) { this.pauseReason = value; return this; }
        public Builder activityType(@NonNull ActivityType value) { this.activityType = value; return this; }
        public Builder elapsedSeconds(long value) { this.elapsedSeconds = value; return this; }
        public Builder movingSeconds(long value) { this.movingSeconds = value; return this; }
        public Builder stoppedSeconds(long value) { this.stoppedSeconds = value; return this; }
        public Builder manualPausedSeconds(long value) { this.manualPausedSeconds = value; return this; }
        public Builder distanceMeters(int value) { this.distanceMeters = value; return this; }
        public Builder calories(int value) { this.calories = value; return this; }
        public Builder pace(@Nullable String value) { this.pace = value; return this; }
        public Builder averageMovingPace(@Nullable String value) { this.averageMovingPace = value; return this; }
        public Builder averageElapsedPace(@Nullable String value) { this.averageElapsedPace = value; return this; }
        public Builder maxSpeedKmhX100(int value) { this.maxSpeedKmhX100 = value; return this; }
        public Builder autoPauseCount(int value) { this.autoPauseCount = value; return this; }
        public Builder manualPauseCount(int value) { this.manualPauseCount = value; return this; }
        public Builder suspiciousSpeedEventCount(int value) { this.suspiciousSpeedEventCount = value; return this; }
        public Builder routePoints(@NonNull List<LatLng> value) { this.routePoints = value; return this; }
        public Builder encodedPolyline(@Nullable String value) { this.encodedPolyline = value; return this; }

        @NonNull
        public TrackingState build() {
            return new TrackingState(
                    status,
                    pauseReason,
                    activityType,
                    elapsedSeconds,
                    movingSeconds,
                    stoppedSeconds,
                    manualPausedSeconds,
                    distanceMeters,
                    calories,
                    pace,
                    averageMovingPace,
                    averageElapsedPace,
                    maxSpeedKmhX100,
                    autoPauseCount,
                    manualPauseCount,
                    suspiciousSpeedEventCount,
                    routePoints,
                    encodedPolyline
            );
        }
    }
}
