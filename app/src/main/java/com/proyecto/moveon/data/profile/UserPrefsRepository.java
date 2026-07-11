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
    private final ExecutorService io;

    /**
     * Inicializa el repositorio a partir del contexto de aplicación:
     * obtiene la instancia singleton de Room y crea un data source remoto
     * para los PATCH best-effort al backend.
     *
     * @param context contexto desde el que se deriva el {@code applicationContext} para evitar fugas.
     */
    public UserPrefsRepository(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        this.db = AppDatabase.getInstance(appContext);
        this.remote = new PerfilRemoteDataSource(appContext);
        this.io = MoveOnExecutors.io();
    }

    /**
     * Constructor con dependencias explícitas para pruebas del repositorio.
     * Evita modificar campos {@code final} mediante reflexión y permite usar
     * un ejecutor síncrono con una base Room en memoria.
     *
     * @param db base de datos que debe usar el repositorio.
     * @param remote fuente remota para los PATCH de objetivos.
     * @param io ejecutor donde se realizan las operaciones de Room.
     */
    UserPrefsRepository(@NonNull AppDatabase db,
                        @NonNull PerfilRemoteDataSource remote,
                        @NonNull ExecutorService io) {
        this.db = db;
        this.remote = remote;
        this.io = io;
    }

    /**
     * Expone en tiempo real la fila de preferencias de la cuenta indicada.
     *
     * @param accountKey clave lógica de la cuenta cuyas metas se quieren observar.
     * @return {@link LiveData} que emite la fila de {@link UserPrefsEntity} al cambiar en Room.
     */
    @NonNull
    public LiveData<UserPrefsEntity> observe(@NonNull String accountKey) {
        return db.userPrefsDao().observe(accountKey);
    }

    /**
     * Actualiza el objetivo semanal:
     * 1. Guarda en Room de inmediato (UI reactiva instantánea).
     * 2. Envía PATCH al servidor de forma asíncrona (no bloquea el hilo IO).
     *
     * <p>Usa {@code patchPerfil(...)} de forma asíncrona para no bloquear el hilo IO.
     * El resultado se sigue tratando como fire-and-forget, pero ya no impide que
     * otras operaciones de IO se ejecuten mientras el backend responde.</p>
     *
     * @param accountKey clave lógica de la cuenta cuyo objetivo semanal se modifica.
     * @param meters nueva meta semanal en metros.
     */
    public void setWeeklyGoal(@NonNull String accountKey, long meters) {
        io.execute(() -> {
            UserPrefsEntity prefs = getOrCreate(accountKey);
            prefs.weeklyGoalMeters = meters;
            prefs.updatedAtMs      = System.currentTimeMillis();
            db.userPrefsDao().upsert(prefs);

            JsonObject body = new JsonObject();
            body.addProperty("objetivo_semanal_metros", meters);
            remote.patchPerfil(body, ignoredResult -> { /* best-effort */ });
        });
    }

    /**
     * Actualiza el objetivo mensual:
     * 1. Guarda en Room de inmediato (UI reactiva instantánea).
     * 2. Envía PATCH al servidor de forma asíncrona (no bloquea el hilo IO).
     *
     * <p>Igual que {@link #setWeeklyGoal(String, long)}, usa un envío asíncrono
     * para no bloquear el hilo IO.</p>
     *
     * @param accountKey clave lógica de la cuenta cuyo objetivo mensual se modifica.
     * @param meters nueva meta mensual en metros.
     */
    public void setMonthlyGoal(@NonNull String accountKey, long meters) {
        io.execute(() -> {
            UserPrefsEntity prefs = getOrCreate(accountKey);
            prefs.monthlyGoalMeters = meters;
            prefs.updatedAtMs       = System.currentTimeMillis();
            db.userPrefsDao().upsert(prefs);

            JsonObject body = new JsonObject();
            body.addProperty("objetivo_mensual_metros", meters);
            remote.patchPerfil(body, ignoredResult -> { /* best-effort */ });
        });
    }

    /**
     * Sincroniza los objetivos recibidos del servidor (llamado al refrescar el perfil).
     * Solo escribe en Room — no hace llamada de red.
     *
     * @param accountKey clave lógica de la cuenta actual.
     * @param weeklyGoalMeters meta semanal resuelta desde backend.
     * @param monthlyGoalMeters meta mensual resuelta desde backend.
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

    /**
     * Devuelve la fila de preferencias de la cuenta si existe; si no,
     * construye una nueva en memoria con los objetivos por defecto.
     *
     * <p>No persiste la entidad nueva: es responsabilidad del llamador
     * modificar los campos necesarios y escribirla con {@code upsert}.</p>
     *
     * @param accountKey clave derivada de la cuenta cuyas preferencias se cargan.
     * @return la entidad existente o una con los objetivos por defecto (sin guardar todavía).
     */
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
