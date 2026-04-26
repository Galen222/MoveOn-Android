package com.proyecto.moveon.data.remote;

import static org.junit.Assert.*;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.proyecto.moveon.core.api.ApiErrorType;
import com.proyecto.moveon.core.api.ApiResult;
import com.proyecto.moveon.testutil.MockServerEnvironment;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;

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
 * Tests de red de {@link AuthenticatedApiClient} cubriendo los caminos
 * asíncrono y bloqueante de cada verbo HTTP soportado: GET, POST JSON,
 * PATCH JSON, DELETE y POST multipart.
 *
 * <p>Se ejecutan bajo {@link RobolectricTestRunner} para que producción pueda
 * resolver los mensajes localizados de error a través de {@code AppLanguageManager}
 * sin necesidad de emulador.</p>
 */
@RunWith(RobolectricTestRunner.class)

public class AuthenticatedApiClientNetworkTest {

    private MockServerEnvironment environment;
    private AuthenticatedApiClient client;

    /**
     * Levanta el servidor falso y construye el cliente bajo prueba.
     */
    @Before
    public void setUp() throws Exception {
        environment = new MockServerEnvironment();
        Context context = ApplicationProvider.getApplicationContext();
        client = new AuthenticatedApiClient(context);
    }

    /**
     * Cierra el servidor falso y restaura los campos estáticos originales
     * de {@code RetrofitProvider}.
     */
    @After
    public void tearDown() throws Exception {
        environment.shutdown();
    }

