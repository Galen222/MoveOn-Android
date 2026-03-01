package com.proyecto.moveon.data.remote.retrofit;

import java.io.IOException;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public final class AppSessionInterceptor implements Interceptor {

    // CACHÉ ESTÁTICA (Fail Fast: lanzará IllegalArgumentException si la BASE_URL es inválida)
    private static final HttpUrl TARGET_URL = HttpUrl.get(com.proyecto.moveon.BuildConfig.BASE_URL);
    private static final String TARGET_HOST = TARGET_URL.host();
    private static final int TARGET_PORT = TARGET_URL.port();

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request original = chain.request();

        // SEGURIDAD: Solo inyectamos el x-app-session si coincide el host Y EL PUERTO
        if (!original.url().host().equals(TARGET_HOST) || original.url().port() != TARGET_PORT) {
            return chain.proceed(original);
        }

        java.util.List<String> segments = original.url().pathSegments();

        // Obtenemos el último segmento REAL (ignorando la barra final '/' si la hubiera)
        String lastSegment = "";
        if (!segments.isEmpty()) {
            lastSegment = segments.get(segments.size() - 1);
            if (lastSegment.isEmpty() && segments.size() > 1) {
                lastSegment = segments.get(segments.size() - 2);
            }
        }

        // En /handshake NO se manda x-app-session (inmune a prefijos y trailing slashes)
        if ("handshake".equals(lastSegment)) {
            return chain.proceed(original);
        }

        try {
            String appSession = AppSessionProvider.getOrFetch();
            Request withSession = original.newBuilder()
                    .header("x-app-session", appSession)
                    .build();

            Response response = chain.proceed(withSession);

            // Solo invalidamos el handshake si el servidor nos indica explícitamente que la app-session caducó
            if (response.code() == 403 && "1".equals(response.header("x-app-session-expired"))) {
                AppSessionProvider.invalidate();
                response.close(); // Importante: cerrar el body de la respuesta fallida

                // Pedimos uno nuevo y reintentamos
                String newSession = AppSessionProvider.getOrFetch();
                Request retryReq = original.newBuilder()
                        .header("x-app-session", newSession)
                        .build();
                return chain.proceed(retryReq);
            }
            return response;
        } catch (Exception e) {
            throw new IOException(e.getMessage(), e);
        }
    }
}