package com.proyecto.moveon.data.session;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.proyecto.moveon.data.remote.retrofit.RetrofitProvider;
import com.proyecto.moveon.data.session.dto.LoginResponseDto;
import com.proyecto.moveon.data.session.dto.RefreshRequestDto;
import com.proyecto.moveon.utils.StringUtils;

import java.io.IOException;

import okhttp3.ResponseBody;
import retrofit2.Response;

/**
 * Coordinador único de refresh para toda la app.
 *
 * Dos disparadores distintos (proactivo al volver/arrancar y reactivo en 401)
 * pasan por este mismo motor para evitar reutilizar el mismo refresh token
 * en paralelo cuando el backend aplica rotación estricta.
 */
public final class SessionRefreshCoordinator {

    private static final long PROACTIVE_REFRESH_WINDOW_SECONDS = 90L;

    private static volatile SessionRefreshCoordinator instance;

    private final Context appContext;
    private final SecureSessionManager sessionManager;
    private final Object monitor = new Object();

    private boolean refreshInFlight = false;
    @NonNull
    private RefreshOutcome lastOutcome = RefreshOutcome.skipped();

    public interface Callback {
        void onComplete(@NonNull RefreshOutcome outcome);
    }

    public enum Status {
        SUCCESS,
        SKIPPED,
        UNAUTHORIZED,
        TRANSIENT_ERROR
    }

    public static final class RefreshOutcome {
        @NonNull private final Status status;
        @Nullable private final String accessToken;
        @Nullable private final String refreshToken;
        private final int httpCode;
        @Nullable private final String retryAfter;
        @Nullable private final String errorCode;
        @Nullable private final String message;

        private RefreshOutcome(@NonNull Status status,
                               @Nullable String accessToken,
                               @Nullable String refreshToken,
                               int httpCode,
                               @Nullable String retryAfter,
                               @Nullable String errorCode,
                               @Nullable String message) {
            this.status = status;
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.httpCode = httpCode;
            this.retryAfter = retryAfter;
            this.errorCode = errorCode;
            this.message = message;
        }

        @NonNull
        public static RefreshOutcome success(@NonNull String accessToken,
                                             @NonNull String refreshToken) {
            return new RefreshOutcome(Status.SUCCESS, accessToken, refreshToken,
                    200, null, null, null);
        }

        @NonNull
        public static RefreshOutcome skipped() {
            return new RefreshOutcome(Status.SKIPPED, null, null,
                    0, null, null, null);
        }

        @NonNull
        public static RefreshOutcome unauthorized(int httpCode,
                                                  @Nullable String errorCode,
                                                  @Nullable String message) {
            return new RefreshOutcome(Status.UNAUTHORIZED, null, null,
                    httpCode, null, errorCode, message);
        }

        @NonNull
        public static RefreshOutcome transientError(int httpCode,
                                                    @Nullable String retryAfter,
                                                    @Nullable String errorCode,
                                                    @Nullable String message) {
            return new RefreshOutcome(Status.TRANSIENT_ERROR, null, null,
                    httpCode, retryAfter, errorCode, message);
        }

        @NonNull public Status getStatus() { return status; }
        @Nullable public String getAccessToken() { return accessToken; }
        @Nullable public String getRefreshToken() { return refreshToken; }
        public int getHttpCode() { return httpCode; }
        @Nullable public String getRetryAfter() { return retryAfter; }
        @Nullable public String getErrorCode() { return errorCode; }
        @Nullable public String getMessage() { return message; }

        public boolean isSuccess() { return status == Status.SUCCESS; }
        public boolean isSkipped() { return status == Status.SKIPPED; }
        public boolean isUnauthorized() { return status == Status.UNAUTHORIZED; }
        public boolean isTransientError() { return status == Status.TRANSIENT_ERROR; }
    }

