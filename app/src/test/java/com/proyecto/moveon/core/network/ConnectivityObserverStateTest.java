package com.proyecto.moveon.core.network;

import static org.junit.Assert.*;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tests JVM puros de {@link ConnectivityObserver}, centrados en el estado
 * inicial del singleton, los listeners de reconexión y los helpers privados
 * que pueden ejercitarse por reflexión.
 *
 * <p>Se ejecutan bajo {@link RobolectricTestRunner} para que {@code init}
 * pueda obtener un {@code ConnectivityManager} simulado del sistema y no
 * lance al registrar callbacks de red.</p>
 */
@RunWith(RobolectricTestRunner.class)

public class ConnectivityObserverStateTest {

    /**
     * Verifica que {@link ConnectivityObserver#getInstance()} devuelve siempre
     * la misma instancia.
     */
    @Test
    public void getInstance_returnsSingleton() {
        ConnectivityObserver first = ConnectivityObserver.getInstance();
        ConnectivityObserver second = ConnectivityObserver.getInstance();

        assertNotNull(first);
        assertSame(first, second);
    }

    /**
     * Verifica que {@link ConnectivityObserver#isConnected()} expone un
     * {@code LiveData} no nulo.
     */
    @Test
    public void isConnected_liveDataIsNotNull() {
        ConnectivityObserver observer = ConnectivityObserver.getInstance();

        assertNotNull(observer.isConnected());
    }

    /**
     * Verifica que añadir y quitar un listener de reconexión no lanza.
     */
    @Test
    public void addAndRemoveOnReconnectListener_isIdempotent() {
        ConnectivityObserver observer = ConnectivityObserver.getInstance();

        AtomicInteger calls = new AtomicInteger();
        Runnable listener = calls::incrementAndGet;

        observer.addOnReconnectListener(listener);
        observer.removeOnReconnectListener(listener);

        assertEquals(0, calls.get());
    }

    /**
     * Verifica que {@code init} no lanza con un {@code Context} de Robolectric
     * y soporta llamadas repetidas de forma idempotente.
     */
    @Test
    public void init_withRobolectricContext_doesNotThrow() {
        ConnectivityObserver observer = ConnectivityObserver.getInstance();
        Context context = ApplicationProvider.getApplicationContext();

        observer.init(context);
        observer.init(context);

        assertNotNull(observer.isConnected());
    }

    /**
     * Verifica que el helper {@code hasUsableInternet} devuelve {@code false}
     * cuando se le pasa {@code null} en lugar de capacidades reales.
     */
    @Test
    public void hasUsableInternet_nullCapabilities_returnsFalse() throws Exception {
        Method method = ConnectivityObserver.class.getDeclaredMethod(
                "hasUsableInternet", android.net.NetworkCapabilities.class);
        method.setAccessible(true);

        Object result = method.invoke(null, new Object[]{null});
        assertEquals(Boolean.FALSE, result);
    }
}
