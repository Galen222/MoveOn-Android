package com.proyecto.moveon.app;

import android.app.Application;
import com.proyecto.moveon.core.theme.ThemeManager;

public class MoveOnApp extends Application {

    private static MoveOnApp instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        ThemeManager.applySavedTheme(this);
    }

    public static MoveOnApp getInstance() {
        if (instance == null) {
            throw new IllegalStateException("MoveOnApp no ha sido inicializada. " +
                    "Asegúrate de que está declarada en el AndroidManifest.xml");
        }
        return instance;
    }
}