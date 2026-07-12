package com.proyecto.moveon.data.profile.remote;

import static org.junit.Assert.*;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Tests de lógica pura privada de {@link PerfilRemoteDataSource}.
 */
public class PerfilRemoteDataSourcePureTest {

    /**
     * Verifica que guessMimeType reconoce PNG y WEBP sin depender de mayúsculas.
     */
    @Test
    public void guessMimeType_recognizesPngAndWebpExtensionsCaseInsensitive() throws Exception {
        PerfilRemoteDataSource dataSource = allocateDataSource();

        assertEquals("image/png", invokeGuessMimeType(dataSource, "avatar.PNG"));
        assertEquals("image/webp", invokeGuessMimeType(dataSource, "avatar.WeBp"));
    }

    /**
     * Verifica que guessMimeType usa JPEG como fallback para JPG, JPEG y nombres sin extensión.
     */
    @Test
    public void guessMimeType_usesJpegFallbackForOtherFileNames() throws Exception {
        PerfilRemoteDataSource dataSource = allocateDataSource();

        assertEquals("image/jpeg", invokeGuessMimeType(dataSource, "avatar.jpg"));
        assertEquals("image/jpeg", invokeGuessMimeType(dataSource, "avatar.jpeg"));
        assertEquals("image/jpeg", invokeGuessMimeType(dataSource, "avatar"));
    }

    private static String invokeGuessMimeType(PerfilRemoteDataSource dataSource, String fileName) throws Exception {
        Method method = PerfilRemoteDataSource.class.getDeclaredMethod("guessMimeType", String.class);
        method.setAccessible(true);
        return (String) method.invoke(dataSource, fileName);
    }

    private static PerfilRemoteDataSource allocateDataSource() throws Exception {
        Field field = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
        field.setAccessible(true);
        Object unsafe = java.util.Objects.requireNonNull(field.get(null), "Unsafe no disponible");
        Method method = unsafe.getClass().getMethod("allocateInstance", Class.class);
        return (PerfilRemoteDataSource) method.invoke(unsafe, PerfilRemoteDataSource.class);
    }
}
