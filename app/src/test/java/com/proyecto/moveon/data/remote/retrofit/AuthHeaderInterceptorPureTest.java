package com.proyecto.moveon.data.remote.retrofit;

import static org.junit.Assert.*;

import com.proyecto.moveon.BuildConfig;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import okhttp3.Request;

/**
 * Tests de clasificación pura de endpoints públicos en {@link AuthHeaderInterceptor}.
 */
public class AuthHeaderInterceptorPureTest {

    /**
     * Verifica que isPublicEndpoint reconoce endpoints de autenticación y handshake sin access token.
     */
    @Test
    public void isPublicEndpoint_recognizesAuthenticationAndHandshakeRoutes() throws Exception {
        AuthHeaderInterceptor interceptor = allocate(AuthHeaderInterceptor.class);

        assertTrue(invokeIsPublicEndpoint(interceptor, request("handshake")));
        assertTrue(invokeIsPublicEndpoint(interceptor, request("auth/login")));
        assertTrue(invokeIsPublicEndpoint(interceptor, request("auth/registro")));
        assertTrue(invokeIsPublicEndpoint(interceptor, request("auth/logout")));
        assertTrue(invokeIsPublicEndpoint(interceptor, request("token/refresh")));
        assertTrue(invokeIsPublicEndpoint(interceptor, request("password/solicitar")));
        assertTrue(invokeIsPublicEndpoint(interceptor, request("password/confirmar")));
    }

    /**
     * Verifica que isPublicEndpoint tolera una barra final en rutas públicas.
     */
    @Test
    public void isPublicEndpoint_ignoresTrailingSlashForPublicRoutes() throws Exception {
        AuthHeaderInterceptor interceptor = allocate(AuthHeaderInterceptor.class);

        assertTrue(invokeIsPublicEndpoint(interceptor, request("auth/login/")));
        assertTrue(invokeIsPublicEndpoint(interceptor, request("token/refresh/")));
    }

    /**
     * Verifica que isPublicEndpoint no marca rutas protegidas o nombres parecidos como públicas.
     */
    @Test
    public void isPublicEndpoint_rejectsProtectedAndLookAlikeRoutes() throws Exception {
        AuthHeaderInterceptor interceptor = allocate(AuthHeaderInterceptor.class);

        assertFalse(invokeIsPublicEndpoint(interceptor, request("perfil/informacion")));
        assertFalse(invokeIsPublicEndpoint(interceptor, request("ranking/obtener")));
        assertFalse(invokeIsPublicEndpoint(interceptor, request("token/refresh-extra")));
        assertFalse(invokeIsPublicEndpoint(interceptor, request("password/cambiar")));
    }

    private static Request request(String path) {
        return new Request.Builder()
                .url(BuildConfig.BASE_URL + path)
                .build();
    }

    private static boolean invokeIsPublicEndpoint(AuthHeaderInterceptor interceptor, Request request) throws Exception {
        Method method = AuthHeaderInterceptor.class.getDeclaredMethod("isPublicEndpoint", Request.class);
        method.setAccessible(true);
        return (Boolean) method.invoke(interceptor, request);
    }

    @SuppressWarnings("unchecked")
    private static <T> T allocate(Class<T> type) throws Exception {
        Field field = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
        field.setAccessible(true);
        Object unsafe = field.get(null);
        Method method = unsafe.getClass().getMethod("allocateInstance", Class.class);
        return (T) method.invoke(unsafe, type);
    }
}
