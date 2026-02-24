package com.proyecto.moveon;

import android.app.Application;

public class MoveOnApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        ThemeManager.applySavedTheme(this);
    }
}