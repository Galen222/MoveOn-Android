package com.proyecto.moveon.data.remote.retrofit;

import android.content.Context;

import androidx.annotation.NonNull;
import com.proyecto.moveon.BuildConfig;
import java.util.concurrent.TimeUnit;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
/**
 * Proveedor de datos o dependencias para retrofit.
 */
public final class RetrofitProvider {

    private static volatile MoveOnApi moveOnApi;
    private static volatile ProtectedApi protectedApi;

    /**
     * Constructor privado: clase de utilidades estática, no se instancia.
     */
    private RetrofitProvider() {}

    /**
     * Devuelve el {@link MoveOnApi} para endpoints públicos y de auth
     * (login, registro, recuperación, handshake). Inicializa perezosamente
     * los clientes Retrofit la primera vez que se pide.
     *
     * @param context cualquier contexto; se usa sólo para inicializar los clientes.
     * @return cliente Retrofit para endpoints de autenticación.
     */
    public static MoveOnApi authApi(Context context) {
        ensureInit(context);
        return moveOnApi;
    }

    /**
     * Devuelve el cliente Retrofit para endpoints protegidos (los que
     * exigen {@code Authorization: Bearer}). Reutiliza el mismo pool HTTP
     * que {@link #authApi} añadiendo el interceptor de token.
     *
     * @param context cualquier contexto; se usa sólo para inicializar los clientes.
     * @return cliente Retrofit para endpoints protegidos.
     */
    public static ProtectedApi protectedApi(Context context) {
        ensureInit(context);
        return protectedApi;
    }

    /**
     * Inicializa (una única vez) los clientes Retrofit con su base URL,
     * interceptores y logger. Hace doble-check locking para que varios
     * hilos pidiendo a la vez no construyan más de un cliente.
     *
     * @param context contexto desde el que se resuelve el {@code applicationContext} para los interceptores.
     */
    private static void ensureInit(Context context) {
        if (moveOnApi != null && protectedApi != null) return;
        synchronized (RetrofitProvider.class) {
            if (moveOnApi != null && protectedApi != null) return;
            String baseUrl = normalizeBaseUrl(BuildConfig.BASE_URL);

            // 1. Configuración del Logger
            HttpLoggingInterceptor log = null;
            if (BuildConfig.DEBUG) {
                log = new HttpLoggingInterceptor();
                log.setLevel(HttpLoggingInterceptor.Level.BODY);

                // Redacta las cabeceras sensibles para no exponer tokens en la consola.
                log.redactHeader("Authorization");
                log.redactHeader("x-app-session");
            }

            // 2. Cliente BASE PURO
            // Punto 2: Añadimos Interceptor de Identidad (User-Agent)
            OkHttpClient baseClientPure = new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .writeTimeout(15, TimeUnit.SECONDS)
                    .addInterceptor(new AppSessionInterceptor())
                    .addInterceptor(chain -> {
                        Request original = chain.request();
                        Request request = original.newBuilder()
                                .header("User-Agent", "MoveOn-Android/" + BuildConfig.VERSION_NAME)
                                .build();
                        return chain.proceed(request);
                    })
                    .build();

            // 3. Cliente PROTEGIDO
            // 8 s ofrece margen suficiente para producción y evita que un backend caído
            // bloquee el hilo IO durante demasiado tiempo.
            // 8 s es generoso para producción (con proxy: TCP connect ~200 ms,
            // sin proxy: ~1 s). Con backend caído, 20 s bloqueaba el hilo IO
            // y congelaba la UI. writeTimeout(60 s) se mantiene para subida de
            // rutas GPS pesadas. readTimeout(30 s) se mantiene para respuestas
            // grandes (historial de actividades).
            OkHttpClient.Builder protectedBuilder = baseClientPure.newBuilder()
                    .connectTimeout(8, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)   // 1 minuto para subir rutas pesadas
                    .readTimeout(30, TimeUnit.SECONDS)
                    .dispatcher(new Dispatcher())
                    .connectionPool(new ConnectionPool())
                    .addInterceptor(new AuthHeaderInterceptor(context))
                    .authenticator(new TokenAuthenticator(context));

            if (log != null) {
                protectedBuilder.addInterceptor(log);
            }
            OkHttpClient protectedClient = protectedBuilder.build();

            // 4. Cliente BASE FINAL con Logs
            OkHttpClient.Builder baseBuilder = baseClientPure.newBuilder();
            if (log != null) {
                baseBuilder.addInterceptor(log);
            }
            OkHttpClient baseClient = baseBuilder.build();

            // 5. Retrofits
            Retrofit publicRetrofit = new Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .addConverterFactory(GsonConverterFactory.create())
                    .client(baseClient)
                    .build();

            Retrofit protectedRetrofit = new Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .addConverterFactory(GsonConverterFactory.create())
                    .client(protectedClient)
                    .build();

            moveOnApi = publicRetrofit.create(MoveOnApi.class);
            protectedApi = protectedRetrofit.create(ProtectedApi.class);
        }
    }


    @NonNull
    /**
     * Garantiza que la base URL termina en exactamente una {@code /}: quita
     * las barras sobrantes y añade una al final. Sin esto Retrofit falla
     * con {@code baseUrl must end in /}.
     *
     * @param raw base URL tal y como viene del BuildConfig.
     * @return URL normalizada con una única barra final.
     */
    private static String normalizeBaseUrl(@NonNull String raw) {
        String trimmed = raw.trim();
        int end = trimmed.length();
        while (end > 0 && trimmed.charAt(end - 1) == '/') {
            end--;
        }
        return trimmed.substring(0, end) + "/";
    }
}
