package com.proyecto.moveon.data.remote.retrofit;

import android.content.Context;

import com.proyecto.moveon.data.session.SecureSessionManager;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public final class AuthHeaderInterceptor implements Interceptor {

    private final SecureSessionManager sessionManager;

    public AuthHeaderInterceptor(Context context) {
        this.sessionManager = new SecureSessionManager(context.getApplicationContext());
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request original = chain.request();
        String path = original.url().encodedPath();

        // No hace falta Authorization en estos:
        if ("/handshake".equals(path) || "/login".equals(path) || "/registro".equals(path)
                || "/token/refresh".equals(path) || "/logout".equals(path)) {
            return chain.proceed(original);
        }

        String access = sessionManager.getAccessToken();
        if (access == null || access.trim().isEmpty()) {
            return chain.proceed(original);
        }

        Request withAuth = original.newBuilder()
                .header("Authorization", "Bearer " + access)
                .build();

        return chain.proceed(withAuth);
    }
}