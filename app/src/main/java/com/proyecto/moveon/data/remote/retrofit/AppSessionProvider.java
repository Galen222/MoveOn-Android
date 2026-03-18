package com.proyecto.moveon.data.remote.retrofit;

import android.os.SystemClock;

import com.proyecto.moveon.BuildConfig;
import com.proyecto.moveon.data.session.dto.AppSessionResponseDto;
import com.proyecto.moveon.utils.StringUtils;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class AppSessionProvider {

    private static volatile String cachedSession = null;
    private static long lastFetchTime = 0;
    private static final long CACHE_TTL_MS = BuildConfig.APP_SESSION_CACHE_TTL_MS;
    private static volatile HandshakeApi handshakeApi;

    // FIX: Caché de fallos para no repetir timeouts de handshake.
    // Sin esto, cada operación de red con sesión expirada pagaba 10 s de
    // connectTimeout. Ahora, tras un fallo, las siguientes llamadas dentro
    // del cooldown fallan instantáneamente.
    private static volatile long lastFailureTime = 0;
    private static final long FAILURE_COOLDOWN_MS = 5_000; // 5 s entre reintentos

    private static final Object LOCK = new Object();

    private AppSessionProvider() {}

    private static HandshakeApi getApi() {
        if (handshakeApi == null) {
            synchronized (AppSessionProvider.class) {
                if (handshakeApi == null) {
                    // FIX: Reducidos de 10/15/15 a 3/5/5 segundos.
                    // 3 s es más que suficiente para un handshake tanto en LAN como
                    // en producción. Con backend caído, el usuario espera 3 s en vez
                    // de 10 s antes de recibir el fallback offline.
                    OkHttpClient cleanClient = new OkHttpClient.Builder()
                            .connectTimeout(3, TimeUnit.SECONDS)
                            .readTimeout(5, TimeUnit.SECONDS)
                            .writeTimeout(5, TimeUnit.SECONDS)
                            .build();

                    String baseUrl = BuildConfig.BASE_URL;
                    if (!baseUrl.endsWith("/")) baseUrl += "/";

                    Retrofit retrofit = new Retrofit.Builder()
                            .baseUrl(baseUrl)
                            .client(cleanClient)
                            .addConverterFactory(GsonConverterFactory.create())
                            .build();
                    handshakeApi = retrofit.create(HandshakeApi.class);
                }
            }
        }
        return handshakeApi;
    }

    public static String getOrFetch() throws Exception {
        long now = SystemClock.elapsedRealtime();

        if (cachedSession != null && (now - lastFetchTime) < CACHE_TTL_MS) {
            return cachedSession;
        }

        synchronized (LOCK) {
            now = SystemClock.elapsedRealtime();
            if (cachedSession != null && (now - lastFetchTime) < CACHE_TTL_MS) {
                return cachedSession;
            }

            // FIX: Si falló recientemente, no reintentar — fallo instantáneo.
            // Evita que cada toggle o cambio de campo pague 3 s de timeout
            // cuando el backend está caído.
            if (lastFailureTime > 0 && (now - lastFailureTime) < FAILURE_COOLDOWN_MS) {
                throw new Exception("Handshake en cooldown tras fallo reciente");
            }

            try {
                cachedSession = fetchNewSession();
                lastFetchTime = SystemClock.elapsedRealtime();
                lastFailureTime = 0; // resetear cooldown tras éxito
                return cachedSession;
            } catch (Exception e) {
                // FIX: Registrar el fallo para activar cooldown.
                lastFailureTime = SystemClock.elapsedRealtime();
                throw e;
            }
        }
    }

    public static void invalidate() {
        synchronized (LOCK) {
            cachedSession = null;
            lastFetchTime = 0;
            // No resetear lastFailureTime aquí — solo se resetea con éxito.
        }
    }

    /**
     * MEJ-11: Resetea el cooldown de fallo para que la próxima operación
     * intente el handshake real en vez de fallar instantáneamente.
     * Llamado por {@code ConnectivityObserver} cuando la red vuelve tras
     * una desconexión — si el backend estaba caído y ahora hay red, el
     * cooldown del fallo anterior ya no es relevante.
     */
    public static void resetFailureCooldown() {
        lastFailureTime = 0;
    }

    private static String fetchNewSession() throws Exception {
        retrofit2.Response<AppSessionResponseDto> response =
                getApi().getHandshake(BuildConfig.APP_ID).execute();

        if (response.isSuccessful() && response.body() != null) {
            String token = response.body().appSession;
            if (StringUtils.hasText(token)) {
                return token;
            }
        }

        // PUNTO 2: Extracción del body para Debug sin fugas de memoria
        String errorSnippet = "";
        if (response.errorBody() != null) {
            try {
                if (BuildConfig.DEBUG) {
                    String fullError = response.errorBody().string();
                    // Recortamos a 200 caracteres para no saturar los logs si el servidor escupe un HTML gigante
                    errorSnippet = " - Detalles: " + (fullError.length() > 200 ? fullError.substring(0, 200) + "..." : fullError);
                }
            } catch (Exception ignored) {
            } finally {
                // El finally garantiza que SIEMPRE se cierra el recurso, haya error de lectura o no
                try {
                    response.errorBody().close();
                } catch (Exception ignored) {}
            }
        }

        throw new Exception("Error Handshake: HTTP " + response.code() + errorSnippet);
    }
}
