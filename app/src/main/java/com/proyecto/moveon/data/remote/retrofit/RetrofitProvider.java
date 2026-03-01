package com.proyecto.moveon.data.remote.retrofit;

import android.content.Context;
import com.proyecto.moveon.BuildConfig;
import java.util.concurrent.TimeUnit;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class RetrofitProvider {

    private static volatile MoveOnApi moveOnApi;
    private static volatile ProtectedApi protectedApi;

    private RetrofitProvider() {}

    public static MoveOnApi authApi(Context context) {
        ensureInit(context);
        return moveOnApi;
    }

    public static ProtectedApi protectedApi(Context context) {
        ensureInit(context);
        return protectedApi;
    }

    private static void ensureInit(Context context) {
        if (moveOnApi != null && protectedApi != null) return;
        synchronized (RetrofitProvider.class) {
            if (moveOnApi != null && protectedApi != null) return;
            String baseUrl = BuildConfig.BASE_URL;
            if (!baseUrl.endsWith("/")) {
                baseUrl += "/";
            }

            // 1. Configuración del Logger
            HttpLoggingInterceptor log = null;
            if (BuildConfig.DEBUG) {
                log = new HttpLoggingInterceptor();
                log.setLevel(HttpLoggingInterceptor.Level.BODY);

                // REDACCIÓN: Evita que los tokens se filtren en la consola de Android Studio
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
            // Punto 3: Gestión de Timeouts más amplios para el cliente protegido
            OkHttpClient.Builder protectedBuilder = baseClientPure.newBuilder()
                    .connectTimeout(20, TimeUnit.SECONDS) // Más margen de conexión
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
}