package com.proyecto.moveon.data.profile;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;

import com.google.gson.JsonObject;
import com.proyecto.moveon.core.concurrency.MoveOnExecutors;
import com.proyecto.moveon.data.local.db.AppDatabase;
import com.proyecto.moveon.data.local.entity.UserPrefsEntity;
import com.proyecto.moveon.data.profile.remote.PerfilRemoteDataSource;

import java.util.concurrent.ExecutorService;

/**
 * Repositorio para las preferencias del usuario (objetivos semanal y mensual).
 * Patrón:
 * 1. El cambio se aplica inmediatamente en Room (UI reactiva instantánea).
 * 2. Se envía al servidor en background con PATCH.
 * Los objetivos viven en el servidor como fuente de verdad.
 * Room actúa como caché local para respuesta inmediata y funcionamiento offline.
 */
public class UserPrefsRepository {

    private static final long DEFAULT_WEEKLY_GOAL_METERS  = 50_000L;
    private static final long DEFAULT_MONTHLY_GOAL_METERS = 150_000L;

    private final AppDatabase db;
    private final PerfilRemoteDataSource remote;
    private final ExecutorService io = MoveOnExecutors.io();

    public UserPrefsRepository(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        this.db     = AppDatabase.getInstance(appContext);
        this.remote = new PerfilRemoteDataSource(appContext);
    }

    /** LiveData reactivo: el ViewModel lo observa para recalcular cuando cambian los objetivos. */
    @NonNull
    public LiveData<UserPrefsEntity> observe(@NonNull String accountKey) {
        return db.userPrefsDao().observe(accountKey);
    }

    /**
     * Actualiza el objetivo semanal:
     * 1. Guarda en Room de inmediato.
     * 2. Envía PATCH al servidor en background.
     */
    public void setWeeklyGoal(@NonNull String accountKey, long meters) {
        io.execute(() -> {
            UserPrefsEntity prefs = getOrCreate(accountKey);
            prefs.weeklyGoalMeters = meters;
            prefs.updatedAtMs      = System.currentTimeMillis();
            db.userPrefsDao().upsert(prefs);

            JsonObject body = new JsonObject();
            body.addProperty("objetivo_semanal_metros", meters);
            remote.patchPerfilBlocking(body);
        });
    }

    /**
     * Actualiza el objetivo mensual:
     * 1. Guarda en Room de inmediato.
     * 2. Envía PATCH al servidor en background.
     */
    public void setMonthlyGoal(@NonNull String accountKey, long meters) {
        io.execute(() -> {
            UserPrefsEntity prefs = getOrCreate(accountKey);
            prefs.monthlyGoalMeters = meters;
            prefs.updatedAtMs       = System.currentTimeMillis();
            db.userPrefsDao().upsert(prefs);

            JsonObject body = new JsonObject();
            body.addProperty("objetivo_mensual_metros", meters);
            remote.patchPerfilBlocking(body);
        });
    }

    /**
     * Sincroniza los objetivos recibidos del servidor (llamado al refrescar el perfil).
     * Solo escribe en Room — no hace llamada de red.
     */
    public void syncFromServer(@NonNull String accountKey,
                               long weeklyGoalMeters,
                               long monthlyGoalMeters) {
        io.execute(() -> {
            UserPrefsEntity prefs = getOrCreate(accountKey);
            prefs.weeklyGoalMeters  = weeklyGoalMeters;
            prefs.monthlyGoalMeters = monthlyGoalMeters;
            prefs.updatedAtMs       = System.currentTimeMillis();
            db.userPrefsDao().upsert(prefs);
        });
    }

    /** Limpia las preferencias al hacer logout. */
    @SuppressWarnings("unused")
    public void clearForAccount(@NonNull String accountKey) {
        io.execute(() -> db.userPrefsDao().deleteByAccount(accountKey));
    }

    @NonNull
    private UserPrefsEntity getOrCreate(@NonNull String accountKey) {
        UserPrefsEntity prefs = db.userPrefsDao().getNow(accountKey);
        if (prefs != null) return prefs;

        UserPrefsEntity newPrefs = new UserPrefsEntity();
        newPrefs.accountKey        = accountKey;
        newPrefs.weeklyGoalMeters  = DEFAULT_WEEKLY_GOAL_METERS;
        newPrefs.monthlyGoalMeters = DEFAULT_MONTHLY_GOAL_METERS;
        newPrefs.updatedAtMs       = 0L;
        return newPrefs;
    }
}
