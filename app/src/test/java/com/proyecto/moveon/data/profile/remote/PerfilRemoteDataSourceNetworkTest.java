package com.proyecto.moveon.data.profile.remote;

import static org.junit.Assert.*;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.google.gson.JsonObject;
import com.proyecto.moveon.core.api.ApiErrorType;
import com.proyecto.moveon.core.api.ApiResult;
import com.proyecto.moveon.data.profile.dto.ProfileInfoDto;
import com.proyecto.moveon.testutil.MockServerEnvironment;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;

/**
 * Tests de red de {@link PerfilRemoteDataSource}, cubriendo lectura,
 * actualización parcial, eliminación y subida de foto contra MockWebServer.
 */
@RunWith(RobolectricTestRunner.class)

public class PerfilRemoteDataSourceNetworkTest {

    private MockServerEnvironment environment;
    private PerfilRemoteDataSource dataSource;

    /**
     * Levanta el servidor falso y construye la fuente de datos.
     */
    @Before
    public void setUp() throws Exception {
        environment = new MockServerEnvironment();
        Context context = ApplicationProvider.getApplicationContext();
        dataSource = new PerfilRemoteDataSource(context);
    }

    /**
     * Cierra el servidor falso y restaura el estado de {@code RetrofitProvider}.
     */
    @After
    public void tearDown() throws Exception {
        environment.shutdown();
    }