    @NonNull
    public static SessionRefreshCoordinator getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (SessionRefreshCoordinator.class) {
                if (instance == null) {
                    instance = new SessionRefreshCoordinator(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private SessionRefreshCoordinator(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        this.sessionManager = SecureSessionManager.getInstance(this.appContext);
    }

    public boolean shouldRefreshProactively() {
        return sessionManager.isAccessTokenExpiringWithinSeconds(PROACTIVE_REFRESH_WINDOW_SECONDS);
    }

    public void ensureFreshSessionAsync(@NonNull Callback callback) {
        Thread worker = new Thread(() -> {
            RefreshOutcome outcome = refreshBlocking(null, false);
            callback.onComplete(outcome);
        }, "moveon-session-refresh");
        worker.start();
    }

    @NonNull
    public RefreshOutcome refreshBlocking(@Nullable String failedAuthorizationHeader,
                                          boolean forceRefresh) {
        synchronized (monitor) {
            RefreshOutcome immediate = buildImmediateOutcomeLocked(failedAuthorizationHeader, forceRefresh);
            if (immediate != null) {
                return immediate;
            }

            if (refreshInFlight) {
                waitForCurrentRefreshLocked();
                return buildOutcomeAfterWaitLocked(failedAuthorizationHeader, forceRefresh);
            }

            refreshInFlight = true;
        }

        RefreshOutcome outcome = executeRefreshNow();

        synchronized (monitor) {
            lastOutcome = outcome;
            refreshInFlight = false;
            monitor.notifyAll();
            return adaptOutcomeForCallerLocked(failedAuthorizationHeader, forceRefresh, outcome);
        }
    }

    @Nullable
    private RefreshOutcome buildImmediateOutcomeLocked(@Nullable String failedAuthorizationHeader,
                                                       boolean forceRefresh) {
        RefreshOutcome reused = tryReuseStoredSessionLocked(failedAuthorizationHeader);
        if (reused != null) {
            return reused;
        }

        if (!forceRefresh && !shouldRefreshProactively()) {
            return RefreshOutcome.skipped();
        }

        String refreshToken = sessionManager.getRefreshToken();
        if (!StringUtils.hasText(refreshToken)) {
            sessionManager.logout();
            return RefreshOutcome.unauthorized(401, null, "No refresh token available");
        }

        return null;
    }

    @NonNull
    private RefreshOutcome buildOutcomeAfterWaitLocked(@Nullable String failedAuthorizationHeader,
                                                        boolean forceRefresh) {
        RefreshOutcome reused = tryReuseStoredSessionLocked(failedAuthorizationHeader);
        if (reused != null) {
            return reused;
        }

        if (!forceRefresh && !shouldRefreshProactively()) {
            return RefreshOutcome.skipped();
        }

        if (lastOutcome.isSuccess()) {
            RefreshOutcome current = buildSuccessFromStoredSession();
            if (current != null) {
                return current;
            }
        }

        if (!StringUtils.hasText(sessionManager.getRefreshToken())) {
            return RefreshOutcome.unauthorized(401, null, "No refresh token available");
        }

        return lastOutcome;
    }

    @NonNull
    private RefreshOutcome adaptOutcomeForCallerLocked(@Nullable String failedAuthorizationHeader,
                                                        boolean forceRefresh,
                                                        @NonNull RefreshOutcome outcome) {
        RefreshOutcome reused = tryReuseStoredSessionLocked(failedAuthorizationHeader);
        if (reused != null) {
            return reused;
        }

        if (outcome.isSuccess()) {
            RefreshOutcome current = buildSuccessFromStoredSession();
            if (current != null) {
                return current;
            }
        }

        if (!forceRefresh && !outcome.isUnauthorized() && !outcome.isTransientError()) {
            return RefreshOutcome.skipped();
        }

        return outcome;
    }

    @Nullable
    private RefreshOutcome tryReuseStoredSessionLocked(@Nullable String failedAuthorizationHeader) {
        if (!StringUtils.hasText(failedAuthorizationHeader)) return null;

        String currentAccess = sessionManager.getAccessToken();
        String currentRefresh = sessionManager.getRefreshToken();
        if (!StringUtils.hasText(currentAccess) || !StringUtils.hasText(currentRefresh)) return null;

        String expected = "Bearer " + currentAccess;
        if (!expected.equals(failedAuthorizationHeader)) return RefreshOutcome.success(currentAccess, currentRefresh);
        return null;
    }

    @Nullable
    private RefreshOutcome buildSuccessFromStoredSession() {
        String currentAccess = sessionManager.getAccessToken();
        String currentRefresh = sessionManager.getRefreshToken();
        if (!StringUtils.hasText(currentAccess) || !StringUtils.hasText(currentRefresh)) {
            return null;
        }
        return RefreshOutcome.success(currentAccess, currentRefresh);
    }

    private void waitForCurrentRefreshLocked() {
        while (refreshInFlight) {
            try {
                monitor.wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    @NonNull
    private RefreshOutcome executeRefreshNow() {
        String refreshToken = sessionManager.getRefreshToken();
        if (!StringUtils.hasText(refreshToken)) {
            sessionManager.logout();
            return RefreshOutcome.unauthorized(401, null, "No refresh token available");
        }

        try {
            Response<LoginResponseDto> refreshResp = RetrofitProvider.authApi(appContext)
                    .refresh(new RefreshRequestDto(refreshToken))
                    .execute();

            if (refreshResp.isSuccessful() && refreshResp.body() != null) {
                LoginResponseDto body = refreshResp.body();
                String newAccess = body.tokenAcceso;
                String newRefresh = body.refreshToken;

                if (!StringUtils.hasText(newAccess) || !StringUtils.hasText(newRefresh)) {
                    sessionManager.logout();
                    return RefreshOutcome.unauthorized(401, null, "Refresh response without valid tokens");
                }

                String username = StringUtils.hasText(body.nombreUsuario)
                        ? body.nombreUsuario
                        : StringUtils.textOf(sessionManager.getUsername());

                sessionManager.saveLogin(username, newAccess, newRefresh);
                return RefreshOutcome.success(newAccess, newRefresh);
            }

            ParsedRefreshError parsed = parseError(refreshResp);
            int code = refreshResp.code();
            if (code == 401 || code == 403) {
                sessionManager.logout();
                return RefreshOutcome.unauthorized(code, parsed.errorCode, parsed.backendMessage);
            }

            return RefreshOutcome.transientError(
                    code,
                    refreshResp.headers().get("Retry-After"),
                    parsed.errorCode,
                    parsed.backendMessage
            );
        } catch (IOException e) {
            return RefreshOutcome.transientError(-1, null, null, e.getMessage());
        }
    }

    @NonNull
    private ParsedRefreshError parseError(@NonNull Response<LoginResponseDto> refreshResp) {
        String backendErrorCode = null;
        String backendMessage = null;

        ResponseBody errorBody = refreshResp.errorBody();
        if (errorBody == null) {
            return new ParsedRefreshError(null, null);
        }

        try {
            String raw = errorBody.string();
            if (!StringUtils.hasText(raw)) {
                return new ParsedRefreshError(null, null);
            }

            JsonElement root = JsonParser.parseString(raw);
            if (root == null || !root.isJsonObject()) {
                return new ParsedRefreshError(null, null);
            }

            JsonObject obj = root.getAsJsonObject();
            backendErrorCode = getString(obj, "error_code");
            backendMessage = firstNonEmpty(
                    getString(obj, "mensaje"),
                    getString(obj, "message"),
                    getString(obj, "error")
            );
            if (!StringUtils.hasText(backendMessage)
                    && obj.has("detail")
                    && obj.get("detail").isJsonPrimitive()) {
                backendMessage = getString(obj, "detail");
            }
        } catch (Exception ignored) {
        } finally {
            try {
                errorBody.close();
            } catch (Exception ignored) {
            }
        }

        return new ParsedRefreshError(backendErrorCode, backendMessage);
    }

    @Nullable
    private static String getString(@NonNull JsonObject obj, @NonNull String key) {
        if (!obj.has(key) || obj.get(key) == null || !obj.get(key).isJsonPrimitive()) return null;
        String value = obj.get(key).getAsString();
        return StringUtils.hasText(value) ? value : null;
    }

    @Nullable
    private static String firstNonEmpty(@Nullable String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (StringUtils.hasText(value)) return value;
        }
        return null;
    }

    private static final class ParsedRefreshError {
        @Nullable final String errorCode;
        @Nullable final String backendMessage;

        ParsedRefreshError(@Nullable String errorCode, @Nullable String backendMessage) {
            this.errorCode = errorCode;
            this.backendMessage = backendMessage;
        }
    }
}
