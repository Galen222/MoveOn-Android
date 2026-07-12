package com.proyecto.moveon.data.remote.retrofit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.proyecto.moveon.BuildConfig;
import com.proyecto.moveon.data.session.SessionRefreshCoordinator;

import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Test de regresión del escenario que provocaba reutilización de refresh token.
 *
 * <p>Simula dos respuestas 401 casi simultáneas con el mismo access token caducado.
 * La expectativa correcta es:</p>
 * <ol>
 *     <li>solo se ejecuta un refresh real contra backend,</li>
 *     <li>el segundo hilo espera y reutiliza la nueva sesión,</li>
 *     <li>ambas peticiones salen reintentas con el access token nuevo.</li>
 * </ol>
 */
public class TokenAuthenticatorConcurrencyTest {

    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json");

    @Test
    public void authenticate_twoConcurrent401s_performSingleRefreshAndReuseNewAccessToken()
            throws Exception {
        InMemorySessionStore sessionStore = new InMemorySessionStore();
        DelayedRefreshBackend refreshBackend = new DelayedRefreshBackend();

        SessionRefreshCoordinator coordinator = SessionRefreshCoordinator.createForTests(
                sessionStore,
                refreshBackend
        );
        TokenAuthenticator authenticator = new TokenAuthenticator(coordinator);

        CountDownLatch startGate = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Request> futureA = executor.submit(() -> authenticateAfterStart(
                    authenticator,
                    startGate,
                    buildUnauthorizedResponse()
            ));
            Future<Request> futureB = executor.submit(() -> authenticateAfterStart(
                    authenticator,
                    startGate,
                    buildUnauthorizedResponse()
            ));

            startGate.countDown();

            Request retriedA = futureA.get(5, TimeUnit.SECONDS);
            Request retriedB = futureB.get(5, TimeUnit.SECONDS);

            assertNotNull(retriedA);
            assertNotNull(retriedB);
            assertEquals("Bearer access_new", retriedA.header("Authorization"));
            assertEquals("Bearer access_new", retriedB.header("Authorization"));
            assertEquals(1, refreshBackend.getCallCount());
            assertEquals("access_new", sessionStore.getStoredSession().getAccessToken());
            assertEquals("refresh_new", sessionStore.getStoredSession().getRefreshToken());
        }
    }

    @NonNull
    private Request authenticateAfterStart(@NonNull TokenAuthenticator authenticator,
                                           @NonNull CountDownLatch startGate,
                                           @NonNull Response response)
            throws InterruptedException, IOException {
        if (!startGate.await(2, TimeUnit.SECONDS)) {
            throw new AssertionError("Los hilos no recibieron la señal de inicio");
        }
        Request retried = authenticator.authenticate(null, response);
        if (retried == null) {
            throw new AssertionError("El autenticador no devolvió la petición reintentada");
        }
        return retried;
    }

    @NonNull
    private Response buildUnauthorizedResponse() {
        HttpUrl url = HttpUrl.get(BuildConfig.BASE_URL)
                .newBuilder()
                .addPathSegment("ranking")
                .addPathSegment("obtener")
                .build();

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer access_old")
                .build();

        return new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(401)
                .message("Unauthorized")
                // API no deprecada de OkHttp para construir un body de prueba.
                .body(ResponseBody.create("{}", JSON_MEDIA_TYPE))
                .build();
    }

    /**
     * Store en memoria para tests concurrentes.
     */
    private static final class InMemorySessionStore implements SessionRefreshCoordinator.SessionStore {
        private final Object lock = new Object();

        @Nullable private String username;
        @Nullable private String accessToken;
        @Nullable private String refreshToken;

        private InMemorySessionStore() {
            this.username = "alice";
            this.accessToken = "access_old";
            this.refreshToken = "refresh_old";
        }

        @Override
        public boolean isAccessTokenExpiringWithinSeconds(long leewaySeconds) {
            return false;
        }

        @NonNull
        @Override
        public SessionRefreshCoordinator.StoredSession getStoredSession() {
            synchronized (lock) {
                return new SessionRefreshCoordinator.StoredSession(
                        username,
                        accessToken,
                        refreshToken
                );
            }
        }

        @Override
        public void saveLoginSync(@Nullable String username,
                                  @Nullable String accessToken,
                                  @Nullable String refreshToken) {
            synchronized (lock) {
                this.username = username;
                this.accessToken = accessToken;
                this.refreshToken = refreshToken;
            }
        }

        @Override
        public void logout() {
            synchronized (lock) {
                username = null;
                accessToken = null;
                refreshToken = null;
            }
        }
    }

    /**
     * Backend falso que duerme lo suficiente para forzar solapamiento entre hilos.
     */
    private static final class DelayedRefreshBackend implements SessionRefreshCoordinator.RefreshBackend {
        private final AtomicInteger callCount = new AtomicInteger(0);

        @Override
        @NonNull
        public SessionRefreshCoordinator.BackendRefreshResult refresh(@NonNull String refreshToken)
                throws IOException {
            callCount.incrementAndGet();

            try {
                Thread.sleep(200L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while simulating refresh", e);
            }

            return SessionRefreshCoordinator.BackendRefreshResult.success(
                    "access_new",
                    "refresh_new",
                    "alice"
            );
        }

        private int getCallCount() {
            return callCount.get();
        }
    }
}
