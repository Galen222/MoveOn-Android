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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
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
import com.proyecto.moveon.ui.main.MainActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Servicio de primer plano que gestiona:
 * - Ubicación en tiempo real via FusedLocationProviderClient
 * - Detección automática Caminar/Correr via acelerómetro (con filtro EMA)
 * - Cronómetro interno con tick cada segundo
 * - Cálculo de distancia acumulada
 * - Cálculo de ritmo (min/km) desde la primera muestra GPS válida
 * - Detección de velocidad excesiva (>20 km/h) para avisar al usuario
 * - Codificación de la ruta en Encoded Polyline al finalizar
 * Se comunica con TrackingViewModel a través del patrón Binder local.
 */
public final class TrackingService extends Service implements SensorEventListener {

    public static void stopService(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        Intent intent = new Intent(appContext, TrackingService.class);
        appContext.stopService(intent);
    }

    // -------------------------------------------------------------------------
    // Constantes de notificación
    // -------------------------------------------------------------------------

    private static final String CHANNEL_ID      = "moveon_tracking_channel";
    private static final int    NOTIFICATION_ID = 1001;
    private static final String ACTION_RESTORE_NOTIFICATION = "com.proyecto.moveon.action.RESTORE_TRACKING_NOTIFICATION";

    // -------------------------------------------------------------------------
    // Constantes de acelerómetro
    // -------------------------------------------------------------------------

    private static final float ACCEL_RUN_THRESHOLD = 15.0f;
    // Ventana ampliada de 15 a 30 muestras (~6 s con SENSOR_DELAY_NORMAL).
    // Con la ventana anterior de 15 (~3 s) los cambios Walking↔Running eran demasiado
    // bruscos y afectaban el cálculo de calorías.
    private static final int   ACCEL_SAMPLE_WINDOW = 30;
    private static final int   CONFIRM_STEPS       = 3;
    // Factor α del filtro de paso bajo (EMA - Exponential Moving Average).
    // Un valor bajo (0.2) suaviza picos puntuales del acelerómetro (ej. baches, gestos
    // bruscos del brazo) sin añadir un retardo perceptible en la detección real de
    // cambios de ritmo.
    private static final float ACCEL_ALPHA         = 0.2f;

    // -------------------------------------------------------------------------
    // Constantes de localización
    // -------------------------------------------------------------------------

    private static final long  LOCATION_INTERVAL_MS    = 3_000L;
    private static final long  LOCATION_FASTEST_MS     = 1_500L;
    private static final float LOCATION_MIN_DISTANCE_M = 5.0f;
    private static final float LOCATION_MIN_ACCURACY_M = 20.0f;

    // -------------------------------------------------------------------------
    // Constantes de ritmo
    // -------------------------------------------------------------------------

    /**
     * Número mínimo de puntos GPS para empezar a calcular el ritmo.
     * Con 2 puntos ya tenemos un segmento real (distancia + tiempo), evitando
     * valores absurdos en los primeros milisegundos pero mostrando el ritmo
     * desde el inicio real de la actividad (~6 s tras el primer fix GPS).
     */
    private static final int PACE_MIN_GPS_POINTS = 2;

    // -------------------------------------------------------------------------
    // Constantes de detección de velocidad excesiva (vehículo)
    // -------------------------------------------------------------------------

    /**
     * Velocidad máxima humana razonable corriendo (m/s).
     * 20 km/h = 5.556 m/s. Ningún corredor amateur supera este ritmo de forma
     * sostenida. Referencia: Pokémon GO usa ~10.5 km/h; Strava/Runkeeper 18-25 km/h.
     * 20 km/h es un umbral conservador y seguro para detectar vehículos.
     */
    private static final float MAX_HUMAN_SPEED_MS = 5.556f; // 20 km/h

    /**
     * Muestras GPS consecutivas por encima de MAX_HUMAN_SPEED_MS necesarias
     * para disparar el aviso. Evita falsas alarmas por un pico GPS puntual
     * (p. ej. deriva del receptor al arrancar o un túnel).
     */
    private static final int SPEED_ALERT_CONSECUTIVE = 3;

