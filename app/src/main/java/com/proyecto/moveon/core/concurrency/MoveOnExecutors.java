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
            3, new NamedThreadFactory()
    );

    /**
     * Constructor privado: clase de utilidades con sólo métodos estáticos,
     * no está pensada para instanciarse.
     */
    private MoveOnExecutors() {}

    /**
     * Devuelve el executor compartido por toda la app para operaciones de
     * I/O (Room, red, disco). Reutilizarlo evita crear hilos a mano en
     * cada ViewModel y mantiene un pool con tamaño razonable.
     *
     * @return el {@link ExecutorService} global para tareas de I/O.
     */
    @NonNull
    public static ExecutorService io() {
        return IO;
    }

    /**
     * Encola una tarea en el executor global de I/O sin exponer su ciclo de vida
     * al consumidor. El pool pertenece al proceso y no debe cerrarse desde una
     * Activity, Fragment, ViewModel o repositorio individual.
     *
     * @param task tarea de I/O que se ejecutará en segundo plano.
     */
    public static void executeIo(@NonNull Runnable task) {
        IO.execute(task);
    }

    private static final class NamedThreadFactory implements ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger(1);


        /**
         * Crea un hilo con el prefijo configurado y un contador incremental
         * en el nombre, para que los hilos sean identificables en logs y
         * herramientas de profiling. Fija prioridad {@code NORM_PRIORITY} para
         * no competir con el hilo principal.
         *
         * @param runnable tarea a ejecutar en el hilo.
         * @return un {@link Thread} listo para arrancar.
         */
        @Override
        public Thread newThread(@NonNull Runnable runnable) {
            Thread thread = new Thread(runnable, "moveon-io-" + counter.getAndIncrement());
            thread.setPriority(Thread.NORM_PRIORITY);
            return thread;
        }
    }
}
