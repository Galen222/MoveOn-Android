package com.proyecto.moveon.app;

import android.app.Application;

import com.proyecto.moveon.core.theme.ThemeManager;
import com.proyecto.moveon.data.local.db.AppDatabase;

public class MoveOnApp extends Application {

    private static MoveOnApp instance;
    private AppDatabase db;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        ThemeManager.applySavedTheme(this);
        db = AppDatabase.getInstance(this);
    }

    public static MoveOnApp getInstance() {
        if (instance == null) {
            throw new IllegalStateException("MoveOnApp no ha sido inicializada. " +
                    "Asegúrate de que está declarada en el AndroidManifest.xml");
        }
        return instance;
    }

    public AppDatabase getDb() {
        return db;
    }
}
