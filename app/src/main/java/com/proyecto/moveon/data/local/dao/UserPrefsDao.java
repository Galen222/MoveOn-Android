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
 * - deleteByAccount: limpieza al hacer logout.
 */
@Dao
public interface UserPrefsDao {

    @Query("SELECT * FROM user_prefs WHERE accountKey = :accountKey LIMIT 1")
    LiveData<UserPrefsEntity> observe(@androidx.annotation.NonNull String accountKey);

    @Query("SELECT * FROM user_prefs WHERE accountKey = :accountKey LIMIT 1")
    UserPrefsEntity getNow(@androidx.annotation.NonNull String accountKey);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(@androidx.annotation.NonNull UserPrefsEntity prefs);

    @Query("DELETE FROM user_prefs WHERE accountKey = :accountKey")
    void deleteByAccount(@androidx.annotation.NonNull String accountKey);
}