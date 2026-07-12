package com.proyecto.moveon.data.remote.retrofit;

import static org.junit.Assert.*;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.proyecto.moveon.BuildConfig;
import com.proyecto.moveon.data.session.SessionRefreshCoordinator;

import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Tests de {@link TokenAuthenticator} centrados en ramas de salida y errores transitorios.
 */
public class TokenAuthenticatorTest {

    private static final MediaType JSON = MediaType.get("application/json");

    /**
     * Verifica que no se intenta refrescar si el 401 pertenece a otro host.
     */
    @Test
    public void authenticate_forDifferentHost_returnsNullWithoutRefresh() throws Exception {
        CountingBackend backend = new CountingBackend(
                SessionRefreshCoordinator.BackendRefreshResult.success("new-a", "new-r", "alice")
        );
        TokenAuthenticator authenticator = new TokenAuthenticator(coordinator(backend));

        Request result = authenticator.authenticate(null, unauthorizedResponse(
                HttpUrl.get("https://example.org/protected"),
                null
        ));

        assertNull(result);
        assertEquals(0, backend.calls.get());
    }

    /**
     * Verifica que el autenticador corta bucles cuando ya hay demasiadas respuestas encadenadas.
     */
    @Test
    public void authenticate_afterSecondResponse_returnsNullWithoutRefresh() throws Exception {
        CountingBackend backend = new CountingBackend(
                SessionRefreshCoordinator.BackendRefreshResult.success("new-a", "new-r", "alice")
        );
        TokenAuthenticator authenticator = new TokenAuthenticator(coordinator(backend));
        HttpUrl url = backendUrl("perfil");
        Response first = unauthorizedResponse(url, null);
        Response second = unauthorizedResponse(url, first);

        Request result = authenticator.authenticate(null, second);

        assertNull(result);
        assertEquals(0, backend.calls.get());
    }

    /**
     * Verifica que un refresh correcto reconstruye la petición con el nuevo access token.
     */
    @Test
    public void authenticate_whenRefreshSucceeds_returnsRequestWithNewAuthorization() throws Exception {
        CountingBackend backend = new CountingBackend(
                SessionRefreshCoordinator.BackendRefreshResult.success("new-a", "new-r", "alice")
        );
        TokenAuthenticator authenticator = new TokenAuthenticator(coordinator(backend));

        Request result = authenticator.authenticate(null, unauthorizedResponse(backendUrl("ranking"), null));

        assertNotNull(result);
        assertEquals("Bearer new-a", result.header("Authorization"));
        assertEquals(1, backend.calls.get());
    }

    /**
     * Verifica que un error transitorio del refresh se convierte en excepción checked con metadatos.
     */
    @Test
    public void authenticate_whenRefreshIsTransient_throwsRefreshFailedException() throws Exception {
        CountingBackend backend = new CountingBackend(
                SessionRefreshCoordinator.BackendRefreshResult.failure(503, "20", "maintenance", "caído")
        );
        TokenAuthenticator authenticator = new TokenAuthenticator(coordinator(backend));

        try {
            authenticator.authenticate(null, unauthorizedResponse(backendUrl("ranking"), null));
            fail("Expected RefreshFailedException");
        } catch (TokenAuthenticator.RefreshFailedException e) {
            assertEquals(503, e.getCode());
            assertEquals("20", e.getRetryAfter());
            assertEquals("maintenance", e.getErrorCode());
            assertEquals("caído", e.getBackendMessage());
            String message = e.getMessage();
            assertNotNull(message);
            assertTrue(message.contains("503"));
            assertTrue(message.contains("maintenance"));
        }
    }

    /**
     * Verifica que una excepción de transporte del backend queda como fallo transitorio de refresh.
     */
    @Test
    public void authenticate_whenBackendThrowsIOException_wrapsAsRefreshFailedException() throws Exception {
        SessionRefreshCoordinator coordinator = SessionRefreshCoordinator.createForTests(
                new FakeSessionStore(),
                refreshToken -> {
                    java.util.Objects.requireNonNull(refreshToken, "refreshToken");
                    throw new IOException("timeout");
                }
        );
        TokenAuthenticator authenticator = new TokenAuthenticator(coordinator);

        try {
            authenticator.authenticate(null, unauthorizedResponse(backendUrl("ranking"), null));
            fail("Expected RefreshFailedException");
        } catch (TokenAuthenticator.RefreshFailedException e) {
            assertEquals(0, e.getCode());
            assertEquals("timeout", e.getBackendMessage());
        }
    }

