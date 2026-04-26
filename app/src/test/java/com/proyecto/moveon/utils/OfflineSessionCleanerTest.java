package com.proyecto.moveon.utils;

import static org.junit.Assert.*;

import com.proyecto.moveon.testutil.MemoryContext;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.proyecto.moveon.data.local.db.AppDatabase;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Tests defensivos de {@link OfflineSessionCleaner}.
 *
 * <p>Se usa {@link MemoryContext} a propósito: WorkManager, Room y Keystore no
 * están inicializados como en una app real, así que los métodos deben absorber
 * esas excepciones y continuar con el borrado local de fotos.</p>
 */
public class OfflineSessionCleanerTest {

    /**
     * Asegura que cualquier intento de Room singleton durante la limpieza no
     * contamine otros tests de la suite.
     */
    @Before
    public void resetDatabaseBefore() throws Exception {
        resetDatabaseSingleton();
    }

    /**
     * Limpia el singleton de Room por si AppDatabase.getInstance llegó a
     * inicializarse antes de lanzar en JVM.
     */
    @After
    public void resetDatabaseAfter() throws Exception {
        resetDatabaseSingleton();
    }

    /**
     * Cubre el constructor privado de la utility class y verifica que mantiene
     * el contrato de no instanciación pública.
     */
    @Test
    public void privateConstructor_isPrivateButReflectivelyReachableForCoverage() throws Exception {
        Constructor<OfflineSessionCleaner> constructor = OfflineSessionCleaner.class.getDeclaredConstructor();
        assertTrue(Modifier.isPrivate(constructor.getModifiers()));

        constructor.setAccessible(true);
        assertNotNull(constructor.newInstance());
    }

    /**
     * Verifica que la limpieza bloqueante no propaga errores aunque falten
     * infraestructuras Android, y que aun así elimina el árbol de fotos.
     */
    @Test
    public void clearSessionAndLocalDataBlocking_ignoresInfrastructureErrorsAndDeletesPhotos() throws Exception {
        MemoryContext context = new MemoryContext();
        File root = createProfilePhoto(context, "blocking-user");
        assertTrue(root.exists());

        OfflineSessionCleaner.clearSessionAndLocalDataBlocking(context);

        assertFalse(root.exists());
    }

    /**
     * Verifica que la variante async cubre el mismo flujo sin propagar errores
     * cuando Room/WorkManager no están disponibles en JVM.
     */
    @Test
    public void clearSessionAndLocalDataAsync_ignoresInfrastructureErrorsAndDeletesPhotos() throws Exception {
        MemoryContext context = new MemoryContext();
        File root = createProfilePhoto(context, "async-user");
        assertTrue(root.exists());

        OfflineSessionCleaner.clearSessionAndLocalDataAsync(context);
        waitUntilDeleted(root);

        assertFalse(root.exists());
    }

    private static void resetDatabaseSingleton() throws Exception {
        java.lang.reflect.Field instance = AppDatabase.class.getDeclaredField("INSTANCE");
        instance.setAccessible(true);
        Object value = instance.get(null);
        if (value instanceof AutoCloseable) {
            try {
                ((AutoCloseable) value).close();
            } catch (Exception ignored) {
            }
        }
        instance.set(null, null);
    }

    private static File createProfilePhoto(MemoryContext context, String accountKey) throws Exception {
        File root = new File(context.getFilesDir(), "profile_photos");
        File accountDir = new File(root, accountKey);
        assertTrue(accountDir.mkdirs());
        Files.write(new File(accountDir, "avatar_v1.jpg").toPath(), "img".getBytes(StandardCharsets.UTF_8));
        return root;
    }

    private static void waitUntilDeleted(File file) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 2_000L;
        while (file.exists() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20L);
        }
    }
}
