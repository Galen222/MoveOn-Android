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
 * {@code new} en cada ViewModel y facilitar el testing (se puede sustituir
 * con {@link #swap(ServiceLocator)} en tests instrumentados).</p>
 *
 * <h3>Factory vs Singleton</h3>
 * <ul>
 *   <li>Repositorios con {@code cancelAll()} → factory ({@code newXxx()}).
 *       Cada consumidor necesita su propia instancia para que
 *       {@code onCleared()} no cancele peticiones de otros ViewModels.</li>
 *   <li>{@link UserPrefsRepository} → singleton. No tiene cancelación,
 *       solo escribe en Room y lanza PATCHs fire-and-forget.</li>
 * </ul>
 */
public class ServiceLocator {

    private static volatile ServiceLocator instance;

    private final Context appContext;

    // Singleton: sin cancelAll, seguro compartir entre consumidores.
    private volatile UserPrefsRepository userPrefsRepository;

    protected ServiceLocator(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
    }

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
     * Permite sustituir el ServiceLocator en tests instrumentados.
     * Llamar antes de que ningún ViewModel acceda a la instancia.
     */
    @VisibleForTesting
    public static void swap(@NonNull ServiceLocator testLocator) {
        synchronized (ServiceLocator.class) {
            instance = testLocator;
        }
    }

    // ── Factory methods (instancia nueva por consumidor) ─────────────────────

    @NonNull
    public AuthRepository newAuthRepository() {
        return new AuthRepository(appContext);
    }

    @NonNull
    public ActivityRepository newActivityRepository() {
        return new ActivityRepository(appContext);
    }

    @NonNull
    public PerfilRepository newPerfilRepository() {
        return new PerfilRepository(appContext, getUserPrefsRepository());
    }

    @NonNull
    public RankingRepository newRankingRepository() {
        return new RankingRepository(appContext);
    }

    // ── Singleton ────────────────────────────────────────────────────────────

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