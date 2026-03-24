package com.proyecto.moveon.ui.home.tracking;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.RemoteViews;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.model.LatLng;
import com.google.maps.android.PolyUtil;
import com.proyecto.moveon.R;
import com.proyecto.moveon.core.i18n.AppLanguageManager;
import com.proyecto.moveon.ui.main.MainActivity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Servicio foreground que calcula métricas de tracking en tiempo real.
 *
 * <p>Responsabilidades principales:</p>
 * <ul>
 *     <li>Recibir muestras de GPS y acelerómetro.</li>
 *     <li>Distinguir entre movimiento real, deriva GPS y velocidad sospechosa.</li>
 *     <li>Acumular distancia, tiempo y calorías sin contaminar las métricas.</li>
 *     <li>Inferir si la actividad se parece más a caminar o a correr.</li>
 * </ul>
 *
 * <p>En esta versión se corrige un problema importante de infra-medición: la distancia
 * ya no se calcula solo contra el último punto observado, sino contra el último punto
 * aceptado, evitando perder tramos pequeños válidos entre muestras.</p>
 */
public final class TrackingService extends Service implements SensorEventListener {

    public static void stopService(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        Intent intent = new Intent(appContext, TrackingService.class);
        appContext.stopService(intent);
    }

    private static final String CHANNEL_ID = "moveon_tracking_channel";
    private static final int NOTIFICATION_ID = 1001;
    private static final String ACTION_RESTORE_NOTIFICATION =
            "com.proyecto.moveon.action.RESTORE_TRACKING_NOTIFICATION";
    private static final String ACTION_NOTIFICATION_PAUSE =
            "com.proyecto.moveon.action.TRACKING_NOTIFICATION_PAUSE";
    private static final String ACTION_NOTIFICATION_RESUME =
            "com.proyecto.moveon.action.TRACKING_NOTIFICATION_RESUME";
    private static final String ACTION_NOTIFICATION_FINISH =
            "com.proyecto.moveon.action.TRACKING_NOTIFICATION_FINISH";

    /**
     * Umbral del acelerómetro para considerar una muestra como "más propia de correr".
     *
     * <p>Se ha rebajado ligeramente porque con algunos teléfonos la señal llega muy
     * filtrada o el móvil se lleva muy estable, y con el valor anterior costaba entrar
     * en {@link TrackingState.ActivityType#RUNNING_ACTIVITY} aunque la persona corriera.</p>
     */
    private static final float ACCEL_RUN_THRESHOLD = 1.30f;

    /**
     * Ventana de muestras del acelerómetro usada para clasificar andar/correr.
     *
     * <p>Reducirla acelera la reacción del clasificador y hace menos probable quedarse
     * "atascado" en walking durante los primeros segundos de carrera.</p>
     */
    private static final int ACCEL_SAMPLE_WINDOW = 24;

    /**
     * Número de confirmaciones consecutivas necesarias antes de cambiar el tipo de actividad.
     */
    private static final int CONFIRM_STEPS = 2;

    private static final float ACCEL_ALPHA = 0.2f;
    private static final float MIN_ACCEL_CHANGE = 0.05f;

    private static final long LOCATION_INTERVAL_MS = 3_000L;
    private static final long LOCATION_FASTEST_MS = 1_500L;
    private static final float LOCATION_MIN_DISTANCE_M = 3.0f;
    private static final float LOCATION_MIN_ACCURACY_M = 20.0f;

    private static final float MAX_HUMAN_SPEED_MS = 5.556f; // 20 km/h
    private static final int SPEED_ALERT_CONSECUTIVE = 3;
    private static final int AUTO_PAUSE_STATIONARY_CONSECUTIVE = 2;
    private static final int AUTO_RESUME_MOVING_CONSECUTIVE = 2;
    private static final float MOVING_SPEED_THRESHOLD_MS = 0.90f;
    private static final float STATIONARY_SPEED_THRESHOLD_MS = 0.40f;
    private static final float MIN_VALID_DISTANCE_M = 4.0f;
    private static final float MAX_VALID_ACCURACY_FOR_SPEED_ALERT_M = 15f;
    private static final int SPEED_WINDOW_SIZE = 5;
    private static final long STOPPED_GRACE_PERIOD_MS = 5_000L;
    private static final long AUTO_PAUSE_INACTIVITY_MS = 8_000L;
    private static final float MOTION_EVIDENCE_DELTA_G = 0.08f;
    private static final long RECENT_MOTION_EVIDENCE_MS = 6_000L;

    /**
     * Factor aplicado a la precisión actual para decidir cuánto debe medir un salto GPS
     * antes de contarlo como distancia válida.
     *
     * <p>Se reduce desde 0.60f a 0.35f para evitar infra-medición cuando el usuario se
     * mueve de verdad pero el GPS tiene una precisión solo aceptable.</p>
     */
    private static final float MIN_MOVING_DISTANCE_ACCURACY_FACTOR = 0.35f;

    /**
     * Fallback por velocidad GPS para detectar carrera aunque el acelerómetro no rebote
     * lo suficiente como para superar el umbral de running.
     */
    private static final float GPS_RUNNING_SPEED_THRESHOLD_MS = 2.40f; // ~8.64 km/h

    /**
     * Umbral superior razonable para considerar que un movimiento válido todavía encaja
     * mejor con andar que con correr.
     */
    private static final float GPS_WALKING_SPEED_THRESHOLD_MS = 2.00f; // ~7.20 km/h

    private final MutableLiveData<TrackingState> stateLiveData =
            new MutableLiveData<>(TrackingState.idle());
    private final MutableLiveData<TrackingAlert> trackingAlertLiveData = new MutableLiveData<>();

    /**
     * Binder local para exponer el propio servicio al controlador.
     */
    public final class LocalBinder extends Binder {
        @NonNull
        public TrackingService getService() {
            return TrackingService.this;
        }
    }

    private final IBinder binder = new LocalBinder();

    @NonNull
    public LiveData<TrackingState> getStateLiveData() {
        return stateLiveData;
    }

    @NonNull
    public LiveData<TrackingAlert> getTrackingAlertLiveData() {
        return trackingAlertLiveData;
    }

    private TrackingState.Status currentStatus = TrackingState.Status.IDLE;
    private TrackingState.PauseReason currentPauseReason = TrackingState.PauseReason.NONE;
    private TrackingState.ActivityType activityType = TrackingState.ActivityType.WALKING;

    private long elapsedSeconds = 0L;
    private long movingSeconds = 0L;
    private long stoppedSeconds = 0L;
    private long manualPausedSeconds = 0L;

    /**
     * Distancia interna precisa acumulada en metros.
     *
     * <p>Se mantiene como {@code double} para que el ritmo medio use la distancia real
     * acumulada y no una versión truncada/redondeada por tramos.</p>
     */
    private double preciseDistanceMeters = 0.0;

    /**
     * Distancia expuesta a UI/estado en metros enteros.
     *
     * <p>Se sincroniza a partir de {@link #preciseDistanceMeters} solo para mostrar o
     * serializar un valor amigable, pero el cálculo del ritmo usa la versión precisa.</p>
     */
    private int distanceMeters = 0;
    private int calories = 0;
    private double caloriesAccumulator = 0.0;
    private double userWeightKg = 70.0;
    private int highSpeedCount = 0;
    private int consecutiveStationarySamples = 0;
    private int consecutiveMovingSamples = 0;
    private int autoPauseCount = 0;
    private int manualPauseCount = 0;
    private int suspiciousSpeedEventCount = 0;
    private int maxSpeedKmhX100 = 0;