    /**
     * Verifica que {@code fetchPerfil} ataca {@code GET perfil/informacion}
     * y devuelve el {@link ProfileInfoDto} mapeado.
     */
    @Test
    public void fetchPerfil_async_returnsProfileInfo() throws Exception {
        environment.getServer().enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"nombre_usuario\":\"alice\"}"));

        ApiResult<ProfileInfoDto> result = await(callback -> dataSource.fetchPerfil(callback));

        assertTrue(result.isSuccess());
        assertNotNull(result.data);

        RecordedRequest sent = environment.getServer().takeRequest();
        assertEquals("GET", sent.getMethod());
        assertEquals("/perfil/informacion", sent.getPath());
    }

    /**
     * Verifica que {@code fetchPerfilBlocking} expone la versión bloqueante.
     */
    @Test
    public void fetchPerfilBlocking_returnsProfileInfo() throws Exception {
        environment.getServer().enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"nombre_usuario\":\"bob\"}"));

        ApiResult<ProfileInfoDto> result = dataSource.fetchPerfilBlocking();

        assertTrue(result.isSuccess());
        assertNotNull(result.data);

        RecordedRequest sent = environment.getServer().takeRequest();
        assertEquals("GET", sent.getMethod());
        assertEquals("/perfil/informacion", sent.getPath());
    }

    /**
     * Verifica que {@code fetchPerfil} con respuesta 401 propaga el fallo.
     */
    @Test
    public void fetchPerfil_unauthorized_returnsUnauthorizedFailure() throws Exception {
        environment.getServer().enqueue(new MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody("{}"));

        ApiResult<ProfileInfoDto> result = await(callback -> dataSource.fetchPerfil(callback));

        assertFalse(result.isSuccess());
        assertNotNull(result.error);
        assertEquals(ApiErrorType.UNAUTHORIZED, result.error.getType());
        assertEquals(401, result.error.getHttpCode());
    }

    /**
     * Verifica que {@code patchPerfil} ataca {@code PATCH perfil/actualizar}.
     */
    @Test
    public void patchPerfil_async_returnsBackendMessage() throws Exception {
        environment.getServer().enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"mensaje\":\"perfil actualizado\"}"));

        JsonObject body = new JsonObject();
        body.addProperty("nombre_real", "Alice");

        ApiResult<String> result = await(callback -> dataSource.patchPerfil(body, callback));

        assertTrue(result.isSuccess());
        assertEquals("perfil actualizado", result.data);

        RecordedRequest sent = environment.getServer().takeRequest();
        assertEquals("PATCH", sent.getMethod());
        assertEquals("/perfil/actualizar", sent.getPath());
        assertTrue(sent.getBody().readUtf8().contains("\"nombre_real\":\"Alice\""));
    }

    /**
     * Verifica que {@code patchPerfil} sin {@code mensaje} usa el fallback {@code "OK"}.
     */
    @Test
    public void patchPerfil_responseWithoutMessage_returnsOkFallback() throws Exception {
        environment.getServer().enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{}"));

        ApiResult<String> result = await(callback -> dataSource.patchPerfil(new JsonObject(), callback));

        assertTrue(result.isSuccess());
        assertEquals("OK", result.data);
    }

    /**
     * Verifica que la versión bloqueante de {@code patchPerfil} aplica el mismo contrato.
     */
    @Test
    public void patchPerfilBlocking_returnsBackendMessage() {
        environment.getServer().enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"mensaje\":\"sync update\"}"));

        ApiResult<String> result = dataSource.patchPerfilBlocking(new JsonObject());

        assertTrue(result.isSuccess());
        assertEquals("sync update", result.data);
    }

    /**
     * Verifica que {@code eliminarCuenta} ataca {@code DELETE perfil/borrar}.
     */
    @Test
    public void eliminarCuenta_async_deletesAccount() throws Exception {
        environment.getServer().enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"mensaje\":\"cuenta eliminada\"}"));

        ApiResult<String> result = await(callback -> dataSource.eliminarCuenta(callback));

        assertTrue(result.isSuccess());
        assertEquals("cuenta eliminada", result.data);

        RecordedRequest sent = environment.getServer().takeRequest();
        assertEquals("DELETE", sent.getMethod());
        assertEquals("/perfil/borrar", sent.getPath());
    }

    /**
     * Verifica que {@code eliminarCuenta} sin {@code mensaje} usa fallback {@code "OK"}.
     */
    @Test
    public void eliminarCuenta_responseWithoutMessage_returnsOkFallback() throws Exception {
        environment.getServer().enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{}"));

        ApiResult<String> result = await(callback -> dataSource.eliminarCuenta(callback));

        assertTrue(result.isSuccess());
        assertEquals("OK", result.data);
    }

    /**
     * Verifica que {@code uploadPhotoBlocking} sube un PNG como multipart.
     */
    @Test
    public void uploadPhotoBlocking_pngFile_usesPngMimeAndPostsMultipart() throws Exception {
        environment.getServer().enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"mensaje\":\"foto subida\"}"));

        File tempPng = Files.createTempFile("perfil-", ".png").toFile();
        Files.write(tempPng.toPath(), new byte[]{0, 1, 2, 3});

        try {
            ApiResult<String> result = dataSource.uploadPhotoBlocking(tempPng);

            assertTrue(result.isSuccess());
            assertEquals("foto subida", result.data);

            RecordedRequest sent = environment.getServer().takeRequest();
            assertEquals("POST", sent.getMethod());
            assertEquals("/perfil/foto", sent.getPath());
            String contentType = sent.getHeader("Content-Type");
            assertNotNull(contentType);
            assertTrue("debe usar multipart: " + contentType, contentType.startsWith("multipart/form-data"));
        } finally {
            //noinspection ResultOfMethodCallIgnored
            tempPng.delete();
        }
    }

    /**
     * Verifica que el fallback de MIME para extensiones no reconocidas no rompe.
     */
    @Test
    public void uploadPhotoBlocking_unknownExtension_fallsBackToJpeg() throws Exception {
        environment.getServer().enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"mensaje\":\"ok\"}"));

        File tempBin = Files.createTempFile("rara-", ".bin").toFile();
        Files.write(tempBin.toPath(), new byte[]{9});

        try {
            ApiResult<String> result = dataSource.uploadPhotoBlocking(tempBin);
            assertTrue(result.isSuccess());

            RecordedRequest sent = environment.getServer().takeRequest();
            assertNotNull(sent.getHeader("Content-Type"));
        } finally {
            //noinspection ResultOfMethodCallIgnored
            tempBin.delete();
        }
    }

    /**
     * Verifica que un 413 propaga el fallo de payload demasiado grande.
     */
    @Test
    public void uploadPhotoBlocking_payloadTooLarge_returnsPayloadFailure() throws Exception {
        environment.getServer().enqueue(new MockResponse()
                .setResponseCode(413)
                .setHeader("Content-Type", "application/json")
                .setBody("{}"));

        File tempJpg = Files.createTempFile("foto-", ".jpg").toFile();
        Files.write(tempJpg.toPath(), new byte[]{1});

        try {
            ApiResult<String> result = dataSource.uploadPhotoBlocking(tempJpg);
            assertFalse(result.isSuccess());
            assertNotNull(result.error);
            assertEquals(ApiErrorType.PAYLOAD_TOO_LARGE, result.error.getType());
            assertEquals(413, result.error.getHttpCode());
        } finally {
            //noinspection ResultOfMethodCallIgnored
            tempJpg.delete();
        }
    }

    /**
     * Adapta un callback asíncrono a una espera bloqueante.
     *
     * @param invocation lambda que dispara la operación entregando el callback.
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
            fail("Timeout esperando callback de la fuente remota");
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
         * Lanza la operación inyectando el callback que el helper ha preparado.
         *
         * @param callback callback con el que la fuente entregará su resultado.
         */
        void invoke(PerfilRemoteDataSource.Callback<T> callback);
    }
}
