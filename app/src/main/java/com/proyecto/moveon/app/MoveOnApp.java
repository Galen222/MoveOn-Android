package com.proyecto.moveon.app;

import android.app.Application;

import com.proyecto.moveon.core.theme.ThemeManager;

public class MoveOnApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        ThemeManager.applySavedTheme(this);
    }
}