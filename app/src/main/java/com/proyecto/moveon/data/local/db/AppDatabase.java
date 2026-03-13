package com.proyecto.moveon.data.local.db;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.proyecto.moveon.data.local.dao.ActividadDao;
import com.proyecto.moveon.data.local.dao.PerfilCacheDao;
import com.proyecto.moveon.data.local.dao.PerfilPendingPatchDao;
import com.proyecto.moveon.data.local.dao.UserPrefsDao;
import com.proyecto.moveon.data.local.entity.ActividadEntity;
import com.proyecto.moveon.data.local.entity.PerfilCacheEntity;
import com.proyecto.moveon.data.local.entity.PerfilPendingPatchEntity;
import com.proyecto.moveon.data.local.entity.UserPrefsEntity;

@Database(
        entities = {
                PerfilCacheEntity.class,
                PerfilPendingPatchEntity.class,
                ActividadEntity.class,
                UserPrefsEntity.class
        },
        version = 5,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;

    public abstract PerfilCacheDao perfilCacheDao();
    public abstract PerfilPendingPatchDao perfilPendingPatchDao();
    public abstract ActividadDao actividadDao();
    public abstract UserPrefsDao userPrefsDao();

    static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // Nueva tabla user_prefs
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `user_prefs` ("
                            + "`accountKey` TEXT NOT NULL, "
                            + "`weeklyGoalMeters` INTEGER NOT NULL, "
                            + "`monthlyGoalMeters` INTEGER NOT NULL, "
                            + "`updatedAtMs` INTEGER NOT NULL, "
                            + "PRIMARY KEY(`accountKey`))"
            );
            // Nuevas columnas en perfil_cache
            database.execSQL("ALTER TABLE `perfil_cache` ADD COLUMN `totalCalorias` INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE `perfil_cache` ADD COLUMN `objetivoSemanalMetros` INTEGER NOT NULL DEFAULT 50000");
            database.execSQL("ALTER TABLE `perfil_cache` ADD COLUMN `objetivoMensualMetros` INTEGER NOT NULL DEFAULT 150000");
        }
    };

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "moveon_local.db"
                            )
                            .addMigrations(MIGRATION_4_5)
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}