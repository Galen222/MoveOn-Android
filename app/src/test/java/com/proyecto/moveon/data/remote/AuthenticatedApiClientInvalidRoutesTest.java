package com.proyecto.moveon.data.remote;

import static org.junit.Assert.*;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Tests de validación privada de rutas inválidas en el cliente autenticado sin tocar Retrofit ni recursos Android.
 */
public class AuthenticatedApiClientInvalidRoutesTest {

    /**
     * Verifica que isInvalidUrl rechaza entradas nulas, vacías, absolutas y protocol-relative.
     */
    @Test
    public void isInvalidUrl_rejectsNullBlankAbsoluteAndProtocolRelativeRoutes() throws Exception {
        AuthenticatedApiClient client = allocateClient();

        assertTrue(invokeIsInvalidUrl(client, null));
        assertTrue(invokeIsInvalidUrl(client, "   "));
        assertTrue(invokeIsInvalidUrl(client, "https://evil.example/perfil"));
        assertTrue(invokeIsInvalidUrl(client, "HTTP://evil.example/perfil"));
        assertTrue(invokeIsInvalidUrl(client, "ftp://evil.example/file"));
        assertTrue(invokeIsInvalidUrl(client, "//evil.example/perfil"));
    }

    /**
     * Verifica que isInvalidUrl acepta rutas relativas, aunque contengan query strings o barras iniciales simples.
     */
    @Test
    public void isInvalidUrl_acceptsSafeRelativeRoutes() throws Exception {
        AuthenticatedApiClient client = allocateClient();

        assertFalse(invokeIsInvalidUrl(client, "perfil/informacion"));
        assertFalse(invokeIsInvalidUrl(client, "/perfil/informacion"));
        assertFalse(invokeIsInvalidUrl(client, "ranking/obtener?provincia=Alicante"));
        assertFalse(invokeIsInvalidUrl(client, "perfil/http://texto-interno"));
    }

    /**
     * Verifica que sanitizeUrl recorta espacios y elimina una única barra inicial de rutas relativas.
     */
    @Test
    public void sanitizeUrl_trimsAndRemovesSingleLeadingSlash() throws Exception {
        AuthenticatedApiClient client = allocateClient();

        assertEquals("perfil/informacion", invokeSanitizeUrl(client, "  /perfil/informacion  "));
        assertEquals("ranking/obtener", invokeSanitizeUrl(client, "ranking/obtener"));
        assertEquals("", invokeSanitizeUrl(client, null));
    }

    /**
     * Verifica que sanitizeUrl sólo normaliza una barra inicial y no oculta rutas protocol-relative inválidas.
     */
    @Test
    public void sanitizeUrl_preservesSecondSlashForProtocolRelativeInputs() throws Exception {
        AuthenticatedApiClient client = allocateClient();

        assertEquals("/evil.example/perfil", invokeSanitizeUrl(client, "//evil.example/perfil"));
    }

    private static AuthenticatedApiClient allocateClient() throws Exception {
        Field field = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
        field.setAccessible(true);
        Object unsafe = java.util.Objects.requireNonNull(field.get(null), "Unsafe no disponible");
        Method method = unsafe.getClass().getMethod("allocateInstance", Class.class);
        return (AuthenticatedApiClient) method.invoke(unsafe, AuthenticatedApiClient.class);
    }

    private static boolean invokeIsInvalidUrl(AuthenticatedApiClient client, String url) throws Exception {
        Method method = AuthenticatedApiClient.class.getDeclaredMethod("isInvalidUrl", String.class);
        method.setAccessible(true);
        return Boolean.TRUE.equals(method.invoke(client, url));
    }

    private static String invokeSanitizeUrl(AuthenticatedApiClient client, String url) throws Exception {
        Method method = AuthenticatedApiClient.class.getDeclaredMethod("sanitizeUrl", String.class);
        method.setAccessible(true);
        return (String) method.invoke(client, url);
    }
}
