package com.proyecto.moveon.core.concurrency;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
/**
 * Pruebas para validar el comportamiento de move on executors.
 */
public class MoveOnExecutorsTest {

    @Test
    public void io_returnsNonNull() {
        assertNotNull(MoveOnExecutors.io());
    }

    @Test
    public void io_returnsSameInstance() {
        Executor first = MoveOnExecutors.io();
        Executor second = MoveOnExecutors.io();

        assertSame(first, second);
    }

    @Test
    public void io_canExecuteTasks() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger result = new AtomicInteger(0);

        MoveOnExecutors.executeIo(() -> {
            result.set(42);
            latch.countDown();
        });

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(42, result.get());
    }

    @Test
    public void io_supportsParallelExecution() throws InterruptedException {
        // El pool tiene 3 hilos; 3 tareas deben poder ejecutarse en paralelo
        int parallelTasks = 3;
        CountDownLatch allStarted = new CountDownLatch(parallelTasks);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch allDone = submitParallelTasks(
                parallelTasks,
                allStarted,
                release
        );

        // Las 3 tareas deben arrancar sin esperarse entre sí
        assertTrue("Las 3 tareas deberían arrancar en paralelo",
                allStarted.await(2, TimeUnit.SECONDS));

        release.countDown();
        assertTrue(allDone.await(2, TimeUnit.SECONDS));
    }


    private static CountDownLatch submitParallelTasks(
            int taskCount,
            CountDownLatch allStarted,
            CountDownLatch release
    ) {
        CountDownLatch allDone = new CountDownLatch(taskCount);
        for (int i = 0; i < taskCount; i++) {
            MoveOnExecutors.executeIo(() -> {
                allStarted.countDown();
                try {
                    if (release.await(2, TimeUnit.SECONDS)) {
                        allDone.countDown();
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        return allDone;
    }

    @Test
    public void io_threadNaming() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        StringBuilder threadName = new StringBuilder();

        MoveOnExecutors.executeIo(() -> {
            threadName.append(Thread.currentThread().getName());
            latch.countDown();
        });

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertTrue("Thread name should start with 'moveon-io-'",
                threadName.toString().startsWith("moveon-io-"));
    }
}
