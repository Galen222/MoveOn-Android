package com.proyecto.moveon.ui.home.tracking;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;

/**
 * Persistencia ligera del estado vivo del tracking.
 *
 * <p>Su única misión es permitir que una sesión sobreviva a muerte del proceso
 * o recreación del servicio sin perder el tiempo total real ni las métricas clave.</p>
 */
public final class TrackingSessionStore {

    private static final String PREF_NAME = "tracking_session_store";
    private static final String KEY_SNAPSHOT_JSON = "snapshot_json";

    private final SharedPreferences prefs;
    private final Gson gson = new Gson();

    /**
     * Crea el store usando siempre el contexto de aplicación.
     *
     * <p>El servicio debe instanciar este store desde {@code onCreate()} o más tarde,
     * nunca en un inicializador de campo del propio {@link android.app.Service}, porque
     * durante la construcción temprana Android todavía puede no haber adjuntado el
     * contexto base y {@code getApplicationContext()} sería nulo.</p>
     */
    public TrackingSessionStore(@NonNull Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Persiste el snapshot del tracking en {@link android.content.SharedPreferences}
     * serializándolo a JSON. Se usa para que, si el sistema mata el proceso
     * del servicio foreground, podamos recuperar la sesión al rearrancar.
     *
     * @param snapshot estado completo del tracking en este instante.
     */
    public void save(@NonNull Snapshot snapshot) {
        prefs.edit().putString(KEY_SNAPSHOT_JSON, gson.toJson(snapshot)).apply();
    }

    @Nullable
    /**
     * Recupera el último snapshot guardado o {@code null} si no hay ninguno
     * (primera ejecución, o el usuario cerró la actividad previamente).
     *
     * @return snapshot deserializado o {@code null} si no había estado persistido.
     */
    public Snapshot restore() {
        String json = prefs.getString(KEY_SNAPSHOT_JSON, null);
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        return gson.fromJson(json, Snapshot.class);
    }

    /**
     * Borra el snapshot guardado. Se llama al cerrar o descartar la
     * actividad para que un rearranque posterior empiece limpio en lugar
     * de ofrecer "continuar sesión".
     */
    public void clear() {
        prefs.edit().remove(KEY_SNAPSHOT_JSON).apply();
    }

    /**
     * Snapshot serializable de la sesión.
     */
    public static final class Snapshot {
        @NonNull public String status = TrackingState.Status.IDLE.name();
        @NonNull public String pauseReason = TrackingState.PauseReason.NONE.name();
        @NonNull public String activityType = TrackingState.ActivityType.WALKING.name();
        public long elapsedSeconds;
        public long movingSeconds;
        public long stoppedSeconds;
        public long autoPausedSeconds;
        public long manualPausedSeconds;
        public long manualPausedAccumulatedMs;
        public int distanceMeters;
        public double preciseDistanceMeters;
        public int calories;
        public double caloriesAccumulator;
        public int steps;
        public int maxPaceSecondsPerKm;
        public int maxSpeedKmhX100;
        public int autoPauseCount;
        public int manualPauseCount;
        public int suspiciousSpeedEventCount;
        public long runningClassifiedSeconds;
        public long walkingClassifiedSeconds;
        public long sessionStartedRealtimeMs;
        public long sessionFinishedRealtimeMs;
        public long manualPauseStartedRealtimeMs;
        public long lastMovementRealtimeMs;
        public long lastAcceptedRealtimeMs;
        public long lastMotionEvidenceRealtimeMs;
        public long activityTypeDowngradeGraceDeadlineRealtimeMs;
        public long sessionStartedAtEpochMs;
        public long sessionFinishedAtEpochMs;
        public long lastTimerTickAtEpochMs;
        public long serviceCreatedAtEpochMs;
        public long serviceDestroyedAtEpochMs;
        public int serviceRestartCount;
        public String encodedPolyline;
        public List<TrackingState.DiagnosticEvent> diagnosticEvents = new ArrayList<>();
    }
}
