package com.proyecto.moveon.data.profile.remote;

import static org.junit.Assert.*;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Tests de utilidades privadas de {@link PerfilRemoteDataSource} sin crear llamadas remotas.
 */
public class PerfilRemoteDataSourcePrivateTest {

    /**
     * Verifica que la deducción de MIME reconoce png, webp y cae a jpeg para el resto de nombres.
     */
    @Test
    public void guessMimeType_detectsSupportedImageExtensions() throws Exception {
        PerfilRemoteDataSource dataSource = allocateDataSource();

        assertEquals("image/png", invokeGuessMimeType(dataSource, "avatar.PNG"));
        assertEquals("image/webp", invokeGuessMimeType(dataSource, "avatar.webp"));
        assertEquals("image/jpeg", invokeGuessMimeType(dataSource, "avatar.jpg"));
        assertEquals("image/jpeg", invokeGuessMimeType(dataSource, "avatar.gif"));
        assertEquals("image/jpeg", invokeGuessMimeType(dataSource, "sin_extension"));
    }

    private static PerfilRemoteDataSource allocateDataSource() throws Exception {
        Field field = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
        field.setAccessible(true);
        Object unsafe = java.util.Objects.requireNonNull(field.get(null), "Unsafe no disponible");
        Method method = unsafe.getClass().getMethod("allocateInstance", Class.class);
        return (PerfilRemoteDataSource) method.invoke(unsafe, PerfilRemoteDataSource.class);
    }

    private static String invokeGuessMimeType(PerfilRemoteDataSource target, String fileName) throws Exception {
        Method method = PerfilRemoteDataSource.class.getDeclaredMethod("guessMimeType", String.class);
        method.setAccessible(true);
        try {
            return (String) method.invoke(target, fileName);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw e;
        }
    }
}
