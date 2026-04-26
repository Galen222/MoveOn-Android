package com.proyecto.moveon.data.session;

import static org.junit.Assert.*;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.proyecto.moveon.core.api.ApiErrorType;
import com.proyecto.moveon.core.api.ApiResult;
import com.proyecto.moveon.domain.auth.LoginSession;
import com.proyecto.moveon.domain.auth.RegisterInput;
import com.proyecto.moveon.domain.auth.SocialRegisterInput;
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
 * Tests de red de {@link AuthRepository} ejercitando login, registro,
 * refresh, logout y recuperación de contraseña contra un {@code MockWebServer}.
 *
 * <p>Se ejecutan bajo {@link RobolectricTestRunner} para que producción pueda
 * resolver mensajes localizados desde {@code Context#getString} mientras
 * trabaja contra el servidor falso. {@link MockServerEnvironment} inyecta por
 * reflexión los clientes Retrofit en {@code RetrofitProvider} sin tocar
 * ninguna clase de producción.</p>
 */
@RunWith(RobolectricTestRunner.class)

public class AuthRepositoryNetworkTest {

    private MockServerEnvironment environment;
    private AuthRepository repository;

    /**
     * Levanta el servidor falso y construye el repositorio bajo prueba antes
     * de cada caso.
     */
    @Before
    public void setUp() throws Exception {
        environment = new MockServerEnvironment();
        Context context = ApplicationProvider.getApplicationContext();
        repository = new AuthRepository(context);
    }

    /**
     * Cierra el servidor falso y restaura el estado estático de
     * {@code RetrofitProvider} después de cada caso.
     */
    @After
    public void tearDown() throws Exception {
        environment.shutdown();
    }

    /**
     * Verifica que un login con respuesta 200 y JSON completo entrega un
     * {@link LoginSession} de éxito al callback con los tres campos del backend.
     */
    @Test
    public void login_successfulResponse_deliversLoginSession() throws Exception {
        environment.getServer().enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"nombre_usuario\":\"alice\",\"token_acceso\":\"a-token\",\"refresh_token\":\"r-token\"}"));

        ApiResult<LoginSession> result = await(callback -> repository.login("alice", "secret", callback));

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertNotNull(result.data);
        assertEquals("alice", result.data.nombreUsuario);
        assertEquals("a-token", result.data.tokenAcceso);
        assertEquals("r-token", result.data.refreshToken);

        RecordedRequest sent = environment.getServer().takeRequest();
        assertEquals("POST", sent.getMethod());
        assertEquals("/login", sent.getPath());
        String body = sent.getBody().readUtf8();
        assertTrue("body debe contener identificador: " + body, body.contains("\"identificador\":\"alice\""));
        assertTrue("body debe contener password: " + body, body.contains("\"password\":\"secret\""));
    }

    /**
     * Verifica que un login con código 401 produce un fallo de tipo
     * {@link ApiErrorType#UNAUTHORIZED} con el código HTTP preservado.
     */
    @Test
    public void login_unauthorizedResponse_deliversUnauthorizedFailure() throws Exception {
        environment.getServer().enqueue(new MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"detail\":\"credenciales invalidas\"}"));

        ApiResult<LoginSession> result = await(callback -> repository.login("alice", "wrong", callback));

        assertFalse(result.isSuccess());
        assertNotNull(result.error);
        assertEquals(ApiErrorType.UNAUTHORIZED, result.error.getType());
        assertEquals(401, result.error.getHttpCode());
    }

    /**
     * Verifica que una respuesta 200 sin tokens se trata como respuesta inválida.
     */
    @Test
    public void login_emptyBody_deliversLocalFailure() throws Exception {
        environment.getServer().enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{}"));

        ApiResult<LoginSession> result = await(callback -> repository.login("alice", "x", callback));

        assertFalse(result.isSuccess());
        assertNotNull(result.error);
        assertEquals(ApiErrorType.UNKNOWN, result.error.getType());
        assertEquals(0, result.error.getHttpCode());
    }

    /**
     * Verifica que una respuesta 200 con el access token vacío produce el
     * mismo fallo local que la respuesta totalmente vacía.
     */
    @Test
    public void login_blankAccessToken_deliversLocalFailure() throws Exception {
        environment.getServer().enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"nombre_usuario\":\"alice\",\"token_acceso\":\"   \",\"refresh_token\":\"r\"}"));

        ApiResult<LoginSession> result = await(callback -> repository.login("alice", "x", callback));

        assertFalse(result.isSuccess());
        assertNotNull(result.error);
        assertEquals(ApiErrorType.UNKNOWN, result.error.getType());
    }

    /**
     * Verifica que un login social satisfactorio devuelve la sesión y envía
     * provider y token al endpoint correcto.
     */
    @Test
    public void loginSocial_successfulResponse_sendsProviderAndToken() throws Exception {
        environment.getServer().enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"nombre_usuario\":\"bob\",\"token_acceso\":\"acc\",\"refresh_token\":\"ref\"}"));

        ApiResult<LoginSession> result = await(callback -> repository.loginSocial("google", "google-token", callback));

        assertTrue(result.isSuccess());
        assertNotNull(result.data);
        assertEquals("bob", result.data.nombreUsuario);

        RecordedRequest sent = environment.getServer().takeRequest();
        assertEquals("POST", sent.getMethod());
        assertEquals("/login/social", sent.getPath());
        String body = sent.getBody().readUtf8();
        assertTrue(body.contains("\"provider\":\"google\""));
        assertTrue(body.contains("\"token\":\"google-token\""));
    }

    /**
     * Verifica que un login social con 4xx propaga el fallo HTTP.
     */
    @Test
    public void loginSocial_failureResponse_deliversHttpError() throws Exception {
        environment.getServer().enqueue(new MockResponse()
                .setResponseCode(403)
                .setHeader("Content-Type", "application/json")
                .setBody("{}"));

        ApiResult<LoginSession> result = await(callback -> repository.loginSocial("google", "bad", callback));

        assertFalse(result.isSuccess());
        assertNotNull(result.error);
        assertEquals(ApiErrorType.FORBIDDEN, result.error.getType());
        assertEquals(403, result.error.getHttpCode());
    }

    /**
     * Verifica que un registro clásico exitoso devuelve el mensaje del backend
     * y envía todos los campos requeridos al endpoint correcto.
     */
    @Test
    public void register_successfulResponse_returnsServerMessage() throws Exception {
        environment.getServer().enqueue(new MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"mensaje\":\"alta correcta\"}"));

        RegisterInput input = new RegisterInput(
                "user1", "user@mail.com", "Password1",
                "1990-01-01", true, "2024-01-01T00:00:00Z", "v1"
        );

        ApiResult<String> result = await(callback -> repository.register(input, callback));

        assertTrue(result.isSuccess());
        assertEquals("alta correcta", result.data);

        RecordedRequest sent = environment.getServer().takeRequest();
        assertEquals("POST", sent.getMethod());
        assertEquals("/registro", sent.getPath());
        String body = sent.getBody().readUtf8();
        assertTrue(body.contains("\"nombre_usuario\":\"user1\""));
        assertTrue(body.contains("\"email\":\"user@mail.com\""));
        assertTrue(body.contains("\"acepta_terminos\":true"));
        assertTrue(body.contains("\"version_terminos\":\"v1\""));
    }

    /**
     * Verifica que un registro exitoso sin {@code mensaje} en el body utiliza
     * un texto local por defecto y mantiene el resultado como éxito.
     */
    @Test
    public void register_successfulWithoutMessage_returnsLocalDefault() throws Exception {
        environment.getServer().enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{}"));

        RegisterInput input = new RegisterInput(
                "user2", "user2@mail.com", "Password1",
                "1990-01-01", true, "2024-01-01T00:00:00Z", "v1"
        );

        ApiResult<String> result = await(callback -> repository.register(input, callback));

        assertTrue(result.isSuccess());
        assertNotNull(result.data);
        assertFalse("Debe devolver un mensaje local", result.data.isEmpty());
    }

    /**
     * Verifica que un registro con 422 devuelve {@link ApiErrorType#VALIDATION}.
     */
    @Test
    public void register_validationResponse_deliversValidationError() throws Exception {
        environment.getServer().enqueue(new MockResponse()
                .setResponseCode(422)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"detail\":[{\"columna\":\"email\",\"error_code\":\"email_format_invalid\"}]}"));

        RegisterInput input = new RegisterInput(
                "user3", "no-email", "Password1",
                "1990-01-01", true, "2024-01-01T00:00:00Z", "v1"
        );

        ApiResult<String> result = await(callback -> repository.register(input, callback));

        assertFalse(result.isSuccess());
        assertEquals(ApiErrorType.VALIDATION, result.error.getType());
        assertEquals(422, result.error.getHttpCode());
    }

    /**
     * Verifica que un registro social satisfactorio devuelve la sesión final.
     */
    @Test
    public void registerSocial_successfulResponse_returnsLoginSession() throws Exception {
        environment.getServer().enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"nombre_usuario\":\"carla\",\"token_acceso\":\"a\",\"refresh_token\":\"r\"}"));

        SocialRegisterInput input = new SocialRegisterInput(
                "google", "g-token", "carla",
                "1995-05-05", true, "2024-01-01T00:00:00Z", "v1"
        );

        ApiResult<LoginSession> result = await(callback -> repository.registerSocial(input, callback));

        assertTrue(result.isSuccess());
        assertEquals("carla", result.data.nombreUsuario);
        assertEquals("a", result.data.tokenAcceso);
        assertEquals("r", result.data.refreshToken);

        RecordedRequest sent = environment.getServer().takeRequest();
        assertEquals("POST", sent.getMethod());
        assertEquals("/registro/social", sent.getPath());
    }

    /**
     * Verifica que un registro social con tokens en blanco devuelve fallo local.
     */
    @Test
    public void registerSocial_blankRefreshToken_deliversLocalFailure() throws Exception {
        environment.getServer().enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"nombre_usuario\":\"carla\",\"token_acceso\":\"a\",\"refresh_token\":\" \"}"));

        SocialRegisterInput input = new SocialRegisterInput(
                "google", "g", "carla",
                "1995-05-05", true, "2024-01-01T00:00:00Z", "v1"
        );

        ApiResult<LoginSession> result = await(callback -> repository.registerSocial(input, callback));

        assertFalse(result.isSuccess());
        assertNotNull(result.error);
        assertEquals(ApiErrorType.UNKNOWN, result.error.getType());
    }

    /**
     * Verifica que un refresh exitoso entrega la nueva sesión completa.
     */
    @Test
    public void refreshSession_successfulResponse_returnsRotatedTokens() throws Exception {
        environment.getServer().enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"nombre_usuario\":\"alice\",\"token_acceso\":\"new-a\",\"refresh_token\":\"new-r\"}"));

        ApiResult<LoginSession> result = await(callback -> repository.refreshSession("old-r", callback));

        assertTrue(result.isSuccess());
        assertEquals("new-a", result.data.tokenAcceso);
        assertEquals("new-r", result.data.refreshToken);

        RecordedRequest sent = environment.getServer().takeRequest();
        assertEquals("POST", sent.getMethod());
        assertEquals("/token/refresh", sent.getPath());
        String body = sent.getBody().readUtf8();
        assertTrue("debe enviar el refresh token recibido: " + body,
                body.contains("\"refresh_token\":\"old-r\""));
    }

    /**
     * Verifica que un refresh con 401 propaga el fallo de autorización.
     */
    @Test
    public void refreshSession_unauthorized_deliversUnauthorizedFailure() throws Exception {
        environment.getServer().enqueue(new MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"error_code\":\"refresh_token_expired\"}"));

        ApiResult<LoginSession> result = await(callback -> repository.refreshSession("old-r", callback));

        assertFalse(result.isSuccess());
        assertEquals(ApiErrorType.UNAUTHORIZED, result.error.getType());
        assertEquals(401, result.error.getHttpCode());
    }

    /**
     * Verifica que el logout exitoso devuelve el mensaje del backend.
     */
    @Test
    public void logout_successfulResponse_returnsServerMessage() throws Exception {
        environment.getServer().enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"mensaje\":\"sesion cerrada\"}"));

        ApiResult<String> result = await(callback -> repository.logout("r-token", callback));

        assertTrue(result.isSuccess());
        assertEquals("sesion cerrada", result.data);

        RecordedRequest sent = environment.getServer().takeRequest();
        assertEquals("POST", sent.getMethod());
        assertEquals("/logout", sent.getPath());
        String body = sent.getBody().readUtf8();
        assertTrue("debe enviar el refresh token: " + body,
                body.contains("\"refresh_token\":\"r-token\""));
    }

    /**
     * Verifica que un logout con respuesta vacía utiliza un mensaje local de cierre.
     */
    @Test
    public void logout_emptyBody_returnsLocalDefaultMessage() throws Exception {
        environment.getServer().enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{}"));

        ApiResult<String> result = await(callback -> repository.logout("r-token", callback));

        assertTrue(result.isSuccess());
        assertNotNull(result.data);
        assertFalse("Debe devolver un mensaje local", result.data.isEmpty());
    }

    /**
     * Verifica que un logout con error 5xx devuelve fallo de servidor.
     */
    @Test
    public void logout_serverError_deliversServerFailure() throws Exception {
        environment.getServer().enqueue(new MockResponse()
                .setResponseCode(503)
                .setHeader("Content-Type", "application/json")
                .setBody("{}"));

        ApiResult<String> result = await(callback -> repository.logout("r-token", callback));

        assertFalse(result.isSuccess());
        assertNotNull(result.error);
        assertEquals(ApiErrorType.SERVER, result.error.getType());
        assertEquals(503, result.error.getHttpCode());
    }

    /**
     * Verifica que la solicitud de recuperación devuelve el mensaje del backend
     * y normaliza el {@code locale} antes de enviarlo.
     */
    @Test
    public void solicitarRecuperacion_successfulResponse_returnsServerMessage() throws Exception {
        environment.getServer().enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"mensaje\":\"correo enviado\"}"));

        ApiResult<String> result =
                await(callback -> repository.solicitarRecuperacion("user@mail.com", "es", callback));

        assertTrue(result.isSuccess());
        assertEquals("correo enviado", result.data);

        RecordedRequest sent = environment.getServer().takeRequest();
        assertEquals("POST", sent.getMethod());
        assertEquals("/password/solicitar", sent.getPath());
        String body = sent.getBody().readUtf8();
        assertTrue(body.contains("\"email\":\"user@mail.com\""));
        assertTrue(body.contains("\"locale\":\"es\""));
    }

    /**
     * Verifica que la solicitud de recuperación con locale desconocido cae a
     * inglés por la normalización del DTO.
     */
    @Test
    public void solicitarRecuperacion_unknownLocale_normalizesToEnglish() throws Exception {
        environment.getServer().enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"mensaje\":\"ok\"}"));

        ApiResult<String> ignored =
                await(callback -> repository.solicitarRecuperacion("u@m.com", "fr", callback));
        assertTrue(ignored.isSuccess());

        RecordedRequest sent = environment.getServer().takeRequest();
        String body = sent.getBody().readUtf8();
        assertTrue("locale debe normalizarse a en: " + body, body.contains("\"locale\":\"en\""));
    }

    /**
     * Verifica que un reseteo de contraseña exitoso envía email, código y
     * nueva contraseña al endpoint dedicado.
     */
    @Test
    public void resetearPassword_successfulResponse_returnsServerMessage() throws Exception {
        environment.getServer().enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"mensaje\":\"contrasena cambiada\"}"));

        ApiResult<String> result = await(callback ->
                repository.resetearPassword("user@mail.com", "1234", "newPass1", callback));

        assertTrue(result.isSuccess());
        assertEquals("contrasena cambiada", result.data);

        RecordedRequest sent = environment.getServer().takeRequest();
        assertEquals("POST", sent.getMethod());
        assertEquals("/password/confirmar", sent.getPath());
        String body = sent.getBody().readUtf8();
        assertTrue(body.contains("\"email\":\"user@mail.com\""));
        assertTrue(body.contains("\"codigo\":\"1234\""));
        assertTrue(body.contains("\"nueva_password\":\"newPass1\""));
    }

    /**
     * Verifica que un reseteo con error 400 devuelve fallo de validación.
     */
    @Test
    public void resetearPassword_validationError_returnsValidationFailure() throws Exception {
        environment.getServer().enqueue(new MockResponse()
                .setResponseCode(400)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"error_code\":\"code_invalid_length\"}"));

        ApiResult<String> result = await(callback ->
                repository.resetearPassword("user@mail.com", "1", "newPass1", callback));

        assertFalse(result.isSuccess());
        assertEquals(ApiErrorType.VALIDATION, result.error.getType());
        assertEquals(400, result.error.getHttpCode());
    }

    /**
     * Verifica que cancelAll detiene las llamadas en vuelo del repositorio
     * sin propagar callback de éxito.
     */
    @Test
    public void cancelAll_inFlightLogin_swallowsCallbackAfterCancellation() throws Exception {
        environment.getServer().enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"nombre_usuario\":\"alice\",\"token_acceso\":\"a\",\"refresh_token\":\"r\"}")
                .setBodyDelay(500, TimeUnit.MILLISECONDS));

        AtomicReference<ApiResult<LoginSession>> received = new AtomicReference<>(null);
        repository.login("alice", "x", received::set);

        repository.cancelAll();

        Thread.sleep(800);

        ApiResult<LoginSession> result = received.get();
        if (result != null) {
            assertFalse("Si llega resultado tras cancelar, no debe ser éxito", result.isSuccess());
        }
    }

    // -------------------------------------------------------------------------
    // Infraestructura de espera
    // -------------------------------------------------------------------------

    /**
     * Adapta un callback asíncrono del repositorio a una llamada bloqueante.
     *
     * @param invocation lambda que dispara la operación entregando el callback recibido.
     * @param <T> tipo del payload del {@link ApiResult}.
     * @return resultado entregado al callback antes de un timeout.
     */
    private static <T> ApiResult<T> await(CallbackInvocation<T> invocation) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ApiResult<T>> ref = new AtomicReference<>();

        invocation.invoke(result -> {
            ref.set(result);
            latch.countDown();
        });

        if (!latch.await(10, TimeUnit.SECONDS)) {
            fail("Timeout esperando callback del repositorio");
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
        void invoke(AuthRepository.Callback<T> callback);
    }
}
