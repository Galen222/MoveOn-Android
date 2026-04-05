package com.proyecto.moveon.data.activities.dto;

import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * DTO enviado al endpoint de diagnóstico de actividad.
 *
 * <p>Solo se usa en builds internas con telemetría activada. Se mantiene separado
 * del DTO de negocio normal para no contaminar el flujo principal de actividades.</p>
 *
 * <p>Importante: el backend espera este payload en {@code snake_case}. Los nombres de
 * los campos Java se mantienen en {@code camelCase} para no romper el código Android,
 * pero la serialización se fuerza con {@link SerializedName} para que el diagnóstico
 * llegue completo y no se persistan {@code NULL} o valores por defecto por desajuste
 * de nombres.</p>
 */
public final class ActivityDiagnosticsRequestDto {

    /** Identificador remoto de la actividad si ya existe. */
    @SerializedName("actividad_id")
    @Nullable public Integer actividadId;

    /** Identificador local temporal usado antes del alta remota. */
    @SerializedName("actividad_local_id")
    @Nullable public String actividadLocalId;

    /** Marca temporal ISO del inicio de la sesión. */
    @SerializedName("session_started_at")
    @Nullable public String sessionStartedAt;

    /** Marca temporal ISO del fin de la sesión. */
    @SerializedName("session_finished_at")
    @Nullable public String sessionFinishedAt;

    /** Marca temporal ISO del último tick del temporizador. */
    @SerializedName("last_timer_tick_at")
    @Nullable public String lastTimerTickAt;

    /** Marca temporal ISO de creación del servicio foreground. */
    @SerializedName("service_created_at")
    @Nullable public String serviceCreatedAt;

    /** Marca temporal ISO de destrucción del servicio foreground. */
    @SerializedName("service_destroyed_at")
    @Nullable public String serviceDestroyedAt;

    /** Tiempo total transcurrido en segundos. */
    @SerializedName("elapsed_seconds")
    public int elapsedSeconds;

    /** Tiempo clasificado como movimiento real en segundos. */
    @SerializedName("moving_seconds")
    public int movingSeconds;

    /** Tiempo clasificado como parado en segundos. */
    @SerializedName("stopped_seconds")
    public int stoppedSeconds;

    /** Tiempo en pausa manual en segundos. */
    @SerializedName("manual_pause_seconds")
    public int manualPauseSeconds;

    /** Distancia total acumulada en metros. */
    @SerializedName("distance_meters")
    public int distanceMeters;

    /** Ritmo medio total en segundos por kilómetro. */
    @SerializedName("average_pace_total")
    public int averagePaceTotal;

    /** Ritmo medio en movimiento en segundos por kilómetro. */
    @SerializedName("average_pace_moving")
    public int averagePaceMoving;

    /** Mejor ritmo válido detectado en segundos por kilómetro. */
    @SerializedName("max_pace")
    public int maxPace;

    /** Número de auto-pausas disparadas durante la sesión. */
    @SerializedName("auto_pauses")
    public int autoPauses;

    /** Número de pausas manuales iniciadas por el usuario. */
    @SerializedName("manual_pauses")
    public int manualPauses;

    /** Número de alertas por velocidad sospechosa. */
    @SerializedName("speed_alerts")
    public int speedAlerts;

    /** Segundos clasificados como carrera. */
    @SerializedName("running_classified_seconds")
    public int runningClassifiedSeconds;

    /** Segundos clasificados como caminata. */
    @SerializedName("walking_classified_seconds")
    public int walkingClassifiedSeconds;

    /** Número de recreaciones del servicio durante la actividad. */
    @SerializedName("service_restart_count")
    public int serviceRestartCount;

    /** Estado actual textual del tracking. */
    @SerializedName("current_status")
    @Nullable public String currentStatus;

    /** Versión visible de la app instalada. */
    @SerializedName("app_version")
    @Nullable public String appVersion;

    /** Versión del sistema operativo del dispositivo. */
    @SerializedName("os_version")
    @Nullable public String osVersion;

    /** Fabricante del terminal. Coincide en ambos formatos y se anota por consistencia. */
    @SerializedName("manufacturer")
    @Nullable public String manufacturer;

    /** Modelo del terminal. Coincide en ambos formatos y se anota por consistencia. */
    @SerializedName("model")
    @Nullable public String model;

    /** Bolsa flexible de metadatos adicionales del dispositivo. */
    @SerializedName("device_info")
    @Nullable public Map<String, Object> deviceInfo;

    /** Línea temporal compacta de eventos emitidos por el servicio. */
    @SerializedName("event_log")
    public final List<EventItem> eventLog = new ArrayList<>();

    /**
     * Línea temporal compacta de eventos del servicio.
     */
    public static final class EventItem {

        /** Marca temporal ISO del evento. */
        @SerializedName("at")
        @Nullable public String at;

        /** Tipo canónico del evento. */
        @SerializedName("tipo")
        @Nullable public String tipo;

        /** Detalle libre asociado al evento. */
        @SerializedName("detalle")
        @Nullable public String detalle;
    }
}
