package com.proyecto.moveon.data.session;

import static org.junit.Assert.*;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tests del coordinador de refresh para aumentar cobertura de ramas sin tocar red real.
 */
public class SessionRefreshCoordinatorTest {

    /**
     * Verifica que {@link SessionRefreshCoordinator.StoredSession#isRefreshTokenMissing()} exige contenido real.
     */
    @Test
    public void storedSession_isRefreshTokenMissing_detectsBlankToken() {
        assertFalse(new SessionRefreshCoordinator.StoredSession("u", "a", "refresh").isRefreshTokenMissing());
        assertTrue(new SessionRefreshCoordinator.StoredSession("u", "a", "   ").isRefreshTokenMissing());
        assertTrue(new SessionRefreshCoordinator.StoredSession("u", "a", null).isRefreshTokenMissing());
    }

    /**
     * Verifica que las factorías de {@link SessionRefreshCoordinator.RefreshOutcome} conservan estado y metadatos.
     */
    @Test
    public void refreshOutcomeFactories_preserveStatusAndMetadata() {
        SessionRefreshCoordinator.RefreshOutcome success =
                SessionRefreshCoordinator.RefreshOutcome.success("access", "refresh");
        SessionRefreshCoordinator.RefreshOutcome skipped =
                SessionRefreshCoordinator.RefreshOutcome.skipped();
        SessionRefreshCoordinator.RefreshOutcome unauthorized =
                SessionRefreshCoordinator.RefreshOutcome.unauthorized(401, "expired", "caducado");
        SessionRefreshCoordinator.RefreshOutcome transientError =
                SessionRefreshCoordinator.RefreshOutcome.transientError(503, "10", "busy", "ocupado");

        assertTrue(success.isSuccess());
        assertEquals(SessionRefreshCoordinator.Status.SUCCESS, success.getStatus());
        assertEquals("access", success.getAccessToken());
        assertEquals("refresh", success.getRefreshToken());
        assertEquals(200, success.getHttpCode());

        assertTrue(skipped.isSkipped());
        assertEquals(SessionRefreshCoordinator.Status.SKIPPED, skipped.getStatus());

        assertTrue(unauthorized.isUnauthorized());
        assertEquals(401, unauthorized.getHttpCode());
        assertEquals("expired", unauthorized.getErrorCode());
        assertEquals("caducado", unauthorized.getMessage());

        assertTrue(transientError.isTransientError());
        assertEquals(503, transientError.getHttpCode());
        assertEquals("10", transientError.getRetryAfter());
        assertEquals("busy", transientError.getErrorCode());
        assertEquals("ocupado", transientError.getMessage());
    }

    /**
     * Verifica que las factorías de {@link SessionRefreshCoordinator.BackendRefreshResult} exponen los datos crudos.
     */
    @Test
    public void backendRefreshResultFactories_preserveRawBackendData() {
        SessionRefreshCoordinator.BackendRefreshResult success =
                SessionRefreshCoordinator.BackendRefreshResult.success("new-a", "new-r", "alice");
        SessionRefreshCoordinator.BackendRefreshResult failure =
                SessionRefreshCoordinator.BackendRefreshResult.failure(429, "30", "rate_limit", "espera");

        assertTrue(success.isSuccessful());
        assertEquals(200, success.getHttpCode());
        assertEquals("new-a", success.getAccessToken());
        assertEquals("new-r", success.getRefreshToken());
        assertEquals("alice", success.getUsername());

        assertFalse(failure.isSuccessful());
        assertEquals(429, failure.getHttpCode());
        assertEquals("30", failure.getRetryAfter());
        assertEquals("rate_limit", failure.getErrorCode());
        assertEquals("espera", failure.getBackendMessage());
    }

    /**
     * Verifica que la renovación proactiva delega en el store usando la ventana interna de seguridad.
     */
    @Test
    public void shouldRefreshProactively_delegatesToStoreWithConfiguredWindow() {
        FakeSessionStore store = new FakeSessionStore("alice", "access", "refresh");
        store.expiring = true;

        SessionRefreshCoordinator coordinator = SessionRefreshCoordinator.createForTests(
                store,
                refreshToken -> SessionRefreshCoordinator.BackendRefreshResult.success("a", "r", "u")
        );

        assertTrue(coordinator.shouldRefreshProactively());
        assertEquals(90L, store.lastLeewaySeconds);
    }

