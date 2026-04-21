package com.proyecto.moveon.data.remote.retrofit;

import android.content.Context;

import androidx.annotation.NonNull;

import com.proyecto.moveon.data.session.SecureSessionManager;
import com.proyecto.moveon.utils.StringUtils;

import java.io.IOException;
import java.util.List;

import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
/**
 * Interceptor que adapta peticiones o respuestas relacionadas con auth header interceptor.
 */
public final class AuthHeaderInterceptor implements Interceptor {

    private static final HttpUrl TARGET_URL = HttpUrl.get(com.proyecto.moveon.BuildConfig.BASE_URL);
    private static final String TARGET_HOST = TARGET_URL.host();
    private static final int TARGET_PORT = TARGET_URL.port();

    private final SecureSessionManager sessionManager;

    public AuthHeaderInterceptor(Context context) {
        this.sessionManager = SecureSessionManager.getInstance(context.getApplicationContext());
    }

    @NonNull
    @Override
    public Response intercept(Chain chain) throws IOException {
        Request original = chain.request();

        if (!original.url().host().equals(TARGET_HOST) || original.url().port() != TARGET_PORT) {
            return chain.proceed(original);
        }

        if (isPublicEndpoint(original)) {
            return chain.proceed(original);
        }

        String access = sessionManager.getAccessToken();
        if (!StringUtils.hasText(access)) {
            return chain.proceed(original);
        }

        Request withAuth = original.newBuilder()
                .header("Authorization", "Bearer " + access)
                .build();
        return chain.proceed(withAuth);
    }

    private boolean isPublicEndpoint(Request req) {
        List<String> seg = req.url().pathSegments();
        if (seg.isEmpty()) return false;

        int size = seg.size();
        String last = seg.get(size - 1);

        if (last.isEmpty() && size > 1) {
            size--;
            last = seg.get(size - 1);
        }

        String prev = size > 1 ? seg.get(size - 2) : "";

        if ("handshake".equals(last)) return true;
        if ("login".equals(last) || "registro".equals(last) || "logout".equals(last)) return true;
        if ("refresh".equals(last) && "token".equals(prev)) return true;
        return "password".equals(prev) && ("solicitar".equals(last) || "confirmar".equals(last));
    }
}