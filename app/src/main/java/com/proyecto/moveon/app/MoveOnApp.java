package com.proyecto.moveon.app;

import android.app.Application;

import com.proyecto.moveon.core.concurrency.MoveOnExecutors;
import com.proyecto.moveon.core.i18n.AppLanguageManager;
import com.proyecto.moveon.core.network.ConnectivityObserver;
import com.proyecto.moveon.core.theme.ThemeManager;
import com.proyecto.moveon.data.local.db.AppDatabase;

/**
 * {@link Application} principal del proceso MoveOn.
 *
 * <p>Centraliza la inicialización temprana que debe ocurrir una sola vez por arranque:
 * idioma, tema, calentamiento de {@link AppDatabase} y registro del
 * {@link ConnectivityObserver} global.</p>
 */
public class MoveOnApp extends Application {

    /**
     * Arranque a nivel de proceso de la app.
     *
     * <p>Aplica idioma y tema persistidos antes de que nazca la primera Activity para
     * evitar parpadeos visuales, precalienta {@link AppDatabase} en {@link MoveOnExecutors#io()}
     * para amortizar la apertura inicial de Room y deja preparado el observador global de red.</p>
     *
     * <p>Cuando la conectividad se recupera, registra una acción de reconexión que reutiliza
     * {@link ServiceLocator#getInstance(android.content.Context)} para lanzar
     * {@link com.proyecto.moveon.data.activities.ActivityRepository#enqueueSync()} y
     * {@link com.proyecto.moveon.data.profile.PerfilRepository#enqueueSync()} sin crear
     * repositorios desconectados del grafo principal.</p>
     *
     * @see ConnectivityObserver#init(android.content.Context)
     * @see ConnectivityObserver#addOnReconnectListener(Runnable)
     */
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