    /**
     * Verifica que un refresh no forzado se omite cuando el access token aún no está cerca de caducar.
     */
    @Test
    public void refreshBlocking_whenNotForcedAndNotExpiring_skipsWithoutCallingBackend() {
        FakeSessionStore store = new FakeSessionStore("alice", "access", "refresh");
        CountingBackend backend = new CountingBackend(
                SessionRefreshCoordinator.BackendRefreshResult.success("unused", "unused", "alice")
        );
        SessionRefreshCoordinator coordinator = SessionRefreshCoordinator.createForTests(store, backend);

        SessionRefreshCoordinator.RefreshOutcome outcome = coordinator.refreshBlocking(null, false);

        assertTrue(outcome.isSkipped());
        assertEquals(0, backend.calls.get());
        assertFalse(store.loggedOut);
    }

    /**
     * Verifica que un refresh forzado sin refresh token cierra sesión y devuelve no autorizado.
     */
    @Test
    public void refreshBlocking_whenForcedWithoutRefreshToken_logsOutAndReturnsUnauthorized() {
        FakeSessionStore store = new FakeSessionStore("alice", "access", null);
        CountingBackend backend = new CountingBackend(
                SessionRefreshCoordinator.BackendRefreshResult.success("unused", "unused", "alice")
        );
        SessionRefreshCoordinator coordinator = SessionRefreshCoordinator.createForTests(store, backend);

        SessionRefreshCoordinator.RefreshOutcome outcome = coordinator.refreshBlocking("Bearer access", true);

        assertTrue(outcome.isUnauthorized());
        assertEquals(401, outcome.getHttpCode());
        assertTrue(store.loggedOut);
        assertEquals(0, backend.calls.get());
    }

    /**
     * Verifica que un refresh correcto persiste tokens y conserva el usuario local si backend no lo envía.
     */
    @Test
    public void refreshBlocking_successPersistsTokensAndKeepsStoredUsernameWhenBackendOmitsIt() {
        FakeSessionStore store = new FakeSessionStore("alice", "old-a", "old-r");
        CountingBackend backend = new CountingBackend(
                SessionRefreshCoordinator.BackendRefreshResult.success("new-a", "new-r", null)
        );
        SessionRefreshCoordinator coordinator = SessionRefreshCoordinator.createForTests(store, backend);

        SessionRefreshCoordinator.RefreshOutcome outcome = coordinator.refreshBlocking("Bearer old-a", true);

        assertTrue(outcome.isSuccess());
        assertEquals("new-a", outcome.getAccessToken());
        assertEquals("new-r", outcome.getRefreshToken());
        assertEquals("alice", store.username);
        assertEquals("new-a", store.accessToken);
        assertEquals("new-r", store.refreshToken);
        assertEquals(1, backend.calls.get());
    }

    /**
     * Verifica que una respuesta exitosa pero sin tokens válidos se trata como sesión irrecuperable.
     */
    @Test
    public void refreshBlocking_successWithBlankTokens_logsOutAsUnauthorized() {
        FakeSessionStore store = new FakeSessionStore("alice", "old-a", "old-r");
        CountingBackend backend = new CountingBackend(
                SessionRefreshCoordinator.BackendRefreshResult.success("   ", "new-r", "alice")
        );
        SessionRefreshCoordinator coordinator = SessionRefreshCoordinator.createForTests(store, backend);

        SessionRefreshCoordinator.RefreshOutcome outcome = coordinator.refreshBlocking("Bearer old-a", true);

        assertTrue(outcome.isUnauthorized());
        assertEquals("Refresh response without valid tokens", outcome.getMessage());
        assertTrue(store.loggedOut);
    }

    /**
     * Verifica que una respuesta 401/403 del refresh cierra la sesión local.
     */
    @Test
    public void refreshBlocking_unauthorizedBackendResponse_logsOut() {
        FakeSessionStore store = new FakeSessionStore("alice", "old-a", "old-r");
        CountingBackend backend = new CountingBackend(
                SessionRefreshCoordinator.BackendRefreshResult.failure(403, null, "reuse", "reutilizado")
        );
        SessionRefreshCoordinator coordinator = SessionRefreshCoordinator.createForTests(store, backend);

        SessionRefreshCoordinator.RefreshOutcome outcome = coordinator.refreshBlocking("Bearer old-a", true);

        assertTrue(outcome.isUnauthorized());
        assertEquals(403, outcome.getHttpCode());
        assertEquals("reuse", outcome.getErrorCode());
        assertEquals("reutilizado", outcome.getMessage());
        assertTrue(store.loggedOut);
    }

