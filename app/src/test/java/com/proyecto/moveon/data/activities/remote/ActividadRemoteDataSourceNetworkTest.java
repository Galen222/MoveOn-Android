package com.proyecto.moveon.data.activities.remote;

import static org.junit.Assert.*;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.google.gson.JsonObject;
import com.proyecto.moveon.core.api.ApiErrorType;
import com.proyecto.moveon.core.api.ApiResult;
import com.proyecto.moveon.data.activities.dto.ActividadResponseDto;
import com.proyecto.moveon.data.activities.dto.BorrarActividadResponseDto;
import com.proyecto.moveon.testutil.MockServerEnvironment;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;

/**
 * Tests de red de {@link ActividadRemoteDataSource} cubriendo creación,
 * borrado y descarga paginada de actividades contra un {@code MockWebServer}.
 *
 * <p>Se ejecutan bajo {@link RobolectricTestRunner} para que los caminos
 * de error que pasan por {@code ApiErrorParser.fromHttp()} dispongan de
 * recursos de string reales del módulo.</p>
 */
@RunWith(RobolectricTestRunner.class)

public class ActividadRemoteDataSourceNetworkTest {

    private MockServerEnvironment environment;
    private ActividadRemoteDataSource dataSource;

    /**
     * Levanta el servidor falso y construye la fuente de datos antes de cada caso.
     */
    @Before
    public void setUp() throws Exception {
        environment = new MockServerEnvironment();
        Context context = ApplicationProvider.getApplicationContext();
        dataSource = new ActividadRemoteDataSource(context);
    }

    /**
     * Cierra el servidor falso y restaura el estado estático de {@code RetrofitProvider}.
     */
    @After
    public void tearDown() throws Exception {
        environment.shutdown();
    }