    /**
     * Verifica que un GET asíncrono entrega el JSON mapeado al callback.
     */
    @Test
    public void get_successfulResponse_invokesMapperAndDeliversValue() throws Exception {
        environment.getServer().enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"value\":\"hola\"}"));

        ApiResult<String> result = await(callback ->
                client.get("ruta/test", AuthenticatedApiClientNetworkTest::extractValue, callback));

        assertTrue(result.isSuccess());
        assertEquals("hola", result.data);

        RecordedRequest sent = environment.getServer().takeRequest();
        assertEquals("GET", sent.getMethod());
        assertEquals("/ruta/test", sent.getPath());
    }

    /**
     * Verifica que un GET con URL inválida no llega a la red y devuelve fallo local.
     */
    @Test
    public void get_invalidUrl_failsLocallyWithoutHittingNetwork() throws Exception {
        ApiResult<String> result = await(callback ->
                client.get("https://otro/server", AuthenticatedApiClientNetworkTest::extractValue, callback));

        assertFalse(result.isSuccess());
        assertNotNull(result.error);
        assertEquals(ApiErrorType.UNKNOWN, result.error.getType());
        assertEquals(0, environment.getServer().getRequestCount());
    }

    /**
     * Verifica que un GET con cuerpo vacío no provoca timeout ni excepción
     * y produce algún resultado del cliente, sea éxito (con fallback de
     * objeto vacío) o fallo local (parse inválido).
     *
     * <p>El comportamiento exacto depende de la decisión interna del cliente
     * sobre si Content-Length 0 es {@code respuesta inválida} o se aplica el
     * fallback {@code "{}"}; ambas opciones se aceptan aquí porque el contrato
     * que importa es "no se cuelga ni revienta".</p>
     */
    @Test
    public void get_emptyBodyResponse_completesWithoutHanging() throws Exception {
        environment.getServer().enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Length", "0"));

        ApiResult<JsonElement> result = await(callback ->
                client.get("vacio", json -> json, callback));

        assertNotNull("debe entregar un ApiResult al callback", result);
        // Si es éxito, el data puede ser JsonObject{} o JsonNull; si es fallo,
        // debe traer un error no nulo. En ambos casos no hay timeout.
        if (result.isSuccess()) {
            assertNotNull(result.data);
        } else {
            assertNotNull(result.error);
        }
    }

    /**
     * Verifica que un mapper que lanza convierte la respuesta en fallo local.
     */
    @Test
    public void get_mapperThrows_returnsLocalParseFailure() throws Exception {
        environment.getServer().enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"value\":\"hola\"}"));

        ApiResult<String> result = await(callback ->
                client.get("rompe", json -> { throw new IllegalStateException("no parseo"); }, callback));

        assertFalse(result.isSuccess());
        assertNotNull(result.error);
    }

    /**
     * Verifica que un GET bloqueante devuelve el modelo mapeado.
     */
    @Test
    public void getBlocking_successfulResponse_returnsMappedValue() throws Exception {
        environment.getServer().enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"value\":\"sync\"}"));

        ApiResult<String> result = client.getBlocking("ruta/sync", AuthenticatedApiClientNetworkTest::extractValue);

        assertTrue(result.isSuccess());
        assertEquals("sync", result.data);
    }

    /**
     * Verifica que un GET bloqueante con URL inválida falla sin llamar a la red.
     */
    @Test
    public void getBlocking_invalidUrl_failsLocallyWithoutHittingNetwork() {
        ApiResult<String> result = client.getBlocking("//absoluto", AuthenticatedApiClientNetworkTest::extractValue);

        assertFalse(result.isSuccess());
        assertEquals(0, environment.getServer().getRequestCount());
    }

    /**
     * Verifica que un GET bloqueante con respuesta 5xx devuelve fallo de servidor.
     */
    @Test
    public void getBlocking_serverError_deliversServerFailure() {
        environment.getServer().enqueue(new MockResponse()
                .setResponseCode(503)
                .setHeader("Content-Type", "application/json")
                .setBody("{}"));

        ApiResult<String> result = client.getBlocking("ruta/error", AuthenticatedApiClientNetworkTest::extractValue);

        assertFalse(result.isSuccess());
        assertEquals(ApiErrorType.SERVER, result.error.getType());
        assertEquals(503, result.error.getHttpCode());
    }

    /**
     * Verifica que un POST JSON envía el body correcto y entrega la respuesta mapeada.
     */
    @Test
    public void postJson_successfulResponse_sendsBodyAndDeliversValue() throws Exception {
        environment.getServer().enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"value\":\"creado\"}"));

        JsonObject body = new JsonObject();
        body.addProperty("nombre", "ana");

        ApiResult<String> result = await(callback ->
                client.postJson("crear", body, AuthenticatedApiClientNetworkTest::extractValue, callback));

        assertTrue(result.isSuccess());
        assertEquals("creado", result.data);

        RecordedRequest sent = environment.getServer().takeRequest();
        assertEquals("POST", sent.getMethod());
        assertEquals("/crear", sent.getPath());
        assertTrue(sent.getBody().readUtf8().contains("\"nombre\":\"ana\""));
    }

    /**
     * Verifica que un POST JSON con URL inválida no llega a la red.
     */
    @Test
    public void postJson_invalidUrl_failsWithoutHittingNetwork() throws Exception {
        JsonObject body = new JsonObject();
        ApiResult<String> result = await(callback ->
                client.postJson("", body, AuthenticatedApiClientNetworkTest::extractValue, callback));

        assertFalse(result.isSuccess());
        assertEquals(0, environment.getServer().getRequestCount());
    }

    /**
     * Verifica que el POST JSON bloqueante respeta el contrato de éxito.
     */
    @Test
    public void postJsonBlocking_successfulResponse_returnsValue() {
        environment.getServer().enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"value\":\"sync-post\"}"));

        ApiResult<String> result = client.postJsonBlocking(
                "crear-sync", new JsonObject(), AuthenticatedApiClientNetworkTest::extractValue);

        assertTrue(result.isSuccess());
        assertEquals("sync-post", result.data);
    }

    /**
     * Verifica que el POST JSON bloqueante con URL inválida falla sin red.
     */
    @Test
    public void postJsonBlocking_invalidUrl_failsLocally() {
        ApiResult<String> result = client.postJsonBlocking(
                "https://abs.com/r", new JsonObject(), AuthenticatedApiClientNetworkTest::extractValue);

        assertFalse(result.isSuccess());
        assertEquals(0, environment.getServer().getRequestCount());
    }

    /**
     * Verifica que un PATCH JSON envía body y devuelve datos mapeados.
     */
    @Test
    public void patchJson_successfulResponse_sendsPatchAndReturnsValue() throws Exception {
        environment.getServer().enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"value\":\"actualizado\"}"));

        JsonObject body = new JsonObject();
        body.addProperty("nombre_real", "Ana Lopez");

        ApiResult<String> result = await(callback ->
                client.patchJson("perfil", body, AuthenticatedApiClientNetworkTest::extractValue, callback));

        assertTrue(result.isSuccess());
        assertEquals("actualizado", result.data);

        RecordedRequest sent = environment.getServer().takeRequest();
        assertEquals("PATCH", sent.getMethod());
        assertEquals("/perfil", sent.getPath());
        assertTrue(sent.getBody().readUtf8().contains("\"nombre_real\":\"Ana Lopez\""));
    }

    /**
     * Verifica que el PATCH JSON con URL inválida falla sin red.
     */
    @Test
    public void patchJson_invalidUrl_failsWithoutHittingNetwork() throws Exception {
        ApiResult<String> result = await(callback ->
                client.patchJson("https://abs.com", new JsonObject(),
                        AuthenticatedApiClientNetworkTest::extractValue, callback));

        assertFalse(result.isSuccess());
        assertEquals(0, environment.getServer().getRequestCount());
    }

    /**
     * Verifica el camino feliz del PATCH JSON bloqueante.
     */
    @Test
    public void patchJsonBlocking_successfulResponse_returnsValue() {
        environment.getServer().enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"value\":\"patched\"}"));

        ApiResult<String> result = client.patchJsonBlocking(
                "perfil", new JsonObject(), AuthenticatedApiClientNetworkTest::extractValue);

        assertTrue(result.isSuccess());
        assertEquals("patched", result.data);
    }

    /**
     * Verifica que el PATCH JSON bloqueante con URL inválida falla sin red.
     */
    @Test
    public void patchJsonBlocking_invalidUrl_failsLocally() {
        ApiResult<String> result = client.patchJsonBlocking(
                "  ", new JsonObject(), AuthenticatedApiClientNetworkTest::extractValue);

        assertFalse(result.isSuccess());
        assertEquals(0, environment.getServer().getRequestCount());
    }

    /**
     * Verifica que un DELETE asíncrono propaga la respuesta mapeada.
     */
    @Test
    public void delete_successfulResponse_returnsMappedValue() throws Exception {
        environment.getServer().enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"value\":\"borrado\"}"));

        ApiResult<String> result = await(callback ->
                client.delete("recursos/42", AuthenticatedApiClientNetworkTest::extractValue, callback));

        assertTrue(result.isSuccess());
        assertEquals("borrado", result.data);

        RecordedRequest sent = environment.getServer().takeRequest();
        assertEquals("DELETE", sent.getMethod());
        assertEquals("/recursos/42", sent.getPath());
    }

    /**
     * Verifica que un DELETE 404 se traduce a {@link ApiErrorType#NOT_FOUND}.
     */
    @Test
    public void delete_notFound_returnsNotFoundFailure() throws Exception {
        environment.getServer().enqueue(new MockResponse()
                .setResponseCode(404)
                .setHeader("Content-Type", "application/json")
                .setBody("{}"));

        ApiResult<String> result = await(callback ->
                client.delete("recursos/no-existe", AuthenticatedApiClientNetworkTest::extractValue, callback));

        assertFalse(result.isSuccess());
        assertEquals(ApiErrorType.NOT_FOUND, result.error.getType());
        assertEquals(404, result.error.getHttpCode());
    }

    /**
     * Verifica que un DELETE con URL inválida falla local sin tocar red.
     */
    @Test
    public void delete_invalidUrl_failsWithoutHittingNetwork() throws Exception {
        ApiResult<String> result = await(callback ->
                client.delete("", AuthenticatedApiClientNetworkTest::extractValue, callback));

        assertFalse(result.isSuccess());
        assertEquals(0, environment.getServer().getRequestCount());
    }

    /**
     * Verifica el camino feliz del DELETE bloqueante.
     */
    @Test
    public void deleteBlocking_successfulResponse_returnsValue() {
        environment.getServer().enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"value\":\"borrado-sync\"}"));

        ApiResult<String> result = client.deleteBlocking(
                "recursos/1", AuthenticatedApiClientNetworkTest::extractValue);

        assertTrue(result.isSuccess());
        assertEquals("borrado-sync", result.data);
    }

    /**
     * Verifica que el DELETE bloqueante con URL inválida falla local.
     */
    @Test
    public void deleteBlocking_invalidUrl_failsLocally() {
        ApiResult<String> result = client.deleteBlocking(
                "https://otro.com", AuthenticatedApiClientNetworkTest::extractValue);

        assertFalse(result.isSuccess());
        assertEquals(0, environment.getServer().getRequestCount());
    }

    /**
     * Verifica que un POST multipart envía el archivo y entrega la respuesta mapeada.
     */
    @Test
    public void postMultipart_successfulResponse_uploadsFileAndReturnsValue() throws Exception {
        environment.getServer().enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"value\":\"subida-ok\"}"));

        MultipartBody.Part filePart = MultipartBody.Part.createFormData(
                "file", "foto.png",
                RequestBody.create("imagen-de-prueba".getBytes(), MediaType.parse("image/png"))
        );

        ApiResult<String> result = await(callback ->
                client.postMultipart("perfil/foto", filePart,
                        AuthenticatedApiClientNetworkTest::extractValue, callback));

        assertTrue(result.isSuccess());
        assertEquals("subida-ok", result.data);

        RecordedRequest sent = environment.getServer().takeRequest();
        assertEquals("POST", sent.getMethod());
        assertEquals("/perfil/foto", sent.getPath());
        String contentType = sent.getHeader("Content-Type");
        assertNotNull(contentType);
        assertTrue("debe usar multipart: " + contentType, contentType.startsWith("multipart/form-data"));
    }

    /**
     * Verifica que el multipart con URL inválida falla local sin red.
     */
    @Test
    public void postMultipart_invalidUrl_failsWithoutHittingNetwork() throws Exception {
        MultipartBody.Part filePart = MultipartBody.Part.createFormData(
                "file", "x.png",
                RequestBody.create(new byte[]{1, 2}, MediaType.parse("image/png"))
        );

        ApiResult<String> result = await(callback ->
                client.postMultipart("https://otro/server", filePart,
                        AuthenticatedApiClientNetworkTest::extractValue, callback));

        assertFalse(result.isSuccess());
        assertEquals(0, environment.getServer().getRequestCount());
    }

    /**
     * Verifica el camino feliz de la versión bloqueante de multipart.
     */
    @Test
    public void postMultipartBlocking_successfulResponse_returnsValue() {
        environment.getServer().enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"value\":\"sync-upload\"}"));

        MultipartBody.Part filePart = MultipartBody.Part.createFormData(
                "file", "x.png",
                RequestBody.create(new byte[]{1, 2}, MediaType.parse("image/png"))
        );

        ApiResult<String> result = client.postMultipartBlocking(
                "perfil/foto-sync", filePart, AuthenticatedApiClientNetworkTest::extractValue);

        assertTrue(result.isSuccess());
        assertEquals("sync-upload", result.data);
    }

    /**
     * Verifica que la versión bloqueante de multipart con URL inválida falla sin red.
     */
    @Test
    public void postMultipartBlocking_invalidUrl_failsLocally() {
        MultipartBody.Part filePart = MultipartBody.Part.createFormData(
                "file", "x.png",
                RequestBody.create(new byte[]{1}, MediaType.parse("image/png"))
        );

        ApiResult<String> result = client.postMultipartBlocking(
                "//absoluto", filePart, AuthenticatedApiClientNetworkTest::extractValue);

        assertFalse(result.isSuccess());
        assertEquals(0, environment.getServer().getRequestCount());
    }

    /**
     * Verifica que cancelAll cancela las llamadas en vuelo del cliente.
     */
    @Test
    public void cancelAll_inFlightCall_clearsTrackedCallsAndCancels() throws Exception {
        environment.getServer().enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"value\":\"slow\"}")
                .setBodyDelay(500, TimeUnit.MILLISECONDS));

        AtomicReference<ApiResult<String>> received = new AtomicReference<>(null);
        client.get("ruta/slow", AuthenticatedApiClientNetworkTest::extractValue, received::set);

        client.cancelAll();

        Thread.sleep(800);
        ApiResult<String> result = received.get();
        if (result != null) {
            assertFalse("Si llega resultado tras cancelar, no debe ser éxito", result.isSuccess());
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Mapper de prueba que extrae el campo {@code value} del JSON recibido.
     *
     * @param json elemento JSON ya parseado.
     * @return contenido del campo {@code value} cuando exista; {@code null} si falta.
     */
    private static String extractValue(JsonElement json) {
        if (json == null || !json.isJsonObject()) return null;
        JsonObject obj = json.getAsJsonObject();
        if (!obj.has("value")) return null;
        return obj.get("value").getAsString();
    }

    /**
     * Adapta un callback asíncrono del cliente a una llamada bloqueante.
     *
     * @param invocation lambda que dispara la operación.
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
            fail("Timeout esperando callback del cliente");
        }
        return ref.get();
    }

    /**
     * Lambda que recibe el callback ya preparado por la espera bloqueante.
     *
     * @param <T> tipo del payload entregado al callback.
     */
    private interface CallbackInvocation<T> {
        /**
         * Lanza la operación inyectando el callback que el helper ha preparado.
         *
         * @param callback callback con el que el cliente entregará su resultado.
         */
        void invoke(AuthenticatedApiClient.Callback<T> callback);
    }
}
