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

/**
 * Base de datos local Room.
 *
 * <p>Versión 7: añade persistencia local del ritmo máximo de actividad.
 * La migración es explícita para no perder historiales locales al actualizar.</p>
 */
@Database(
        entities = {
                PerfilCacheEntity.class,
                PerfilPendingPatchEntity.class,
                ActividadEntity.class,
                UserPrefsEntity.class
        },
        version = 7,
        exportSchema = true
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
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `user_prefs` ("
                            + "`accountKey` TEXT NOT NULL, "
                            + "`weeklyGoalMeters` INTEGER NOT NULL, "
                            + "`monthlyGoalMeters` INTEGER NOT NULL, "
                            + "`updatedAtMs` INTEGER NOT NULL, "
                            + "PRIMARY KEY(`accountKey`))"
            );
            database.execSQL("ALTER TABLE `perfil_cache` ADD COLUMN `totalCalorias` INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE `perfil_cache` ADD COLUMN `objetivoSemanalMetros` INTEGER NOT NULL DEFAULT 50000");
            database.execSQL("ALTER TABLE `perfil_cache` ADD COLUMN `objetivoMensualMetros` INTEGER NOT NULL DEFAULT 150000");
        }
    };

    static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `actividades_locales` ADD COLUMN `duracion_total` INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE `actividades_locales` ADD COLUMN `duracion_movimiento` INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE `actividades_locales` ADD COLUMN `duracion_parado` INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE `actividades_locales` ADD COLUMN `duracion_pausa_manual` INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE `actividades_locales` ADD COLUMN `ritmo_medio_movimiento` INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE `actividades_locales` ADD COLUMN `ritmo_medio_total` INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE `actividades_locales` ADD COLUMN `velocidad_media_x100` INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE `actividades_locales` ADD COLUMN `velocidad_max_x100` INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE `actividades_locales` ADD COLUMN `auto_pausas` INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE `actividades_locales` ADD COLUMN `pausas_manuales` INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE `actividades_locales` ADD COLUMN `alertas_velocidad` INTEGER NOT NULL DEFAULT 0");

            database.execSQL("UPDATE `actividades_locales` SET `duracion_total` = `duracion`");
            database.execSQL("UPDATE `actividades_locales` SET `duracion_movimiento` = `duracion`");
            database.execSQL("UPDATE `actividades_locales` SET `duracion_parado` = 0");
            database.execSQL("UPDATE `actividades_locales` SET `duracion_pausa_manual` = 0");

            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `actividades_locales_tmp` ("
                            + "`localId` TEXT NOT NULL, "
                            + "`accountKey` TEXT NOT NULL, "
                            + "`remoteId` INTEGER, "
                            + "`tipo` TEXT NOT NULL, "
                            + "`distancia` INTEGER NOT NULL, "
                            + "`duracion_total` INTEGER NOT NULL, "
                            + "`duracion_movimiento` INTEGER NOT NULL, "
                            + "`duracion_parado` INTEGER NOT NULL, "
                            + "`duracion_pausa_manual` INTEGER NOT NULL, "
                            + "`calorias_quemadas` INTEGER NOT NULL, "
                            + "`ritmo_medio_movimiento` INTEGER NOT NULL, "
                            + "`ritmo_medio_total` INTEGER NOT NULL, "
                            + "`velocidad_media_x100` INTEGER NOT NULL, "
                            + "`velocidad_max_x100` INTEGER NOT NULL, "
                            + "`auto_pausas` INTEGER NOT NULL, "
                            + "`pausas_manuales` INTEGER NOT NULL, "
                            + "`alertas_velocidad` INTEGER NOT NULL, "
                            + "`ruta_polilinea` TEXT, "
                            + "`ruta_mapa_url` TEXT, "
                            + "`fecha_ruta` TEXT NOT NULL, "
                            + "`sync_state` TEXT NOT NULL, "
                            + "`last_error` TEXT, "
                            + "`created_at_ms` INTEGER NOT NULL, "
                            + "`updated_at_ms` INTEGER NOT NULL, "
                            + "PRIMARY KEY(`localId`))"
            );
            database.execSQL(
                    "INSERT INTO `actividades_locales_tmp` ("
                            + "`localId`,`accountKey`,`remoteId`,`tipo`,`distancia`,"
                            + "`duracion_total`,`duracion_movimiento`,`duracion_parado`,`duracion_pausa_manual`,"
                            + "`calorias_quemadas`,`ritmo_medio_movimiento`,`ritmo_medio_total`,"
                            + "`velocidad_media_x100`,`velocidad_max_x100`,`auto_pausas`,`pausas_manuales`,`alertas_velocidad`,"
                            + "`ruta_polilinea`,`ruta_mapa_url`,`fecha_ruta`,`sync_state`,`last_error`,`created_at_ms`,`updated_at_ms`"
                            + ") SELECT "
                            + "`localId`,`accountKey`,`remoteId`,`tipo`,`distancia`,"
                            + "`duracion_total`,`duracion_movimiento`,`duracion_parado`,`duracion_pausa_manual`,"
                            + "`calorias_quemadas`,`ritmo_medio_movimiento`,`ritmo_medio_total`,"
                            + "`velocidad_media_x100`,`velocidad_max_x100`,`auto_pausas`,`pausas_manuales`,`alertas_velocidad`,"
                            + "`ruta_polilinea`,`ruta_mapa_url`,`fecha_ruta`,`sync_state`,`last_error`,`created_at_ms`,`updated_at_ms` "
                            + "FROM `actividades_locales`"
            );
            database.execSQL("DROP TABLE `actividades_locales`");
            database.execSQL("ALTER TABLE `actividades_locales_tmp` RENAME TO `actividades_locales`");
            database.execSQL("CREATE INDEX IF NOT EXISTS `ix_actividad_account_fecha` ON `actividades_locales` (`accountKey`, `fecha_ruta`)");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `ix_actividad_account_remote` ON `actividades_locales` (`accountKey`, `remoteId`)");
        }
    };


    static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `actividades_locales` ADD COLUMN `ritmo_maximo` INTEGER NOT NULL DEFAULT 0");
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
                            .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                            .fallbackToDestructiveMigration(true)
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