    /**
     * Verifica que un 429/5xx se clasifica como error transitorio y conserva la sesión.
     */
    @Test
    public void refreshBlocking_retryableBackendResponse_returnsTransientErrorWithoutLogout() {
        FakeSessionStore store = new FakeSessionStore("alice", "old-a", "old-r");
        CountingBackend backend = new CountingBackend(
                SessionRefreshCoordinator.BackendRefreshResult.failure(503, "15", "maintenance", "caído")
        );
        SessionRefreshCoordinator coordinator = SessionRefreshCoordinator.createForTests(store, backend);

        SessionRefreshCoordinator.RefreshOutcome outcome = coordinator.refreshBlocking("Bearer old-a", true);

        assertTrue(outcome.isTransientError());
        assertEquals(503, outcome.getHttpCode());
        assertEquals("15", outcome.getRetryAfter());
        assertEquals("maintenance", outcome.getErrorCode());
        assertFalse(store.loggedOut);
    }

    /**
     * Verifica que una excepción de transporte se propaga como error transitorio de refresh.
     */
    @Test
    public void refreshBlocking_ioException_returnsTransientError() {
        FakeSessionStore store = new FakeSessionStore("alice", "old-a", "old-r");
        SessionRefreshCoordinator coordinator = SessionRefreshCoordinator.createForTests(
                store,
                refreshToken -> { throw new IOException("timeout"); }
        );

        SessionRefreshCoordinator.RefreshOutcome outcome = coordinator.refreshBlocking("Bearer old-a", true);

        assertTrue(outcome.isTransientError());
        assertEquals(0, outcome.getHttpCode());
        assertEquals("timeout", outcome.getMessage());
        assertFalse(store.loggedOut);
    }

    /**
     * Verifica que una petición con access antiguo reutiliza la sesión nueva sin llamar al backend.
     */
    @Test
    public void refreshBlocking_withStaleAuthorizationHeader_reusesStoredNewTokens() {
        FakeSessionStore store = new FakeSessionStore("alice", "new-a", "new-r");
        CountingBackend backend = new CountingBackend(
                SessionRefreshCoordinator.BackendRefreshResult.success("unused", "unused", "alice")
        );
        SessionRefreshCoordinator coordinator = SessionRefreshCoordinator.createForTests(store, backend);

        SessionRefreshCoordinator.RefreshOutcome outcome = coordinator.refreshBlocking("Bearer old-a", true);

        assertTrue(outcome.isSuccess());
        assertEquals("new-a", outcome.getAccessToken());
        assertEquals("new-r", outcome.getRefreshToken());
        assertEquals(0, backend.calls.get());
    }

    /**
     * Verifica que una petición con el access actual sí dispara el backend de refresh.
     */
    @Test
    public void refreshBlocking_withCurrentAuthorizationHeader_executesBackendRefresh() {
        FakeSessionStore store = new FakeSessionStore("alice", "old-a", "old-r");
        CountingBackend backend = new CountingBackend(
                SessionRefreshCoordinator.BackendRefreshResult.success("new-a", "new-r", "alice")
        );
        SessionRefreshCoordinator coordinator = SessionRefreshCoordinator.createForTests(store, backend);

        SessionRefreshCoordinator.RefreshOutcome outcome = coordinator.refreshBlocking("Bearer old-a", true);

        assertTrue(outcome.isSuccess());
        assertEquals("new-a", outcome.getAccessToken());
        assertEquals(1, backend.calls.get());
    }

    /**
     * Store mutable en memoria para comprobar los efectos del coordinador sin SharedPreferences.
     */
    private static final class FakeSessionStore implements SessionRefreshCoordinator.SessionStore {
        @Nullable String username;
        @Nullable String accessToken;
        @Nullable String refreshToken;
        boolean expiring;
        boolean loggedOut;
        long lastLeewaySeconds = -1L;

        private FakeSessionStore(@Nullable String username,
                                 @Nullable String accessToken,
                                 @Nullable String refreshToken) {
            this.username = username;
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
        }

        @Override
        public boolean isAccessTokenExpiringWithinSeconds(long leewaySeconds) {
            lastLeewaySeconds = leewaySeconds;
            return expiring;
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
            loggedOut = true;
            username = null;
            accessToken = null;
            refreshToken = null;
        }
    }

    /**
     * Backend falso que devuelve siempre el resultado recibido y contabiliza llamadas.
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
