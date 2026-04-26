package com.proyecto.moveon.data.remote.retrofit;

import static org.junit.Assert.*;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.proyecto.moveon.BuildConfig;
import com.proyecto.moveon.data.session.dto.AppSessionResponseDto;

import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Connection;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Timeout;

/**
 * Tests JVM de ramas seguras de {@link AppSessionInterceptor} sin ejecutar red real.
 */
public class AppSessionInterceptorTest {

    private static final MediaType JSON = MediaType.get("application/json");

    /**
     * Limpia la caché estática de app-session entre tests.
     */
    @After
    public void tearDown() throws Exception {
        setStatic("cachedSession", null);
        setStatic("lastFetchTime", 0L);
        setStatic("lastFailureTime", 0L);
        setStatic("handshakeApi", null);
    }

    /**
     * Verifica que no se añade x-app-session a hosts ajenos al backend configurado.
     */
    @Test
    public void intercept_differentHostProceedsWithoutAppSessionHeader() throws Exception {
        AppSessionInterceptor interceptor = new AppSessionInterceptor();
        RecordingChain chain = new RecordingChain(new Request.Builder()
                .url("https://example.com/protected")
                .build());

        Response response = interceptor.intercept(chain);

        assertEquals(200, response.code());
        assertEquals(1, chain.proceedCalls);
        assertNull(chain.lastRequest.header("x-app-session"));
    }

    /**
     * Verifica que el endpoint handshake no recibe x-app-session para evitar bucles de renovación.
     */
    @Test
    public void intercept_handshakeEndpointProceedsWithoutAppSessionHeader() throws Exception {
        AppSessionInterceptor interceptor = new AppSessionInterceptor();
        RecordingChain chain = new RecordingChain(new Request.Builder()
                .url(BuildConfig.BASE_URL + "handshake/")
                .build());

        interceptor.intercept(chain);

        assertEquals(1, chain.proceedCalls);
        assertNull(chain.lastRequest.header("x-app-session"));
    }

    /**
     * Verifica que una ruta protegida del backend recibe la app-session cacheada.
     */
    @Test
    public void intercept_backendProtectedRouteAddsCachedAppSessionHeader() throws Exception {
        setStatic("cachedSession", "cached-app-session");
        setStatic("lastFetchTime", 0L);
        AppSessionInterceptor interceptor = new AppSessionInterceptor();
        RecordingChain chain = new RecordingChain(new Request.Builder()
                .url(BuildConfig.BASE_URL + "perfil/informacion")
                .build());

        interceptor.intercept(chain);

        assertEquals(1, chain.proceedCalls);
        assertEquals("cached-app-session", chain.lastRequest.header("x-app-session"));
    }

    /**
     * Verifica que un 403 con app-session expirada fuerza invalidación y reintento con nuevo token.
     */
    @Test
    public void intercept_expiredAppSessionInvalidatesAndRetriesOnce() throws Exception {
        installSuccessfulHandshake("fresh-app-session");
        setStatic("cachedSession", "expired-app-session");
        setStatic("lastFetchTime", 0L);
        AppSessionInterceptor interceptor = new AppSessionInterceptor();
        RecordingChain chain = new RecordingChain(new Request.Builder()
                .url(BuildConfig.BASE_URL + "perfil/informacion")
                .build());
        chain.firstResponseCode = 403;
        chain.firstExpiredHeader = true;

        interceptor.intercept(chain);

        assertEquals(2, chain.proceedCalls);
        assertEquals("expired-app-session", chain.requests[0].header("x-app-session"));
        assertEquals("fresh-app-session", chain.requests[1].header("x-app-session"));
    }

    private static void installSuccessfulHandshake(String token) throws Exception {
        AppSessionResponseDto dto = new AppSessionResponseDto();
        dto.appSession = token;
        setStatic("handshakeApi", new FakeHandshakeApi(retrofit2.Response.success(dto)));
    }

    private static void setStatic(String name, Object value) throws Exception {
        Field field = AppSessionProvider.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }

    private static final class FakeHandshakeApi implements HandshakeApi {
        private final retrofit2.Response<AppSessionResponseDto> response;

        FakeHandshakeApi(retrofit2.Response<AppSessionResponseDto> response) {
            this.response = response;
        }

        @Override
        public retrofit2.Call<AppSessionResponseDto> getHandshake(String appId) {
            return new FakeCall(response);
        }
    }

    private static final class FakeCall implements retrofit2.Call<AppSessionResponseDto> {
        private final retrofit2.Response<AppSessionResponseDto> response;
        private boolean canceled;

        FakeCall(retrofit2.Response<AppSessionResponseDto> response) {
            this.response = response;
        }

        @Override
        public retrofit2.Response<AppSessionResponseDto> execute() throws IOException {
            return response;
        }

        @Override
        public void enqueue(@NonNull retrofit2.Callback<AppSessionResponseDto> callback) {
            callback.onResponse(this, response);
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
        public retrofit2.Call<AppSessionResponseDto> clone() {
            return new FakeCall(response);
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

    private static final class RecordingChain implements Interceptor.Chain {
        private final Request original;
        private final Request[] requests = new Request[4];
        private Request lastRequest;
        private int proceedCalls;
        private int firstResponseCode = 200;
        private boolean firstExpiredHeader;

        RecordingChain(Request original) {
            this.original = original;
        }

        @NonNull
        @Override
        public Request request() {
            return original;
        }

        @NonNull
        @Override
        public Response proceed(@NonNull Request request) {
            lastRequest = request;
            requests[proceedCalls] = request;
            proceedCalls++;
            Response.Builder builder = new Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(proceedCalls == 1 ? firstResponseCode : 200)
                    .message("OK")
                    .body(ResponseBody.create("{}", JSON));
            if (proceedCalls == 1 && firstExpiredHeader) {
                builder.header("x-app-session-expired", "1");
            }
            return builder.build();
        }

        @Nullable
        @Override
        public Connection connection() {
            return null;
        }

        @NonNull
        @Override
        public Call call() {
            throw new UnsupportedOperationException("No se usa en estos tests");
        }

        @Override
        public int connectTimeoutMillis() {
            return 0;
        }

        @NonNull
        @Override
        public Interceptor.Chain withConnectTimeout(int timeout, @NonNull TimeUnit unit) {
            return this;
        }

        @Override
        public int readTimeoutMillis() {
            return 0;
        }

        @NonNull
        @Override
        public Interceptor.Chain withReadTimeout(int timeout, @NonNull TimeUnit unit) {
            return this;
        }

        @Override
        public int writeTimeoutMillis() {
            return 0;
        }

        @NonNull
        @Override
        public Interceptor.Chain withWriteTimeout(int timeout, @NonNull TimeUnit unit) {
            return this;
        }
    }
}
