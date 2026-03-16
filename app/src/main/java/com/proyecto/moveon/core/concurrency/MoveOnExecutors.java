package com.proyecto.moveon.core.concurrency;

import androidx.annotation.NonNull;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Ejecutores compartidos a nivel de proceso.
 *
 * <p>Los repositorios se recrean con frecuencia junto con sus ViewModels; por eso
 * no deben crear un ExecutorService propio en cada instancia. Este ejecutor
 * serial compartido conserva el orden de las operaciones de datos y evita fugas
 * de hilos entre recreaciones de pantalla o ciclos de login/logout.</p>
 */
public final class MoveOnExecutors {

    private static final ExecutorService IO = Executors.newSingleThreadExecutor(
            new NamedThreadFactory("moveon-io")
    );

    private MoveOnExecutors() {}

    @NonNull
    public static ExecutorService io() {
        return IO;
    }

    private static final class NamedThreadFactory implements ThreadFactory {
        private final String prefix;
        private final AtomicInteger counter = new AtomicInteger(1);

        private NamedThreadFactory(@NonNull String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(@NonNull Runnable runnable) {
            Thread thread = new Thread(runnable, prefix + "-" + counter.getAndIncrement());
            thread.setPriority(Thread.NORM_PRIORITY);
            return thread;
        }
    }
}
