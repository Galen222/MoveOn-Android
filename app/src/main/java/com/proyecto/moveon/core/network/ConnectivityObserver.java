package com.proyecto.moveon.core.network;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.proyecto.moveon.core.concurrency.MoveOnExecutors;
import com.proyecto.moveon.data.remote.retrofit.AppSessionProvider;

/**
 * Observador de conectividad a nivel de proceso.
 *
 * <p>Monitoriza la red con {@link ConnectivityManager.NetworkCallback} y expone
 * un {@link LiveData} con el estado actual de conectividad. Cuando detecta que
 * la red vuelve tras una desconexión:</p>
 * <ol>
 *   <li>Resetea el cooldown de {@link AppSessionProvider} para que el próximo
 *       handshake se intente de verdad.</li>
 *   <li>Ejecuta un {@link Runnable} opcional (inyectado vía {@link #setOnReconnect})
 *       para lanzar la sincronización de repos pendientes.</li>
 * </ol>
 *
 * <p>Inicializar una vez en {@code Application.onCreate()} con {@link #init(Context)}.</p>
 */
public final class ConnectivityObserver {

    private static volatile ConnectivityObserver instance;

    private final MutableLiveData<Boolean> connected = new MutableLiveData<>(true);
    private volatile Runnable onReconnect;

    private ConnectivityObserver() {}

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
     * Registra el NetworkCallback. Llamar una vez desde {@code Application.onCreate()}.
     */
    public void init(@NonNull Context context) {
        ConnectivityManager cm = (ConnectivityManager)
                context.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return;

        // Estado inicial
        connected.postValue(isCurrentlyConnected(cm));

        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();

        cm.registerNetworkCallback(request, new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                boolean wasOffline = Boolean.FALSE.equals(connected.getValue());
                connected.postValue(true);

                if (wasOffline) {
                    onNetworkRestored();
                }
            }

            @Override
            public void onLost(@NonNull Network network) {
                // Verificar si hay OTRA red disponible antes de marcar offline.
                // En dispositivos con Wi-Fi + datos, perder Wi-Fi no implica
                // estar offline si los datos móviles siguen activos.
                connected.postValue(isCurrentlyConnected(cm));
            }

            @Override
            public void onCapabilitiesChanged(@NonNull Network network,
                                              @NonNull NetworkCapabilities caps) {
                boolean hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
                boolean wasOffline = Boolean.FALSE.equals(connected.getValue());
                connected.postValue(hasInternet);

                if (hasInternet && wasOffline) {
                    onNetworkRestored();
                }
            }
        });
    }

    /**
     * Estado actual de la red. {@code true} = online, {@code false} = offline.
     * Observar desde la Activity para mostrar/ocultar el banner.
     */
    @NonNull
    public LiveData<Boolean> isConnected() {
        return connected;
    }

    /**
     * Inyecta la acción a ejecutar cuando la red vuelve.
     * Típicamente: enqueueSync de todos los repositorios con patches pendientes.
     * Se ejecuta en hilo IO para no bloquear el callback del sistema.
     */
    public void setOnReconnect(@NonNull Runnable action) {
        this.onReconnect = action;
    }

    private void onNetworkRestored() {
        // Resetear el cooldown del handshake para que la primera operación
        // tras reconectar no falle con "cooldown tras fallo reciente".
        AppSessionProvider.resetFailureCooldown();

        Runnable action = onReconnect;
        if (action != null) {
            MoveOnExecutors.io().execute(action);
        }
    }

    private static boolean isCurrentlyConnected(@NonNull ConnectivityManager cm) {
        Network active = cm.getActiveNetwork();
        if (active == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(active);
        return caps != null
                && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }
}
