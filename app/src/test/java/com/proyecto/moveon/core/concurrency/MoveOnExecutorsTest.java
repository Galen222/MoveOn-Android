package com.proyecto.moveon.core.concurrency;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
/**
 * Pruebas para validar el comportamiento de move on executors.
 */
public class MoveOnExecutorsTest {

    /**
     * Verifica el escenario cubierto por {@link #io_returnsNonNull()}.
     */
    @Test
    public void io_returnsNonNull() {
        ExecutorService io = MoveOnExecutors.io();
        assertNotNull(io);
    }

    /**
     * Verifica el escenario cubierto por {@link #io_returnsSameInstance()}.
     */
    @Test
    public void io_returnsSameInstance() {
        ExecutorService a = MoveOnExecutors.io();
        ExecutorService b = MoveOnExecutors.io();
        assertSame(a, b);
    }

    /**
     * Verifica el escenario cubierto por {@link #io_canExecuteTasks()}.
     */
    @Test
    public void io_canExecuteTasks() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger result = new AtomicInteger(0);

        MoveOnExecutors.io().execute(() -> {
            result.set(42);
            latch.countDown();
        });

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(42, result.get());
    }

    /**
     * Verifica el escenario cubierto por {@link #io_supportsParallelExecution()}.
     */
    @Test
    public void io_supportsParallelExecution() throws InterruptedException {
        // El pool tiene 3 hilos; 3 tareas deben poder ejecutarse en paralelo
        int parallelTasks = 3;
        CountDownLatch allStarted = new CountDownLatch(parallelTasks);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch allDone = new CountDownLatch(parallelTasks);

        for (int i = 0; i < parallelTasks; i++) {
            MoveOnExecutors.io().execute(() -> {
                allStarted.countDown();
                try {
                    release.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {}
                allDone.countDown();
            });
        }

        // Las 3 tareas deben arrancar sin esperarse entre sí
        assertTrue("Las 3 tareas deberían arrancar en paralelo",
                allStarted.await(2, TimeUnit.SECONDS));

        release.countDown();
        assertTrue(allDone.await(2, TimeUnit.SECONDS));
    }

    /**
     * Verifica el escenario cubierto por {@link #io_threadNaming()}.
     */
    @Test
    public void io_threadNaming() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        StringBuilder threadName = new StringBuilder();

        MoveOnExecutors.io().execute(() -> {
            threadName.append(Thread.currentThread().getName());
            latch.countDown();
        });

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertTrue("Thread name should start with 'moveon-io-'",
                threadName.toString().startsWith("moveon-io-"));
    }
}