    @Nullable private Location lastAcceptedLocation = null;
    @Nullable private Location lastObservedLocation = null;
    private long lastObservedRealtimeMs = 0L;
    private long manualPauseStartedRealtimeMs = 0L;
    private long sessionStartedRealtimeMs = 0L;
    private long lastMovementRealtimeMs = 0L;
    private long lastMotionEvidenceRealtimeMs = 0L;
    private boolean currentMovementSample = false;

    private final ArrayDeque<Float> recentMovingSpeeds = new ArrayDeque<>();
    private final List<LatLng> routePoints = new ArrayList<>();

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    @Nullable private ScheduledFuture<?> timerFuture = null;

    private FusedLocationProviderClient fusedLocationClient;
    private SensorManager sensorManager;
    @Nullable private Sensor accelerometer;
    private int accelRunSamples = 0;
    private int accelTotalSamples = 0;
    private int runningConfirmCount = 0;
    private int walkingConfirmCount = 0;

    /**
     * Confirmaciones independientes basadas en velocidad GPS.
     *
     * <p>Se mantienen separadas de las del acelerómetro para no mezclar señales de distinta
     * naturaleza y evitar cambios de actividad erráticos.</p>
     */
    private int gpsRunningConfirmCount = 0;
    private int gpsWalkingConfirmCount = 0;

    private float accelFilteredMag = SensorManager.GRAVITY_EARTH;

    private final LocationCallback locationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(@NonNull LocationResult result) {
            Location location = result.getLastLocation();
            if (location == null) return;
            if (location.getAccuracy() > LOCATION_MIN_ACCURACY_M) return;
            onNewLocation(location);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;

        if (ACTION_NOTIFICATION_PAUSE.equals(action)) {
            pauseTracking();
            return START_STICKY;
        }

        if (ACTION_NOTIFICATION_RESUME.equals(action)) {
            if (currentStatus == TrackingState.Status.PAUSED) {
                startTracking();
            }
            return START_STICKY;
        }

        if (ACTION_NOTIFICATION_FINISH.equals(action)) {
            openAppForStopConfirmation();
            return START_STICKY;
        }

        if (ACTION_RESTORE_NOTIFICATION.equals(action)) {
            if (currentStatus != TrackingState.Status.IDLE) {
                startForeground(NOTIFICATION_ID, buildNotification());
            }
            return START_STICKY;
        }

        startForeground(NOTIFICATION_ID, buildNotification());
        return START_STICKY;
    }

