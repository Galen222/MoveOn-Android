package com.proyecto.moveon.testutil;

import androidx.annotation.NonNull;

import com.proyecto.moveon.data.remote.retrofit.MoveOnApi;
import com.proyecto.moveon.data.remote.retrofit.ProtectedApi;
import com.proyecto.moveon.data.remote.retrofit.RetrofitProvider;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockWebServer;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Entorno controlado para tests JVM que necesitan ejercitar la capa de red
 * sin tocar el backend real ni el cliente productivo de Retrofit.
 *
 * <p>Levanta un {@link MockWebServer} local y publica clientes Retrofit
 * apuntando contra él. Inyecta esos clientes en los campos estáticos
 * {@code moveOnApi} y {@code protectedApi} de {@link RetrofitProvider}
 * por reflexión, de forma que cualquier código de producción que llame
 * a {@code RetrofitProvider.authApi(ctx)} o {@code protectedApi(ctx)}
 * use el cliente apuntando al servidor falso.</p>
 *
 * <p>Esta clase no modifica el código de producción: sólo manipula sus
 * campos privados via {@link Field#setAccessible(boolean)} para el alcance
 * del test, y restaura los valores originales en {@link #shutdown()}.</p>
 */
public final class MockServerEnvironment {

    private final MockWebServer server;
    private final Retrofit retrofit;
    private final MoveOnApi moveOnApi;
    private final ProtectedApi protectedApi;

    private final Object originalMoveOnApi;
    private final Object originalProtectedApi;

    /**
     * Inicia un {@link MockWebServer} listo para recibir peticiones, construye
     * los clientes Retrofit asociados y los publica en {@link RetrofitProvider}.
     *
     * @throws IOException si el servidor falso no puede arrancar.
     */
    public MockServerEnvironment() throws IOException {
        this.server = new MockWebServer();
        this.server.start();

        // Cliente OkHttp con timeouts cortos: los tests no deben quedar
        // bloqueados por errores accidentales de configuración.
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(2, TimeUnit.SECONDS)
                .writeTimeout(2, TimeUnit.SECONDS)
                .callTimeout(5, TimeUnit.SECONDS)
                .build();

        this.retrofit = new Retrofit.Builder()
                .baseUrl(server.url("/"))
                .client(httpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        this.moveOnApi = retrofit.create(MoveOnApi.class);
        this.protectedApi = retrofit.create(ProtectedApi.class);

        this.originalMoveOnApi = readStaticField("moveOnApi");
        this.originalProtectedApi = readStaticField("protectedApi");

        writeStaticField("moveOnApi", moveOnApi);
        writeStaticField("protectedApi", protectedApi);
    }

    /**
     * Devuelve el {@link MockWebServer} para que los tests puedan encolar
     * respuestas y verificar las peticiones recibidas.
     *
     * @return servidor falso ya iniciado.
     */
    @NonNull
    public MockWebServer getServer() {
        return server;
    }

    /**
     * Devuelve el {@link Retrofit} compartido por las dos APIs publicadas.
     *
     * @return cliente Retrofit apuntando contra el servidor falso.
     */
    @NonNull
    public Retrofit getRetrofit() {
        return retrofit;
    }

    /**
     * Devuelve el cliente público que el código de producción recupera vía
     * {@link RetrofitProvider#authApi}.
     *
     * @return cliente Retrofit del MoveOnApi falso.
     */
    @NonNull
    public MoveOnApi getMoveOnApi() {
        return moveOnApi;
    }

    /**
     * Devuelve el cliente protegido que el código de producción recupera vía
     * {@link RetrofitProvider#protectedApi(android.content.Context)}.
     *
     * @return cliente Retrofit del ProtectedApi falso.
     */
    @NonNull
    public ProtectedApi getProtectedApi() {
        return protectedApi;
    }

    /**
     * Detiene el servidor falso y restaura los campos estáticos originales
     * de {@link RetrofitProvider} para no contaminar otros tests.
     *
     * @throws IOException si el servidor falla al cerrarse.
     */
    public void shutdown() throws IOException {
        try {
            writeStaticField("moveOnApi", originalMoveOnApi);
            writeStaticField("protectedApi", originalProtectedApi);
        } finally {
            server.shutdown();
        }
    }

    /**
     * Lee por reflexión el valor actual de un campo estático de
     * {@link RetrofitProvider}, marcándolo accesible si fuese necesario.
     *
     * @param fieldName nombre exacto del campo estático privado.
     * @return valor leído del campo.
     */
    private static Object readStaticField(@NonNull String fieldName) {
        try {
            Field field = RetrofitProvider.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(null);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("No se pudo leer " + fieldName + " de RetrofitProvider", ex);
        }
    }

    /**
     * Sobrescribe por reflexión un campo estático de {@link RetrofitProvider}.
     *
     * @param fieldName nombre exacto del campo estático privado.
     * @param value nuevo valor a publicar; puede ser {@code null}.
     */
    private static void writeStaticField(@NonNull String fieldName, Object value) {
        try {
            Field field = RetrofitProvider.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(null, value);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("No se pudo escribir " + fieldName + " en RetrofitProvider", ex);
        }
    }
}
