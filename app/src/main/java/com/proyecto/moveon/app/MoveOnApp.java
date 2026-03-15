package com.proyecto.moveon.app;

import android.app.Application;

import com.proyecto.moveon.core.i18n.AppLanguageManager;
import com.proyecto.moveon.core.theme.ThemeManager;
import com.proyecto.moveon.data.local.db.AppDatabase;

/**
 * Application sin singleton estático expuesto.
 * La base de datos se obtiene siempre por contexto vía AppDatabase.getInstance(...).
 */
public class MoveOnApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        AppLanguageManager.applySavedLanguage(this);
        ThemeManager.applySavedTheme(this);

        // Inicialización temprana para calentar Room.
        AppDatabase.getInstance(this);
    }
}
