package com.proyecto.moveon.data.remote.retrofit;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public final class AppSessionInterceptor implements Interceptor {

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request original = chain.request();

        String path = original.url().encodedPath();
        // En /handshake NO se manda x-app-session
        if ("/handshake".equals(path)) {
            return chain.proceed(original);
        }

        try {
            String appSession = AppSessionProvider.getOrFetch();

            Request withSession = original.newBuilder()
                    .header("x-app-session", appSession)
                    .build();

            Response response = chain.proceed(withSession);

            // Si el backend devuelve 403 por token app-session inválido/expirado, invalidamos cache
            if (response.code() == 403) {
                AppSessionProvider.invalidate();
            }

            return response;

        } catch (Exception e) {
            throw new IOException(e.getMessage(), e);
        }
    }
}