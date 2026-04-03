package com.proyecto.moveon.data.activities.dto;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * DTO enviado al endpoint de diagnóstico de actividad.
 *
 * <p>Solo se usa en builds internas con telemetría activada. Se mantiene separado
 * del DTO de negocio normal para no contaminar el flujo principal de actividades.</p>
 */
public final class ActivityDiagnosticsRequestDto {

    @Nullable public Integer actividadId;
    @Nullable public String actividadLocalId;

    @Nullable public String sessionStartedAt;
    @Nullable public String sessionFinishedAt;
    @Nullable public String lastTimerTickAt;
    @Nullable public String serviceCreatedAt;
    @Nullable public String serviceDestroyedAt;

    public int elapsedSeconds;
    public int movingSeconds;
    public int stoppedSeconds;
    public int manualPauseSeconds;

    public int distanceMeters;
    public int averagePaceTotal;
    public int averagePaceMoving;
    public int maxPace;

    public int autoPauses;
    public int manualPauses;
    public int speedAlerts;

    public int runningClassifiedSeconds;
    public int walkingClassifiedSeconds;
    public int serviceRestartCount;

    @Nullable public String currentStatus;
    @Nullable public String appVersion;
    @Nullable public String osVersion;
    @Nullable public String manufacturer;
    @Nullable public String model;
    @Nullable public Map<String, Object> deviceInfo;

    public final List<EventItem> eventLog = new ArrayList<>();

    /**
     * Línea temporal compacta de eventos del servicio.
     */
    public static final class EventItem {
        @Nullable public String at;
        @Nullable public String tipo;
        @Nullable public String detalle;
    }
}
