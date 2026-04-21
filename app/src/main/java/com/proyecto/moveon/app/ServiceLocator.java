package com.proyecto.moveon.app;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.proyecto.moveon.data.activities.ActivityRepository;
import com.proyecto.moveon.data.profile.PerfilRepository;
import com.proyecto.moveon.data.profile.UserPrefsRepository;
import com.proyecto.moveon.data.ranking.RankingRepository;
import com.proyecto.moveon.data.session.AuthRepository;

/**
 * Localizador de servicios a nivel de proceso.
 *
 * <p>Centraliza la creación de repositorios para eliminar el acoplamiento con
 * {@code new} en cada ViewModel y facilitar el testing.</p>
 *
 * <h3>Factory vs Singleton</h3>
 * <ul>
 *   <li>Repositorios con {@code cancelAll()} se crean por consumidor para que
 *   {@code onCleared()} no cancele peticiones ajenas.</li>
 *   <li>{@link UserPrefsRepository} se comparte como singleton porque no mantiene
 *   llamadas en vuelo asociadas a una pantalla concreta.</li>
 * </ul>
 */
public class ServiceLocator {

    private static volatile ServiceLocator instance;

    private final Context appContext;

    // Singleton: sin cancelAll, seguro compartir entre consumidores.
    private volatile UserPrefsRepository userPrefsRepository;

    /**
     * Crea un localizador ligado al contexto de aplicación.
     *
     * @param context cualquier contexto Android desde el que obtener el {@code applicationContext}.
     */
    protected ServiceLocator(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
    }

    /**
     * Devuelve la instancia global del localizador, creándola de forma perezosa si todavía no existe.
     *
     * @param context cualquier contexto Android desde el que obtener el {@code applicationContext}.
     * @return instancia singleton de {@link ServiceLocator}.
     */
    @NonNull
    public static ServiceLocator getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (ServiceLocator.class) {
                if (instance == null) {
                    instance = new ServiceLocator(context);
                }
            }
        }
        return instance;
    }

    /**
     * Permite sustituir el localizador global por una implementación de pruebas.
     *
     * @param testLocator instancia preparada para el escenario de test.
     */
    @VisibleForTesting
    public static void swap(@NonNull ServiceLocator testLocator) {
        synchronized (ServiceLocator.class) {
            instance = testLocator;
        }
    }

    /**
     * Crea un repositorio de autenticación independiente para el consumidor actual.
     *
     * @return {@link AuthRepository} nuevo para el consumidor actual.
     */
    @NonNull
    public AuthRepository newAuthRepository() {
        return new AuthRepository(appContext);
    }

    /**
     * Crea un repositorio de actividades con ciclo de vida propio.
     *
     * @return {@link ActivityRepository} nuevo para el consumidor actual.
     */
    @NonNull
    public ActivityRepository newActivityRepository() {
        return new ActivityRepository(appContext);
    }

    /**
     * Crea un repositorio de perfil que reutiliza el singleton de preferencias del usuario.
     *
     * @return {@link PerfilRepository} nuevo para el consumidor actual.
     */
    @NonNull
    public PerfilRepository newPerfilRepository() {
        return new PerfilRepository(appContext, getUserPrefsRepository());
    }

    /**
     * Crea un repositorio de ranking desacoplado del resto de consumidores.
     *
     * @return {@link RankingRepository} nuevo para el consumidor actual.
     */
    @NonNull
    public RankingRepository newRankingRepository() {
        return new RankingRepository(appContext);
    }

    /**
     * Devuelve el repositorio singleton de preferencias de usuario, creándolo la primera vez que se solicita.
     *
     * @return singleton de {@link UserPrefsRepository}.
     */
    @NonNull
    public UserPrefsRepository getUserPrefsRepository() {
        if (userPrefsRepository == null) {
            synchronized (this) {
                if (userPrefsRepository == null) {
                    userPrefsRepository = new UserPrefsRepository(appContext);
                }
            }
        }
        return userPrefsRepository;
    }
}
