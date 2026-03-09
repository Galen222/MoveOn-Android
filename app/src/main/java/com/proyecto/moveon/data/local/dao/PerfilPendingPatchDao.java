package com.proyecto.moveon.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.proyecto.moveon.data.local.entity.PerfilPendingPatchEntity;

import java.util.List;

@Dao
public interface PerfilPendingPatchDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(PerfilPendingPatchEntity entity);

    @Query("SELECT * FROM perfil_pending_patch WHERE accountKey = :accountKey AND state = 'PENDING' ORDER BY createdAtMs ASC")
    List<PerfilPendingPatchEntity> getPending(String accountKey);

    @Query("SELECT COUNT(*) FROM perfil_pending_patch WHERE accountKey = :accountKey AND state = 'PENDING'")
    int countPending(String accountKey);

    @Update
    void update(PerfilPendingPatchEntity entity);

    @Query("DELETE FROM perfil_pending_patch WHERE operationId = :operationId")
    void deleteById(String operationId);

    @Query("DELETE FROM perfil_pending_patch WHERE accountKey = :accountKey")
    void deleteAllByAccount(String accountKey);
}