    // -------------------------------------------------------------------------
    // Binder local
    // -------------------------------------------------------------------------

    public final class LocalBinder extends Binder {
        @NonNull
        public TrackingService getService() {
            return TrackingService.this;
        }
    }

    private final IBinder binder = new LocalBinder();

    // -------------------------------------------------------------------------
    // LiveData expuesta al ViewModel
    // -------------------------------------------------------------------------

    private final MutableLiveData<TrackingState> stateLiveData =
            new MutableLiveData<>(TrackingState.idle());

    /**
     * Evento de velocidad excesiva (vehículo detectado).
     * Se emite con {@code true} exactamente cuando el contador llega a
     * {@link #SPEED_ALERT_CONSECUTIVE}, no en cada muestra posterior,
     * para no bombardear la UI con alertas repetidas.
     * El ViewModel lo convierte en un {@code Event<String>} de un solo disparo.
     */
    private final MutableLiveData<Boolean> vehicleSpeedDetected = new MutableLiveData<>();

    @NonNull
    public LiveData<TrackingState> getStateLiveData() {
        return stateLiveData;
    }

    @NonNull
    public LiveData<Boolean> getVehicleSpeedDetected() {
        return vehicleSpeedDetected;
    }

    // -------------------------------------------------------------------------
    // Estado interno
    // -------------------------------------------------------------------------

    private TrackingState.Status       currentStatus = TrackingState.Status.IDLE;
    private TrackingState.ActivityType activityType  = TrackingState.ActivityType.WALKING;

    private long   elapsedSeconds = 0L;
    /** Segundos en estado RUNNING — excluye pausas. Usado para el cálculo de ritmo. */
    private long   activeSeconds  = 0L;
    private int    distanceMeters = 0;
    private int    calories       = 0;
    private double userWeightKg   = 70.0;

    /** Número de puntos GPS acumulados en la sesión actual (solo muestras válidas). */
    private int    gpsPointCount  = 0;

    /** Contador de muestras GPS consecutivas por encima de MAX_HUMAN_SPEED_MS. */
    private int    highSpeedCount = 0;

    private final List<LatLng> routePoints = new ArrayList<>();
    @Nullable private Location lastLocation = null;

    // -------------------------------------------------------------------------
    // Cronómetro
    // Handler en main looper para centralizar todo el estado mutable
    // en el hilo principal y eliminar la condición de carrera con onNewLocation().
    // -------------------------------------------------------------------------

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    @Nullable private ScheduledFuture<?>   timerFuture = null;

    // -------------------------------------------------------------------------
    // Localización
    // -------------------------------------------------------------------------

    private FusedLocationProviderClient fusedLocationClient;

