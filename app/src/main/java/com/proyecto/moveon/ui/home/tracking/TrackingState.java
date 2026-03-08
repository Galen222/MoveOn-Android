package com.proyecto.moveon.ui.home.tracking;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.maps.model.LatLng;

import java.util.Collections;
import java.util.List;

/**
 * Estado inmutable del módulo de tracking.
 *
 * Para modificar el estado se usa el {@link Builder}.
 *
 * Ciclo de vida: IDLE → RUNNING → PAUSED → RUNNING → FINISHED
 */
public final class TrackingState {

    // -------------------------------------------------------------------------
    // Enums
    // -------------------------------------------------------------------------

    public enum Status {
        /** Sin actividad en curso. */
        IDLE,
        /** Grabando ubicación y acelerómetro. */
        RUNNING,
        /** Pausado por el usuario. */
        PAUSED,
        /** Actividad finalizada, lista para guardar. */
        FINISHED
    }

    public enum ActivityType {
        WALKING,
        RUNNING_ACTIVITY
    }

    // -------------------------------------------------------------------------
    // Campos (todos inmutables)
    // -------------------------------------------------------------------------

    @NonNull  private final Status       status;
    @NonNull  private final ActivityType activityType;
    private   final long                 elapsedSeconds;
    private   final int                  distanceMeters;
    private   final int                  calories;
    @NonNull  private final List<LatLng> routePoints;
    @Nullable private final String       encodedPolyline;

    // -------------------------------------------------------------------------
    // Constructor privado — solo accesible desde Builder
    // -------------------------------------------------------------------------

    private TrackingState(
            @NonNull  Status       status,
            @NonNull  ActivityType activityType,
            long                   elapsedSeconds,
            int                    distanceMeters,
            int                    calories,
            @NonNull  List<LatLng> routePoints,
            @Nullable String       encodedPolyline) {

        this.status          = status;
        this.activityType    = activityType;
        this.elapsedSeconds  = elapsedSeconds;
        this.distanceMeters  = distanceMeters;
        this.calories        = calories;
        this.routePoints     = Collections.unmodifiableList(routePoints);
        this.encodedPolyline = encodedPolyline;
    }

    // -------------------------------------------------------------------------
    // Estado inicial
    // -------------------------------------------------------------------------

    @NonNull
    public static TrackingState idle() {
        return new Builder().build();
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    @NonNull  public Status       getStatus()          { return status; }
    @NonNull  public ActivityType getActivityType()    { return activityType; }
    public    long                getElapsedSeconds()  { return elapsedSeconds; }
    public    int                 getDistanceMeters()  { return distanceMeters; }
    public    int                 getCalories()        { return calories; }
    @NonNull  public List<LatLng> getRoutePoints()     { return routePoints; }
    @Nullable public String       getEncodedPolyline() { return encodedPolyline; }

    // -------------------------------------------------------------------------
    // Helpers de conveniencia
    // -------------------------------------------------------------------------

    public boolean isIdle()     { return status == Status.IDLE; }
    public boolean isRunning()  { return status == Status.RUNNING; }
    public boolean isPaused()   { return status == Status.PAUSED; }
    public boolean isFinished() { return status == Status.FINISHED; }

    /** true si hay una actividad en curso (grabando o pausada). */
    public boolean isActive()   { return status == Status.RUNNING || status == Status.PAUSED; }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    @NonNull
    public Builder toBuilder() {
        return new Builder(this);
    }

    public static final class Builder {

        @NonNull  private Status       status          = Status.IDLE;
        @NonNull  private ActivityType activityType    = ActivityType.WALKING;
        private   long                 elapsedSeconds  = 0L;
        private   int                  distanceMeters  = 0;
        private   int                  calories        = 0;
        @NonNull  private List<LatLng> routePoints     = Collections.emptyList();
        @Nullable private String       encodedPolyline = null;

        public Builder() {}

        private Builder(@NonNull TrackingState s) {
            this.status          = s.status;
            this.activityType    = s.activityType;
            this.elapsedSeconds  = s.elapsedSeconds;
            this.distanceMeters  = s.distanceMeters;
            this.calories        = s.calories;
            this.routePoints     = s.routePoints;
            this.encodedPolyline = s.encodedPolyline;
        }

        public Builder status(@NonNull Status v)              { this.status          = v; return this; }
        public Builder activityType(@NonNull ActivityType v)  { this.activityType    = v; return this; }
        public Builder elapsedSeconds(long v)                 { this.elapsedSeconds  = v; return this; }
        public Builder distanceMeters(int v)                  { this.distanceMeters  = v; return this; }
        public Builder calories(int v)                        { this.calories        = v; return this; }
        public Builder routePoints(@NonNull List<LatLng> v)   { this.routePoints     = v; return this; }
        public Builder encodedPolyline(@Nullable String v)    { this.encodedPolyline = v; return this; }

        @NonNull
        public TrackingState build() {
            return new TrackingState(
                    status, activityType, elapsedSeconds,
                    distanceMeters, calories, routePoints, encodedPolyline);
        }
    }
}