
package com.proyecto.moveon.ui.home.tracking;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;
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
    private final long autoPausedSeconds;
    private final long manualPausedSeconds;
    private final int distanceMeters;
    private final double preciseDistanceMeters;
    private final int calories;
    @Nullable private final String pace;
    @Nullable private final String averageMovingPace;
    @Nullable private final String averageElapsedPace;
    @Nullable private final String maxPace;
    private final int maxSpeedKmhX100;
    private final int autoPauseCount;
    private final int manualPauseCount;
    private final int suspiciousSpeedEventCount;
    @NonNull private final List<LatLng> routePoints;
    @Nullable private final LatLng currentLocation;
    @Nullable private final String encodedPolyline;
    private final long runningClassifiedSeconds;
    private final long walkingClassifiedSeconds;
    private final long sessionStartedAtEpochMs;
    private final long sessionFinishedAtEpochMs;
    private final long lastTimerTickAtEpochMs;
    private final long serviceCreatedAtEpochMs;
    private final long serviceDestroyedAtEpochMs;
    private final int serviceRestartCount;
    @NonNull private final List<DiagnosticEvent> diagnosticEvents;

    private TrackingState(
            @NonNull Status status,
            @NonNull PauseReason pauseReason,
            @NonNull ActivityType activityType,
            long elapsedSeconds,
            long movingSeconds,
            long stoppedSeconds,
            long autoPausedSeconds,
            long manualPausedSeconds,
            int distanceMeters,
            double preciseDistanceMeters,
            int calories,
            @Nullable String pace,
            @Nullable String averageMovingPace,
            @Nullable String averageElapsedPace,
            @Nullable String maxPace,
            int maxSpeedKmhX100,
            int autoPauseCount,
            int manualPauseCount,
            int suspiciousSpeedEventCount,
            @NonNull List<LatLng> routePoints,
            @Nullable LatLng currentLocation,
            @Nullable String encodedPolyline,
            long runningClassifiedSeconds,
            long walkingClassifiedSeconds,
            long sessionStartedAtEpochMs,
            long sessionFinishedAtEpochMs,
            long lastTimerTickAtEpochMs,
            long serviceCreatedAtEpochMs,
            long serviceDestroyedAtEpochMs,
            int serviceRestartCount,
            @NonNull List<DiagnosticEvent> diagnosticEvents) {
        this.status = status;
        this.pauseReason = pauseReason;
        this.activityType = activityType;
        this.elapsedSeconds = elapsedSeconds;
        this.movingSeconds = movingSeconds;
        this.stoppedSeconds = stoppedSeconds;
        this.autoPausedSeconds = autoPausedSeconds;
        this.manualPausedSeconds = manualPausedSeconds;
        this.distanceMeters = distanceMeters;
        this.preciseDistanceMeters = preciseDistanceMeters;
        this.calories = calories;
        this.pace = pace;
        this.averageMovingPace = averageMovingPace;
        this.averageElapsedPace = averageElapsedPace;
        this.maxPace = maxPace;
        this.maxSpeedKmhX100 = maxSpeedKmhX100;
        this.autoPauseCount = autoPauseCount;
        this.manualPauseCount = manualPauseCount;
        this.suspiciousSpeedEventCount = suspiciousSpeedEventCount;
        this.routePoints = Collections.unmodifiableList(routePoints);
        this.currentLocation = currentLocation;
        this.encodedPolyline = encodedPolyline;
        this.runningClassifiedSeconds = runningClassifiedSeconds;
        this.walkingClassifiedSeconds = walkingClassifiedSeconds;
        this.sessionStartedAtEpochMs = sessionStartedAtEpochMs;
        this.sessionFinishedAtEpochMs = sessionFinishedAtEpochMs;
        this.lastTimerTickAtEpochMs = lastTimerTickAtEpochMs;
        this.serviceCreatedAtEpochMs = serviceCreatedAtEpochMs;
        this.serviceDestroyedAtEpochMs = serviceDestroyedAtEpochMs;
        this.serviceRestartCount = serviceRestartCount;
        this.diagnosticEvents = Collections.unmodifiableList(new ArrayList<>(diagnosticEvents));
    }

    /**
     * Crea un estado inicial vacío listo para representar una sesión de tracking aún no iniciada.
     *
     * @return snapshot base construido con los valores por defecto del {@link Builder}.
     */
    @NonNull
    public static TrackingState idle() {
        return new Builder().build();
    }

    /**
     * Devuelve el estado principal actual de la sesión.
     *
     * @return estado resumido con el que la UI decide si la sesión está inactiva, corriendo o pausada.
     */
    @NonNull public Status getStatus() { return status; }
    /**
     * Devuelve el motivo de pausa actualmente registrado.
     *
     * @return causa de la pausa activa o {@link PauseReason#NONE} cuando no aplica.
     */
    @NonNull public PauseReason getPauseReason() { return pauseReason; }
    /**
     * Devuelve el tipo de actividad clasificado en tiempo real.
     *
     * @return clasificación actual usada para métricas y feedback de tracking.
     */
    @NonNull public ActivityType getActivityType() { return activityType; }
    /**
     * Devuelve el tiempo total transcurrido desde que comenzó la sesión.
     *
     * @return segundos totales desde el inicio, incluyendo pausas.
     */
    public long getElapsedSeconds() { return elapsedSeconds; }
    /**
     * Devuelve los segundos acumulados con movimiento válido detectado.
     *
     * @return segundos contabilizados como desplazamiento efectivo.
     */
    public long getMovingSeconds() { return movingSeconds; }
    /**
     * Devuelve los segundos acumulados en parada sin contar pausas manuales.
     *
     * @return tiempo detenido que la lógica interna atribuye a falta de movimiento.
     */
    public long getStoppedSeconds() { return stoppedSeconds; }
    /**
     * Devuelve los segundos acumulados en auto-pausa.
     *
     * @return tiempo total en auto-pausa por reglas internas del servicio.
     */
    public long getAutoPausedSeconds() { return autoPausedSeconds; }
    /**
     * Devuelve los segundos acumulados en pausa manual.
     *
     * @return tiempo pausado explícitamente por el usuario.
     */
    public long getManualPausedSeconds() { return manualPausedSeconds; }
    /**
     * Devuelve la distancia redondeada en metros usada por la UI principal.
     *
     * @return distancia acumulada ya preparada para visualización rápida.
     */
    public int getDistanceMeters() { return distanceMeters; }
    /**
     * Devuelve la distancia precisa acumulada en metros con decimales.
     *
     * @return distancia con mayor precisión, útil para recálculos y persistencia.
     */
    public double getPreciseDistanceMeters() { return preciseDistanceMeters; }
    /**
     * Devuelve el tiempo efectivo de sesión sumando movimiento y parada, pero excluyendo pausas explícitas.
     *
     * @return segundos efectivos de seguimiento capturados por el servicio.
     */
    public long getEffectiveElapsedSeconds() { return movingSeconds + stoppedSeconds; }
    /**
     * Devuelve las calorías estimadas acumuladas.
     *
     * @return gasto calórico aproximado asociado a la sesión actual.
     */
    public int getCalories() { return calories; }
    /**
     * Devuelve el ritmo instantáneo o principal calculado para la sesión.
     *
     * @return ritmo visible actual o {@code null} si todavía no puede estimarse.
     */
    @Nullable public String getPace() { return pace; }
    /**
     * Devuelve el ritmo medio considerando solo el tiempo en movimiento.
     *
     * @return ritmo medio en movimiento o {@code null} si faltan datos suficientes.
     */
    @Nullable public String getAverageMovingPace() { return averageMovingPace; }
    /**
     * Devuelve el ritmo medio calculado sobre todo el tiempo transcurrido.
     *
     * @return ritmo medio global de la sesión o {@code null} si aún no procede mostrarlo.
     */
    @Nullable public String getAverageElapsedPace() { return averageElapsedPace; }
    /**
     * Devuelve el mejor ritmo máximo alcanzado durante la sesión.
     *
     * @return mejor ritmo registrado o {@code null} cuando todavía no existe un máximo válido.
     */
    @Nullable public String getMaxPace() { return maxPace; }
    /**
     * Devuelve la velocidad máxima en km/h multiplicada por 100 para evitar pérdidas al persistirla.
     *
     * @return velocidad máxima escalada x100 para almacenamiento preciso.
     */
    public int getMaxSpeedKmhX100() { return maxSpeedKmhX100; }
    /**
     * Devuelve cuántas auto-pausas se detectaron en la sesión.
     *
     * @return contador acumulado de auto-pausas.
     */
    public int getAutoPauseCount() { return autoPauseCount; }
    /**
     * Devuelve cuántas pausas manuales realizó el usuario.
     *
     * @return número de pausas manuales confirmadas.
     */
    public int getManualPauseCount() { return manualPauseCount; }
    /**
     * Devuelve el número de eventos marcados como velocidad sospechosa.
     *
     * @return contador de alertas de velocidad anómala detectadas por el servicio.
     */
    public int getSuspiciousSpeedEventCount() { return suspiciousSpeedEventCount; }
    /**
     * Devuelve la ruta capturada como lista inmutable de puntos.
     *
     * @return snapshot inmutable del trazado registrado hasta este momento.
     */
    @NonNull public List<LatLng> getRoutePoints() { return routePoints; }
    /**
     * Devuelve la última ubicación conocida asociada al snapshot.
     *
     * @return último punto aceptado o {@code null} si aún no hay una posición válida.
     */
    @Nullable public LatLng getCurrentLocation() { return currentLocation; }
    /**
     * Devuelve la polilínea codificada lista para persistencia o compartición.
     *
     * @return polilínea codificada o {@code null} si aún no se ha generado.
     */
    @Nullable public String getEncodedPolyline() { return encodedPolyline; }
    /**
     * Devuelve los segundos clasificados como carrera.
     *
     * @return tiempo acumulado que el clasificador interpreta como carrera.
     */
    public long getRunningClassifiedSeconds() { return runningClassifiedSeconds; }
    /**
     * Devuelve los segundos clasificados como caminata.
     *
     * @return tiempo acumulado que el clasificador interpreta como caminata.
     */
    public long getWalkingClassifiedSeconds() { return walkingClassifiedSeconds; }
    /**
     * Devuelve el instante epoch en milisegundos en el que comenzó la sesión.
     *
     * @return marca temporal del inicio o {@code 0} si nunca llegó a arrancar.
     */
    public long getSessionStartedAtEpochMs() { return sessionStartedAtEpochMs; }
    /**
     * Devuelve el instante epoch en milisegundos en el que finalizó la sesión, si ya terminó.
     *
     * @return marca temporal del cierre o {@code 0} mientras la sesión siga abierta.
     */
    public long getSessionFinishedAtEpochMs() { return sessionFinishedAtEpochMs; }
    /**
     * Devuelve el momento del último tick del temporizador interno.
     *
     * @return instante del último refresco temporal publicado por el servicio.
     */
    public long getLastTimerTickAtEpochMs() { return lastTimerTickAtEpochMs; }
    /**
     * Devuelve el instante en que se creó el servicio de tracking asociado al snapshot.
     *
     * @return epoch ms de creación del servicio o {@code 0} si no se ha informado.
     */
    public long getServiceCreatedAtEpochMs() { return serviceCreatedAtEpochMs; }
    /**
     * Devuelve el instante en que se destruyó el servicio de tracking, si aplica.
     *
     * @return epoch ms de destrucción o {@code 0} si el servicio no se ha destruido aún.
     */
    public long getServiceDestroyedAtEpochMs() { return serviceDestroyedAtEpochMs; }
    /**
     * Devuelve cuántas veces se reinició el servicio durante la sesión.
     *
     * @return número acumulado de recreaciones del servicio.
     */
    public int getServiceRestartCount() { return serviceRestartCount; }
    /**
     * Devuelve los eventos de diagnóstico acumulados como lista inmutable.
     *
     * @return historial de eventos técnicos asociados a la sesión.
     */
    @NonNull public List<DiagnosticEvent> getDiagnosticEvents() { return diagnosticEvents; }

    /**
     * Indica si la sesión todavía no se ha iniciado.
     *
     * @return {@code true} cuando el snapshot representa un estado inicial sin tracking activo.
     */
    public boolean isIdle() { return status == Status.IDLE; }
    /**
     * Indica si la sesión está capturando actividad sin estar pausada.
     *
     * @return {@code true} cuando el servicio está siguiendo movimiento en tiempo real.
     */
    public boolean isRunning() { return status == Status.RUNNING; }
    /**
     * Indica si la sesión está en cualquier tipo de pausa.
     *
     * @return {@code true} para pausas manuales y auto-pausas.
     */
    public boolean isPaused() { return status == Status.PAUSED || status == Status.AUTO_PAUSED; }
    /**
     * Indica si la pausa actual fue iniciada manualmente por el usuario.
     *
     * @return {@code true} cuando el estado activo es una pausa manual.
     */
    public boolean isManuallyPaused() { return status == Status.PAUSED; }
    /**
     * Indica si la sesión está auto-pausada por la lógica interna de tracking.
     *
     * @return {@code true} cuando el servicio frenó la captura automáticamente.
     */
    public boolean isAutoPaused() { return status == Status.AUTO_PAUSED; }
    /**
     * Indica si la sesión ya se dio por finalizada.
     *
     * @return {@code true} cuando el tracking ya cerró definitivamente la actividad.
     */
    public boolean isFinished() { return status == Status.FINISHED; }
    /**
     * Indica si la sesión sigue viva aunque esté temporalmente pausada.
     *
     * @return {@code true} para estados que todavía permiten reanudar o cerrar la sesión activa.
     */
    public boolean isActive() {
        return status == Status.RUNNING || status == Status.PAUSED || status == Status.AUTO_PAUSED;
    }

    /**
     * Crea un builder mutable precargado con todos los valores del snapshot actual.
     *
     * @return nuevo {@link Builder} inicializado con la copia exacta del estado actual.
     */
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
        private long autoPausedSeconds = 0L;
        private long manualPausedSeconds = 0L;
        private int distanceMeters = 0;
        private double preciseDistanceMeters = 0.0;
        private int calories = 0;
        @Nullable private String pace = null;
        @Nullable private String averageMovingPace = null;
        @Nullable private String averageElapsedPace = null;
        @Nullable private String maxPace = null;
        private int maxSpeedKmhX100 = 0;
        private int autoPauseCount = 0;
        private int manualPauseCount = 0;
        private int suspiciousSpeedEventCount = 0;
        @NonNull private List<LatLng> routePoints = Collections.emptyList();
        @Nullable private LatLng currentLocation = null;
        @Nullable private String encodedPolyline = null;
        private long runningClassifiedSeconds = 0L;
        private long walkingClassifiedSeconds = 0L;
        private long sessionStartedAtEpochMs = 0L;
        private long sessionFinishedAtEpochMs = 0L;
        private long lastTimerTickAtEpochMs = 0L;
        private long serviceCreatedAtEpochMs = 0L;
        private long serviceDestroyedAtEpochMs = 0L;
        private int serviceRestartCount = 0;
        @NonNull private List<DiagnosticEvent> diagnosticEvents = Collections.emptyList();

        /**
         * Crea un builder inicializado con los valores por defecto del estado inactivo.
         */
        public Builder() {
        }

        /**
         * Copia todos los campos de un estado existente para poder modificar solo los necesarios.
         */
        private Builder(@NonNull TrackingState source) {
            status = source.status;
            pauseReason = source.pauseReason;
            activityType = source.activityType;
            elapsedSeconds = source.elapsedSeconds;
            movingSeconds = source.movingSeconds;
            stoppedSeconds = source.stoppedSeconds;
            autoPausedSeconds = source.autoPausedSeconds;
            manualPausedSeconds = source.manualPausedSeconds;
            distanceMeters = source.distanceMeters;
            preciseDistanceMeters = source.preciseDistanceMeters;
            calories = source.calories;
            pace = source.pace;
            averageMovingPace = source.averageMovingPace;
            averageElapsedPace = source.averageElapsedPace;
            maxPace = source.maxPace;
            maxSpeedKmhX100 = source.maxSpeedKmhX100;
            autoPauseCount = source.autoPauseCount;
            manualPauseCount = source.manualPauseCount;
            suspiciousSpeedEventCount = source.suspiciousSpeedEventCount;
            routePoints = source.routePoints;
            currentLocation = source.currentLocation;
            encodedPolyline = source.encodedPolyline;
            runningClassifiedSeconds = source.runningClassifiedSeconds;
            walkingClassifiedSeconds = source.walkingClassifiedSeconds;
            sessionStartedAtEpochMs = source.sessionStartedAtEpochMs;
            sessionFinishedAtEpochMs = source.sessionFinishedAtEpochMs;
            lastTimerTickAtEpochMs = source.lastTimerTickAtEpochMs;
            serviceCreatedAtEpochMs = source.serviceCreatedAtEpochMs;
            serviceDestroyedAtEpochMs = source.serviceDestroyedAtEpochMs;
            serviceRestartCount = source.serviceRestartCount;
            diagnosticEvents = source.diagnosticEvents;
        }

        /**
         * Actualiza el estado principal de la sesión en construcción.
         */
        public Builder status(@NonNull Status value) { this.status = value; return this; }
        /**
         * Actualiza el motivo de pausa asociado al estado en construcción.
         */
        public Builder pauseReason(@NonNull PauseReason value) { this.pauseReason = value; return this; }
        /**
         * Define el tipo de actividad clasificado para el snapshot resultante.
         */
        public Builder activityType(@NonNull ActivityType value) { this.activityType = value; return this; }
        /**
         * Fija el tiempo total transcurrido en segundos.
         */
        public Builder elapsedSeconds(long value) { this.elapsedSeconds = value; return this; }
        /**
         * Fija los segundos acumulados con movimiento válido.
         */
        public Builder movingSeconds(long value) { this.movingSeconds = value; return this; }
        /**
         * Fija los segundos acumulados en parada.
         */
        public Builder stoppedSeconds(long value) { this.stoppedSeconds = value; return this; }
        /**
         * Fija los segundos acumulados en auto-pausa.
         */
        public Builder autoPausedSeconds(long value) { this.autoPausedSeconds = value; return this; }
        /**
         * Fija los segundos acumulados en pausa manual.
         */
        public Builder manualPausedSeconds(long value) { this.manualPausedSeconds = value; return this; }
        /**
         * Fija la distancia redondeada en metros.
         */
        public Builder distanceMeters(int value) { this.distanceMeters = value; return this; }
        /**
         * Fija la distancia precisa acumulada en metros.
         */
        public Builder preciseDistanceMeters(double value) { this.preciseDistanceMeters = value; return this; }
        /**
         * Fija la estimación de calorías del snapshot.
         */
        public Builder calories(int value) { this.calories = value; return this; }
        /**
         * Fija el ritmo principal calculado para la sesión.
         */
        public Builder pace(@Nullable String value) { this.pace = value; return this; }
        /**
         * Fija el ritmo medio en movimiento.
         */
        public Builder averageMovingPace(@Nullable String value) { this.averageMovingPace = value; return this; }
        /**
         * Fija el ritmo medio sobre el tiempo total transcurrido.
         */
        public Builder averageElapsedPace(@Nullable String value) { this.averageElapsedPace = value; return this; }
        /**
         * Fija el mejor ritmo máximo alcanzado.
         */
        public Builder maxPace(@Nullable String value) { this.maxPace = value; return this; }
        /**
         * Fija la velocidad máxima expresada en km/h x100.
         */
        public Builder maxSpeedKmhX100(int value) { this.maxSpeedKmhX100 = value; return this; }
        /**
         * Fija el contador de auto-pausas detectadas.
         */
        public Builder autoPauseCount(int value) { this.autoPauseCount = value; return this; }
        /**
         * Fija el contador de pausas manuales realizadas.
         */
        public Builder manualPauseCount(int value) { this.manualPauseCount = value; return this; }
        /**
         * Fija el número de eventos de velocidad sospechosa detectados.
         */
        public Builder suspiciousSpeedEventCount(int value) { this.suspiciousSpeedEventCount = value; return this; }
        /**
         * Fija la colección de puntos que compone la ruta.
         */
        public Builder routePoints(@NonNull List<LatLng> value) { this.routePoints = value; return this; }
        /**
         * Fija la última ubicación conocida del snapshot.
         */
        public Builder currentLocation(@Nullable LatLng value) { this.currentLocation = value; return this; }
        /**
         * Fija la polilínea codificada usada para persistencia o compartición.
         */
        public Builder encodedPolyline(@Nullable String value) { this.encodedPolyline = value; return this; }
        /**
         * Fija los segundos clasificados como carrera.
         */
        public Builder runningClassifiedSeconds(long value) { this.runningClassifiedSeconds = value; return this; }
        /**
         * Fija los segundos clasificados como caminata.
         */
        public Builder walkingClassifiedSeconds(long value) { this.walkingClassifiedSeconds = value; return this; }
        /**
         * Fija el instante de inicio de sesión en epoch ms.
         */
        public Builder sessionStartedAtEpochMs(long value) { this.sessionStartedAtEpochMs = value; return this; }
        /**
         * Fija el instante de finalización de sesión en epoch ms.
         */
        public Builder sessionFinishedAtEpochMs(long value) { this.sessionFinishedAtEpochMs = value; return this; }
        /**
         * Fija el instante del último tick del temporizador interno.
         */
        public Builder lastTimerTickAtEpochMs(long value) { this.lastTimerTickAtEpochMs = value; return this; }
        /**
         * Fija el instante de creación del servicio asociado.
         */
        public Builder serviceCreatedAtEpochMs(long value) { this.serviceCreatedAtEpochMs = value; return this; }
        /**
         * Fija el instante de destrucción del servicio asociado.
         */
        public Builder serviceDestroyedAtEpochMs(long value) { this.serviceDestroyedAtEpochMs = value; return this; }
        /**
         * Fija el número de reinicios del servicio.
         */
        public Builder serviceRestartCount(int value) { this.serviceRestartCount = value; return this; }
        /**
         * Fija la lista de eventos diagnósticos que acompañarán al snapshot.
         */
        public Builder diagnosticEvents(@NonNull List<DiagnosticEvent> value) { this.diagnosticEvents = value; return this; }

        /**
         * Materializa un snapshot inmutable con todos los valores acumulados en el builder.
         */
        @NonNull
        public TrackingState build() {
            return new TrackingState(
                    status,
                    pauseReason,
                    activityType,
                    elapsedSeconds,
                    movingSeconds,
                    stoppedSeconds,
                    autoPausedSeconds,
                    manualPausedSeconds,
                    distanceMeters,
                    preciseDistanceMeters,
                    calories,
                    pace,
                    averageMovingPace,
                    averageElapsedPace,
                    maxPace,
                    maxSpeedKmhX100,
                    autoPauseCount,
                    manualPauseCount,
                    suspiciousSpeedEventCount,
                    routePoints,
                    currentLocation,
                    encodedPolyline,
                    runningClassifiedSeconds,
                    walkingClassifiedSeconds,
                    sessionStartedAtEpochMs,
                    sessionFinishedAtEpochMs,
                    lastTimerTickAtEpochMs,
                    serviceCreatedAtEpochMs,
                    serviceDestroyedAtEpochMs,
                    serviceRestartCount,
                    diagnosticEvents
            );
        }
    }

    /**
     * Evento inmutable de diagnóstico de tracking.
     *
     * <p>Se usa para reconstruir qué ocurrió en sesiones problemáticas
     * probadas por testers remotos sin acceso físico al dispositivo.</p>
     */
    public static final class DiagnosticEvent {
        private final long atEpochMs;
        @NonNull private final String type;
        @Nullable private final String detail;

        /**
         * Crea un evento de diagnóstico puntual con fecha, tipo y detalle opcional.
         */
        public DiagnosticEvent(long atEpochMs, @NonNull String type, @Nullable String detail) {
            this.atEpochMs = atEpochMs;
            this.type = type;
            this.detail = detail;
        }

        /**
         * Devuelve el instante exacto del evento en epoch ms.
         */
        public long getAtEpochMs() { return atEpochMs; }
        /**
         * Devuelve el tipo compacto del evento de diagnóstico.
         */
        @NonNull public String getType() { return type; }
        /**
         * Devuelve el detalle adicional del evento, si se registró.
         */
        @Nullable public String getDetail() { return detail; }
    }
}
