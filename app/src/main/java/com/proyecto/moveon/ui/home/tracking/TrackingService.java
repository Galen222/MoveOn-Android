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
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
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
import com.proyecto.moveon.core.settings.AppSettingsManager;
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

    /**
     * Solicita la detención explícita del servicio foreground usando siempre el contexto de aplicación.
     *
     * @param context contexto desde el que se quiere parar el servicio; se normaliza a
     *                {@link Context#getApplicationContext()} para evitar fugas.
     */
    public static void stopService(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        Intent intent = new Intent(appContext, TrackingService.class);
        appContext.stopService(intent);
    }

    private static final String TAG = "TrackingService";
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
    private static final int ACCEL_SAMPLE_WINDOW = 20;

    /**
     * Confirmaciones mínimas para promocionar a carrera.
     */
    private static final int SENSOR_CONFIRM_STEPS_TO_RUNNING = 2;

    /**
     * Confirmaciones mínimas para degradar de carrera a caminata con acelerómetro.
     */
    private static final int SENSOR_CONFIRM_STEPS_TO_WALKING = 4;

    /**
     * Confirmaciones mínimas para promocionar a carrera mediante GPS.
     */
    private static final int GPS_CONFIRM_STEPS_TO_RUNNING = 2;

    /**
     * Confirmaciones mínimas para degradar de carrera a caminata mediante GPS.
     */
    private static final int GPS_CONFIRM_STEPS_TO_WALKING = 4;

    /**
     * Muestras GPS válidas mínimas antes de usar la velocidad para cambiar andar/correr.
     *
     * <p>Evita que un pico aislado de GPS active “correr” al inicio o con deriva de señal.</p>
     */
    private static final int GPS_ACTIVITY_MIN_SPEED_SAMPLES = 3;

    /**
     * Magnitud esperada del acelerómetro cuando el teléfono está quieto, expresada en g.
     */
    private static final float ACCEL_RESTING_GRAVITY_G = 1.0f;

    private static final float ACCEL_ALPHA = 0.2f;
    private static final float MIN_ACCEL_CHANGE = 0.05f;

    private static final long LOCATION_INTERVAL_MS = 3_000L;
    private static final long LOCATION_FASTEST_MS = 1_500L;
    private static final float LOCATION_MIN_DISTANCE_M = 3.0f;
    private static final float LOCATION_MIN_ACCURACY_M = 20.0f;

    private static final float MAX_HUMAN_SPEED_MS = 5.556f; // 20 km/h
    private static final int SPEED_ALERT_CONSECUTIVE = 3;
    private static final int AUTO_PAUSE_STATIONARY_CONSECUTIVE = 4;
    private static final int AUTO_RESUME_MOVING_CONSECUTIVE = 2;
    private static final float MOVING_SPEED_THRESHOLD_MS = 0.90f;
    private static final float STATIONARY_SPEED_THRESHOLD_MS = 0.40f;
    private static final float MIN_VALID_DISTANCE_M = 4.0f;
    private static final float MAX_VALID_ACCURACY_FOR_SPEED_ALERT_M = 15f;
    private static final int SPEED_WINDOW_SIZE = 5;
    private static final long STOPPED_GRACE_PERIOD_MS = 12_000L;
    private static final long AUTO_PAUSE_INACTIVITY_MS = 20_000L;
    private static final float MOTION_EVIDENCE_DELTA_G = 0.08f;
    private static final long RECENT_MOTION_EVIDENCE_MS = 8_000L;

    /**
     * Ventana durante la que una lectura de velocidad sospechosa bloquea la auto-pausa
     * por inactividad. En vehículo puede haber velocidad real, pero no distancia válida
     * para la ruta y sin esta protección el temporizador puede alternar entre aviso de
     * velocidad alta y auto-pausa por parado.
     */
    private static final long RECENT_SUSPICIOUS_SPEED_MS = 15_000L;

    /**
     * Factor aplicado a la precisión actual para decidir cuánto debe medir un salto GPS
     * antes de contarlo como distancia válida.
     *
     * <p>Se reduce desde 0.60f a 0.35f para evitar infra-medición cuando el usuario se
     * mueve de verdad pero el GPS tiene una precisión solo aceptable.</p>
     */
    private static final float MIN_MOVING_DISTANCE_ACCURACY_FACTOR = 0.35f;

    /**
     * Precisión máxima admitida para acumular distancia sobre la ruta.
     *
     * <p>El punto puede seguir sirviendo para estado o clasificación, pero no suma metros
     * si la precisión es peor que este umbral porque ahí es donde suelen aparecer los
     * saltos fantasma que inflan distancia frente al reloj.</p>
     */
    private static final float MAX_VALID_ACCURACY_FOR_DISTANCE_ACCUMULATION_M = 12.0f;

    /**
     * Umbral más estricto para sumar distancia que el usado para detectar movimiento.
     *
     * <p>La actividad puede seguir marcada como en movimiento con GPS aceptable, pero para
     * añadir metros a la ruta exigimos un salto más claramente superior al error GPS.</p>
     */
    private static final float DISTANCE_ACCUMULATION_ACCURACY_FACTOR = 0.60f;

    /**
     * Confirmaciones mínimas consecutivas para empezar a acumular distancia.
     *
     * <p>Con esto evitamos contar un único salto aislado que parezca válido pero sea ruido.
     * Cuando llega la segunda muestra consistente, se suma toda la distancia acumulada desde
     * el último punto aceptado, así que no se pierden los metros legítimos entre ambas.</p>
     */
    private static final int DISTANCE_ACCUMULATION_CONFIRMATION_SAMPLES = 2;

    /**
     * Fallback por velocidad GPS para detectar carrera aunque el acelerómetro no rebote
     * lo suficiente como para superar el umbral de running.
     */
    private static final float GPS_RUNNING_SPEED_THRESHOLD_MS = 2.20f; // ~7.92 km/h

    /**
     * Umbral superior razonable para considerar que un movimiento válido todavía encaja
     * mejor con andar que con correr.
     */
    private static final float GPS_WALKING_SPEED_THRESHOLD_MS = 1.70f; // ~6.12 km/h

    /**
     * Umbral claramente propio de carrera usado para forzar RUNNING cuando el GPS es inequívoco.
     */
    private static final float GPS_STRONG_RUNNING_SPEED_THRESHOLD_MS = 2.60f; // ~9.36 km/h

    /**
     * Ventana de gracia tras iniciar/reanudar para evitar degradaciones prematuras a caminata.
     */
    private static final long ACTIVITY_TYPE_DOWNGRADE_GRACE_MS = 12_000L;

    /**
     * Número mínimo de muestras recientes necesarias para consolidar un ritmo máximo útil.
     */
    private static final int MAX_PACE_MIN_SAMPLE_COUNT = 5;

    /**
     * Margen aplicado a la velocidad plausible para limitar saltos GPS grandes.
     *
     * <p>No recorta el movimiento normal, pero sí evita que una o dos lecturas GPS ruidosas
     * inflen decenas de metros frente a lo que haría un reloj deportivo.</p>
     */
    private static final float GPS_DISTANCE_CAP_SPEED_FACTOR = 1.35f;

    /**
     * Parte de la precisión actual que se permite sumar como holgura al cap dinámico.
     */
    private static final float GPS_DISTANCE_CAP_ACCURACY_WEIGHT = 0.35f;

    /**
     * Si un salto supera ampliamente el cap dinámico, se rechaza por completo.
     */
    private static final float GPS_DISTANCE_HARD_REJECT_MULTIPLIER = 2.40f;

    /**
     * Mínimo multiplicador sobre el umbral base para considerar un salto como totalmente implausible.
     */
    private static final float GPS_DISTANCE_HARD_REJECT_THRESHOLD_MULTIPLIER = 3.00f;

    private final MutableLiveData<TrackingState> stateLiveData =
            new MutableLiveData<>(TrackingState.idle());
    private final MutableLiveData<TrackingAlert> trackingAlertLiveData = new MutableLiveData<>();

    /**
     * Binder local para exponer el propio servicio al controlador.
     */
    public final class LocalBinder extends Binder {
        @NonNull
        /**
         * Devuelve la instancia viva del servicio enlazado para que el controlador pueda invocar su API pública.
         *
         * @return servicio de tracking actualmente expuesto por este binder local.
         */
        public TrackingService getService() {
            return TrackingService.this;
        }
    }

    private final IBinder binder = new LocalBinder();

    @NonNull
    /**
     * Expone el flujo observable con el snapshot completo del tracking.
     *
     * @return {@link LiveData} que publica estados construidos por {@link #publishState()}.
     */
    public LiveData<TrackingState> getStateLiveData() {
        return stateLiveData;
    }

    @NonNull
    /**
     * Expone los avisos transitorios asociados a auto-pausas y detección de velocidad sospechosa.
     *
     * @return {@link LiveData} con alertas consumibles por la UI de tracking.
     */
    public LiveData<TrackingAlert> getTrackingAlertLiveData() {
        return trackingAlertLiveData;
    }

    private TrackingState.Status currentStatus = TrackingState.Status.IDLE;
    private TrackingState.PauseReason currentPauseReason = TrackingState.PauseReason.NONE;
    private TrackingState.ActivityType activityType = TrackingState.ActivityType.WALKING;

    private long elapsedSeconds = 0L;
    private long movingSeconds = 0L;
    private long stoppedSeconds = 0L;
    private long autoPausedSeconds = 0L;
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
    private int steps = 0;
    private double userWeightKg = 70.0;
    private int highSpeedCount = 0;
    private int consecutiveStationarySamples = 0;
    private int consecutiveMovingSamples = 0;
    private int autoPauseCount = 0;
    private int manualPauseCount = 0;
    private int suspiciousSpeedEventCount = 0;
    private int maxSpeedKmhX100 = 0;
    private int consecutiveDistanceAccumulationSamples = 0;

    /** Mejor ritmo sostenido detectado sobre ventana suavizada, en seg/km. */
    private double maxPaceSecondsPerKm = Double.POSITIVE_INFINITY;

    @Nullable private Location lastAcceptedLocation = null;
    @Nullable private Location lastObservedLocation = null;
    private long lastAcceptedRealtimeMs = 0L;
    private long lastObservedRealtimeMs = 0L;
    private long manualPauseStartedRealtimeMs = 0L;
    private long manualPausedAccumulatedMs = 0L;
    private long sessionStartedRealtimeMs = 0L;
    private long sessionFinishedRealtimeMs = 0L;
    private long lastMovementRealtimeMs = 0L;
    private long lastMotionEvidenceRealtimeMs = 0L;
    private long lastSuspiciousSpeedRealtimeMs = 0L;
    private long activityTypeDowngradeGraceDeadlineRealtimeMs = 0L;
    private boolean currentMovementSample = false;
    private long runningClassifiedSeconds = 0L;
    private long walkingClassifiedSeconds = 0L;
    private long sessionStartedAtEpochMs = 0L;
    private long sessionFinishedAtEpochMs = 0L;
    private long lastTimerTickAtEpochMs = 0L;
    private long serviceCreatedAtEpochMs = 0L;
    private long serviceDestroyedAtEpochMs = 0L;
    private int serviceRestartCount = 0;
    private final List<TrackingState.DiagnosticEvent> diagnosticEvents = new ArrayList<>();

    private final ArrayDeque<Float> recentMovingSpeeds = new ArrayDeque<>();
    private final List<LatLng> routePoints = new ArrayList<>();

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    /**
     * Persistencia ligera de la sesión viva.
     *
     * <p>Se inicializa en {@link #onCreate()} y no en el inicializador de campo para evitar
     * crashes durante la construcción del servicio, cuando Android todavía no ha adjuntado
     * completamente el {@link Context} base.</p>
     */
    @Nullable private TrackingSessionStore sessionStore;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    @Nullable private ScheduledFuture<?> timerFuture = null;

    private FusedLocationProviderClient fusedLocationClient;
    private SensorManager sensorManager;
    @Nullable private Sensor accelerometer;
    @Nullable private Sensor stepDetector;
    @Nullable private Sensor stepCounter;
    private float lastStepCounterValue = -1f;
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

    private float accelFilteredMag = ACCEL_RESTING_GRAVITY_G;

    private final LocationCallback locationCallback = new LocationCallback() {
        @Override
        /**
         * Recibe el último lote de localizaciones del proveedor fused y filtra rápidamente lecturas nulas o demasiado imprecisas.
         *
         * @param result paquete de localizaciones entregado por Google Play Services.
         */
        public void onLocationResult(@NonNull LocationResult result) {
            Location location = result.getLastLocation();
            if (location == null) return;
            if (location.getAccuracy() > LOCATION_MIN_ACCURACY_M) return;
            onNewLocation(location);
        }
    };

    @Override
    /**
     * Inicializa dependencias del servicio, canal de notificación y posible restauración de sesión persistida.
     */
    public void onCreate() {
        super.onCreate();
        sessionStore = new TrackingSessionStore(getApplicationContext());
        serviceCreatedAtEpochMs = System.currentTimeMillis();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            stepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
            stepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        }
        createNotificationChannel();
        logDiagnosticEvent("SERVICE_CREATED", null);
        restoreSessionIfPossible();

        // Si no había una sesión activa que restaurar, publica igualmente el
        // estado IDLE con la disponibilidad real del sensor de pasos. De este
        // modo la UI no conserva el valor provisional del arranque.
        if (currentStatus == TrackingState.Status.IDLE) {
            publishState();
        }
    }

    @Override
    /**
     * Atiende acciones disparadas desde la notificación y asegura que el servicio siga en foreground.
     *
     * @param intent intención recibida al arrancar o reentregar el servicio.
     * @param flags flags de reinicio proporcionados por Android.
     * @param startId identificador de esta petición de arranque.
     * @return {@link #START_STICKY} para permitir recreación del servicio si el proceso muere.
     */
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
    /**
     * Devuelve el binder local que permite a la capa UI interactuar con el servicio ya creado.
     *
     * @param intent intent de enlace recibido por Android.
     * @return binder local con acceso a {@link TrackingService}.
     */
    public IBinder onBind(@NonNull Intent intent) {
        return binder;
    }

    @Override
    /**
     * Persiste el último snapshot, detiene sensores y libera el scheduler antes de destruir el servicio.
     */
    public void onDestroy() {
        serviceDestroyedAtEpochMs = System.currentTimeMillis();
        logDiagnosticEvent("SERVICE_DESTROYED", null);
        persistSessionSnapshot();
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
            consecutiveDistanceAccumulationSamples = 0;
            recentMovingSpeeds.clear();
            armActivityTypeDowngradeGracePeriod();
            elapsedSeconds = computeElapsedSecondsNow();
            persistSessionSnapshot();
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
            // Marca el inicio real de la sesión para que el tiempo total se derive de timestamps absolutos.
            sessionStartedRealtimeMs = nowRealtime;
            sessionStartedAtEpochMs = System.currentTimeMillis();
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
        consecutiveDistanceAccumulationSamples = 0;
        recentMovingSpeeds.clear();
        armActivityTypeDowngradeGracePeriod();

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
        consecutiveDistanceAccumulationSamples = 0;
        manualPauseCount++;
        manualPauseStartedRealtimeMs = SystemClock.elapsedRealtime();
        logDiagnosticEvent("MANUAL_PAUSE", null);

        // Recalcula el total real justo en el instante de pausar para no depender del último tick.
        elapsedSeconds = computeElapsedSecondsNow();
        stopTimer();
        stopLocationUpdates();
        stopAccelerometer();
        persistSessionSnapshot();
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
        sessionFinishedRealtimeMs = SystemClock.elapsedRealtime();
        sessionFinishedAtEpochMs = System.currentTimeMillis();
        elapsedSeconds = computeElapsedSecondsNow();
        logDiagnosticEvent("TRACKING_FINISHED", null);
        currentPauseReason = TrackingState.PauseReason.NONE;
        currentMovementSample = false;

        stopTrackingInternal();
        persistSessionSnapshot();
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
        logDiagnosticEvent("TRACKING_RESET", null);
        resetInternalState();
        if (sessionStore != null) {
            sessionStore.clear();
        }
        currentStatus = TrackingState.Status.IDLE;
        currentPauseReason = TrackingState.PauseReason.NONE;
        publishState();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @SuppressWarnings("MissingPermission")
    /**
     * Activa las actualizaciones de localización con la configuración de frecuencia y distancia mínima del tracking.
     *
     * <p>Se anota con {@code MissingPermission} porque la comprobación de permisos ocurre fuera, en la capa que controla el servicio.</p>
     */
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

    /**
     * Cancela la suscripción actual a localizaciones para que el servicio no siga consumiendo GPS en pausa o fin de sesión.
     */
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
            // Primera lectura válida de la sesión: queda registrada como punto observado y aceptado.
            lastObservedLocation = location;
            lastAcceptedLocation = location;
            lastAcceptedRealtimeMs = nowRealtime;
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
        long acceptedDeltaTimeMs = computeAcceptedDeltaTimeMs(nowRealtime);
        float derivedSpeedMs = deltaTimeMs > 0
                ? (observedDeltaMeters / (deltaTimeMs / 1000f))
                : 0f;
        float resolvedSpeedMs = resolveSpeedMs(location, derivedSpeedMs);

        // El "último observado" siempre avanza, aunque este punto no acabe contando
        // distancia. Así la velocidad y el filtrado trabajan con muestras frescas.
        lastObservedLocation = location;
        lastObservedRealtimeMs = nowRealtime;

        if (isSuspiciousVehicleSpeed(location, resolvedSpeedMs)) {
            lastSuspiciousSpeedRealtimeMs = nowRealtime;
            currentMovementSample = false;
            consecutiveMovingSamples = 0;
            consecutiveStationarySamples = 0;
            consecutiveDistanceAccumulationSamples = 0;
            recentMovingSpeeds.clear();

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
            trackSpeedWindow(location, resolvedSpeedMs);
            updateMaxSpeed(resolvedSpeedMs);
            updateMaxPaceFromRecentWindow();

            // Fallback: si la velocidad GPS encaja claramente con carrera o con andar,
            // se usa para reforzar el tipo de actividad aunque el acelerómetro sea pobre.
            updateActivityTypeFromGps(resolvedSpeedMs);

            if (currentStatus == TrackingState.Status.AUTO_PAUSED
                    && currentPauseReason != TrackingState.PauseReason.SUSPICIOUS_SPEED
                    && consecutiveMovingSamples >= AUTO_RESUME_MOVING_CONSECUTIVE) {
                currentStatus = TrackingState.Status.RUNNING;
                currentPauseReason = TrackingState.PauseReason.NONE;
                armActivityTypeDowngradeGracePeriod();
            }

            if (currentStatus == TrackingState.Status.RUNNING) {
                float sanitizedAcceptedDeltaMeters = sanitizeAcceptedDeltaMeters(
                        location,
                        acceptedDeltaMeters,
                        acceptedDeltaTimeMs,
                        resolvedSpeedMs,
                        movingSample
                );

                boolean accumulableDistanceSample = isDistanceAccumulableSample(
                        location,
                        sanitizedAcceptedDeltaMeters,
                        movingSample
                );
                if (accumulableDistanceSample) {
                    consecutiveDistanceAccumulationSamples++;
                } else {
                    consecutiveDistanceAccumulationSamples = 0;
                }

                if (shouldAccumulateDistance(location, sanitizedAcceptedDeltaMeters, movingSample)) {
                    // Se suma la distancia desde el último punto aceptado, no desde el último
                    // observado. Así evitamos "evaporar" metros en saltos pequeños consecutivos.
                    //
                    // Además se aplica una segunda capa anti-ruido que limita saltos muy por encima
                    // de la velocidad humana plausible para el intervalo real entre puntos aceptados.
                    preciseDistanceMeters += sanitizedAcceptedDeltaMeters;
                    syncRoundedDistanceMeters();
                    acceptRoutePoint(location);
                    lastAcceptedLocation = location;
                    lastAcceptedRealtimeMs = nowRealtime;
                }
            } else {
                consecutiveDistanceAccumulationSamples = 0;
            }
        } else if (stationarySample) {
            consecutiveStationarySamples++;
            consecutiveMovingSamples = 0;
            consecutiveDistanceAccumulationSamples = 0;
            recentMovingSpeeds.clear();

            if (currentStatus == TrackingState.Status.RUNNING
                    && consecutiveStationarySamples >= AUTO_PAUSE_STATIONARY_CONSECUTIVE) {
                enterAutoPause(
                        TrackingState.PauseReason.STATIONARY,
                        TrackingAlert.Type.STATIONARY_AUTO_PAUSE
                );
            }
        } else {
            consecutiveDistanceAccumulationSamples = 0;
        }

        publishState();
        updateNotification();
    }

    /**
     * Añade el punto aceptado a la ruta si realmente cambia respecto al último vértice guardado.
     *
     * @param location localización que acaba de validarse como parte útil de la ruta.
     */
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

    /**
     * Calcula el intervalo temporal entre dos muestras observadas priorizando los timestamps del proveedor GPS.
     *
     * @param previous localización observada inmediatamente anterior.
     * @param current localización recién recibida.
     * @param nowRealtime reloj monotónico actual como fallback defensivo.
     * @return milisegundos transcurridos entre ambas muestras, nunca menores que 1.
     */
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

    /**
     * Calcula el intervalo desde el último punto aceptado para usarlo al limitar saltos GPS grandes.
     *
     * @param nowRealtime instante monotónico actual.
     * @return milisegundos transcurridos desde el último punto aceptado u observado.
     */
    private long computeAcceptedDeltaTimeMs(long nowRealtime) {
        if (lastAcceptedRealtimeMs > 0L) {
            return Math.max(1L, nowRealtime - lastAcceptedRealtimeMs);
        }
        if (lastObservedRealtimeMs > 0L) {
            return Math.max(1L, nowRealtime - lastObservedRealtimeMs);
        }
        return LOCATION_INTERVAL_MS;
    }

    private float sanitizeAcceptedDeltaMeters(
            @NonNull Location location,
            float acceptedDeltaMeters,
            long acceptedDeltaTimeMs,
            float speedMs,
            boolean movingSample) {
        if (!movingSample || acceptedDeltaMeters <= 0f) {
            return 0f;
        }

        float threshold = getDistanceAccumulationThreshold(location);
        if (acceptedDeltaMeters < threshold) {
            return acceptedDeltaMeters;
        }

        float plausibleSpeedMs = getPlausibleDistanceSpeedReferenceMs(speedMs);
        float intervalSeconds = Math.max(1f, acceptedDeltaTimeMs / 1000f);
        float slackMeters = Math.min(
                location.getAccuracy(),
                MAX_VALID_ACCURACY_FOR_DISTANCE_ACCUMULATION_M
        ) * GPS_DISTANCE_CAP_ACCURACY_WEIGHT;
        float dynamicCapMeters = Math.max(
                threshold,
                (plausibleSpeedMs * intervalSeconds * GPS_DISTANCE_CAP_SPEED_FACTOR) + slackMeters
        );
        float hardRejectMeters = Math.max(
                dynamicCapMeters * GPS_DISTANCE_HARD_REJECT_MULTIPLIER,
                threshold * GPS_DISTANCE_HARD_REJECT_THRESHOLD_MULTIPLIER
        );

        if (acceptedDeltaMeters > hardRejectMeters) {
            logDiagnosticEvent(
                    "GPS_DISTANCE_REJECTED",
                    String.format(
                            Locale.US,
                            "delta=%.2f cap=%.2f threshold=%.2f dt=%d acc=%.2f speed=%.2f",
                            acceptedDeltaMeters,
                            dynamicCapMeters,
                            threshold,
                            acceptedDeltaTimeMs,
                            location.getAccuracy(),
                            speedMs
                    )
            );
            return 0f;
        }

        if (acceptedDeltaMeters > dynamicCapMeters) {
            logDiagnosticEvent(
                    "GPS_DISTANCE_CLIPPED",
                    String.format(
                            Locale.US,
                            "delta=%.2f clipped=%.2f threshold=%.2f dt=%d acc=%.2f speed=%.2f",
                            acceptedDeltaMeters,
                            dynamicCapMeters,
                            threshold,
                            acceptedDeltaTimeMs,
                            location.getAccuracy(),
                            speedMs
                    )
            );
            return dynamicCapMeters;
        }

        return acceptedDeltaMeters;
    }

    /**
     * Obtiene una referencia de velocidad plausible combinando velocidad actual, media reciente y el tipo de actividad.
     *
     * @param speedMs velocidad resuelta de la muestra actual.
     * @return velocidad de referencia limitada por {@link #MAX_HUMAN_SPEED_MS}.
     */
    private float getPlausibleDistanceSpeedReferenceMs(float speedMs) {
        float recentAverageMs = (float) getAverageRecentMovingSpeedMs();
        float activityFloorMs = activityType == TrackingState.ActivityType.RUNNING_ACTIVITY
                ? GPS_RUNNING_SPEED_THRESHOLD_MS
                : MOVING_SPEED_THRESHOLD_MS;
        float plausibleSpeedMs = Math.max(activityFloorMs, Math.max(speedMs, recentAverageMs));
        return Math.min(plausibleSpeedMs, MAX_HUMAN_SPEED_MS);
    }

    /**
     * Resuelve la velocidad final de una muestra mezclando la velocidad GPS nativa con la derivada por distancia/tiempo.
     *
     * @param location localización que puede incluir velocidad calculada por el proveedor.
     * @param derivedSpeedMs velocidad derivada a partir del salto observado.
     * @return velocidad final en metros por segundo nunca negativa.
     */
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

    /**
     * Marca una muestra como sospechosa de corresponder a vehículo cuando supera el límite humano con precisión fiable.
     *
     * @param location localización evaluada.
     * @param speedMs velocidad resuelta de la muestra.
     * @return {@code true} si la lectura debe contar para el detector de velocidad sospechosa.
     */
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
        return isDistanceAccumulableSample(location, acceptedDeltaMeters, movingSample)
                && consecutiveDistanceAccumulationSamples >= DISTANCE_ACCUMULATION_CONFIRMATION_SAMPLES;
    }

    /**
     * Decide si una muestra es lo bastante sólida como para sumar distancia real.
     *
     * <p>Este filtro es más estricto que el de movimiento general: la app puede seguir
     * considerando que el usuario está activo, pero no añadirá metros hasta que el GPS
     * tenga una precisión suficientemente buena y el salto supere un umbral más duro.</p>
     */
    private boolean isDistanceAccumulableSample(
            @NonNull Location location,
            float acceptedDeltaMeters,
            boolean movingSample) {
        return movingSample
                && location.getAccuracy() <= MAX_VALID_ACCURACY_FOR_DISTANCE_ACCUMULATION_M
                && acceptedDeltaMeters >= getDistanceAccumulationThreshold(location);
    }

    /**
     * Calcula el salto mínimo aceptable según la precisión actual del GPS.
     */
    private float getMovingDistanceThreshold(@NonNull Location location) {
        return Math.max(MIN_VALID_DISTANCE_M, location.getAccuracy() * MIN_MOVING_DISTANCE_ACCURACY_FACTOR);
    }

    /**
     * Umbral reforzado para acumular distancia en la ruta.
     */
    private float getDistanceAccumulationThreshold(@NonNull Location location) {
        return Math.max(MIN_VALID_DISTANCE_M, location.getAccuracy() * DISTANCE_ACCUMULATION_ACCURACY_FACTOR);
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

    /**
     * Indica si se ha detectado velocidad incompatible con actividad humana hace poco.
     *
     * <p>Sirve para no convertir un desplazamiento en vehículo en auto-pausa por parado
     * solo porque esos puntos no se usen para dibujar polilínea ni sumar distancia.</p>
     */
    private boolean hasRecentSuspiciousSpeed(long nowRealtime) {
        if (lastSuspiciousSpeedRealtimeMs <= 0L) {
            return false;
        }
        return (nowRealtime - lastSuspiciousSpeedRealtimeMs) <= RECENT_SUSPICIOUS_SPEED_MS;
    }

    /**
     * Alimenta la ventana suavizada de velocidades recientes solo con muestras útiles para ritmo y velocidad máxima.
     *
     * @param location localización que aporta la velocidad actual.
     * @param speedMs velocidad resuelta de la muestra.
     */
    private void trackSpeedWindow(@NonNull Location location, float speedMs) {
        if (speedMs <= 0f) {
            return;
        }
        if (location.getAccuracy() > MAX_VALID_ACCURACY_FOR_DISTANCE_ACCUMULATION_M) {
            // El mejor ritmo no debe contaminarse con velocidades calculadas sobre una lectura imprecisa.
            return;
        }
        recentMovingSpeeds.addLast(speedMs);
        while (recentMovingSpeeds.size() > SPEED_WINDOW_SIZE) {
            recentMovingSpeeds.removeFirst();
        }
    }

    /**
     * Actualiza la velocidad máxima registrada durante la sesión en centésimas de km/h.
     *
     * @param speedMs velocidad candidata en metros por segundo.
     */
    private void updateMaxSpeed(float speedMs) {
        if (speedMs <= 0f) {
            return;
        }
        int kmhX100 = (int) Math.round(speedMs * 3.6 * 100.0);
        if (kmhX100 > maxSpeedKmhX100) {
            maxSpeedKmhX100 = kmhX100;
        }
    }

    /**
     * Calcula la velocidad media reciente a partir de la ventana suavizada de GPS.
     */
    private double getAverageRecentMovingSpeedMs() {
        if (recentMovingSpeeds.isEmpty()) {
            return 0.0;
        }

        double total = 0.0;
        for (Float speed : recentMovingSpeeds) {
            total += speed;
        }
        return total / recentMovingSpeeds.size();
    }

    /**
     * Consolida el mejor ritmo sostenido reciente.
     *
     * <p>No usa el pico instantáneo bruto del GPS. En su lugar toma la media de la ventana
     * reciente para acercarse más al comportamiento de un reloj deportivo y evitar falsos
     * máximos por ruido o saltos aislados.</p>
     */
    private void updateMaxPaceFromRecentWindow() {
        if (currentStatus != TrackingState.Status.RUNNING) {
            return;
        }
        if (recentMovingSpeeds.size() < MAX_PACE_MIN_SAMPLE_COUNT) {
            return;
        }

        double averageSpeedMs = getAverageRecentMovingSpeedMs();
        if (averageSpeedMs < MOVING_SPEED_THRESHOLD_MS) {
            return;
        }

        double paceSecondsPerKm = 1000.0 / averageSpeedMs;
        if (paceSecondsPerKm >= 60.0 && paceSecondsPerKm <= 1800.0
                && paceSecondsPerKm < maxPaceSecondsPerKm) {
            maxPaceSecondsPerKm = paceSecondsPerKm;
        }
    }

    /**
     * Abre una ventana de protección temporal frente a degradaciones rápidas a WALKING.
     */
    private void armActivityTypeDowngradeGracePeriod() {
        activityTypeDowngradeGraceDeadlineRealtimeMs =
                SystemClock.elapsedRealtime() + ACTIVITY_TYPE_DOWNGRADE_GRACE_MS;
    }

    /**
     * Indica si ya se puede degradar la sesión a WALKING.
     */
    private boolean canDowngradeActivityTypeToWalking() {
        return SystemClock.elapsedRealtime() >= activityTypeDowngradeGraceDeadlineRealtimeMs;
    }

    /**
     * Aplica el tipo de actividad final y deja una traza útil de depuración.
     */
    private void applyActivityType(@NonNull TrackingState.ActivityType newType,
                                   @NonNull String source,
                                   @NonNull String reason) {
        if (activityType == newType) {
            return;
        }

        TrackingState.ActivityType previousType = activityType;
        activityType = newType;

        if (newType == TrackingState.ActivityType.RUNNING_ACTIVITY) {
            armActivityTypeDowngradeGracePeriod();
        }

        logClassificationChange(previousType, newType, source, reason);
        publishState();
    }

    /**
     * Registra el cambio de clasificación con suficiente contexto para depurar casos reales.
     */
    private void logClassificationChange(@NonNull TrackingState.ActivityType previousType,
                                         @NonNull TrackingState.ActivityType newType,
                                         @NonNull String source,
                                         @NonNull String reason) {
        String detail = previousType + "->" + newType
                + " source=" + source
                + " reason=" + reason
                + " sensorRun=" + runningConfirmCount
                + " sensorWalk=" + walkingConfirmCount
                + " gpsRun=" + gpsRunningConfirmCount
                + " gpsWalk=" + gpsWalkingConfirmCount
                + " gpsSamples=" + recentMovingSpeeds.size()
                + " avgSpeedMs=" + String.format(Locale.US, "%.2f", getAverageRecentMovingSpeedMs());

        Log.d(TAG, "activityType " + detail);
        logDiagnosticEvent("ACTIVITY_TYPE_CHANGE", detail);
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
        consecutiveDistanceAccumulationSamples = 0;
        recentMovingSpeeds.clear();

        if (pauseReason == TrackingState.PauseReason.STATIONARY) {
            autoPauseCount++;
        }

        Log.d(TAG, "enterAutoPause reason=" + pauseReason + " moving=" + consecutiveMovingSamples
                + " stationary=" + consecutiveStationarySamples
                + " avgSpeedMs=" + String.format(Locale.US, "%.2f", getAverageRecentMovingSpeedMs()));

        logDiagnosticEvent("AUTO_PAUSE", pauseReason.name());
        persistSessionSnapshot();
        trackingAlertLiveData.postValue(new TrackingAlert(alertType));
    }

    /**
     * Registra el listener del acelerómetro cuando el dispositivo dispone del sensor y el servicio está operativo.
     */
    private void startAccelerometer() {
        if (sensorManager == null) {
            return;
        }
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        }
        lastStepCounterValue = -1f;
        if (stepDetector != null) {
            sensorManager.registerListener(this, stepDetector, SensorManager.SENSOR_DELAY_NORMAL);
        } else if (stepCounter != null) {
            sensorManager.registerListener(this, stepCounter, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    /**
     * Desregistra el listener del acelerómetro para evitar lecturas en pausa o tras finalizar la sesión.
     */
    private void stopAccelerometer() {
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    /**
     * Procesa muestras del acelerómetro, extrae evidencia reciente de movimiento y alimenta el clasificador andar/correr.
     *
     * @param event evento del sensor recibido por Android.
     */
    public void onSensorChanged(@NonNull SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_STEP_DETECTOR) {
            if (currentStatus == TrackingState.Status.RUNNING
                    || currentStatus == TrackingState.Status.AUTO_PAUSED) {
                steps++;
                lastMotionEvidenceRealtimeMs = SystemClock.elapsedRealtime();
                publishState();
            }
            return;
        }
        if (event.sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
            float currentValue = event.values[0];
            if (lastStepCounterValue >= 0f
                    && (currentStatus == TrackingState.Status.RUNNING
                    || currentStatus == TrackingState.Status.AUTO_PAUSED)) {
                int delta = Math.max(0, Math.round(currentValue - lastStepCounterValue));
                if (delta > 0) {
                    steps += delta;
                    lastMotionEvidenceRealtimeMs = SystemClock.elapsedRealtime();
                    publishState();
                }
            }
            lastStepCounterValue = currentValue;
            return;
        }
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

        if (runningConfirmCount >= SENSOR_CONFIRM_STEPS_TO_RUNNING
                && activityType != TrackingState.ActivityType.RUNNING_ACTIVITY) {
            applyActivityType(
                    TrackingState.ActivityType.RUNNING_ACTIVITY,
                    "accelerometer",
                    "mostlyRunning=" + mostlyRunning
            );
            return;
        }

        if (!mostlyRunning
                && walkingConfirmCount >= SENSOR_CONFIRM_STEPS_TO_WALKING
                && activityType == TrackingState.ActivityType.RUNNING_ACTIVITY
                && canDowngradeActivityTypeToWalking()) {
            applyActivityType(
                    TrackingState.ActivityType.WALKING,
                    "accelerometer",
                    "mostlyRunning=" + mostlyRunning
            );
        }
    }

    /**
     * Refuerza la clasificación andar/correr a partir de velocidad GPS.

     *
     * <p>Solo actúa cuando la muestra ya fue considerada movimiento real. De este modo
     * no degradamos la clasificación por deriva GPS en parado.</p>
     */
    private void updateActivityTypeFromGps(float speedMs) {
        if (speedMs <= 0f) {
            return;
        }

        if (recentMovingSpeeds.size() < GPS_ACTIVITY_MIN_SPEED_SAMPLES) {
            return;
        }

        double averageRecentSpeedMs = getAverageRecentMovingSpeedMs();
        boolean strongRunning = averageRecentSpeedMs >= GPS_STRONG_RUNNING_SPEED_THRESHOLD_MS;
        boolean runningLike = averageRecentSpeedMs >= GPS_RUNNING_SPEED_THRESHOLD_MS;
        boolean walkingLike = speedMs <= GPS_WALKING_SPEED_THRESHOLD_MS
                && averageRecentSpeedMs > 0.0
                && averageRecentSpeedMs <= GPS_WALKING_SPEED_THRESHOLD_MS;

        if (strongRunning) {
            gpsRunningConfirmCount = GPS_CONFIRM_STEPS_TO_RUNNING;
            gpsWalkingConfirmCount = 0;
        } else if (runningLike) {
            gpsRunningConfirmCount++;
            gpsWalkingConfirmCount = 0;
        } else if (walkingLike) {
            gpsWalkingConfirmCount++;
            gpsRunningConfirmCount = 0;
        } else {
            // Zona neutra: reiniciamos confirmaciones para no arrastrar decisiones viejas.
            gpsRunningConfirmCount = 0;
            gpsWalkingConfirmCount = 0;
            return;
        }

        if (gpsRunningConfirmCount >= GPS_CONFIRM_STEPS_TO_RUNNING
                && activityType != TrackingState.ActivityType.RUNNING_ACTIVITY) {
            applyActivityType(
                    TrackingState.ActivityType.RUNNING_ACTIVITY,
                    "gps",
                    String.format(Locale.US, "speed=%.2f avg=%.2f", speedMs, averageRecentSpeedMs)
            );
            return;
        }

        if (walkingLike
                && gpsWalkingConfirmCount >= GPS_CONFIRM_STEPS_TO_WALKING
                && activityType == TrackingState.ActivityType.RUNNING_ACTIVITY
                && canDowngradeActivityTypeToWalking()) {
            applyActivityType(
                    TrackingState.ActivityType.WALKING,
                    "gps",
                    String.format(Locale.US, "speed=%.2f avg=%.2f", speedMs, averageRecentSpeedMs)
            );
        }
    }

    @Override
    /**
     * Callback obligatorio de {@link SensorEventListener} que aquí se ignora porque la clasificación no depende del nivel de precisión reportado.
     *
     * @param sensor sensor cuyo nivel de precisión ha cambiado.
     * @param accuracy nuevo nivel de precisión comunicado por el sistema.
     */
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

            lastTimerTickAtEpochMs = System.currentTimeMillis();
            elapsedSeconds = computeElapsedSecondsNow();

            if (currentStatus == TrackingState.Status.RUNNING) {
                long nowRealtime = SystemClock.elapsedRealtime();
                long inactivityMs = computeInactivityMs(nowRealtime);

                if (activityType == TrackingState.ActivityType.RUNNING_ACTIVITY) {
                    runningClassifiedSeconds++;
                } else {
                    walkingClassifiedSeconds++;
                }

                boolean motionStillFresh = hasRecentMotionEvidence(nowRealtime);
                if (currentMovementSample && motionStillFresh) {
                    // Solo el movimiento válido y reciente consume calorías y suma tiempo en movimiento.
                    movingSeconds++;
                    caloriesAccumulator += calculateCaloriesPerSecond();
                    calories = (int) Math.round(caloriesAccumulator);
                } else if (inactivityMs >= STOPPED_GRACE_PERIOD_MS) {
                    // Dejamos una gracia algo mayor antes de etiquetar como parado para no penalizar
                    // microcortes de GPS o tramos donde el acelerómetro llega con latencia.
                    currentMovementSample = false;
                    stoppedSeconds++;
                }

                if (inactivityMs >= AUTO_PAUSE_INACTIVITY_MS
                        && !hasRecentSuspiciousSpeed(nowRealtime)) {
                    enterAutoPause(
                            TrackingState.PauseReason.STATIONARY,
                            TrackingAlert.Type.STATIONARY_AUTO_PAUSE
                    );
                }
            } else if (currentStatus == TrackingState.Status.AUTO_PAUSED) {
                autoPausedSeconds++;
            }

            persistSessionSnapshot();
            publishState();
            updateNotification();
        }), 1L, 1L, TimeUnit.SECONDS);
    }

    /**
     * Cancela el timer periódico de sesión cuando existe una tarea programada todavía activa.
     */
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

    /**
     * Calcula las calorías por segundo según el peso configurado y el tipo de actividad actualmente clasificado.
     *
     * @return consumo energético instantáneo estimado por segundo.
     */
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

    /**
     * Calcula el ritmo instantáneo a partir de la velocidad media reciente suavizada.
     *
     * @return ritmo instantáneo formateado o {@code null} si no hay datos suficientes.
     */
    @Nullable
    private String calculateInstantPace() {
        if (currentStatus != TrackingState.Status.RUNNING || recentMovingSpeeds.isEmpty()) {
            return null;
        }

        double averageSpeedMs = getAverageRecentMovingSpeedMs();
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
        return formatPaceFromTotals(calculateEffectiveElapsedSeconds(), preciseDistanceMeters);
    }

    /**
     * Tiempo efectivo de actividad usado para duración total y ritmo medio total.
     *
     * <p>Excluye pausas manuales y también el tiempo transcurrido mientras la sesión
     * estuvo en auto-pausa. Así el histórico queda mucho más cerca del reloj, que no
     * suele penalizar el ritmo total con minutos completos sin movimiento real.</p>
     */
    private long calculateEffectiveElapsedSeconds() {
        return movingSeconds + stoppedSeconds;
    }

    /**
     * Devuelve el ritmo medio que debe mostrarse según la preferencia actual de la app.
     *
     * @return ritmo medio en movimiento o total, según {@link AppSettingsManager#isPaceDisplayMoving(Context)}.
     */
    @Nullable
    private String calculatePreferredAveragePace() {
        if (AppSettingsManager.isPaceDisplayMoving(this)) {
            String movingPace = calculateAverageMovingPace();
            if (movingPace != null) {
                return movingPace;
            }
        }
        return calculateAverageElapsedPace();
    }

    /**
     * Devuelve el mejor ritmo sostenido detectado durante la sesión.
     */
    @Nullable
    private String calculateMaxPace() {
        if (!Double.isFinite(maxPaceSecondsPerKm)) {
            return null;
        }
        return formatPaceFromSeconds(maxPaceSecondsPerKm);
    }

    /**
     * Devuelve el mejor ritmo en formato numérico para persistencia y sincronización.
     */
    private int calculateMaxPaceSecondsPerKm() {
        if (!Double.isFinite(maxPaceSecondsPerKm)) {
            return 0;
        }
        return (int) Math.round(maxPaceSecondsPerKm);
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

    /**
     * Convierte una velocidad expresada en m/s al formato de ritmo por kilómetro usado en la UI.
     *
     * @param speedMs velocidad lineal en metros por segundo.
     * @return cadena con el ritmo formateado o {@code null} si la velocidad no es válida.
     */
    @Nullable
    private String formatPaceFromSpeed(double speedMs) {
        if (speedMs <= 0.0) {
            return null;
        }
        double paceSecondsPerKm = 1000.0 / speedMs;
        return formatPaceFromSeconds(paceSecondsPerKm);
    }

    /**
     * Formatea un ritmo expresado en segundos por kilómetro validando que caiga dentro de márgenes razonables.
     *
     * @param paceSecondsPerKm ritmo en segundos por kilómetro.
     * @return texto tipo {@code 5'12"} o {@code null} si el ritmo es implausible.
     */
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

    /**
     * Reconstruye el snapshot público del tracking y lo publica hacia la UI.
     *
     * <p>Cuando la sesión termina también serializa la ruta con {@link PolyUtil#encode(List)} para dejarla lista para persistencia o envío.</p>
     */
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
                .autoPausedSeconds(autoPausedSeconds)
                .manualPausedSeconds(manualPausedSeconds)
                // El estado público mantiene metros enteros para no romper compatibilidad.
                .distanceMeters(distanceMeters)
                .preciseDistanceMeters(preciseDistanceMeters)
                .calories(calories)
                .steps(steps)
                .stepSensorAvailable(stepDetector != null || stepCounter != null)
                .pace(calculateInstantPace())
                .averageMovingPace(calculateAverageMovingPace())
                .averageElapsedPace(calculateAverageElapsedPace())
                .maxPace(calculateMaxPace())
                .maxPaceSecondsPerKm(calculateMaxPaceSecondsPerKm())
                .maxSpeedKmhX100(maxSpeedKmhX100)
                .autoPauseCount(autoPauseCount)
                .manualPauseCount(manualPauseCount)
                .suspiciousSpeedEventCount(suspiciousSpeedEventCount)
                .routePoints(new ArrayList<>(routePoints))
                .currentLocation(locationToLatLng(lastObservedLocation != null ? lastObservedLocation : lastAcceptedLocation))
                .encodedPolyline(encodedPolyline)
                .runningClassifiedSeconds(runningClassifiedSeconds)
                .walkingClassifiedSeconds(walkingClassifiedSeconds)
                .sessionStartedAtEpochMs(sessionStartedAtEpochMs)
                .sessionFinishedAtEpochMs(sessionFinishedAtEpochMs)
                .lastTimerTickAtEpochMs(lastTimerTickAtEpochMs)
                .serviceCreatedAtEpochMs(serviceCreatedAtEpochMs)
                .serviceDestroyedAtEpochMs(serviceDestroyedAtEpochMs)
                .serviceRestartCount(serviceRestartCount)
                .diagnosticEvents(new ArrayList<>(diagnosticEvents))
                .build();

        stateLiveData.postValue(state);
    }


    /**
     * Calcula el tiempo total real a partir de timestamps absolutos de la sesión.
     *
     * <p>Ya no dependemos de un simple {@code elapsedSeconds++} en memoria como fuente
     * única de verdad, porque eso pierde tiempo real si el proceso muere y luego el
     * servicio se recrea.</p>
     */
    private long computeElapsedSecondsNow() {
        if (sessionStartedRealtimeMs <= 0L) {
            return 0L;
        }

        long referenceRealtimeMs = sessionFinishedRealtimeMs > 0L
                ? sessionFinishedRealtimeMs
                : SystemClock.elapsedRealtime();

        long pausedMs = manualPausedAccumulatedMs;
        if (currentStatus == TrackingState.Status.PAUSED && manualPauseStartedRealtimeMs > 0L) {
            pausedMs += Math.max(0L, referenceRealtimeMs - manualPauseStartedRealtimeMs);
        }

        long activeMs = Math.max(0L, referenceRealtimeMs - sessionStartedRealtimeMs - pausedMs);
        return TimeUnit.MILLISECONDS.toSeconds(activeMs);
    }

    /**
     * Registra un evento compacto de diagnóstico y limita su tamaño para no crecer sin control.
     */
    private void logDiagnosticEvent(@NonNull String type, @Nullable String detail) {
        diagnosticEvents.add(new TrackingState.DiagnosticEvent(System.currentTimeMillis(), type, detail));
        if (diagnosticEvents.size() > 200) {
            diagnosticEvents.remove(0);
        }
    }

    /**
     * Persiste un snapshot mínimo de la sesión viva para restauración tras muerte del proceso.
     */
    private void persistSessionSnapshot() {
        if (currentStatus == TrackingState.Status.IDLE) {
            if (sessionStore != null) {
            sessionStore.clear();
        }
            return;
        }

        if (sessionStore == null) {
            return;
        }

        TrackingSessionStore.Snapshot snapshot = new TrackingSessionStore.Snapshot();
        snapshot.status = currentStatus.name();
        snapshot.pauseReason = currentPauseReason.name();
        snapshot.activityType = activityType.name();
        snapshot.elapsedSeconds = elapsedSeconds;
        snapshot.movingSeconds = movingSeconds;
        snapshot.stoppedSeconds = stoppedSeconds;
        snapshot.autoPausedSeconds = autoPausedSeconds;
        snapshot.manualPausedSeconds = manualPausedSeconds;
        snapshot.manualPausedAccumulatedMs = manualPausedAccumulatedMs;
        snapshot.distanceMeters = distanceMeters;
        snapshot.preciseDistanceMeters = preciseDistanceMeters;
        snapshot.calories = calories;
        snapshot.caloriesAccumulator = caloriesAccumulator;
        snapshot.steps = steps;
        snapshot.maxPaceSecondsPerKm = calculateMaxPaceSecondsPerKm();
        snapshot.maxSpeedKmhX100 = maxSpeedKmhX100;
        snapshot.autoPauseCount = autoPauseCount;
        snapshot.manualPauseCount = manualPauseCount;
        snapshot.suspiciousSpeedEventCount = suspiciousSpeedEventCount;
        snapshot.runningClassifiedSeconds = runningClassifiedSeconds;
        snapshot.walkingClassifiedSeconds = walkingClassifiedSeconds;
        snapshot.sessionStartedRealtimeMs = sessionStartedRealtimeMs;
        snapshot.sessionFinishedRealtimeMs = sessionFinishedRealtimeMs;
        snapshot.manualPauseStartedRealtimeMs = manualPauseStartedRealtimeMs;
        snapshot.lastMovementRealtimeMs = lastMovementRealtimeMs;
        snapshot.lastAcceptedRealtimeMs = lastAcceptedRealtimeMs;
        snapshot.lastMotionEvidenceRealtimeMs = lastMotionEvidenceRealtimeMs;
        snapshot.activityTypeDowngradeGraceDeadlineRealtimeMs = activityTypeDowngradeGraceDeadlineRealtimeMs;
        snapshot.sessionStartedAtEpochMs = sessionStartedAtEpochMs;
        snapshot.sessionFinishedAtEpochMs = sessionFinishedAtEpochMs;
        snapshot.lastTimerTickAtEpochMs = lastTimerTickAtEpochMs;
        snapshot.serviceCreatedAtEpochMs = serviceCreatedAtEpochMs;
        snapshot.serviceDestroyedAtEpochMs = serviceDestroyedAtEpochMs;
        snapshot.serviceRestartCount = serviceRestartCount;
        if (!routePoints.isEmpty()) {
            snapshot.encodedPolyline = PolyUtil.encode(routePoints);
        }
        snapshot.diagnosticEvents = new ArrayList<>(diagnosticEvents);
        sessionStore.save(snapshot);
    }

    /**
     * Restaura una sesión previa si el proceso murió mientras el tracking seguía vivo.
     */
    private void restoreSessionIfPossible() {
        if (sessionStore == null) {
            return;
        }

        TrackingSessionStore.Snapshot snapshot = sessionStore.restore();
        if (snapshot == null || TrackingState.Status.IDLE.name().equals(snapshot.status)) {
            return;
        }

        try {
            currentStatus = TrackingState.Status.valueOf(snapshot.status);
            currentPauseReason = TrackingState.PauseReason.valueOf(snapshot.pauseReason);
            activityType = TrackingState.ActivityType.valueOf(snapshot.activityType);
        } catch (Exception ignored) {
            if (sessionStore != null) {
            sessionStore.clear();
        }
            return;
        }

        elapsedSeconds = snapshot.elapsedSeconds;
        movingSeconds = snapshot.movingSeconds;
        stoppedSeconds = snapshot.stoppedSeconds;
        autoPausedSeconds = snapshot.autoPausedSeconds;
        manualPausedSeconds = snapshot.manualPausedSeconds;
        manualPausedAccumulatedMs = snapshot.manualPausedAccumulatedMs;
        distanceMeters = snapshot.distanceMeters;
        preciseDistanceMeters = snapshot.preciseDistanceMeters;
        calories = snapshot.calories;
        caloriesAccumulator = snapshot.caloriesAccumulator;
        steps = snapshot.steps;
        maxPaceSecondsPerKm = snapshot.maxPaceSecondsPerKm > 0
                ? snapshot.maxPaceSecondsPerKm
                : Double.POSITIVE_INFINITY;
        maxSpeedKmhX100 = snapshot.maxSpeedKmhX100;
        autoPauseCount = snapshot.autoPauseCount;
        manualPauseCount = snapshot.manualPauseCount;
        suspiciousSpeedEventCount = snapshot.suspiciousSpeedEventCount;
        runningClassifiedSeconds = snapshot.runningClassifiedSeconds;
        walkingClassifiedSeconds = snapshot.walkingClassifiedSeconds;
        sessionStartedRealtimeMs = snapshot.sessionStartedRealtimeMs;
        sessionFinishedRealtimeMs = snapshot.sessionFinishedRealtimeMs;
        manualPauseStartedRealtimeMs = snapshot.manualPauseStartedRealtimeMs;
        lastMovementRealtimeMs = snapshot.lastMovementRealtimeMs;
        lastAcceptedRealtimeMs = snapshot.lastAcceptedRealtimeMs;
        lastMotionEvidenceRealtimeMs = snapshot.lastMotionEvidenceRealtimeMs;
        activityTypeDowngradeGraceDeadlineRealtimeMs = snapshot.activityTypeDowngradeGraceDeadlineRealtimeMs;
        sessionStartedAtEpochMs = snapshot.sessionStartedAtEpochMs;
        sessionFinishedAtEpochMs = snapshot.sessionFinishedAtEpochMs;
        lastTimerTickAtEpochMs = snapshot.lastTimerTickAtEpochMs;
        if (snapshot.serviceCreatedAtEpochMs > 0L) {
            serviceCreatedAtEpochMs = snapshot.serviceCreatedAtEpochMs;
        }
        serviceDestroyedAtEpochMs = snapshot.serviceDestroyedAtEpochMs;
        serviceRestartCount = snapshot.serviceRestartCount + 1;
        diagnosticEvents.clear();
        if (snapshot.diagnosticEvents != null) {
            diagnosticEvents.addAll(snapshot.diagnosticEvents);
        }
        if (snapshot.encodedPolyline != null && !snapshot.encodedPolyline.isEmpty()) {
            routePoints.clear();
            routePoints.addAll(PolyUtil.decode(snapshot.encodedPolyline));
            if (!routePoints.isEmpty()) {
                lastAcceptedLocation = latLngToLocation(routePoints.get(routePoints.size() - 1));
            }
        }

        logDiagnosticEvent("SERVICE_RESTORED", null);

        if (currentStatus == TrackingState.Status.RUNNING
                || currentStatus == TrackingState.Status.AUTO_PAUSED) {
            startLocationUpdates();
            startAccelerometer();
            startTimer();
        }

        // Publica ya el total reconstruido desde timestamps absolutos, sin esperar al siguiente tick.
        elapsedSeconds = computeElapsedSecondsNow();
        publishState();
    }

    /**
     * Detiene todos los productores de datos de la sesión sin modificar por sí mismo el estado lógico del tracking.
     */
    private void stopTrackingInternal() {
        stopTimer();
        stopLocationUpdates();
        stopAccelerometer();
    }

    @NonNull
    @Nullable
    /**
     * Convierte una {@link Location} opcional al tipo ligero {@link LatLng} usado por el estado público.
     *
     * @param location localización Android que se quiere exponer.
     * @return coordenada equivalente o {@code null} si no hay posición disponible.
     */
    private LatLng locationToLatLng(@Nullable Location location) {
        if (location == null) {
            return null;
        }
        return new LatLng(location.getLatitude(), location.getLongitude());
    }

    /**
     * Reconstruye una {@link Location} sintética a partir de un punto de ruta persistido.
     *
     * @param point coordenada recuperada desde la polilínea serializada.
     * @return localización artificial utilizable como último punto aceptado restaurado.
     */
    private Location latLngToLocation(@NonNull LatLng point) {
        Location location = new Location("restored_route_point");
        location.setLatitude(point.latitude);
        location.setLongitude(point.longitude);
        location.setAccuracy(MAX_VALID_ACCURACY_FOR_DISTANCE_ACCUMULATION_M);
        location.setTime(System.currentTimeMillis());
        return location;
    }

    /**
     * Reinicia contadores, sensores, timestamps y ruta para arrancar una sesión totalmente limpia.
     */
    private void resetInternalState() {
        elapsedSeconds = 0L;
        movingSeconds = 0L;
        stoppedSeconds = 0L;
        autoPausedSeconds = 0L;
        manualPausedSeconds = 0L;
        preciseDistanceMeters = 0.0;
        distanceMeters = 0;
        calories = 0;
        caloriesAccumulator = 0.0;
        steps = 0;
        lastStepCounterValue = -1f;
        highSpeedCount = 0;
        consecutiveStationarySamples = 0;
        consecutiveMovingSamples = 0;
        autoPauseCount = 0;
        manualPauseCount = 0;
        suspiciousSpeedEventCount = 0;
        maxSpeedKmhX100 = 0;
        consecutiveDistanceAccumulationSamples = 0;
        maxPaceSecondsPerKm = Double.POSITIVE_INFINITY;
        currentMovementSample = false;
        manualPauseStartedRealtimeMs = 0L;
        manualPausedAccumulatedMs = 0L;
        sessionStartedRealtimeMs = 0L;
        sessionFinishedRealtimeMs = 0L;
        sessionStartedAtEpochMs = 0L;
        sessionFinishedAtEpochMs = 0L;
        lastTimerTickAtEpochMs = 0L;
        serviceDestroyedAtEpochMs = 0L;
        runningClassifiedSeconds = 0L;
        walkingClassifiedSeconds = 0L;
        serviceRestartCount = 0;
        diagnosticEvents.clear();
        lastMovementRealtimeMs = 0L;
        lastAcceptedRealtimeMs = 0L;
        lastMotionEvidenceRealtimeMs = 0L;
        lastSuspiciousSpeedRealtimeMs = 0L;
        activityTypeDowngradeGraceDeadlineRealtimeMs = 0L;
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
        accelFilteredMag = ACCEL_RESTING_GRAVITY_G;
        activityType = TrackingState.ActivityType.WALKING;
    }

    /**
     * Consolida en los acumulados el tiempo transcurrido desde que empezó la pausa manual actual.
     */
    private void accumulateManualPauseTime() {
        if (manualPauseStartedRealtimeMs <= 0L) {
            return;
        }
        long pausedMs = SystemClock.elapsedRealtime() - manualPauseStartedRealtimeMs;
        if (pausedMs > 0L) {
            manualPausedAccumulatedMs += pausedMs;
            manualPausedSeconds = TimeUnit.MILLISECONDS.toSeconds(manualPausedAccumulatedMs);
        }
        manualPauseStartedRealtimeMs = 0L;
    }

    /**
     * Crea el canal estable de notificaciones del tracking foreground con prioridad baja y sin badge.
     */
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

    String averagePace = calculatePreferredAveragePace();
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

    /**
     * Vincula el valor y la etiqueta de una tarjeta métrica dentro de las {@link RemoteViews} de la notificación.
     *
     * @param views vistas remotas que se están componiendo.
     * @param valueViewId identificador del texto que muestra el valor.
     * @param labelViewId identificador del texto que muestra la etiqueta.
     * @param value valor ya formateado para mostrar.
     * @param label etiqueta descriptiva de la métrica.
     */
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

    /**
     * Actualiza la píldora de estado de la notificación con el texto y fondo apropiados para la situación actual.
     *
     * @param views vistas remotas a modificar.
     * @param pillViewId identificador del chip de estado dentro del layout remoto.
     */
    private void bindStatusPill(@NonNull RemoteViews views, int pillViewId) {
    views.setTextViewText(pillViewId, buildStatusPillText());
    views.setInt(pillViewId, "setBackgroundResource", resolveStatusPillBackground());
}

    /**
     * Resuelve el texto corto que resume el estado actual del tracking dentro de la notificación.
     *
     * @return cadena localizada para la píldora de estado.
     */
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

    /**
     * Selecciona el fondo visual de la píldora de estado según si la sesión está activa, pausada o en revisión.
     *
     * @return drawable de fondo a aplicar sobre la píldora remota.
     */
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

    /**
     * Genera el valor de la tercera métrica de la vista compacta, priorizando ritmo medio y usando texto de estado como fallback.
     *
     * @return texto compacto que ocupa la métrica derecha de la notificación.
     */
    @NonNull
    private String buildCompactRightMetricValue() {
    String averagePace = calculatePreferredAveragePace();
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

    /**
     * Configura las dos acciones visibles de la notificación en función del estado actual del tracking.
     *
     * @param views vistas remotas que alojan los botones.
     * @param primaryContainerId contenedor del botón principal.
     * @param primaryIconId icono del botón principal.
     * @param primaryTextId texto del botón principal.
     * @param secondaryContainerId contenedor del botón secundario.
     * @param secondaryIconId icono del botón secundario.
     * @param secondaryTextId texto del botón secundario.
     */
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

    /**
     * Aplica icono, etiqueta y {@link PendingIntent} a un botón concreto dentro de una {@link RemoteViews}.
     *
     * @param views vistas remotas que contienen el botón.
     * @param containerId contenedor clicable del botón.
     * @param iconId vista del icono del botón.
     * @param textId vista del texto del botón.
     * @param iconResId recurso drawable del icono.
     * @param label texto localizado de la acción.
     * @param pendingIntent intent que debe ejecutarse al pulsar cualquiera de sus zonas.
     */
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

    /**
     * Construye el {@link PendingIntent} que reabre la actividad principal al tocar la notificación.
     *
     * @param requestCode código único para distinguir instancias de intents dentro de la notificación.
     * @return pending intent preparado para lanzar {@link MainActivity}.
     */
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
     *
     * @param requestCode código único del pending intent usado por esta acción.
     * @return pending intent que lanza {@link MainActivity} en modo confirmación de parada.
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

    /**
     * Construye un {@link PendingIntent} dirigido de nuevo al propio servicio para procesar acciones internas de la notificación.
     *
     * @param action acción concreta que el servicio debe interpretar en {@link #onStartCommand(Intent, int, int)}.
     * @param requestCode código único del pending intent.
     * @return pending intent de servicio listo para enviar la acción solicitada.
     */
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
    /**
     * Construye el título principal de la notificación según el estado y el tipo de actividad detectado.
     *
     * @return título localizado mostrado en la cabecera de la notificación.
     */
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

            case IDLE:
            default:
                return tr(R.string.mo_tracking_notification_title);
        }
    }

    @NonNull
    /**
     * Genera la línea compacta secundaria de la notificación combinando distancia, ritmo o texto de estado.
     *
     * @return resumen breve apto para la vista compacta.
     */
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
    /**
     * Resume en una sola frase el estado relevante de la sesión para el subtítulo de la notificación expandida.
     *
     * @return texto corto localizado acorde al estado actual.
     */
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
    /**
     * Devuelve la variante textual específica para el título de la notificación según el tipo de actividad.
     *
     * @return texto corto localizado para interpolar en el título.
     */
    private String buildNotificationActivityTitleLabel() {
        if (activityType == TrackingState.ActivityType.RUNNING_ACTIVITY) {
            return tr(R.string.mo_tracking_notification_activity_run);
        }
        return tr(R.string.mo_tracking_notification_activity_walk);
    }

    @NonNull
    /**
     * Formatea la distancia acumulada usando metros o kilómetros según la magnitud actual.
     *
     * @return distancia localizada lista para la notificación.
     */
    private String formatNotificationDistance() {
        if (distanceMeters >= 1000) {
            return tr(R.string.tracking_distance_km_format, distanceMeters / 1000.0f);
        }
        return tr(R.string.tracking_distance_m_format, distanceMeters);
    }

    /**
     * Reemite la notificación foreground con el último contenido calculado si el sistema todavía expone un {@link NotificationManager}.
     */
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
    /**
     * Convierte una duración en segundos al formato legible usado por la UI y la notificación.
     *
     * @param seconds duración total a formatear.
     * @return cadena en formato {@code mm:ss} o {@code h:mm:ss}.
     */
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
