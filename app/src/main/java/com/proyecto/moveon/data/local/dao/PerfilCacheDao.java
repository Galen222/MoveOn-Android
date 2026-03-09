package com.proyecto.moveon.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.proyecto.moveon.data.local.entity.PerfilCacheEntity;

@Dao
public interface PerfilCacheDao {

    @Query("SELECT * FROM perfil_cache WHERE accountKey = :accountKey LIMIT 1")
    LiveData<PerfilCacheEntity> observe(String accountKey);

    @Query("SELECT * FROM perfil_cache WHERE accountKey = :accountKey LIMIT 1")
    PerfilCacheEntity getNow(String accountKey);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(PerfilCacheEntity entity);

    @Update
    void update(PerfilCacheEntity entity);

    @Query("DELETE FROM perfil_cache WHERE accountKey = :accountKey")
    void deleteByAccount(String accountKey);
}
