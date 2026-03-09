package com.proyecto.moveon.data.local.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.proyecto.moveon.data.local.dao.ActividadDao;
import com.proyecto.moveon.data.local.dao.PerfilCacheDao;
import com.proyecto.moveon.data.local.dao.PerfilPendingPatchDao;
import com.proyecto.moveon.data.local.entity.ActividadEntity;
import com.proyecto.moveon.data.local.entity.PerfilCacheEntity;
import com.proyecto.moveon.data.local.entity.PerfilPendingPatchEntity;

@Database(
        entities = {
                PerfilCacheEntity.class,
                PerfilPendingPatchEntity.class,
                ActividadEntity.class
        },
        version = 4,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;

    public abstract PerfilCacheDao perfilCacheDao();
    public abstract PerfilPendingPatchDao perfilPendingPatchDao();
    public abstract ActividadDao actividadDao();

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "moveon_local.db"
                            )
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
