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
 * compartido evita fugas de hilos entre recreaciones de pantalla o ciclos de
 * login/logout.</p>
 *
 * <h3>¿Por qué un pool de 3 hilos y no 1?</h3>
 * <p>Con un solo hilo, cualquier llamada de red bloqueante (timeout de 8-30 s
 * con backend caído) impedía que las demás operaciones se ejecutaran. Ejemplo
 * real: {@code setWeeklyGoal} bloqueaba el hilo 30 s con un
 * {@code patchPerfilBlocking} cuyo resultado ni siquiera se usaba, y detrás
 * se encolaba el {@code applyLocalPatchAndEnqueue} del toggle de perfil
 * visible, que tardaba otros 30 s más. Total: 1 minuto de hilo IO bloqueado
 * y la app congelada.</p>
 *
 * <p>Con 3 hilos, un patch, una subida de foto y una lectura de Room pueden
 * correr en paralelo. Room usa WAL mode y es thread-safe. No se sube más
 * de 4 porque la contención de SQLite crece sin aportar beneficio.</p>
 */
public final class MoveOnExecutors {

    // Un pool fijo evita que una llamada de red bloqueante monopolice
    // todas las operaciones de IO encoladas detrás.
    private static final ExecutorService IO = Executors.newFixedThreadPool(
            3, new NamedThreadFactory("moveon-io")
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
