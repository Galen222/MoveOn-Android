package com.proyecto.moveon.data.remote;

import static org.junit.Assert.*;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Tests de validación de rutas privadas de {@link AuthenticatedApiClient} sin crear un cliente Retrofit real.
 */
public class AuthenticatedApiClientPrivateUrlTest {

    /**
     * Verifica que se rechazan URLs vacías, nulas, absolutas o protocol-relative.
     */
    @Test
    public void isInvalidUrl_rejectsUnsafeOrEmptyRoutes() throws Exception {
        AuthenticatedApiClient client = allocateClient();

        assertEquals(Boolean.TRUE, invoke(client, "isInvalidUrl", new Class<?>[]{String.class}, new Object[]{null}));
        assertEquals(Boolean.TRUE, invoke(client, "isInvalidUrl", new Class<?>[]{String.class}, "   "));
        assertEquals(Boolean.TRUE, invoke(client, "isInvalidUrl", new Class<?>[]{String.class}, "https://api.example.com/users"));
        assertEquals(Boolean.TRUE, invoke(client, "isInvalidUrl", new Class<?>[]{String.class}, "ftp://example.com/file"));
        assertEquals(Boolean.TRUE, invoke(client, "isInvalidUrl", new Class<?>[]{String.class}, "//example.com/file"));
    }

    /**
     * Verifica que las rutas relativas válidas no se consideran peligrosas.
     */
    @Test
    public void isInvalidUrl_acceptsRelativeRoutes() throws Exception {
        AuthenticatedApiClient client = allocateClient();

        assertEquals(Boolean.FALSE, invoke(client, "isInvalidUrl", new Class<?>[]{String.class}, "perfil/informacion"));
        assertEquals(Boolean.FALSE, invoke(client, "isInvalidUrl", new Class<?>[]{String.class}, "/perfil/informacion"));
        assertEquals(Boolean.FALSE, invoke(client, "isInvalidUrl", new Class<?>[]{String.class}, "ranking/obtener?provincia=Alicante"));
    }

    /**
     * Verifica que la sanitización recorta espacios, elimina solo una barra inicial y tolera null.
     */
    @Test
    public void sanitizeUrl_trimsAndRemovesSingleLeadingSlash() throws Exception {
        AuthenticatedApiClient client = allocateClient();

        assertEquals("perfil/informacion", invoke(client, "sanitizeUrl", new Class<?>[]{String.class}, "  /perfil/informacion  "));
        assertEquals("ranking/obtener", invoke(client, "sanitizeUrl", new Class<?>[]{String.class}, "ranking/obtener"));
        assertEquals("/doble/barra", invoke(client, "sanitizeUrl", new Class<?>[]{String.class}, "//doble/barra"));
        assertEquals("", invoke(client, "sanitizeUrl", new Class<?>[]{String.class}, new Object[]{null}));
    }

    private static AuthenticatedApiClient allocateClient() throws Exception {
        Field field = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
        field.setAccessible(true);
        Object unsafe = java.util.Objects.requireNonNull(field.get(null), "Unsafe no disponible");
        Method method = unsafe.getClass().getMethod("allocateInstance", Class.class);
        return (AuthenticatedApiClient) method.invoke(unsafe, AuthenticatedApiClient.class);
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
