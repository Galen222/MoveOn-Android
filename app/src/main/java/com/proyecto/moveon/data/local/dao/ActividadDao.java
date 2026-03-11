package com.proyecto.moveon.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.proyecto.moveon.data.local.entity.ActividadEntity;

import java.util.List;

/**
 * DAO real de actividades para la entidad ActividadEntity actual.
 */
@Dao
public interface ActividadDao {

    @Query("SELECT * FROM actividades_locales " +
            "WHERE accountKey = :accountKey AND sync_state != 'PENDING_DELETE' " +
            "ORDER BY fecha_ruta DESC, created_at_ms DESC")
    LiveData<List<ActividadEntity>> observeVisible(String accountKey);

    @Query("SELECT COUNT(*) FROM actividades_locales " +
            "WHERE accountKey = :accountKey AND sync_state != 'PENDING_DELETE'")
    int countVisibleNow(String accountKey);

    @Query("SELECT * FROM actividades_locales WHERE accountKey = :accountKey")
    List<ActividadEntity> getAllNow(String accountKey);

    @Query("SELECT * FROM actividades_locales " +
            "WHERE accountKey = :accountKey AND sync_state IN ('PENDING_CREATE', 'FAILED_CREATE') " +
            "ORDER BY created_at_ms ASC")
    List<ActividadEntity> getPendingCreates(String accountKey);

    @Query("SELECT * FROM actividades_locales " +
            "WHERE accountKey = :accountKey AND sync_state IN ('PENDING_DELETE', 'FAILED_DELETE') " +
            "ORDER BY updated_at_ms ASC")
    List<ActividadEntity> getPendingDeletes(String accountKey);

    @Query("SELECT * FROM actividades_locales WHERE localId = :localId LIMIT 1")
    ActividadEntity getByLocalId(String localId);

    @Query("SELECT * FROM actividades_locales WHERE accountKey = :accountKey AND remoteId = :remoteId LIMIT 1")
    ActividadEntity getByRemoteId(String accountKey, int remoteId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(ActividadEntity entity);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<ActividadEntity> items);

    @Query("DELETE FROM actividades_locales WHERE localId = :localId")
    void deleteByLocalId(String localId);

    @Query("DELETE FROM actividades_locales WHERE accountKey = :accountKey")
    void deleteByAccount(String accountKey);
}
