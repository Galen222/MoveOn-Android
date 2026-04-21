package com.proyecto.moveon.app;

import android.app.Application;

import com.proyecto.moveon.core.concurrency.MoveOnExecutors;
import com.proyecto.moveon.core.i18n.AppLanguageManager;
import com.proyecto.moveon.core.network.ConnectivityObserver;
import com.proyecto.moveon.core.theme.ThemeManager;
import com.proyecto.moveon.data.local.db.AppDatabase;

/**
 * Clase Application principal encargada de inicializar la aplicación.
 */
public class MoveOnApp extends Application {

    @Override
    /**
     * Arranque a nivel de proceso. Aplica idioma y tema persistidos antes
     * de que se cree la primera Activity (evita parpadeos) y hace un
     * calentamiento de Room en IO para que la primera navegación no
     * pague el coste de abrir la base de datos en el hilo principal.
     *
     * <p>También arranca el observador de conectividad e inscribe la
     * acción de reconexión que dispara los Workers de sincronización
     * cuando la red vuelve.</p>
     */
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

        // Usar ServiceLocator en vez de new para que
        // PerfilRepository reutilice el UserPrefsRepository singleton.
        // Se registra como listener adicional para no sobrescribir otros callbacks de reconexión.
        connectivity.addOnReconnectListener(() -> {
            ServiceLocator locator = ServiceLocator.getInstance(this);
            locator.newActivityRepository().enqueueSync();
            locator.newPerfilRepository().enqueueSync();
        });
    }
}