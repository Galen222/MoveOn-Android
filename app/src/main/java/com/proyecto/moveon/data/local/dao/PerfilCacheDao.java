package com.proyecto.moveon.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.proyecto.moveon.data.local.entity.PerfilCacheEntity;

/**
 * DAO real de caché de perfil.
 * El PerfilRepository actual necesita:
 * - observe(...)
 * - getNow(...)
 * - upsert(...)
 * - deleteByAccount(...)
 */
@Dao
public interface PerfilCacheDao {

    @Query("SELECT * FROM perfil_cache WHERE accountKey = :accountKey LIMIT 1")
    /**
     * Observable reactivo con la fila de caché del perfil para la cuenta
     * dada. La UI lo observa y se refresca sin volver a preguntar al DAO
     * tras cada actualización local o remota.
     *
     * @param accountKey clave de la cuenta cuyo perfil se observa.
     * @return LiveData que emite la entidad actual cada vez que cambia.
     */
    LiveData<PerfilCacheEntity> observe(String accountKey);

    @Query("SELECT * FROM perfil_cache WHERE accountKey = :accountKey LIMIT 1")
    /**
     * Lectura síncrona, pensada para hilos de I/O. Devuelve {@code null}
     * si todavía no hay perfil cacheado para la cuenta (primera sesión,
     * tras un clear, etc.).
     *
     * @param accountKey clave de la cuenta a consultar.
     * @return fila cacheada o {@code null} si aún no existe.
     */
    PerfilCacheEntity getNow(String accountKey);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    /**
     * Inserta o reemplaza la fila completa por cuenta: hay como mucho una,
     * así que la estrategia {@link OnConflictStrategy#REPLACE} actúa como
     * upsert por clave primaria.
     *
     * @param entity entidad con el estado que debe quedar persistido.
     */
    void upsert(PerfilCacheEntity entity);

    @Query("DELETE FROM perfil_cache WHERE accountKey = :accountKey")
    /**
     * Borra la caché de perfil de una cuenta. Se invoca al cerrar sesión
     * para que otro usuario que inicie sesión en el mismo dispositivo no
     * vea la foto ni los datos del anterior mientras espera la primera
     * sincronización.
     *
     * @param accountKey clave de la cuenta cuya caché se elimina.
     */
    void deleteByAccount(String accountKey);
}
