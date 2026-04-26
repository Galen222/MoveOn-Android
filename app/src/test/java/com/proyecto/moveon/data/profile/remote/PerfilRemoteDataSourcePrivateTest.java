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
        PerfilRemoteDataSource dataSource = allocate(PerfilRemoteDataSource.class);

        assertEquals("image/png", invoke(dataSource, "guessMimeType", new Class<?>[]{String.class}, "avatar.PNG"));
        assertEquals("image/webp", invoke(dataSource, "guessMimeType", new Class<?>[]{String.class}, "avatar.webp"));
        assertEquals("image/jpeg", invoke(dataSource, "guessMimeType", new Class<?>[]{String.class}, "avatar.jpg"));
        assertEquals("image/jpeg", invoke(dataSource, "guessMimeType", new Class<?>[]{String.class}, "avatar.gif"));
        assertEquals("image/jpeg", invoke(dataSource, "guessMimeType", new Class<?>[]{String.class}, "sin_extension"));
    }

    @SuppressWarnings("unchecked")
    private static <T> T allocate(Class<T> type) throws Exception {
        Field field = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
        field.setAccessible(true);
        Object unsafe = field.get(null);
        Method method = unsafe.getClass().getMethod("allocateInstance", Class.class);
        return (T) method.invoke(unsafe, type);
    }

    private static Object invoke(Object target, String methodName, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw e;
        }
    }
}
