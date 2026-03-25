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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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

    @Test
    public void authenticate_twoConcurrent401s_performSingleRefreshAndReuseNewAccessToken()
            throws Exception {
        InMemorySessionStore sessionStore = new InMemorySessionStore(
                "alice",
                "access_old",
                "refresh_old",
                "5"
        );
        DelayedRefreshBackend refreshBackend = new DelayedRefreshBackend(
                "access_new",
                "refresh_new",
                "alice",
                200L
        );

        SessionRefreshCoordinator coordinator = SessionRefreshCoordinator.createForTests(
                sessionStore,
                refreshBackend
        );
        TokenAuthenticator authenticator = new TokenAuthenticator(coordinator);

        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Request> futureA = executor.submit(() -> authenticateAfterStart(
                    authenticator,
                    startGate,
                    buildUnauthorizedResponse("access_old")
            ));
            Future<Request> futureB = executor.submit(() -> authenticateAfterStart(
                    authenticator,
                    startGate,
                    buildUnauthorizedResponse("access_old")
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
        } finally {
            executor.shutdownNow();
        }
    }

    @NonNull
    private Request authenticateAfterStart(@NonNull TokenAuthenticator authenticator,
                                           @NonNull CountDownLatch startGate,
                                           @NonNull Response response)
            throws InterruptedException, IOException {
        startGate.await(2, TimeUnit.SECONDS);
        return authenticator.authenticate(null, response);
    }

    @NonNull
    private Response buildUnauthorizedResponse(@NonNull String accessToken) {
        HttpUrl url = HttpUrl.get(BuildConfig.BASE_URL)
                .newBuilder()
                .addPathSegment("ranking")
                .addPathSegment("obtener")
                .build();

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + accessToken)
                .build();

        return new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(401)
                .message("Unauthorized")
                .body(ResponseBody.create(MediaType.parse("application/json"), "{}"))
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
        @Nullable private String userId;

        private InMemorySessionStore(@Nullable String username,
                                     @Nullable String accessToken,
                                     @Nullable String refreshToken,
                                     @Nullable String userId) {
            this.username = username;
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.userId = userId;
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
                        refreshToken,
                        userId
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
                userId = null;
            }
        }
    }

    /**
     * Backend falso que duerme lo suficiente para forzar solapamiento entre hilos.
     */
    private static final class DelayedRefreshBackend implements SessionRefreshCoordinator.RefreshBackend {
        private final String nextAccessToken;
        private final String nextRefreshToken;
        private final String username;
        private final long delayMillis;
        private final AtomicInteger callCount = new AtomicInteger(0);

        private DelayedRefreshBackend(@NonNull String nextAccessToken,
                                      @NonNull String nextRefreshToken,
                                      @Nullable String username,
                                      long delayMillis) {
            this.nextAccessToken = nextAccessToken;
            this.nextRefreshToken = nextRefreshToken;
            this.username = username;
            this.delayMillis = delayMillis;
        }

        @NonNull
        @Override
        public SessionRefreshCoordinator.BackendRefreshResult refresh(@NonNull String refreshToken)
                throws IOException {
            callCount.incrementAndGet();
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while simulating refresh backend", e);
            }
            return SessionRefreshCoordinator.BackendRefreshResult.success(
                    nextAccessToken,
                    nextRefreshToken,
                    username
            );
        }

        private int getCallCount() {
            return callCount.get();
        }
    }
}
