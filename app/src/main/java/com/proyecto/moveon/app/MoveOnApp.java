package com.proyecto.moveon.app;

import android.app.Application;

import com.proyecto.moveon.core.concurrency.MoveOnExecutors;
import com.proyecto.moveon.core.i18n.AppLanguageManager;
import com.proyecto.moveon.core.network.ConnectivityObserver;
import com.proyecto.moveon.core.theme.ThemeManager;
import com.proyecto.moveon.data.activities.ActivityRepository;
import com.proyecto.moveon.data.local.db.AppDatabase;
import com.proyecto.moveon.data.profile.PerfilRepository;

public class MoveOnApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        AppLanguageManager.applySavedLanguage(this);
        ThemeManager.applySavedTheme(this);

        // Calentamiento de Room movido a hilo IO.
        MoveOnExecutors.io().execute(() -> AppDatabase.getInstance(this));

        // Observador de conectividad a nivel de proceso.
        // Registra el NetworkCallback y configura la acción de reconexión:
        // cuando la red vuelve, se lanzan los Workers de sincronización
        // de ambos repositorios para que los patches pendientes se envíen
        // sin esperar a que el usuario haga otra acción.
        ConnectivityObserver connectivity = ConnectivityObserver.getInstance();
        connectivity.init(this);
        connectivity.setOnReconnect(() -> {
            new ActivityRepository(this).enqueueSync();
            new PerfilRepository(this).enqueueSync();
        });
    }
}