    private final LocationCallback locationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(@NonNull LocationResult result) {
            Location location = result.getLastLocation();
            if (location == null) return;
            if (location.getAccuracy() > LOCATION_MIN_ACCURACY_M) return;
            onNewLocation(location);
        }
    };

    // -------------------------------------------------------------------------
    // Acelerómetro
    // -------------------------------------------------------------------------

    private SensorManager    sensorManager;
    @Nullable private Sensor accelerometer;

    private int   accelRunSamples     = 0;
    private int   accelTotalSamples   = 0;
    private int   runningConfirmCount = 0;
    private int   walkingConfirmCount = 0;
    // Magnitud filtrada con EMA. Se inicializa en gravedad terrestre
    // (≈9.81 m/s²) para que la primera muestra no arranque desde 0 y genere un
    // falso positivo de running.
    private float accelFilteredMag    = SensorManager.GRAVITY_EARTH;

    // -------------------------------------------------------------------------
    // Ciclo de vida del servicio
    // -------------------------------------------------------------------------

    @Override
    public void onCreate() {
        super.onCreate();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        sensorManager       = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;

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
        // Envolver shutdownNow() en try-catch para que una excepción
        // inesperada (ej. SecurityException en ciertos OEMs, o estado corrupto tras
        // una recreación por START_STICKY) no interrumpa la cadena de limpieza y
        // permita que super.onDestroy() se ejecute siempre.
        try {
            scheduler.shutdownNow();
        } catch (Exception ignored) {}
        super.onDestroy();
    }

    // -------------------------------------------------------------------------
    // API pública — llamada desde TrackingViewModel
    // -------------------------------------------------------------------------

    /** Establece el peso del usuario para el cálculo de calorías. */
    public void setUserWeight(double weightKg) {
        this.userWeightKg = (weightKg > 0) ? weightKg : 70.0;
    }

    /** Inicia o reanuda el tracking. */
    public void startTracking() {
        if (currentStatus == TrackingState.Status.RUNNING) return;

        if (currentStatus == TrackingState.Status.IDLE
                || currentStatus == TrackingState.Status.FINISHED) {
            resetInternalState();
        }

        currentStatus = TrackingState.Status.RUNNING;
        startLocationUpdates();
        startAccelerometer();
        startTimer();
        publishState();
    }

    /** Pausa el tracking: congela cronómetro y ubicación. */
    public void pauseTracking() {
        if (currentStatus != TrackingState.Status.RUNNING) return;
        currentStatus = TrackingState.Status.PAUSED;
        stopTimer();
        stopLocationUpdates();
        stopAccelerometer();
        publishState();
    }

    /** Finaliza el tracking y codifica la polilínea. */
    public void stopTracking() {
        if (currentStatus == TrackingState.Status.IDLE) return;
        currentStatus = TrackingState.Status.FINISHED;
        stopTimer();
        stopLocationUpdates();
        stopAccelerometer();
        publishState();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    /** Descarta la sesión actual y vuelve a IDLE. */
    public void resetTracking() {
        stopTrackingInternal();
        resetInternalState();
        currentStatus = TrackingState.Status.IDLE;
        publishState();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    // -------------------------------------------------------------------------
    // Localización
    // -------------------------------------------------------------------------

    @SuppressWarnings("MissingPermission")
    private void startLocationUpdates() {
        LocationRequest request = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, LOCATION_INTERVAL_MS)
                .setMinUpdateIntervalMillis(LOCATION_FASTEST_MS)
                .setMinUpdateDistanceMeters(LOCATION_MIN_DISTANCE_M)
                .build();

        fusedLocationClient.requestLocationUpdates(
                request,
                locationCallback,
                Looper.getMainLooper());
    }

    private void stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback);
    }

    private void onNewLocation(@NonNull Location location) {
        if (currentStatus != TrackingState.Status.RUNNING) return;

        // -----------------------------------------------------------------
        // 1. Detección de velocidad excesiva (vehículo).
        //    Se comprueba ANTES de acumular distancia para no inflar
        //    estadísticas con trayectos en tren/coche/moto.
        //    Usamos location.getSpeed() (velocidad instantánea GPS en m/s)
        //    que FusedLocationProvider filtra internamente — más fiable que
        //    calcular manualmente la distancia entre dos puntos.
        // -----------------------------------------------------------------
        if (location.hasSpeed() && location.getSpeed() > MAX_HUMAN_SPEED_MS) {
            highSpeedCount++;
            if (highSpeedCount == SPEED_ALERT_CONSECUTIVE) {
                // Emitir el aviso una sola vez al alcanzar el umbral.
                vehicleSpeedDetected.postValue(true);
                // Pausar la actividad: detiene cronómetro, GPS y acelerómetro.
                // El usuario deberá pulsar Play manualmente para reanudar,
                // igual que si hubiera pausado a mano — así tiempo y calorías
                // no se inflan mientras va en vehículo.
                pauseTracking();
            }
            // Aunque aún no se haya alcanzado el umbral, descartamos el punto:
            // no acumulamos distancia ni actualizamos lastLocation.
            return;
        }

        // Velocidad dentro del rango humano: reiniciamos racha de alta velocidad.
        highSpeedCount = 0;

        // -----------------------------------------------------------------
        // 2. Acumulación normal de distancia y puntos de ruta.
        // -----------------------------------------------------------------
        LatLng point = new LatLng(location.getLatitude(), location.getLongitude());
        routePoints.add(point);
        gpsPointCount++;

        if (lastLocation != null) {
            float delta = lastLocation.distanceTo(location);
            distanceMeters += Math.round(delta);
            calories = calculateCalories();
        }

        lastLocation = location;
        publishState();
        updateNotification();
    }

    // -------------------------------------------------------------------------
    // Acelerómetro
    // -------------------------------------------------------------------------

    private void startAccelerometer() {
        if (accelerometer != null) {
            sensorManager.registerListener(
                    this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
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
        if (currentStatus != TrackingState.Status.RUNNING) return;

        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];
        float magnitude = (float) Math.sqrt(x * x + y * y + z * z);

        // Filtro de paso bajo (EMA) para suavizar picos puntuales del
        // acelerómetro. Sin este filtro, un bache, un gesto brusco del brazo o una
        // vibración del dispositivo pueden disparar un pico de magnitud >15 m/s²
        // que se contabiliza como muestra de running, contaminando la ventana.
        //
        // EMA: filteredMag = α·raw + (1−α)·filteredMag_prev
        // Con α=0.2, el filtro atenúa picos aislados un 80% y converge en ~5
        // muestras (~1 s) ante un cambio real y sostenido de ritmo.
        accelFilteredMag = ACCEL_ALPHA * magnitude + (1f - ACCEL_ALPHA) * accelFilteredMag;

        accelTotalSamples++;
        if (accelFilteredMag > ACCEL_RUN_THRESHOLD) {
            accelRunSamples++;
        }

        if (accelTotalSamples >= ACCEL_SAMPLE_WINDOW) {
            boolean mayoriaCorrer = accelRunSamples >= ACCEL_SAMPLE_WINDOW / 2;

            if (mayoriaCorrer) {
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

            accelRunSamples   = 0;
            accelTotalSamples = 0;
        }
    }

    @Override
    public void onAccuracyChanged(@NonNull Sensor sensor, int accuracy) {}

    // -------------------------------------------------------------------------
    // Cronómetro
    // -------------------------------------------------------------------------

    private void startTimer() {
        stopTimer();
        // Guardia para evitar operar sobre un scheduler ya cerrado.
        if (scheduler.isShutdown()) return;
        timerFuture = scheduler.scheduleWithFixedDelay(
                () -> mainHandler.post(() -> {
                    elapsedSeconds++;
                    activeSeconds++;
                    calories = calculateCalories();
                    publishState();
                    updateNotification();
                }),
                1L, 1L, TimeUnit.SECONDS);
    }

    private void stopTimer() {
        if (timerFuture != null && !timerFuture.isCancelled()) {
            timerFuture.cancel(false);
            timerFuture = null;
        }
    }

    // -------------------------------------------------------------------------
    // Cálculo de calorías (MET × peso × tiempo)
    // -------------------------------------------------------------------------

    private int calculateCalories() {
        if (elapsedSeconds <= 0 || userWeightKg <= 0) return 0;
        double met         = (activityType == TrackingState.ActivityType.RUNNING_ACTIVITY) ? 8.0 : 3.5;
        double hoursActive = elapsedSeconds / 3600.0;
        return (int) Math.round(met * userWeightKg * hoursActive);
    }

    // -------------------------------------------------------------------------
    // Cálculo de ritmo (min/km)
    // -------------------------------------------------------------------------

    /**
     * Devuelve el ritmo como "M'SS\"" o null mientras no haya datos suficientes.
     * Condiciones para mostrar ritmo:
     *   - Al menos {@link #PACE_MIN_GPS_POINTS} puntos GPS válidos recibidos
     *   - distanceMeters > 0 y activeSeconds > 0
     *   - Resultado dentro del rango razonable [1'00"/km – 30'00"/km]
     * Usar gpsPointCount en vez de un umbral de metros fijo hace que el ritmo
     * aparezca desde el inicio de la actividad (~6 s tras el primer fix GPS)
     * y se vaya ajustando a medida que se acumulan más datos.
     */
    @Nullable
    private String calculatePace() {
        if (gpsPointCount < PACE_MIN_GPS_POINTS
                || distanceMeters <= 0
                || activeSeconds <= 0) {
            return null;
        }
        double paceMinKm = (activeSeconds / 60.0) / (distanceMeters / 1000.0);
        // Descartar valores fuera del rango humano (evita 0'01"/km o 99'00"/km)
        if (paceMinKm < 1.0 || paceMinKm > 30.0) return null;
        int mins = (int) paceMinKm;
        int secs = (int) Math.round((paceMinKm - mins) * 60);
        if (secs == 60) { mins++; secs = 0; }
        return String.format(Locale.US, "%d'%02d\"", mins, secs);
    }

    // -------------------------------------------------------------------------
    // Publicar estado al LiveData
    // -------------------------------------------------------------------------

    private void publishState() {
        String polyline = null;
        if (currentStatus == TrackingState.Status.FINISHED && !routePoints.isEmpty()) {
            polyline = PolyUtil.encode(routePoints);
        }

        TrackingState state = new TrackingState.Builder()
                .status(currentStatus)
                .activityType(activityType)
                .elapsedSeconds(elapsedSeconds)
                .distanceMeters(distanceMeters)
                .calories(calories)
                .pace(calculatePace())
                .routePoints(new ArrayList<>(routePoints))
                .encodedPolyline(polyline)
                .build();

        stateLiveData.postValue(state);
    }

    // -------------------------------------------------------------------------
    // Reset interno
    // -------------------------------------------------------------------------

    private void stopTrackingInternal() {
        stopTimer();
        stopLocationUpdates();
        stopAccelerometer();
    }

    private void resetInternalState() {
        elapsedSeconds      = 0L;
        activeSeconds       = 0L;
        distanceMeters      = 0;
        calories            = 0;
        gpsPointCount       = 0;
        highSpeedCount      = 0;
        routePoints.clear();
        lastLocation        = null;
        accelRunSamples     = 0;
        accelTotalSamples   = 0;
        runningConfirmCount = 0;
        walkingConfirmCount = 0;
        // FIX BUG-11: Reiniciar la magnitud filtrada a gravedad terrestre para que
        // la siguiente sesión no arrastre el estado del filtro EMA.
        accelFilteredMag    = SensorManager.GRAVITY_EARTH;
        activityType        = TrackingState.ActivityType.WALKING;
    }

    // -------------------------------------------------------------------------
    // Notificación de primer plano
    // -------------------------------------------------------------------------

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.tracking_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(getString(R.string.tracking_notification_channel_desc));
        channel.setShowBadge(false);

        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    @NonNull
    private Notification buildNotification() {
        Intent tapIntent = new Intent(this, MainActivity.class);
        tapIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent restoreIntent = new Intent(this, TrackingService.class);
        restoreIntent.setAction(ACTION_RESTORE_NOTIFICATION);

        PendingIntent restorePendingIntent = PendingIntent.getService(
                this, 1, restoreIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String distText = distanceMeters >= 1000
                ? String.format(Locale.US, "%.2f km", distanceMeters / 1000.0)
                : distanceMeters + " m";
        String paceText = calculatePace();
        String contentText = formatElapsed(elapsedSeconds) + "  ·  " + distText
                + (paceText != null ? "  ·  " + paceText + "/km" : "");

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.tracking_notification_title))
                .setContentText(contentText)
                .setSmallIcon(R.drawable.run_icon)
                .setContentIntent(pendingIntent)
                .setDeleteIntent(restorePendingIntent)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setOngoing(true)
                .setAutoCancel(false)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .build();
    }

    private void updateNotification() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, buildNotification());
        }
    }

    // -------------------------------------------------------------------------
    // Utilidades
    // -------------------------------------------------------------------------

    @NonNull
    private String formatElapsed(long seconds) {
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        if (h > 0) {
            return String.format(Locale.US, "%d:%02d:%02d", h, m, s);
        }
        return String.format(Locale.US, "%02d:%02d", m, s);
    }
}