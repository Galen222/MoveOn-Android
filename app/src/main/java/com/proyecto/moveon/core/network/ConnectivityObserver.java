package com.proyecto.moveon.core.network;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.proyecto.moveon.core.concurrency.MoveOnExecutors;
import com.proyecto.moveon.data.remote.retrofit.AppSessionProvider;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Observador de conectividad a nivel de proceso.
 *
 * <p>Expone un {@link LiveData} con el estado online/offline consumido por la UI
 * y además permite registrar listeners para disparar sincronizaciones cuando la
 * conectividad vuelve de verdad.</p>
 *
 * <p>Este archivo corrige dos problemas típicos del banner offline:</p>
 * <ol>
 *   <li>No escucha "cualquier red" del sistema, sino la <b>red por defecto</b>
 *       de la app mediante {@link ConnectivityManager#registerDefaultNetworkCallback}.</li>
 *   <li>No mezcla snapshots síncronas transitorias dentro del callback con el
 *       estado real de la red que Android está notificando.</li>
 * </ol>
 *
 * <p>La app solo se considera online cuando la red por defecto tiene capacidad
 * de Internet y además está validada por el sistema operativo.</p>
 *
 * <p>Inicializar una vez en {@code Application.onCreate()} con {@link #init(Context)}.</p>
 */
public final class ConnectivityObserver {

    /**
     * Ventana mínima entre despachos de listeners de reconexión (ms).
     * Evita ráfagas cuando la red oscila o cambia entre Wi‑Fi y datos.
     */
    private static final long RECONNECT_THROTTLE_MS = 15_000L;

    private static volatile ConnectivityObserver instance;

    /**
     * Estado observable de conectividad consumido por la UI.
     *
     * <p>Se inicializa a {@code true} para evitar un falso negativo visual antes
     * de calcular el estado real en {@link #init(Context)}.</p>
     */
    private final MutableLiveData<Boolean> connected = new MutableLiveData<>(true);

    /**
     * Lista de listeners a ejecutar cuando la conectividad usable vuelve.
     *
     * <p>{@link CopyOnWriteArrayList} es adecuada aquí porque hay muy pocas altas
     * y bajas de listeners, pero muchas lecturas en comparación.</p>
     */
    private final List<Runnable> reconnectListeners = new CopyOnWriteArrayList<>();

    /**
     * Timestamp del último despacho de listeners de reconexión.
     *
     * <p>Se usa {@link SystemClock#elapsedRealtime()} porque es monotónico y no
     * depende de cambios manuales de hora del sistema.</p>
     */
    private final AtomicLong lastReconnectDispatchMs = new AtomicLong(0L);

    /**
     * Referencia al callback registrado para evitar registrar varias veces el
     * observador si {@link #init(Context)} se llama más de una vez por error.
     */
    @Nullable
    private volatile ConnectivityManager.NetworkCallback networkCallback;

    /**
     * Constructor privado: esta clase se usa como singleton; obtener la
     * instancia con {@link #getInstance()} garantiza un único observador
     * registrado frente al sistema.
     */
    private ConnectivityObserver() {
    }

    /**
     * Devuelve la instancia singleton del observador.
     *
     * @return instancia única de {@link ConnectivityObserver} para todo el proceso.
     */
    @NonNull
    public static ConnectivityObserver getInstance() {
        if (instance == null) {
            synchronized (ConnectivityObserver.class) {
                if (instance == null) {
                    instance = new ConnectivityObserver();
                }
            }
        }
        return instance;
    }

    /**
     * Registra el callback de conectividad.
     *
     * <p>Debe llamarse una sola vez desde {@code Application.onCreate()}.</p>
     *
     * @param context contexto de aplicación.
     */
    public synchronized void init(@NonNull Context context) {
        final ConnectivityManager cm = (ConnectivityManager)
                context.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return;
        }

        // Evita callbacks duplicados si init(...) se invoca más de una vez.
        if (networkCallback != null) {
            return;
        }

        // Estado inicial calculado con el mismo criterio estricto que usaremos
        // en el resto de caminos.
        connected.postValue(isCurrentlyConnected(cm));

        final ConnectivityManager.NetworkCallback callback = new ConnectivityManager.NetworkCallback() {
            @Override
            /**
             * Callback del sistema cuando hay una red disponible. Se deja vacío a
             * propósito: {@code onAvailable} no garantiza todavía Internet usable,
             * por lo que esperamos a {@link #onCapabilitiesChanged} para actualizar
             * el estado y disparar la reconexión.
             *
             * @param network red recién disponible.
             */
            public void onAvailable(@NonNull Network network) {
                // OJO: onAvailable NO garantiza todavía Internet usable.
                // Android puede notificar una red disponible antes de validarla,
                // por eso aquí no marcamos online ni lanzamos reconexión.
            }

            @Override
            /**
             * Callback del sistema cuando se pierde la red por defecto. Marcamos
             * offline inmediatamente para que el banner aparezca sin esperar a un
             * snapshot asíncrono de {@code activeNetwork}.
             *
             * @param network red que se acaba de perder.
             */
            public void onLost(@NonNull Network network) {
                // En el callback de la red por defecto, perder esa red significa
                // que la app se ha quedado sin red por defecto efectiva.
                // Marcamos offline inmediatamente para que el banner aparezca
                // sin depender de snapshots transitorias de activeNetwork.
                connected.postValue(false);
            }

            @Override
            /**
             * Callback fiable para saber si la red por defecto tiene Internet
             * realmente usable: cuando las capabilities indican INTERNET y
             * VALIDATED, pasamos a online y, si veníamos de offline, disparamos
             * la lógica de reconexión (drenar colas, refrescar datos).
             *
             * @param network red cuyas capabilities han cambiado.
             * @param caps capabilities actualizadas que permiten comprobar si hay Internet validado.
             */
            public void onCapabilitiesChanged(@NonNull Network network,
                                              @NonNull NetworkCapabilities caps) {
                // Este es el callback fiable para saber si la red por defecto ya
                // tiene Internet usable de verdad.
                boolean wasOffline = Boolean.FALSE.equals(connected.getValue());
                boolean onlineNow = hasUsableInternet(caps);

                connected.postValue(onlineNow);

                // Solo se dispara la lógica de reconexión cuando veníamos de
                // offline y la red por defecto pasa a estar validada.
                if (onlineNow && wasOffline) {
                    onNetworkRestored();
                }
            }
        };

        networkCallback = callback;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // API correcta para seguir la red efectiva de la app.
            cm.registerDefaultNetworkCallback(callback);
        } else {
            // Fallback defensivo para APIs antiguas, aunque en ese caso el
            // comportamiento puede ser menos preciso que con la red por defecto.
            cm.registerNetworkCallback(
                    new android.net.NetworkRequest.Builder()
                            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                            .build(),
                    callback
            );
        }
    }

    /**
     * Devuelve un observable con el estado actual de conectividad.
     *
     * @return {@code true} si la app tiene Internet usable; {@code false} en caso contrario.
     */
    @NonNull
    public LiveData<Boolean> isConnected() {
        return connected;
    }

    /**
     * Registra una acción a ejecutar cuando la conectividad vuelve de verdad.
     *
     * <p>Típicamente se usa para encolar sincronizaciones pendientes de los
     * repositorios offline-first. Los listeners se ejecutan en el executor de
     * {@link MoveOnExecutors#io()} cuando {@link #onNetworkRestored()} confirma
     * una reconexión válida.</p>
     *
     * @param listener listener a ejecutar al recuperar conectividad usable.
     */
    public void addOnReconnectListener(@NonNull Runnable listener) {
        reconnectListeners.add(listener);
    }

    /**
     * Elimina un listener registrado previamente.
     *
     * @param listener listener a eliminar.
     */
    public void removeOnReconnectListener(@NonNull Runnable listener) {
        reconnectListeners.remove(listener);
    }

    /**
     * Ejecuta la lógica de reconexión con throttling.
     *
     * <p>Si la última ejecución ocurrió hace menos de
     * {@link #RECONNECT_THROTTLE_MS}, se ignora el evento para no disparar
     * sincronizaciones duplicadas por oscilaciones de red.</p>
     */
    private void onNetworkRestored() {
        // Reseteamos el cooldown de fallos de handshake para que la primera
        // operación tras reconectar no herede un estado viejo de error.
        AppSessionProvider.resetFailureCooldown();

        long now = SystemClock.elapsedRealtime();
        long last = lastReconnectDispatchMs.get();

        // Ignoramos ráfagas de reconexión demasiado cercanas.
        if (now - last < RECONNECT_THROTTLE_MS) {
            return;
        }

        // Solo un hilo gana el derecho a despachar listeners.
        if (!lastReconnectDispatchMs.compareAndSet(last, now)) {
            return;
        }

        // Ejecutamos en IO para no bloquear el callback del sistema.
        for (Runnable listener : reconnectListeners) {
            MoveOnExecutors.io().execute(listener);
        }
    }

    /**
     * Comprueba si unas capabilities representan Internet realmente usable.
     *
     * @param caps capabilities de una red concreta.
     * @return {@code true} si la red tiene INTERNET y además está VALIDATED.
     */
    private static boolean hasUsableInternet(@Nullable NetworkCapabilities caps) {
        return caps != null
                && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    /**
     * Calcula el estado actual de conectividad usable de la app.
     *
     * <p>Se usa solo para el estado inicial fuera de los callbacks. Dentro de los
     * callbacks nos apoyamos en la red por defecto que Android ya nos notifica.</p>
     *
     * @param cm connectivity manager del sistema.
     * @return {@code true} si la red activa actual es usable.
     */
    private static boolean isCurrentlyConnected(@NonNull ConnectivityManager cm) {
        Network active = cm.getActiveNetwork();
        if (active == null) {
            return false;
        }
        return hasUsableInternet(cm.getNetworkCapabilities(active));
    }
}
