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
 * - Detección automática Caminar/Correr via acelerómetro
 * - Cronómetro interno con tick cada segundo
 * - Cálculo de distancia acumulada
 * - Cálculo de ritmo (min/km)
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

    private static final float ACCEL_RUN_THRESHOLD  = 15.0f;
    private static final int   ACCEL_SAMPLE_WINDOW  = 15;
    private static final int   CONFIRM_STEPS        = 3;

    // -------------------------------------------------------------------------
    // Constantes de localización
    // -------------------------------------------------------------------------

    private static final long  LOCATION_INTERVAL_MS    = 3_000L;
    private static final long  LOCATION_FASTEST_MS     = 1_500L;
    private static final float LOCATION_MIN_DISTANCE_M = 5.0f;
    private static final float LOCATION_MIN_ACCURACY_M = 20.0f;

    /** Distancia mínima (m) para mostrar ritmo — evita valores absurdos al inicio. */
    private static final int PACE_MIN_DISTANCE_M = 200;

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

    @NonNull
    public LiveData<TrackingState> getStateLiveData() {
        return stateLiveData;
    }

    // -------------------------------------------------------------------------
    // Estado interno
    // -------------------------------------------------------------------------

    private TrackingState.Status       currentStatus = TrackingState.Status.IDLE;
    private TrackingState.ActivityType activityType  = TrackingState.ActivityType.WALKING;

    private long   elapsedSeconds  = 0L;
    /** Segundos en estado RUNNING — excluye pausas. Usado para el cálculo de ritmo. */
    private long   activeSeconds   = 0L;
    private int    distanceMeters  = 0;
    private int    calories        = 0;
    private double userWeightKg    = 70.0;

    private final List<LatLng> routePoints = new ArrayList<>();
    @Nullable private Location lastLocation = null;

    // -------------------------------------------------------------------------
    // Cronómetro
    // BUG-02: Handler en main looper para centralizar todo el estado mutable
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

    private int accelRunSamples     = 0;
    private int accelTotalSamples   = 0;
    private int runningConfirmCount = 0;
    private int walkingConfirmCount = 0;

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
        scheduler.shutdownNow();
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
        // BUG-03: Detener el foreground service para eliminar la notificación persistente
        // y liberar los recursos del servicio.
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    /** Descarta la sesión actual y vuelve a IDLE. */
    public void resetTracking() {
        stopTrackingInternal();
        resetInternalState();
        currentStatus = TrackingState.Status.IDLE;
        publishState();
        // BUG-03: Ídem — resetear también detiene el foreground service.
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

        LatLng point = new LatLng(location.getLatitude(), location.getLongitude());
        routePoints.add(point);

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

        accelTotalSamples++;
        if (magnitude > ACCEL_RUN_THRESHOLD) {
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
        // BUG-02: El tick se despacha al main looper via mainHandler.post() para que
        // elapsedSeconds, calories y publishState() se ejecuten en el mismo hilo que
        // onNewLocation() y onSensorChanged(), eliminando la condición de carrera.
        // BUG-12: Se recalculan las calorías en cada tick para que avancen aunque el
        // GPS no envíe actualizaciones (usuario lento / filtro de 5 m no superado).
        timerFuture = scheduler.scheduleWithFixedDelay(
                () -> mainHandler.post(() -> {
                    elapsedSeconds++;
                    activeSeconds++; // solo cuenta cuando el timer está activo (RUNNING)
                    calories = calculateCalories(); // BUG-12
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
     * Devuelve el ritmo como "M'SS\"" o null si aún no hay suficiente distancia.
     * Usa activeSeconds (excluye pausas) para un ritmo real.
     * No se muestra hasta PACE_MIN_DISTANCE_M metros para evitar valores absurdos.
     */
    @Nullable
    private String calculatePace() {
        if (distanceMeters < PACE_MIN_DISTANCE_M || activeSeconds <= 0) return null;
        double paceMinKm = (activeSeconds / 60.0) / (distanceMeters / 1000.0);
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
        routePoints.clear();
        lastLocation        = null;
        accelRunSamples     = 0;
        accelTotalSamples   = 0;
        runningConfirmCount = 0;
        walkingConfirmCount = 0;
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