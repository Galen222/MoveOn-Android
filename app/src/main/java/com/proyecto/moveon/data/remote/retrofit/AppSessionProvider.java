package com.proyecto.moveon.data.remote.retrofit;

import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.proyecto.moveon.BuildConfig;
import com.proyecto.moveon.data.session.dto.AppSessionResponseDto;
import com.proyecto.moveon.utils.StringUtils;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Gestiona el token de sesión de aplicación usado por el handshake con el backend.
 *
 * <p>Mantiene una caché corta en memoria y un cooldown tras fallo para evitar que cada petición
 * pague un nuevo handshake cuando el backend está temporalmente caído. Es la contraparte en memoria
 * de {@link AppSessionInterceptor}, que consume esta sesión técnica en cada request al backend.</p>
 */
public final class AppSessionProvider {

    private static volatile String cachedSession = null;
    private static long lastFetchTime = 0;
    private static final long CACHE_TTL_MS = BuildConfig.APP_SESSION_CACHE_TTL_MS;
    private static volatile HandshakeApi handshakeApi;

    // Registra el instante del último fallo para aplicar un cooldown corto
    // y evitar reintentos de handshake consecutivos cuando el backend está caído.
    private static volatile long lastFailureTime = 0;
    private static final long FAILURE_COOLDOWN_MS = 5_000; // 5 s entre reintentos

    private static final Object LOCK = new Object();

    /**
     * Evita instancias de una clase utilitaria puramente estática.
     */
    private AppSessionProvider() {}

    /**
     * Devuelve la instancia de {@link HandshakeApi} construida con un
     * OkHttpClient "limpio" (sin interceptores de sesión) y timeouts
     * agresivos. Así el handshake no depende de sí mismo y, si el backend
     * no responde rápido, la app cae al fallback offline sin bloquearse.
     *
     * @return cliente Retrofit para el endpoint de handshake.
     *
     * @see HandshakeApi#getHandshake(String)
     */
    private static HandshakeApi getApi() {
        if (handshakeApi == null) {
            synchronized (AppSessionProvider.class) {
                if (handshakeApi == null) {
                    // Tiempos agresivos pero suficientes para un handshake ligero.
                    // Así el fallback offline llega rápido cuando el backend no responde.
                    OkHttpClient cleanClient = new OkHttpClient.Builder()
                            .connectTimeout(3, TimeUnit.SECONDS)
                            .readTimeout(5, TimeUnit.SECONDS)
                            .writeTimeout(5, TimeUnit.SECONDS)
                            .build();

                    Retrofit retrofit = new Retrofit.Builder()
                            .baseUrl(BuildConfig.BASE_URL)
                            .client(cleanClient)
                            .addConverterFactory(GsonConverterFactory.create())
                            .build();
                    handshakeApi = retrofit.create(HandshakeApi.class);
                }
            }
        }
        return handshakeApi;
    }

    /**
     * Devuelve el token de sesión de app cacheado si sigue dentro de TTL y, en caso contrario,
     * lo pide de nuevo al backend con doble-check locking para que varios hilos no dupliquen la
     * misma llamada de red.
     *
     * @return token de sesión válido para poblar la cabecera {@code x-app-session}.
     * @throws Exception si el handshake falla, si el provider sigue en cooldown tras un fallo reciente
     * o si el backend devuelve una respuesta sin token utilizable.
     * @see #invalidate()
     * @see #resetFailureCooldown()
     * @see #fetchNewSession()
     */
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

            // Si hubo un fallo reciente, no reintenta todavía para evitar
            // que cada operación vuelva a pagar el timeout de handshake.
            if (lastFailureTime > 0 && (now - lastFailureTime) < FAILURE_COOLDOWN_MS) {
                throw new Exception("Handshake en cooldown tras fallo reciente");
            }

            try {
                cachedSession = fetchNewSession();
                lastFetchTime = SystemClock.elapsedRealtime();
                lastFailureTime = 0; // resetear cooldown tras éxito
                return cachedSession;
            } catch (Exception e) {
                // Registra el fallo para activar el cooldown antes del siguiente intento.
                lastFailureTime = SystemClock.elapsedRealtime();
                throw e;
            }
        }
    }

    /**
     * Invalida la sesión de app cacheada para forzar un nuevo handshake en el siguiente acceso.
     *
     * <p>No resetea el cooldown de fallos, que solo se limpia tras un fetch exitoso o una
     * reconexión explícita.</p>
     *
     * @see #getOrFetch()
     */
    public static void invalidate() {
        synchronized (LOCK) {
            cachedSession = null;
            lastFetchTime = 0;
            // No resetear lastFailureTime aquí — solo se resetea con éxito.
        }
    }

    /**
     * Limpia el cooldown tras un fallo reciente para permitir un nuevo intento de handshake.
     *
     * <p>Lo invoca el flujo de reconexión global cuando la red vuelve y el fallo previo deja de
     * ser representativo.</p>
     *
     * @see #getOrFetch()
     */
    public static void resetFailureCooldown() {
        lastFailureTime = 0;
    }

    /**
     * Ejecuta la llamada síncrona al endpoint de handshake y extrae el token.
     *
     * <p>En debug intenta incorporar una porción del cuerpo de error para facilitar diagnósticos;
     * en release evita exponer ese detalle adicional.</p>
     *
     * @return token fresco emitido por el backend.
     * @throws Exception si la respuesta no es exitosa, si llega sin {@code appSession} útil o si
     * falla la red al ejecutar el handshake.
     * @see #getApi()
     */
    private static String fetchNewSession() throws Exception {
        retrofit2.Response<AppSessionResponseDto> response =
                getApi().getHandshake(BuildConfig.APP_ID).execute();

        if (response.isSuccessful() && response.body() != null) {
            String token = response.body().appSession;
            if (StringUtils.hasText(token)) {
                return token;
            }
        }

        throw new Exception(
                "Error Handshake: HTTP " + response.code() + readErrorSnippet(response.errorBody())
        );
    }

    /**
     * Lee una porción acotada del cuerpo de error en builds de depuración.
     * El cuerpo se cierra siempre, aunque no se añada al mensaje final.
     */
    @NonNull
    private static String readErrorSnippet(@Nullable ResponseBody errorBody) {
        try (ResponseBody body = errorBody) {
            if (body == null || !BuildConfig.DEBUG) {
                return "";
            }

            String fullError = body.string();
            String details = fullError.length() > 200
                    ? fullError.substring(0, 200) + "..."
                    : fullError;
            return " - Detalles: " + details;
        } catch (Exception ignored) {
            // El cuerpo es solo informativo; el código HTTP sigue siendo suficiente.
            return "";
        }
    }
}
