package com.proyecto.moveon.data.remote.retrofit;

import static org.junit.Assert.*;

import androidx.annotation.NonNull;

import com.proyecto.moveon.data.session.dto.AppSessionResponseDto;

import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.ResponseBody;
import okio.Timeout;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Tests JVM de la caché y el handshake técnico de {@link AppSessionProvider}.
 */
public class AppSessionProviderTest {

    private static final MediaType TEXT = MediaType.get("text/plain");

    /**
     * Limpia estado estático compartido para que cada test parta de una caché vacía.
     */
    @After
    public void tearDown() throws Exception {
        setStatic("cachedSession", null);
        setStatic("lastFetchTime", 0L);
        setStatic("lastFailureTime", 0L);
        setStatic("handshakeApi", null);
    }

    /**
     * Verifica que fetchNewSession devuelve el token cuando el backend responde correctamente.
     */
    @Test
    public void fetchNewSession_successfulResponseReturnsAppSessionToken() throws Exception {
        AppSessionResponseDto dto = new AppSessionResponseDto();
        dto.appSession = "session-token";
        setStatic("handshakeApi", new FakeHandshakeApi(Response.success(dto)));

        String token = (String) method("fetchNewSession").invoke(null);

        assertEquals("session-token", token);
    }

    /**
     * Verifica que fetchNewSession rechaza respuestas exitosas sin token útil.
     */
    @Test
    public void fetchNewSession_blankTokenThrowsHandshakeException() throws Exception {
        AppSessionResponseDto dto = new AppSessionResponseDto();
        dto.appSession = "   ";
        setStatic("handshakeApi", new FakeHandshakeApi(Response.success(dto)));

        try {
            method("fetchNewSession").invoke(null);
            fail("Debe fallar cuando el token de app-session está vacío");
        } catch (InvocationTargetException expected) {
            assertTrue(expected.getCause() instanceof Exception);
            assertTrue(expected.getCause().getMessage().contains("HTTP 200"));
        }
    }

    /**
     * Verifica que fetchNewSession conserva el código HTTP cuando el handshake falla.
     */
    @Test
    public void fetchNewSession_httpErrorIncludesStatusCode() throws Exception {
        setStatic("handshakeApi", new FakeHandshakeApi(
                Response.error(503, ResponseBody.create("backend down", TEXT))
        ));

        try {
            method("fetchNewSession").invoke(null);
            fail("Debe fallar cuando el backend responde error");
        } catch (InvocationTargetException expected) {
            assertTrue(expected.getCause() instanceof Exception);
            assertTrue(expected.getCause().getMessage().contains("HTTP 503"));
        }
    }

    /**
     * Verifica que fetchNewSession propaga fallos de red del Call de Retrofit.
     */
    @Test
    public void fetchNewSession_networkFailureIsPropagated() throws Exception {
        IOException io = new IOException("offline");
        setStatic("handshakeApi", new FakeHandshakeApi(io));

        try {
            method("fetchNewSession").invoke(null);
            fail("Debe propagar el fallo de red");
        } catch (InvocationTargetException expected) {
            assertSame(io, expected.getCause());
        }
    }

    /**
     * Verifica que getOrFetch devuelve la sesión cacheada mientras el TTL no haya expirado.
     */
    @Test
    public void getOrFetch_returnsCachedSessionWithoutCallingHandshake() throws Exception {
        setStatic("cachedSession", "cached-session");
        setStatic("lastFetchTime", 0L);
        setStatic("handshakeApi", new FakeHandshakeApi(new AssertionError("No debería llamarse al handshake")));

        assertEquals("cached-session", AppSessionProvider.getOrFetch());
    }

    /**
     * Verifica que invalidate limpia token y timestamp pero no modifica el cooldown de fallo.
     */
    @Test
    public void invalidate_clearsCachedSessionAndKeepsFailureCooldown() throws Exception {
        setStatic("cachedSession", "cached-session");
        setStatic("lastFetchTime", 123L);
        setStatic("lastFailureTime", 456L);

        AppSessionProvider.invalidate();

        assertNull(getStatic("cachedSession"));
        assertEquals(0L, getStatic("lastFetchTime"));
        assertEquals(456L, getStatic("lastFailureTime"));
    }

    /**
     * Verifica que resetFailureCooldown borra únicamente el instante del último fallo.
     */
    @Test
    public void resetFailureCooldown_clearsLastFailureTimeOnly() throws Exception {
        setStatic("cachedSession", "cached-session");
        setStatic("lastFetchTime", 123L);
        setStatic("lastFailureTime", 456L);

        AppSessionProvider.resetFailureCooldown();

        assertEquals("cached-session", getStatic("cachedSession"));
        assertEquals(123L, getStatic("lastFetchTime"));
        assertEquals(0L, getStatic("lastFailureTime"));
    }

    private static Method method(String name) throws Exception {
        Method method = AppSessionProvider.class.getDeclaredMethod(name);
        method.setAccessible(true);
        return method;
    }

    private static void setStatic(String name, Object value) throws Exception {
        Field field = AppSessionProvider.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }

    private static Object getStatic(String name) throws Exception {
        Field field = AppSessionProvider.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }

    private static final class FakeHandshakeApi implements HandshakeApi {
        private final Response<AppSessionResponseDto> response;
        private final Throwable failure;

        FakeHandshakeApi(Response<AppSessionResponseDto> response) {
            this.response = response;
            this.failure = null;
        }

        FakeHandshakeApi(Throwable failure) {
            this.response = null;
            this.failure = failure;
        }

        @Override
        public Call<AppSessionResponseDto> getHandshake(String appId) {
            return new FakeCall(response, failure);
        }
    }

    private static final class FakeCall implements Call<AppSessionResponseDto> {
        private final Response<AppSessionResponseDto> response;
        private final Throwable failure;
        private boolean canceled;

        FakeCall(Response<AppSessionResponseDto> response, Throwable failure) {
            this.response = response;
            this.failure = failure;
        }

        @Override
        public Response<AppSessionResponseDto> execute() throws IOException {
            if (failure instanceof IOException) {
                throw (IOException) failure;
            }
            if (failure instanceof RuntimeException) {
                throw (RuntimeException) failure;
            }
            if (failure instanceof Error) {
                throw (Error) failure;
            }
            return response;
        }

        @Override
        public void enqueue(@NonNull Callback<AppSessionResponseDto> callback) {
            if (failure != null) {
                callback.onFailure(this, failure);
            } else {
                callback.onResponse(this, response);
            }
        }

        @Override
        public boolean isExecuted() {
            return false;
        }

        @Override
        public void cancel() {
            canceled = true;
        }

        @Override
        public boolean isCanceled() {
            return canceled;
        }

        @Override
        public Call<AppSessionResponseDto> clone() {
            return new FakeCall(response, failure);
        }

        @Override
        public Request request() {
            return new Request.Builder().url("http://localhost/handshake").build();
        }

        @Override
        public Timeout timeout() {
            return Timeout.NONE;
        }
    }
}