    /**
     * Verifica que {@code createActividadBlocking} envía POST y devuelve la actividad creada.
     */
    @Test
    public void createActividadBlocking_successfulResponse_returnsCreatedActivity() throws Exception {
        environment.getServer().enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"id\":42,\"tipo\":\"Correr\",\"distancia\":1500}"));

        JsonObject body = new JsonObject();
        body.addProperty("tipo", "Correr");

        ApiResult<ActividadResponseDto> result = dataSource.createActividadBlocking(body);

        assertTrue(result.isSuccess());
        assertNotNull(result.data);
        assertEquals(42, result.data.id);
        assertEquals("Correr", result.data.tipo);
        assertEquals(1500, result.data.distancia);

        RecordedRequest sent = environment.getServer().takeRequest();
        assertEquals("POST", sent.getMethod());
        assertEquals("/actividad/guardar", sent.getPath());
    }

    /**
     * Verifica que un error 422 al crear se traduce a {@link ApiErrorType#VALIDATION}.
     */
    @Test
    public void createActividadBlocking_validationError_returnsValidationFailure() {
        environment.getServer().enqueue(new MockResponse()
                .setResponseCode(422)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"error_code\":\"distance_must_be_positive\"}"));

        ApiResult<ActividadResponseDto> result =
                dataSource.createActividadBlocking(new JsonObject());

        assertFalse(result.isSuccess());
        assertNotNull(result.error);
        assertEquals(ApiErrorType.VALIDATION, result.error.getType());
        assertEquals(422, result.error.getHttpCode());
    }

    /**
     * Verifica que {@code createActividad} asíncrono entrega la actividad al callback.
     */
    @Test
    public void createActividad_async_deliversActivityToCallback() throws Exception {
        environment.getServer().enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"id\":7,\"tipo\":\"Caminar\"}"));

        ApiResult<ActividadResponseDto> result = await(callback ->
                dataSource.createActividad(new JsonObject(), callback));

        assertTrue(result.isSuccess());
        assertEquals(7, result.data.id);
        assertEquals("Caminar", result.data.tipo);

        RecordedRequest sent = environment.getServer().takeRequest();
        assertEquals("/actividad/guardar", sent.getPath());
    }

    /**
     * Verifica que {@code deleteActividad} usa DELETE con el id remoto.
     */
    @Test
    public void deleteActividad_async_deletesByRemoteIdAndReturnsResponse() throws Exception {
        environment.getServer().enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"estatus\":\"ok\",\"mensaje\":\"borrada\",\"nuevo_total_puntos\":5}"));

        ApiResult<BorrarActividadResponseDto> result = await(callback ->
                dataSource.deleteActividad(99, callback));

        assertTrue(result.isSuccess());
        assertEquals("ok", result.data.estatus);
        assertEquals("borrada", result.data.mensaje);
        assertEquals(5, result.data.nuevoTotalPuntos);

        RecordedRequest sent = environment.getServer().takeRequest();
        assertEquals("DELETE", sent.getMethod());
        assertEquals("/actividad/borrar/99", sent.getPath());
    }

    /**
     * Verifica que un borrado contra un id inexistente devuelve {@link ApiErrorType#NOT_FOUND}.
     */
    @Test
    public void deleteActividad_notFound_returnsNotFoundFailure() throws Exception {
        environment.getServer().enqueue(new MockResponse()
                .setResponseCode(404)
                .setHeader("Content-Type", "application/json")
                .setBody("{}"));

        ApiResult<BorrarActividadResponseDto> result = await(callback ->
                dataSource.deleteActividad(123, callback));

        assertFalse(result.isSuccess());
        assertNotNull(result.error);
        assertEquals(ApiErrorType.NOT_FOUND, result.error.getType());
    }

    /**
     * Verifica que la descarga paginada bloqueante itera todas las páginas.
     */
    @Test
    public void fetchAllActividadesBlocking_paginatesUntilBackendExhausts() throws Exception {
        environment.getServer().enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"items\":[{\"id\":1},{\"id\":2}],\"total\":3,\"skip\":0,\"limit\":100,\"has_more\":true}"));
        environment.getServer().enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"items\":[{\"id\":3}],\"total\":3,\"skip\":100,\"limit\":100,\"has_more\":false}"));

        ApiResult<List<ActividadResponseDto>> result = dataSource.fetchAllActividadesBlocking();

        assertTrue(result.isSuccess());
        assertNotNull(result.data);
        assertEquals(3, result.data.size());
        assertEquals(1, result.data.get(0).id);
        assertEquals(2, result.data.get(1).id);
        assertEquals(3, result.data.get(2).id);

        RecordedRequest first = environment.getServer().takeRequest();
        assertEquals("/actividad/obtener_todas?skip=0&limit=100", first.getPath());
        RecordedRequest second = environment.getServer().takeRequest();
        assertEquals("/actividad/obtener_todas?skip=100&limit=100", second.getPath());
    }

    /**
     * Verifica que una primera página vacía sin {@code has_more} devuelve lista vacía.
     */
    @Test
    public void fetchAllActividadesBlocking_emptyFirstPage_returnsEmptyList() {
        environment.getServer().enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"items\":[],\"total\":0,\"skip\":0,\"limit\":100,\"has_more\":false}"));

        ApiResult<List<ActividadResponseDto>> result = dataSource.fetchAllActividadesBlocking();

        assertTrue(result.isSuccess());
        assertNotNull(result.data);
        assertTrue(result.data.isEmpty());
        assertEquals(1, environment.getServer().getRequestCount());
    }

    /**
     * Verifica que un fallo HTTP en cualquier página interrumpe la paginación.
     */
    @Test
    public void fetchAllActividadesBlocking_httpError_propagatesFailure() {
        environment.getServer().enqueue(new MockResponse()
                .setResponseCode(500)
                .setHeader("Content-Type", "application/json")
                .setBody("{}"));

        ApiResult<List<ActividadResponseDto>> result = dataSource.fetchAllActividadesBlocking();

        assertFalse(result.isSuccess());
        assertNotNull(result.error);
        assertEquals(ApiErrorType.SERVER, result.error.getType());
        assertEquals(500, result.error.getHttpCode());
    }

    /**
     * Adapta un callback asíncrono a una espera bloqueante.
     *
     * @param invocation lambda que dispara la operación entregando el callback.
     * @param <T> tipo del payload del {@link ApiResult}.
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
            fail("Timeout esperando callback de la fuente de datos");
        }
        return ref.get();
    }

    /**
     * Lambda compatible con el patrón {@code dataSource.metodo(..., callback)}.
     *
     * @param <T> tipo del payload entregado al callback.
     */
    private interface CallbackInvocation<T> {
        /**
         * Lanza la operación inyectando el callback preparado por el helper.
         *
         * @param callback callback con el que la fuente entregará su resultado.
         */
        void invoke(ActividadRemoteDataSource.Callback<T> callback);
    }
}