    /**
     * Verifica que la excepción de refresh no añade sufijo de error cuando el código funcional está vacío.
     */
    @Test
    public void refreshFailedException_withoutErrorCode_usesCompactMessage() {
        TokenAuthenticator.RefreshFailedException ex =
                new TokenAuthenticator.RefreshFailedException(429, "30", "   ", "rate limited");

        assertEquals(429, ex.getCode());
        assertEquals("30", ex.getRetryAfter());
        assertEquals("   ", ex.getErrorCode());
        assertEquals("rate limited", ex.getBackendMessage());
        assertEquals("Refresh error: 429", ex.getMessage());
    }

    /**
     * Construye un coordinador con sesión y backend falsos para el autenticador.
     */
    private static SessionRefreshCoordinator coordinator(@NonNull CountingBackend backend) {
        return SessionRefreshCoordinator.createForTests(
                new FakeSessionStore(),
                backend
        );
    }

    /**
     * Construye una URL dentro de la base configurada por BuildConfig.
     */
    private static HttpUrl backendUrl(@NonNull String segment) {
        return HttpUrl.get(BuildConfig.BASE_URL)
                .newBuilder()
                .addPathSegment(segment)
                .build();
    }

    /**
     * Crea una respuesta 401 con cadena opcional de respuestas previas.
     */
    private static Response unauthorizedResponse(@NonNull HttpUrl url,
                                                 @Nullable Response priorResponse) {
        Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer old-a");
        Response.Builder responseBuilder = new Response.Builder()
                .request(requestBuilder.build())
                .protocol(Protocol.HTTP_1_1)
                .code(401)
                .message("Unauthorized")
                .body(ResponseBody.create("{}", JSON));
        if (priorResponse != null) {
            responseBuilder.priorResponse(priorResponseWithoutBody(priorResponse));
        }
        return responseBuilder.build();
    }

    /**
     * Crea una respuesta previa válida para OkHttp sin cuerpo, requisito de {@code priorResponse}.
     */
    private static Response priorResponseWithoutBody(@NonNull Response response) {
        return new Response.Builder()
                .request(response.request())
                .protocol(response.protocol())
                .code(response.code())
                .message(response.message())
                .build();
    }

    /**
     * Store mínimo usado para ejecutar el refresh sin tocar Android ni almacenamiento seguro.
     */
    private static final class FakeSessionStore implements SessionRefreshCoordinator.SessionStore {
        @Nullable private String username;
        @Nullable private String accessToken;
        @Nullable private String refreshToken;

        private FakeSessionStore() {
            this.username = "alice";
            this.accessToken = "old-a";
            this.refreshToken = "old-r";
        }

        @Override
        public boolean isAccessTokenExpiringWithinSeconds(long leewaySeconds) {
            return false;
        }

        @NonNull
        @Override
        public SessionRefreshCoordinator.StoredSession getStoredSession() {
            return new SessionRefreshCoordinator.StoredSession(username, accessToken, refreshToken);
        }

        @Override
        public void saveLoginSync(@Nullable String username,
                                  @Nullable String accessToken,
                                  @Nullable String refreshToken) {
            this.username = username;
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
        }

        @Override
        public void logout() {
            username = null;
            accessToken = null;
            refreshToken = null;
        }
    }

    /**
     * Backend falso que devuelve un resultado fijo y cuenta invocaciones.
     */
    private static final class CountingBackend implements SessionRefreshCoordinator.RefreshBackend {
        private final SessionRefreshCoordinator.BackendRefreshResult result;
        private final AtomicInteger calls = new AtomicInteger();

        private CountingBackend(@NonNull SessionRefreshCoordinator.BackendRefreshResult result) {
            this.result = result;
        }

        @NonNull
        @Override
        public SessionRefreshCoordinator.BackendRefreshResult refresh(@NonNull String refreshToken) {
            calls.incrementAndGet();
            return result;
        }
    }
}
