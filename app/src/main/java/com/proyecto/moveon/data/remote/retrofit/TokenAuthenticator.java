package com.proyecto.moveon.data.remote.retrofit;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.proyecto.moveon.core.auth.GlobalAuthManager;
import com.proyecto.moveon.data.session.SessionRefreshCoordinator;
import com.proyecto.moveon.utils.StringUtils;

import java.io.IOException;

import okhttp3.Authenticator;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;

public class TokenAuthenticator implements Authenticator {

    private static final HttpUrl TARGET_URL = HttpUrl.get(com.proyecto.moveon.BuildConfig.BASE_URL);
    private static final String TARGET_HOST = TARGET_URL.host();
    private static final int TARGET_PORT = TARGET_URL.port();

    private final SessionRefreshCoordinator sessionRefreshCoordinator;

    public TokenAuthenticator(Context context) {
        this.sessionRefreshCoordinator = SessionRefreshCoordinator.getInstance(context.getApplicationContext());
    }

    @Nullable
    @Override
    public Request authenticate(@Nullable Route route, @NonNull Response response) throws IOException {
        HttpUrl requestUrl = response.request().url();
        if (!requestUrl.scheme().equals(TARGET_URL.scheme())
                || !requestUrl.host().equals(TARGET_HOST)
                || requestUrl.port() != TARGET_PORT) {
            return null;
        }

        if (responseCount(response) >= 2) {
            return null;
        }

        SessionRefreshCoordinator.RefreshOutcome outcome =
                sessionRefreshCoordinator.refreshBlocking(response.request().header("Authorization"), true);

        if (outcome.isSuccess() && StringUtils.hasText(outcome.getAccessToken())) {
            return response.request().newBuilder()
                    .header("Authorization", "Bearer " + outcome.getAccessToken())
                    .build();
        }

        if (outcome.isUnauthorized()) {
            GlobalAuthManager.getInstance().notifySessionExpired();
            return null;
        }

        throw new RefreshFailedException(
                outcome.getHttpCode(),
                outcome.getRetryAfter(),
                outcome.getErrorCode(),
                outcome.getMessage()
        );
    }

    private int responseCount(Response response) {
        int result = 1;
        while ((response = response.priorResponse()) != null) result++;
        return result;
    }

    public static class RefreshFailedException extends IOException {
        private final int code;
        @Nullable private final String retryAfter;
        @Nullable private final String errorCode;
        @Nullable private final String backendMessage;

        public RefreshFailedException(int code,
                                      @Nullable String retryAfter,
                                      @Nullable String errorCode,
                                      @Nullable String backendMessage) {
            super("Refresh error: " + code + (StringUtils.hasText(errorCode) ? " / " + errorCode : ""));
            this.code = code;
            this.retryAfter = retryAfter;
            this.errorCode = errorCode;
            this.backendMessage = backendMessage;
        }

        public int getCode() { return code; }
        @Nullable public String getRetryAfter() { return retryAfter; }
        @Nullable public String getErrorCode() { return errorCode; }
        @Nullable public String getBackendMessage() { return backendMessage; }
    }
}
