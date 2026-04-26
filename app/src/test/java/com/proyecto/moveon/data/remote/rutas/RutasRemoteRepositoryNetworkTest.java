package com.proyecto.moveon.data.remote.rutas;

import static org.junit.Assert.*;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.proyecto.moveon.core.api.ApiErrorType;
import com.proyecto.moveon.core.api.ApiResult;
import com.proyecto.moveon.testutil.MockServerEnvironment;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;

/**
 * Tests de red de {@link RutasRemoteRepository} cubriendo el listado y la
 * subida de rutas pendientes contra un {@code MockWebServer}.
 */
@RunWith(RobolectricTestRunner.class)

public class RutasRemoteRepositoryNetworkTest {

    private MockServerEnvironment environment;
    private RutasRemoteRepository repository;

    /**
     * Levanta el servidor falso y construye el repositorio antes de cada caso.
     */
    @Before
    public void setUp() throws Exception {
        environment = new MockServerEnvironment();
        Context context = ApplicationProvider.getApplicationContext();
        repository = new RutasRemoteRepository(context);
    }

    /**
     * Cierra el servidor falso y restaura el estado de {@code RetrofitProvider}.
     */
    @After
    public void tearDown() throws Exception {
        environment.shutdown();
    }

    /**
     * Verifica que {@code obtenerRutas} extrae el array {@code rutas}.
     */
    @Test
    public void obtenerRutas_responseWithRutasArray_returnsExtractedArray() throws Exception {
        environment.getServer().enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"rutas\":[{\"id\":1},{\"id\":2}]}"));

        ApiResult<JsonArray> result = await(callback -> repository.obtenerRutas(callback));

        assertTrue(result.isSuccess());
        assertNotNull(result.data);
        assertEquals(2, result.data.size());

        RecordedRequest sent = environment.getServer().takeRequest();
        assertEquals("GET", sent.getMethod());
        assertEquals("/rutas", sent.getPath());
    }

    /**
     * Verifica que un payload sin la clave {@code rutas} cae al fallback de array vacío.
     */
    @Test
    public void obtenerRutas_responseWithoutRutasKey_fallsBackToEmptyArray() throws Exception {
        environment.getServer().enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"otra\":42}"));

        ApiResult<JsonArray> result = await(callback -> repository.obtenerRutas(callback));

        assertTrue(result.isSuccess());
        assertNotNull(result.data);
        assertEquals(0, result.data.size());
    }

    /**
     * Verifica que un fallo HTTP en {@code obtenerRutas} se propaga.
     */
    @Test
    public void obtenerRutas_httpError_propagatesFailure() throws Exception {
        environment.getServer().enqueue(new MockResponse()
                .setResponseCode(403)
                .setHeader("Content-Type", "application/json")
                .setBody("{}"));

        ApiResult<JsonArray> result = await(callback -> repository.obtenerRutas(callback));

        assertFalse(result.isSuccess());
        assertNotNull(result.error);
        assertEquals(ApiErrorType.FORBIDDEN, result.error.getType());
        assertEquals(403, result.error.getHttpCode());
    }

    /**
     * Verifica que {@code subirRutaPendiente} envía POST con el cuerpo recibido.
     */
    @Test
    public void subirRutaPendiente_successfulResponse_returnsResponseObject() throws Exception {
        environment.getServer().enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"id\":99,\"estado\":\"sincronizada\"}"));

        JsonObject payload = new JsonObject();
        payload.addProperty("nombre", "ruta1");

        ApiResult<JsonObject> result = await(callback ->
                repository.subirRutaPendiente(payload, callback));

        assertTrue(result.isSuccess());
        assertNotNull(result.data);
        assertEquals(99, result.data.get("id").getAsInt());

        RecordedRequest sent = environment.getServer().takeRequest();
        assertEquals("POST", sent.getMethod());
        assertEquals("/rutas", sent.getPath());
        assertTrue(sent.getBody().readUtf8().contains("\"nombre\":\"ruta1\""));
    }

    /**
     * Verifica que una respuesta no-objeto cae al fallback de JsonObject vacío.
     */
    @Test
    public void subirRutaPendiente_nonObjectResponse_fallsBackToEmptyObject() throws Exception {
        environment.getServer().enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("[1,2,3]"));

        ApiResult<JsonObject> result = await(callback ->
                repository.subirRutaPendiente(new JsonObject(), callback));

        assertTrue(result.isSuccess());
        assertNotNull(result.data);
        assertEquals(0, result.data.size());
    }

    /**
     * Verifica que {@code cancelAll} delega en el cliente subyacente sin lanzar.
     */
    @Test
    public void cancelAll_doesNotThrowOnIdleRepository() {
        repository.cancelAll();
        assertTrue(true);
    }

    /**
     * Adapta un callback asíncrono a una espera bloqueante.
     *
     * @param invocation lambda que dispara la operación.
     * @param <T> tipo del payload entregado en el {@link ApiResult}.
     * @return resultado entregado al callback antes del timeout.
     */
    private static <T> ApiResult<T> await(CallbackInvocation<T> invocation) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ApiResult<T>> ref = new AtomicReference<>();

        invocation.invoke(result -> {
            ref.set(result);
            latch.countDown();
        });

        if (!latch.await(10, TimeUnit.SECONDS)) {
            fail("Timeout esperando callback del repositorio de rutas");
        }
        return ref.get();
    }

    /**
     * Lambda compatible con el patrón {@code repo.metodo(..., callback)}.
     *
     * @param <T> tipo del payload entregado al callback.
     */
    private interface CallbackInvocation<T> {
        /**
         * Lanza la operación inyectando el callback que el helper ha preparado.
         *
         * @param callback callback con el que el repositorio entregará su resultado.
         */
        void invoke(RutasRemoteRepository.Callback<T> callback);
    }
}
