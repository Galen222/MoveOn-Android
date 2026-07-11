package com.proyecto.moveon.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.proyecto.moveon.data.local.entity.UserPrefsEntity;

/**
 * DAO para la tabla user_prefs.
 * Operaciones disponibles:
 * - observe: LiveData reactivo para que el ViewModel se actualice automáticamente.
 * - getNow: lectura síncrona para acceso puntual sin observación.
 * - upsert: inserta o reemplaza la fila completa (INSERT OR REPLACE).
 */
@Dao
public interface UserPrefsDao {

    /**
     * Observable reactivo de la fila de preferencias del usuario indicado.
     * La UI lo suscribe para recalcular progreso y objetivos sin tener
     * que re-consultar a mano tras cada PATCH.
     *
     * @param accountKey clave de la cuenta cuyas preferencias se observan.
     * @return LiveData que emite la fila actual cada vez que cambia.
     */
    @Query("SELECT * FROM user_prefs WHERE accountKey = :accountKey LIMIT 1")
    LiveData<UserPrefsEntity> observe(@androidx.annotation.NonNull String accountKey);

    /**
     * Lectura síncrona usada desde hilos de I/O (workers, repositorios).
     * Devuelve {@code null} si aún no hay preferencias guardadas para la
     * cuenta.
     *
     * @param accountKey clave de la cuenta a consultar.
     * @return fila existente o {@code null} si no hay preferencias persistidas todavía.
     */
    @Query("SELECT * FROM user_prefs WHERE accountKey = :accountKey LIMIT 1")
    UserPrefsEntity getNow(@androidx.annotation.NonNull String accountKey);

    /**
     * Inserta o reemplaza la fila de preferencias: hay como mucho una por
     * cuenta, así que la estrategia {@link OnConflictStrategy#REPLACE}
     * actúa como un upsert por clave primaria.
     *
     * @param prefs entidad con el estado que debe quedar persistido.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(@androidx.annotation.NonNull UserPrefsEntity prefs);
}
