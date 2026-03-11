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
        exportSchema = true
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
                            // Importante: NO destruir datos offline silenciosamente.
                            // .fallbackToDestructiveMigration(true)
                            // Como no se está cambiando el esquema,
                            // no hace falta tocar la versión ni añadir migraciones nuevas.
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