    @NonNull
    @Override
    public IBinder onBind(@NonNull Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        stopTrackingInternal();
        try {
            scheduler.shutdownNow();
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }

    /**
     * Permite configurar el peso del usuario desde el perfil.
     */
    public void setUserWeight(double weightKg) {
        this.userWeightKg = (weightKg > 0) ? weightKg : 70.0;
    }

    /**
     * Inicia una sesión nueva, reanuda una pausa manual o sale de una auto-pausa.
     */
    public void startTracking() {
        if (currentStatus == TrackingState.Status.RUNNING) {
            return;
        }

        if (currentStatus == TrackingState.Status.AUTO_PAUSED) {
            currentStatus = TrackingState.Status.RUNNING;
            currentPauseReason = TrackingState.PauseReason.NONE;
            currentMovementSample = false;
            consecutiveStationarySamples = 0;
            consecutiveMovingSamples = 0;
            gpsRunningConfirmCount = 0;
            gpsWalkingConfirmCount = 0;
            recentMovingSpeeds.clear();
            publishState();
            updateNotification();
            return;
        }

        if (currentStatus == TrackingState.Status.IDLE || currentStatus == TrackingState.Status.FINISHED) {
            resetInternalState();
        }

        if (currentStatus == TrackingState.Status.PAUSED) {
            accumulateManualPauseTime();
        }

        currentStatus = TrackingState.Status.RUNNING;
        currentPauseReason = TrackingState.PauseReason.NONE;
        currentMovementSample = false;

        long nowRealtime = SystemClock.elapsedRealtime();
        if (sessionStartedRealtimeMs <= 0L) {
            // Marca el inicio de la sesión para dar un pequeño margen antes de contar parado.
            sessionStartedRealtimeMs = nowRealtime;
        }
        if (lastMovementRealtimeMs <= 0L) {
            // Evita clasificar como parado el mismo segundo en que se pulsa iniciar.
            lastMovementRealtimeMs = nowRealtime;
        }
        if (lastMotionEvidenceRealtimeMs <= 0L) {
            // Usa la propia acción de iniciar como primera evidencia temporal de actividad.
            lastMotionEvidenceRealtimeMs = nowRealtime;
        }
        consecutiveStationarySamples = 0;
        consecutiveMovingSamples = 0;
        gpsRunningConfirmCount = 0;
        gpsWalkingConfirmCount = 0;
        recentMovingSpeeds.clear();

        startLocationUpdates();
        startAccelerometer();
        startTimer();
        publishState();
    }

    /**
     * Pausa manualmente la sesión y detiene sensores.
     */
    public void pauseTracking() {
        if (currentStatus != TrackingState.Status.RUNNING
                && currentStatus != TrackingState.Status.AUTO_PAUSED) {
            return;
        }

        currentStatus = TrackingState.Status.PAUSED;
        currentPauseReason = TrackingState.PauseReason.MANUAL;
        currentMovementSample = false;
        highSpeedCount = 0;
        consecutiveMovingSamples = 0;
        consecutiveStationarySamples = 0;
        recentMovingSpeeds.clear();
        gpsRunningConfirmCount = 0;
        gpsWalkingConfirmCount = 0;
        manualPauseCount++;
        manualPauseStartedRealtimeMs = SystemClock.elapsedRealtime();

        stopTimer();
        stopLocationUpdates();
        stopAccelerometer();
        publishState();
        updateNotification();
    }

    /**
     * Marca la actividad como finalizada y publica el último snapshot.
     */
    public void stopTracking() {
        if (currentStatus == TrackingState.Status.IDLE) {
            return;
        }

        if (currentStatus == TrackingState.Status.PAUSED) {
            accumulateManualPauseTime();
        }

        currentStatus = TrackingState.Status.FINISHED;
        currentPauseReason = TrackingState.PauseReason.NONE;
        currentMovementSample = false;

        stopTrackingInternal();
        publishState();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    /**
     * Descarta por completo la sesión actual.
     */
    public void resetTracking() {
        if (currentStatus == TrackingState.Status.PAUSED) {
            accumulateManualPauseTime();
        }

        stopTrackingInternal();
        resetInternalState();
        currentStatus = TrackingState.Status.IDLE;
        currentPauseReason = TrackingState.PauseReason.NONE;
        publishState();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @SuppressWarnings("MissingPermission")
    private void startLocationUpdates() {
        LocationRequest request = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                LOCATION_INTERVAL_MS
        )
                .setMinUpdateIntervalMillis(LOCATION_FASTEST_MS)
                .setMinUpdateDistanceMeters(LOCATION_MIN_DISTANCE_M)
                .build();
        fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper());
    }

    private void stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback);
    }

    /**
     * Procesa una nueva localización GPS y decide tres cosas:
     *
     * <ol>
     *     <li>Si la muestra parece humana o compatible con vehículo.</li>
     *     <li>Si hay movimiento real suficiente como para sumar distancia.</li>
     *     <li>Si la actividad encaja mejor con caminar o con correr.</li>
     * </ol>
     *
     * <p>La corrección principal aquí es separar dos conceptos distintos:</p>
     * <ul>
     *     <li><b>Último punto observado</b>: sirve para estimar velocidad y detectar ruido.</li>
     *     <li><b>Último punto aceptado</b>: sirve para acumular distancia real sin perder
     *     tramos pequeños válidos entre muestras.</li>
     * </ul>
     */
    private void onNewLocation(@NonNull Location location) {
        if (currentStatus != TrackingState.Status.RUNNING
                && currentStatus != TrackingState.Status.AUTO_PAUSED) {
            return;
        }

        long nowRealtime = SystemClock.elapsedRealtime();

        if (lastObservedLocation == null) {
            // Primer fix de la sesión: queda registrado como punto observado y aceptado.
            lastObservedLocation = location;
            lastAcceptedLocation = location;
            lastObservedRealtimeMs = nowRealtime;

            if (routePoints.isEmpty()) {
                routePoints.add(new LatLng(location.getLatitude(), location.getLongitude()));
            }

            publishState();
            updateNotification();
            return;
        }

        // Distancia entre el último punto observado y el actual.
        // Se usa para derivar velocidad y para filtrar ruido puntual.
        float observedDeltaMeters = lastObservedLocation.distanceTo(location);

        // Distancia acumulada desde el último punto realmente aceptado.
        // Esta es la que debemos sumar para no perder metros pequeños válidos.
        float acceptedDeltaMeters = lastAcceptedLocation != null
                ? lastAcceptedLocation.distanceTo(location)
                : observedDeltaMeters;

        long deltaTimeMs = computeDeltaTimeMs(lastObservedLocation, location, nowRealtime);
        float derivedSpeedMs = deltaTimeMs > 0
                ? (observedDeltaMeters / (deltaTimeMs / 1000f))
                : 0f;
        float resolvedSpeedMs = resolveSpeedMs(location, derivedSpeedMs);

        // El "último observado" siempre avanza, aunque este punto no acabe contando
        // distancia. Así la velocidad y el filtrado trabajan con muestras frescas.
        lastObservedLocation = location;
        lastObservedRealtimeMs = nowRealtime;

        if (isSuspiciousVehicleSpeed(location, resolvedSpeedMs)) {
            highSpeedCount++;
            if (highSpeedCount >= SPEED_ALERT_CONSECUTIVE) {
                highSpeedCount = 0;
                suspiciousSpeedEventCount++;
                enterAutoPause(
                        TrackingState.PauseReason.SUSPICIOUS_SPEED,
                        TrackingAlert.Type.SUSPICIOUS_SPEED
                );
            }
            publishState();
            updateNotification();
            return;
        }

        highSpeedCount = 0;

        boolean hasRecentMotionEvidence = hasRecentMotionEvidence(nowRealtime);
        boolean movingSample = isMovingSample(
                location,
                observedDeltaMeters,
                resolvedSpeedMs,
                hasRecentMotionEvidence
        );
        boolean stationarySample = isStationarySample(
                location,
                observedDeltaMeters,
                resolvedSpeedMs
        );

        currentMovementSample = movingSample;

        if (movingSample) {
            lastMovementRealtimeMs = nowRealtime;
            consecutiveMovingSamples++;
            consecutiveStationarySamples = 0;
            trackSpeedWindow(resolvedSpeedMs);
            updateMaxSpeed(resolvedSpeedMs);

            // Fallback: si la velocidad GPS encaja claramente con carrera o con andar,
            // se usa para reforzar el tipo de actividad aunque el acelerómetro sea pobre.
            updateActivityTypeFromGps(resolvedSpeedMs, true);

            if (currentStatus == TrackingState.Status.AUTO_PAUSED
                    && consecutiveMovingSamples >= AUTO_RESUME_MOVING_CONSECUTIVE) {
                currentStatus = TrackingState.Status.RUNNING;
                currentPauseReason = TrackingState.PauseReason.NONE;
            }

            if (shouldAccumulateDistance(location, acceptedDeltaMeters, movingSample)) {
                // Se suma la distancia desde el último punto aceptado, no desde el último
                // observado. Así evitamos "evaporar" metros en saltos pequeños consecutivos.
                //
                // Importante: la acumulación interna se hace en double para que el ritmo medio
                // use la distancia real acumulada y no la suma de redondeos de cada tramo.
                preciseDistanceMeters += acceptedDeltaMeters;
                syncRoundedDistanceMeters();
                acceptRoutePoint(location);
                lastAcceptedLocation = location;
            }
        } else if (stationarySample) {
            consecutiveStationarySamples++;
            consecutiveMovingSamples = 0;
            recentMovingSpeeds.clear();

            if (currentStatus == TrackingState.Status.RUNNING
                    && consecutiveStationarySamples >= AUTO_PAUSE_STATIONARY_CONSECUTIVE) {
                enterAutoPause(
                        TrackingState.PauseReason.STATIONARY,
                        TrackingAlert.Type.STATIONARY_AUTO_PAUSE
                );
            }
        }

        publishState();
        updateNotification();
    }

    private void acceptRoutePoint(@NonNull Location location) {
        LatLng point = new LatLng(location.getLatitude(), location.getLongitude());
        if (routePoints.isEmpty()) {
            routePoints.add(point);
            return;
        }

        LatLng lastPoint = routePoints.get(routePoints.size() - 1);
        if (lastPoint.latitude != point.latitude || lastPoint.longitude != point.longitude) {
            routePoints.add(point);
        }
    }

    private long computeDeltaTimeMs(@NonNull Location previous, @NonNull Location current, long nowRealtime) {
        long locationDelta = current.getTime() - previous.getTime();
        if (locationDelta > 0L) {
            return locationDelta;
        }
        if (lastObservedRealtimeMs > 0L) {
            return Math.max(1L, nowRealtime - lastObservedRealtimeMs);
        }
        return LOCATION_INTERVAL_MS;
    }

    private float resolveSpeedMs(@NonNull Location location, float derivedSpeedMs) {
        float gpsSpeedMs = location.hasSpeed() ? location.getSpeed() : -1f;
        if (gpsSpeedMs > 0f && derivedSpeedMs > 0f) {
            return (gpsSpeedMs + derivedSpeedMs) / 2f;
        }
        if (gpsSpeedMs > 0f) {
            return gpsSpeedMs;
        }
        return Math.max(0f, derivedSpeedMs);
    }

    private boolean isSuspiciousVehicleSpeed(@NonNull Location location, float speedMs) {
        return speedMs > MAX_HUMAN_SPEED_MS
                && location.getAccuracy() <= MAX_VALID_ACCURACY_FOR_SPEED_ALERT_M;
    }

    /**
     * Decide si una muestra realmente representa movimiento útil.
     *
     * <p>Se endurece la lógica para evitar "pasos fantasma" por deriva GPS en interior.
     * Ya no basta con una velocidad derivada de un salto de posición; se exige además
     * evidencia reciente de movimiento físico o una distancia claramente superior al
     * margen de error de la propia localización.</p>
     */
    private boolean isMovingSample(
            @NonNull Location location,
            float deltaMeters,
            float speedMs,
            boolean hasRecentMotionEvidence) {
        float movingDistanceThreshold = getMovingDistanceThreshold(location);
        boolean hasStrongDistanceJump = deltaMeters >= (movingDistanceThreshold * 1.35f);
        boolean hasValidSpeed = speedMs >= MOVING_SPEED_THRESHOLD_MS;

        return (hasValidSpeed && hasRecentMotionEvidence)
                || (deltaMeters >= movingDistanceThreshold && hasRecentMotionEvidence)
                || hasStrongDistanceJump;
    }

    /**
     * Considera una muestra como estacionaria cuando la velocidad es baja y el salto
     * observado sigue dentro del error razonable del GPS.
     */
    private boolean isStationarySample(@NonNull Location location, float deltaMeters, float speedMs) {
        float movingDistanceThreshold = getMovingDistanceThreshold(location);
        return speedMs <= STATIONARY_SPEED_THRESHOLD_MS && deltaMeters < movingDistanceThreshold;
    }

    /**
     * Decide si el tramo debe incorporarse a la distancia total.
     *
     * <p>Importante: aquí se usa la distancia respecto al último punto aceptado, no el
     * salto entre la última muestra observada y la actual. Esto permite acumular tramos
     * pequeños legítimos que, individualmente, pueden quedar por debajo del umbral.</p>
     */
    private boolean shouldAccumulateDistance(
            @NonNull Location location,
            float acceptedDeltaMeters,
            boolean movingSample) {
        return movingSample && acceptedDeltaMeters >= getMovingDistanceThreshold(location);
    }

    /**
     * Calcula el salto mínimo aceptable según la precisión actual del GPS.
     */
    private float getMovingDistanceThreshold(@NonNull Location location) {
        return Math.max(MIN_VALID_DISTANCE_M, location.getAccuracy() * MIN_MOVING_DISTANCE_ACCURACY_FACTOR);
    }

    /**
     * Indica si hay evidencia de movimiento suficientemente reciente.
     */
    private boolean hasRecentMotionEvidence(long nowRealtime) {
        long lastEvidenceRealtimeMs = Math.max(lastMovementRealtimeMs, lastMotionEvidenceRealtimeMs);
        if (lastEvidenceRealtimeMs <= 0L) {
            return false;
        }
        return (nowRealtime - lastEvidenceRealtimeMs) <= RECENT_MOTION_EVIDENCE_MS;
    }

    private void trackSpeedWindow(float speedMs) {
        if (speedMs <= 0f) {
            return;
        }
        recentMovingSpeeds.addLast(speedMs);
        while (recentMovingSpeeds.size() > SPEED_WINDOW_SIZE) {
            recentMovingSpeeds.removeFirst();
        }
    }

    private void updateMaxSpeed(float speedMs) {
        if (speedMs <= 0f) {
            return;
        }
        int kmhX100 = (int) Math.round(speedMs * 3.6 * 100.0);
        if (kmhX100 > maxSpeedKmhX100) {
            maxSpeedKmhX100 = kmhX100;
        }
    }

    private void enterAutoPause(
            @NonNull TrackingState.PauseReason pauseReason,
            @NonNull TrackingAlert.Type alertType) {
        if (currentStatus == TrackingState.Status.AUTO_PAUSED
                && currentPauseReason == pauseReason) {
            return;
        }

        currentStatus = TrackingState.Status.AUTO_PAUSED;
        currentPauseReason = pauseReason;
        currentMovementSample = false;
        recentMovingSpeeds.clear();

        if (pauseReason == TrackingState.PauseReason.STATIONARY) {
            autoPauseCount++;
        }

        trackingAlertLiveData.postValue(new TrackingAlert(alertType));
    }

    private void startAccelerometer() {
        if (sensorManager != null && accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    private void stopAccelerometer() {
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    public void onSensorChanged(@NonNull SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;
        if (currentStatus != TrackingState.Status.RUNNING
                && currentStatus != TrackingState.Status.AUTO_PAUSED) return;

        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];
        float magnitude = (float) Math.sqrt((x * x) + (y * y) + (z * z));
        float magnitudeG = magnitude / SensorManager.GRAVITY_EARTH;

        if (Math.abs(magnitudeG - accelFilteredMag) < MIN_ACCEL_CHANGE) {
            return;
        }

        accelFilteredMag = (ACCEL_ALPHA * magnitudeG) + ((1f - ACCEL_ALPHA) * accelFilteredMag);

        if (Math.abs(accelFilteredMag - 1.0f) >= MOTION_EVIDENCE_DELTA_G) {
            // Guarda evidencia de movimiento aunque todavía no haya llegado un nuevo punto GPS.
            lastMotionEvidenceRealtimeMs = SystemClock.elapsedRealtime();
        }

        accelTotalSamples++;
        if (accelFilteredMag > ACCEL_RUN_THRESHOLD) {
            accelRunSamples++;
        }

        if (accelTotalSamples < ACCEL_SAMPLE_WINDOW) {
            return;
        }

        // Antes se exigía ~50% de la ventana como "running". Ahora basta 1/3 para
        // tolerar móviles más estables y señales de acelerómetro menos expresivas.
        boolean mostlyRunning = accelRunSamples >= (ACCEL_SAMPLE_WINDOW / 3);

        updateActivityTypeFromSensor(mostlyRunning);

        accelRunSamples = 0;
        accelTotalSamples = 0;
    }

    /**
     * Actualiza el tipo de actividad usando únicamente la señal del acelerómetro.
     *
     * <p>Este clasificador sigue siendo útil, pero ya no es la única fuente de verdad:
     * el GPS puede reforzar la decisión cuando la velocidad es suficientemente clara.</p>
     */
    private void updateActivityTypeFromSensor(boolean mostlyRunning) {
        if (mostlyRunning) {
            runningConfirmCount++;
            walkingConfirmCount = 0;
        } else {
            walkingConfirmCount++;
            runningConfirmCount = 0;
        }

        if (runningConfirmCount >= CONFIRM_STEPS
                && activityType != TrackingState.ActivityType.RUNNING_ACTIVITY) {
            activityType = TrackingState.ActivityType.RUNNING_ACTIVITY;
            publishState();
        } else if (walkingConfirmCount >= CONFIRM_STEPS
                && activityType != TrackingState.ActivityType.WALKING) {
            activityType = TrackingState.ActivityType.WALKING;
            publishState();
        }
    }

    /**
     * Refuerza la clasificación andar/correr a partir de velocidad GPS.
     *
     * <p>Solo actúa cuando la muestra ya fue considerada movimiento real. De este modo
     * no degradamos la clasificación por deriva GPS en parado.</p>
     */
    private void updateActivityTypeFromGps(float speedMs, boolean movingSample) {
        if (!movingSample || speedMs <= 0f) {
            return;
        }

        if (speedMs >= GPS_RUNNING_SPEED_THRESHOLD_MS) {
            gpsRunningConfirmCount++;
            gpsWalkingConfirmCount = 0;
        } else if (speedMs <= GPS_WALKING_SPEED_THRESHOLD_MS) {
            gpsWalkingConfirmCount++;
            gpsRunningConfirmCount = 0;
        } else {
            // Zona neutra: no forzamos cambio de estado si la velocidad cae entre umbrales.
            gpsRunningConfirmCount = 0;
            gpsWalkingConfirmCount = 0;
            return;
        }

        if (gpsRunningConfirmCount >= CONFIRM_STEPS
                && activityType != TrackingState.ActivityType.RUNNING_ACTIVITY) {
            activityType = TrackingState.ActivityType.RUNNING_ACTIVITY;
            publishState();
        } else if (gpsWalkingConfirmCount >= CONFIRM_STEPS
                && activityType != TrackingState.ActivityType.WALKING) {
            activityType = TrackingState.ActivityType.WALKING;
            publishState();
        }
    }

    @Override
    public void onAccuracyChanged(@NonNull Sensor sensor, int accuracy) {
        // Sin uso.
    }

    /**
     * Timer de sesión. Sigue corriendo en auto-pausa para contar tiempo parado,
     * pero se detiene en pausa manual.
     *
     * <p>Importante: el tiempo parado ya no depende únicamente de recibir puntos GPS.
     * Si pasan varios segundos sin evidencia de movimiento real, el servicio empieza
     * a contar tiempo parado y, tras un umbral mayor, entra en auto-pausa aunque el
     * dispositivo esté en interior y no entren nuevas localizaciones.</p>
     */
    private void startTimer() {
        stopTimer();
        if (scheduler.isShutdown()) {
            return;
        }

        timerFuture = scheduler.scheduleWithFixedDelay(() -> mainHandler.post(() -> {
            if (currentStatus != TrackingState.Status.RUNNING
                    && currentStatus != TrackingState.Status.AUTO_PAUSED) {
                return;
            }

            elapsedSeconds++;

            if (currentStatus == TrackingState.Status.RUNNING) {
                long nowRealtime = SystemClock.elapsedRealtime();
                long inactivityMs = computeInactivityMs(nowRealtime);

                boolean motionStillFresh = hasRecentMotionEvidence(nowRealtime);
                if (currentMovementSample && motionStillFresh) {
                    // Solo el movimiento válido y reciente consume calorías y suma tiempo en movimiento.
                    movingSeconds++;
                    caloriesAccumulator += calculateCaloriesPerSecond();
                    calories = (int) Math.round(caloriesAccumulator);
                } else if (inactivityMs >= STOPPED_GRACE_PERIOD_MS) {
                    currentMovementSample = false;
                    stoppedSeconds++;
                }

                if (inactivityMs >= AUTO_PAUSE_INACTIVITY_MS) {
                    enterAutoPause(
                            TrackingState.PauseReason.STATIONARY,
                            TrackingAlert.Type.STATIONARY_AUTO_PAUSE
                    );
                }
            } else if (currentStatus == TrackingState.Status.AUTO_PAUSED) {
                stoppedSeconds++;
            }

            publishState();
            updateNotification();
        }), 1L, 1L, TimeUnit.SECONDS);
    }

    private void stopTimer() {
        if (timerFuture != null && !timerFuture.isCancelled()) {
            timerFuture.cancel(false);
            timerFuture = null;
        }
    }

    /**
     * Calcula el tiempo de inactividad tomando la evidencia más reciente de movimiento.
     *
     * <p>Combina movimiento GPS válido y movimiento detectado por acelerómetro para no
     * depender solo de nuevas posiciones, que en interior pueden tardar o no llegar.</p>
     */
    private long computeInactivityMs(long nowRealtime) {
        long lastEvidenceRealtimeMs = Math.max(
                sessionStartedRealtimeMs,
                Math.max(lastMovementRealtimeMs, lastMotionEvidenceRealtimeMs)
        );
        if (lastEvidenceRealtimeMs <= 0L) {
            return 0L;
        }
        return Math.max(0L, nowRealtime - lastEvidenceRealtimeMs);
    }

    private double calculateCaloriesPerSecond() {
        if (userWeightKg <= 0.0) {
            return 0.0;
        }

        double met = activityType == TrackingState.ActivityType.RUNNING_ACTIVITY ? 8.3 : 3.8;
        return (met * userWeightKg) / 3600.0;
    }

    /**
     * Sincroniza la distancia pública entera a partir del acumulado preciso.
     *
     * <p>La UI y el estado siguen trabajando en metros enteros, pero el cálculo del
     * ritmo se apoya en {@link #preciseDistanceMeters} para no introducir error por
     * redondeos repetidos.</p>
     */
    private void syncRoundedDistanceMeters() {
        distanceMeters = (int) Math.round(preciseDistanceMeters);
    }

    @Nullable
    private String calculateInstantPace() {
        if (currentStatus != TrackingState.Status.RUNNING || recentMovingSpeeds.isEmpty()) {
            return null;
        }

        double total = 0.0;
        for (Float speed : recentMovingSpeeds) {
            total += speed;
        }
        double averageSpeedMs = total / recentMovingSpeeds.size();
        return formatPaceFromSpeed(averageSpeedMs);
    }

    /**
     * Calcula el ritmo medio solo durante los segundos marcados como movimiento real.
     *
     * <p>Usa la distancia precisa acumulada para evitar que el ritmo quede sesgado por
     * los redondeos intermedios de cada tramo GPS.</p>
     */
    @Nullable
    private String calculateAverageMovingPace() {
        return formatPaceFromTotals(movingSeconds, preciseDistanceMeters);
    }

    /**
     * Calcula el ritmo medio contando todo el tiempo transcurrido de la sesión.
     */
    @Nullable
    private String calculateAverageElapsedPace() {
        return formatPaceFromTotals(elapsedSeconds, preciseDistanceMeters);
    }

    /**
     * Convierte tiempo total y distancia total en ritmo por kilómetro.
     *
     * <p>Se recibe la distancia en double para aprovechar todo el detalle acumulado y
     * no depender del valor entero mostrado en pantalla.</p>
     */
    @Nullable
    private String formatPaceFromTotals(long seconds, double meters) {
        if (seconds <= 0L || meters <= 0.0) {
            return null;
        }
        double paceSecondsPerKm = (seconds * 1000.0) / meters;
        return formatPaceFromSeconds(paceSecondsPerKm);
    }

    @Nullable
    private String formatPaceFromSpeed(double speedMs) {
        if (speedMs <= 0.0) {
            return null;
        }
        double paceSecondsPerKm = 1000.0 / speedMs;
        return formatPaceFromSeconds(paceSecondsPerKm);
    }

    @Nullable
    private String formatPaceFromSeconds(double paceSecondsPerKm) {
        if (paceSecondsPerKm < 60.0 || paceSecondsPerKm > 1800.0) {
            return null;
        }

        int totalSeconds = (int) Math.round(paceSecondsPerKm);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format(Locale.US, "%d'%02d\"", minutes, seconds);
    }

    private void publishState() {
        String encodedPolyline = null;
        if (currentStatus == TrackingState.Status.FINISHED && !routePoints.isEmpty()) {
            encodedPolyline = PolyUtil.encode(routePoints);
        }

        TrackingState state = new TrackingState.Builder()
                .status(currentStatus)
                .pauseReason(currentPauseReason)
                .activityType(activityType)
                .elapsedSeconds(elapsedSeconds)
                .movingSeconds(movingSeconds)
                .stoppedSeconds(stoppedSeconds)
                .manualPausedSeconds(manualPausedSeconds)
                // El estado público mantiene metros enteros para no romper compatibilidad.
                .distanceMeters(distanceMeters)
                .calories(calories)
                .pace(calculateInstantPace())
                .averageMovingPace(calculateAverageMovingPace())
                .averageElapsedPace(calculateAverageElapsedPace())
                .maxSpeedKmhX100(maxSpeedKmhX100)
                .autoPauseCount(autoPauseCount)
                .manualPauseCount(manualPauseCount)
                .suspiciousSpeedEventCount(suspiciousSpeedEventCount)
                .routePoints(new ArrayList<>(routePoints))
                .encodedPolyline(encodedPolyline)
                .build();

        stateLiveData.postValue(state);
    }

    private void stopTrackingInternal() {
        stopTimer();
        stopLocationUpdates();
        stopAccelerometer();
    }

    private void resetInternalState() {
        elapsedSeconds = 0L;
        movingSeconds = 0L;
        stoppedSeconds = 0L;
        manualPausedSeconds = 0L;
        preciseDistanceMeters = 0.0;
        distanceMeters = 0;
        calories = 0;
        caloriesAccumulator = 0.0;
        highSpeedCount = 0;
        consecutiveStationarySamples = 0;
        consecutiveMovingSamples = 0;
        autoPauseCount = 0;
        manualPauseCount = 0;
        suspiciousSpeedEventCount = 0;
        maxSpeedKmhX100 = 0;
        currentMovementSample = false;
        manualPauseStartedRealtimeMs = 0L;
        sessionStartedRealtimeMs = 0L;
        lastMovementRealtimeMs = 0L;
        lastMotionEvidenceRealtimeMs = 0L;
        lastAcceptedLocation = null;
        lastObservedLocation = null;
        lastObservedRealtimeMs = 0L;
        recentMovingSpeeds.clear();
        routePoints.clear();

        accelRunSamples = 0;
        accelTotalSamples = 0;
        runningConfirmCount = 0;
        walkingConfirmCount = 0;
        gpsRunningConfirmCount = 0;
        gpsWalkingConfirmCount = 0;
        accelFilteredMag = SensorManager.GRAVITY_EARTH;
        activityType = TrackingState.ActivityType.WALKING;
    }

    private void accumulateManualPauseTime() {
        if (manualPauseStartedRealtimeMs <= 0L) {
            return;
        }
        long pausedMs = SystemClock.elapsedRealtime() - manualPauseStartedRealtimeMs;
        if (pausedMs > 0L) {
            manualPausedSeconds += (pausedMs / 1000L);
        }
        manualPauseStartedRealtimeMs = 0L;
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                tr(R.string.mo_tracking_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription(tr(R.string.mo_tracking_notification_channel_desc));
        channel.setShowBadge(false);
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    /**
     * Construye la notificación foreground del tracking.
     *
     * <p>El {@code smallIcon} de la barra de estado sigue usando {@code run_icon}, porque
     * Android lo procesa correctamente como icono monocromo del sistema. En cambio, el
     * icono interno de las {@link RemoteViews} usa {@code run_icon_notification}, que tiene
     * una variante en {@code res/drawable/} y otra en {@code res/drawable-night/} para
     * mostrarse negro en modo claro y blanco en modo oscuro.</p>
     */
    @NonNull
    private Notification buildNotification() {
    PendingIntent contentIntent = buildNotificationContentIntent(0);
    PendingIntent restorePendingIntent = buildServiceActionPendingIntent(
            ACTION_RESTORE_NOTIFICATION,
            1
    );

    String title = buildNotificationTitle();
    String compactText = buildNotificationCompactText();

    NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.run_icon)
            .setColor(ContextCompat.getColor(this, R.color.greenPrimary))
            .setContentTitle(title)
            .setContentText(compactText)
            .setSubText(buildNotificationSummaryText())
            .setContentIntent(contentIntent)
            .setDeleteIntent(restorePendingIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setStyle(new NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(buildCollapsedNotificationRemoteViews())
            .setCustomBigContentView(buildExpandedNotificationRemoteViews())
            .setCustomHeadsUpContentView(buildExpandedNotificationRemoteViews());

    return builder.build();
}

    /**
     * Construye la vista compacta de la notificación.
     *
     * <p>Se usa un drawable específico de notificación para no depender de tint dinámico en
     * {@link RemoteViews}, ya que algunos fabricantes ignoran ese tint dentro del contenido
     * de la notificación.</p>
     */
    @NonNull
    private RemoteViews buildCollapsedNotificationRemoteViews() {
        RemoteViews views = new RemoteViews(getPackageName(), R.layout.notification_tracking_compact);

        // Icono interno de la tarjeta de notificación: day/night separado para asegurar contraste.
        views.setImageViewResource(R.id.iv_tracking_header_icon, R.drawable.run_icon_notification);
    views.setTextViewText(R.id.tv_tracking_title, buildNotificationTitle());
    views.setTextViewText(R.id.tv_tracking_subtitle, buildNotificationCompactText());

    bindStatusPill(views, R.id.tv_tracking_status_pill);

    views.setTextViewText(R.id.tv_metric_primary_value, formatElapsed(elapsedSeconds));
    views.setTextViewText(
            R.id.tv_metric_primary_label,
            tr(R.string.mo_tracking_notification_metric_time_compact)
    );

    views.setTextViewText(R.id.tv_metric_secondary_value, formatNotificationDistance());
    views.setTextViewText(
            R.id.tv_metric_secondary_label,
            tr(R.string.mo_tracking_notification_metric_distance_compact)
    );

    views.setTextViewText(R.id.tv_metric_tertiary_value, buildCompactRightMetricValue());
    views.setTextViewText(
            R.id.tv_metric_tertiary_label,
            tr(R.string.mo_tracking_notification_metric_right_compact)
    );


    return views;
}

    /**
     * Construye la vista expandida de la notificación.
     *
     * <p>Igual que en la vista compacta, el icono del encabezado usa un recurso propio con
     * variante nocturna para mantener el color correcto dentro de la notificación.</p>
     */
    @NonNull
    private RemoteViews buildExpandedNotificationRemoteViews() {
        RemoteViews views = new RemoteViews(getPackageName(), R.layout.notification_tracking_expanded);

        // Mismo recurso day/night que en la vista compacta para mantener consistencia visual.
        views.setImageViewResource(R.id.iv_tracking_header_icon, R.drawable.run_icon_notification);
    views.setTextViewText(R.id.tv_tracking_title, buildNotificationTitle());
    views.setTextViewText(R.id.tv_tracking_summary, buildNotificationSummaryText());

    bindStatusPill(views, R.id.tv_tracking_status_pill);

    bindMetricCard(
            views,
            R.id.tv_card_time_value,
            R.id.tv_card_time_label,
            formatElapsed(elapsedSeconds),
            tr(R.string.mo_tracking_notification_metric_time)
    );
    bindMetricCard(
            views,
            R.id.tv_card_distance_value,
            R.id.tv_card_distance_label,
            formatNotificationDistance(),
            tr(R.string.mo_tracking_notification_metric_distance)
    );

    String averagePace = calculateAverageMovingPace();
    String paceText = averagePace != null
            ? averagePace + "/km"
            : tr(R.string.tracking_default_pace) + "/km";

    bindMetricCard(
            views,
            R.id.tv_card_pace_value,
            R.id.tv_card_pace_label,
            paceText,
            tr(R.string.mo_tracking_notification_metric_pace)
    );
    bindMetricCard(
            views,
            R.id.tv_card_calories_value,
            R.id.tv_card_calories_label,
            tr(R.string.tracking_calories_format, calories),
            tr(R.string.mo_tracking_notification_metric_calories)
    );
    bindMetricCard(
            views,
            R.id.tv_card_moving_value,
            R.id.tv_card_moving_label,
            formatElapsed(movingSeconds),
            tr(R.string.mo_tracking_notification_metric_moving)
    );
    bindMetricCard(
            views,
            R.id.tv_card_stopped_value,
            R.id.tv_card_stopped_label,
            formatElapsed(stoppedSeconds),
            tr(R.string.mo_tracking_notification_metric_stopped)
    );

    bindNotificationButtons(
            views,
            R.id.action_primary_container,
            R.id.iv_action_primary_icon,
            R.id.tv_action_primary,
            R.id.action_secondary_container,
            R.id.iv_action_secondary_icon,
            R.id.tv_action_secondary
    );

    return views;
}

private void bindMetricCard(
        @NonNull RemoteViews views,
        int valueViewId,
        int labelViewId,
        @NonNull String value,
        @NonNull String label
) {
    views.setTextViewText(valueViewId, value);
    views.setTextViewText(labelViewId, label);
}

private void bindStatusPill(@NonNull RemoteViews views, int pillViewId) {
    views.setTextViewText(pillViewId, buildStatusPillText());
    views.setInt(pillViewId, "setBackgroundResource", resolveStatusPillBackground());
}

@NonNull
private String buildStatusPillText() {
    switch (currentStatus) {
        case RUNNING:
            return tr(R.string.mo_tracking_notification_status_live_badge);

        case PAUSED:
            return tr(R.string.mo_tracking_notification_status_paused_badge);

        case AUTO_PAUSED:
            if (currentPauseReason == TrackingState.PauseReason.SUSPICIOUS_SPEED) {
                return tr(R.string.mo_tracking_notification_status_review_badge);
            }
            return tr(R.string.mo_tracking_notification_status_waiting_badge);

        case FINISHED:
        case IDLE:
        default:
            return tr(R.string.mo_tracking_notification_title);
    }
}

private int resolveStatusPillBackground() {
    switch (currentStatus) {
        case RUNNING:
            return R.drawable.bg_tracking_notification_status_live;

        case PAUSED:
            return R.drawable.bg_tracking_notification_status_paused;

        case AUTO_PAUSED:
            return R.drawable.bg_tracking_notification_status_alert;

        case FINISHED:
        case IDLE:
        default:
            return R.drawable.bg_tracking_notification_status_neutral;
    }
}

@NonNull
private String buildCompactRightMetricValue() {
    String averagePace = calculateAverageMovingPace();
    if (averagePace != null) {
        return averagePace + "/km";
    }
    if (currentStatus == TrackingState.Status.PAUSED) {
        return tr(R.string.mo_tracking_notification_status_paused_short);
    }
    if (currentStatus == TrackingState.Status.AUTO_PAUSED) {
        if (currentPauseReason == TrackingState.PauseReason.SUSPICIOUS_SPEED) {
            return tr(R.string.mo_tracking_notification_status_review_short);
        }
        return tr(R.string.mo_tracking_notification_status_waiting_short);
    }
    return tr(R.string.tracking_default_pace) + "/km";
}

private void bindNotificationButtons(
        @NonNull RemoteViews views,
        int primaryContainerId,
        int primaryIconId,
        int primaryTextId,
        int secondaryContainerId,
        int secondaryIconId,
        int secondaryTextId
) {
    switch (currentStatus) {
        case RUNNING:
            bindNotificationButton(
                    views,
                    primaryContainerId,
                    primaryIconId,
                    primaryTextId,
                    R.drawable.ic_pause_notification_action,
                    tr(R.string.mo_tracking_notification_action_pause),
                    buildServiceActionPendingIntent(ACTION_NOTIFICATION_PAUSE, 10)
            );
            bindNotificationButton(
                    views,
                    secondaryContainerId,
                    secondaryIconId,
                    secondaryTextId,
                    R.drawable.ic_stop_notification_action,
                    tr(R.string.mo_tracking_notification_action_finish),
                    buildStopConfirmationPendingIntent(11)
            );
            break;

        case PAUSED:
            bindNotificationButton(
                    views,
                    primaryContainerId,
                    primaryIconId,
                    primaryTextId,
                    R.drawable.ic_play_arrow_24,
                    tr(R.string.mo_tracking_notification_action_resume),
                    buildServiceActionPendingIntent(ACTION_NOTIFICATION_RESUME, 12)
            );
            bindNotificationButton(
                    views,
                    secondaryContainerId,
                    secondaryIconId,
                    secondaryTextId,
                    R.drawable.ic_stop_notification_action,
                    tr(R.string.mo_tracking_notification_action_finish),
                    buildStopConfirmationPendingIntent(13)
            );
            break;

        case AUTO_PAUSED:
            if (currentPauseReason == TrackingState.PauseReason.SUSPICIOUS_SPEED) {
                bindNotificationButton(
                        views,
                        primaryContainerId,
                        primaryIconId,
                        primaryTextId,
                        R.drawable.ic_rate_review_24,
                        tr(R.string.mo_tracking_notification_action_review),
                        buildNotificationContentIntent(14)
                );
            } else {
                bindNotificationButton(
                        views,
                        primaryContainerId,
                        primaryIconId,
                        primaryTextId,
                        R.drawable.ic_open_notification_action,
                        tr(R.string.mo_tracking_notification_action_open),
                        buildNotificationContentIntent(15)
                );
            }

            bindNotificationButton(
                    views,
                    secondaryContainerId,
                    secondaryIconId,
                    secondaryTextId,
                    R.drawable.ic_stop_notification_action,
                    tr(R.string.mo_tracking_notification_action_finish),
                    buildStopConfirmationPendingIntent(16)
            );
            break;

        case FINISHED:
        case IDLE:
        default:
            bindNotificationButton(
                    views,
                    primaryContainerId,
                    primaryIconId,
                    primaryTextId,
                    R.drawable.ic_open_notification_action,
                    tr(R.string.mo_tracking_notification_action_open),
                    buildNotificationContentIntent(17)
            );
            bindNotificationButton(
                    views,
                    secondaryContainerId,
                    secondaryIconId,
                    secondaryTextId,
                    R.drawable.ic_stop_notification_action,
                    tr(R.string.mo_tracking_notification_action_finish),
                    buildStopConfirmationPendingIntent(18)
            );
            break;
    }
}

private void bindNotificationButton(
        @NonNull RemoteViews views,
        int containerId,
        int iconId,
        int textId,
        @DrawableRes int iconResId,
        @NonNull String label,
        @NonNull PendingIntent pendingIntent
) {
            // Los iconos de acciones en RemoteViews deben resolverse por recurso
        // (base y -night) para que claro/oscuro funcionen en todos los fabricantes.
        views.setImageViewResource(iconId, iconResId);
    views.setTextViewText(textId, label);
    views.setOnClickPendingIntent(containerId, pendingIntent);
    views.setOnClickPendingIntent(iconId, pendingIntent);
    views.setOnClickPendingIntent(textId, pendingIntent);
}

@NonNull
private PendingIntent buildNotificationContentIntent(int requestCode) {
    Intent tapIntent = new Intent(this, MainActivity.class);
    tapIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    return PendingIntent.getActivity(
            this,
            requestCode,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
    );
}

/**
 * Construye la acción "Detener" de la notificación para abrir la app y mostrar
 * el mismo diálogo Guardar / Cancelar / Descartar que existe en Inicio.
 */
@NonNull
private PendingIntent buildStopConfirmationPendingIntent(int requestCode) {
    Intent intent = MainActivity.createLaunchIntentToShowTrackingStopDialog(this);
    return PendingIntent.getActivity(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
    );
}

/**
 * Compatibilidad defensiva para notificaciones antiguas que todavía apunten a la
 * acción de servicio de "Detener". En vez de cerrar en seco, reenviamos a la app.
 */
private void openAppForStopConfirmation() {
    Intent intent = MainActivity.createLaunchIntentToShowTrackingStopDialog(this);
    startActivity(intent);
}

@NonNull
private PendingIntent buildServiceActionPendingIntent(@NonNull String action, int requestCode) {
    Intent intent = new Intent(this, TrackingService.class);
    intent.setAction(action);
    return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
    );
}

    @NonNull
    private String buildNotificationTitle() {
        switch (currentStatus) {
            case RUNNING:
                return tr(
                        R.string.mo_tracking_notification_title_running,
                        buildNotificationActivityTitleLabel()
                );

            case PAUSED:
                return tr(R.string.mo_tracking_notification_title_manual_pause);

            case AUTO_PAUSED:
                if (currentPauseReason == TrackingState.PauseReason.SUSPICIOUS_SPEED) {
                    return tr(R.string.mo_tracking_notification_title_suspicious_speed);
                }
                return tr(R.string.mo_tracking_notification_title_auto_pause);

            case FINISHED:
                return tr(R.string.mo_tracking_notification_title);

            case IDLE:
            default:
                return tr(R.string.mo_tracking_notification_title);
        }
    }

    @NonNull
    private String buildNotificationCompactText() {
        String distanceText = formatNotificationDistance();
        String instantPace = calculateInstantPace();

        switch (currentStatus) {
            case RUNNING:
                if (instantPace != null) {
                    return distanceText + " · " + instantPace + "/km";
                }
                return distanceText + " · " + tr(R.string.mo_tracking_notification_status_live_short);

            case PAUSED:
                return distanceText + " · " + tr(R.string.mo_tracking_notification_status_paused_short);

            case AUTO_PAUSED:
                if (currentPauseReason == TrackingState.PauseReason.SUSPICIOUS_SPEED) {
                    return distanceText + " · "
                            + tr(R.string.mo_tracking_notification_status_review_short);
                }
                return distanceText + " · "
                        + tr(R.string.mo_tracking_notification_status_waiting_short);

            case FINISHED:
            case IDLE:
            default:
                return formatElapsed(elapsedSeconds) + " · " + distanceText;
        }
    }

    @NonNull
    private String buildNotificationExpandedText() {
        String elapsedLine = tr(
                R.string.mo_tracking_notification_line_elapsed,
                formatElapsed(elapsedSeconds)
        );
        String distanceLine = tr(
                R.string.mo_tracking_notification_line_distance,
                formatNotificationDistance()
        );
        String movingStoppedLine = tr(
                R.string.mo_tracking_notification_line_moving_stopped,
                formatElapsed(movingSeconds),
                formatElapsed(stoppedSeconds)
        );

        String averagePace = calculateAverageMovingPace();
        String paceText = (averagePace != null ? averagePace : tr(R.string.tracking_default_pace)) + "/km";
        String caloriesText = tr(R.string.tracking_calories_format, calories);
        String paceCaloriesLine = tr(
                R.string.mo_tracking_notification_line_pace_calories,
                paceText,
                caloriesText
        );

        StringBuilder expanded = new StringBuilder();

        if (currentStatus == TrackingState.Status.AUTO_PAUSED) {
            if (currentPauseReason == TrackingState.PauseReason.SUSPICIOUS_SPEED) {
                expanded.append(tr(R.string.mo_tracking_notification_review_required)).append('\n');
            } else {
                expanded.append(tr(R.string.mo_tracking_notification_waiting_for_movement)).append('\n');
            }
        } else if (currentStatus == TrackingState.Status.PAUSED) {
            expanded.append(tr(R.string.tracking_status_manual_pause)).append('\n');
        }

        expanded.append(elapsedLine)
                .append('\n')
                .append(distanceLine)
                .append('\n')
                .append(movingStoppedLine)
                .append('\n')
                .append(paceCaloriesLine);

        return expanded.toString();
    }

    @NonNull
    private String buildNotificationSummaryText() {
        switch (currentStatus) {
            case RUNNING:
                return tr(
                        R.string.mo_tracking_notification_summary_running,
                        formatElapsed(movingSeconds),
                        formatElapsed(stoppedSeconds)
                );

            case PAUSED:
                return tr(R.string.mo_tracking_notification_status_paused_short);

            case AUTO_PAUSED:
                if (currentPauseReason == TrackingState.PauseReason.SUSPICIOUS_SPEED) {
                    return tr(R.string.mo_tracking_notification_status_review_short);
                }
                return tr(R.string.mo_tracking_notification_status_waiting_short);

            case FINISHED:
            case IDLE:
            default:
                return tr(R.string.mo_tracking_notification_title);
        }
    }

    @NonNull
    private String buildNotificationActivityLabel() {
        if (activityType == TrackingState.ActivityType.RUNNING_ACTIVITY) {
            return tr(R.string.inicio_running);
        }
        return tr(R.string.inicio_walking);
    }

    @NonNull
    private String buildNotificationActivityTitleLabel() {
        if (activityType == TrackingState.ActivityType.RUNNING_ACTIVITY) {
            return tr(R.string.mo_tracking_notification_activity_run);
        }
        return tr(R.string.mo_tracking_notification_activity_walk);
    }

    @NonNull
    private String formatNotificationDistance() {
        if (distanceMeters >= 1000) {
            return tr(R.string.tracking_distance_km_format, distanceMeters / 1000.0f);
        }
        return tr(R.string.tracking_distance_m_format, distanceMeters);
    }

    private void updateNotification() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, buildNotification());
        }
    }


    /**
     * Devuelve un contexto de recursos envuelto con el idioma activo.
     *
     * <p>El tracking vive dentro de un {@link Service}, así que no pasa por
     * {@code Activity.attachBaseContext()}. Sin este paso, la notificación puede
     * seguir resolviendo cadenas en el idioma anterior aunque la UI ya haya
     * cambiado al nuevo idioma.</p>
     */
    @NonNull
    private Context notificationTextContext() {
        return AppLanguageManager.localizedContext(this);
    }

    /**
     * Atajo para resolver una cadena simple con el idioma activo de la app.
     */
    @NonNull
    private String tr(@StringRes int resId) {
        return notificationTextContext().getString(resId);
    }

    /**
     * Atajo para resolver una cadena con argumentos y el idioma activo de la app.
     */
    @NonNull
    private String tr(@StringRes int resId, @NonNull Object... args) {
        return notificationTextContext().getString(resId, args);
    }

    @NonNull
    private String formatElapsed(long seconds) {
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long secs = seconds % 60L;
        if (hours > 0L) {
            return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, secs);
        }
        return String.format(Locale.US, "%02d:%02d", minutes, secs);
    }
}
