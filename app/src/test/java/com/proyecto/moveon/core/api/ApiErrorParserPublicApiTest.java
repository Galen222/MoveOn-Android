package com.proyecto.moveon.core.api;

import static org.junit.Assert.*;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;
import java.net.SocketTimeoutException;

import okhttp3.MediaType;
import okhttp3.ResponseBody;
import retrofit2.Response;

/**
 * Tests de la superficie pública de {@link ApiErrorParser} ejercitando
 * {@code fromHttp} y {@code fromThrowable} con respuestas Retrofit reales.
 *
 * <p>Se ejecuta bajo {@link RobolectricTestRunner} para que los caminos de
 * producción que pasan por {@code AppLanguageManager.localizedContext()}
 * dispongan de {@code Configuration.getLocales()} funcional sin emulador.</p>
 */
@RunWith(RobolectricTestRunner.class)

public class ApiErrorParserPublicApiTest {

    private static final MediaType JSON = MediaType.parse("application/json");

    /**
     * Devuelve el contexto de aplicación gestionado por Robolectric.
     *
     * @return contexto Android funcional con recursos del módulo {@code app}.
     */
    private static Context appContext() {
        return ApplicationProvider.getApplicationContext();
    }

    /**
     * Verifica que {@code fromHttp} con 401 produce {@link ApiErrorType#UNAUTHORIZED}.
     */
    @Test
    public void fromHttp_401Response_returnsUnauthorizedError() {
        Response<Object> response = Response.error(401,
                ResponseBody.create("{\"detail\":\"token caducado\"}", JSON));

        ApiError error = ApiErrorParser.fromHttp(appContext(), response);

        assertEquals(ApiErrorType.UNAUTHORIZED, error.getType());
        assertEquals(401, error.getHttpCode());
        assertNotNull(error.getMessage());
    }

    /**
     * Verifica que {@code fromHttp} con un cuerpo {@code detail} estructurado
     * por columnas extrae los errores por campo.
     */
    @Test
    public void fromHttp_422DetailWithColumna_extractsFieldErrors() {
        String body = "{\"detail\":["
                + "{\"columna\":\"email\",\"error_code\":\"email_format_invalid\",\"mensaje\":\"email mal formado\"},"
                + "{\"columna\":\"password\",\"mensaje\":\"clave demasiado corta\"}"
                + "]}";
        Response<Object> response = Response.error(422,
                ResponseBody.create(body, JSON));

        ApiError error = ApiErrorParser.fromHttp(appContext(), response);

        assertEquals(ApiErrorType.VALIDATION, error.getType());
        assertEquals(422, error.getHttpCode());
        assertTrue("debe registrar errores por campo", error.hasFieldErrors());
        assertNotNull(error.firstFieldMessage("email"));
        assertNotNull(error.firstFieldMessage("password"));
    }

    /**
     * Verifica que {@code fromHttp} sigue las claves alternativas {@code msg}
     * cuando {@code mensaje} no está presente.
     */
    @Test
    public void fromHttp_detailWithMsgKey_returnsMessageFromMsg() {
        String body = "{\"detail\":[{\"loc\":[\"body\",\"email\"],\"msg\":\"requerido\"}]}";
        Response<Object> response = Response.error(422,
                ResponseBody.create(body, JSON));

        ApiError error = ApiErrorParser.fromHttp(appContext(), response);

        assertEquals(ApiErrorType.VALIDATION, error.getType());
        assertNotNull(error.firstFieldMessage("email"));
    }

    /**
     * Verifica que {@code fromHttp} con {@code detail} primitivo string usa
     * directamente ese texto como mensaje principal.
     */
    @Test
    public void fromHttp_detailAsPrimitive_returnsPrimitiveAsMessage() {
        String body = "{\"detail\":\"un mensaje plano\"}";
        Response<Object> response = Response.error(403,
                ResponseBody.create(body, JSON));

        ApiError error = ApiErrorParser.fromHttp(appContext(), response);

        assertEquals(ApiErrorType.FORBIDDEN, error.getType());
        assertNotNull(error.getMessage());
    }

    /**
     * Verifica que {@code fromHttp} con cuerpo JSON inválido sintácticamente
     * cae en la rama {@code PARSE} y limpia los errores por campo.
     */
    @Test
    public void fromHttp_invalidJsonBody_returnsParseError() {
        Response<Object> response = Response.error(500,
                ResponseBody.create("no es json {{{", JSON));

        ApiError error = ApiErrorParser.fromHttp(appContext(), response);

        assertEquals(ApiErrorType.PARSE, error.getType());
        assertFalse(error.hasFieldErrors());
    }

    /**
     * Verifica que {@code fromHttp} con un 429 y cabecera {@code Retry-After}
     * cae en la rama de rate limit.
     */
    @Test
    public void fromHttp_rateLimitWithRetryAfter_usesRetryAfterInMessage() {
        Response<Object> response = Response.error(
                ResponseBody.create("{}", JSON),
                new okhttp3.Response.Builder()
                        .code(429)
                        .message("Too Many Requests")
                        .protocol(okhttp3.Protocol.HTTP_1_1)
                        .request(new okhttp3.Request.Builder().url("http://localhost/x").build())
                        .header("Retry-After", "30")
                        .build()
        );

        ApiError error = ApiErrorParser.fromHttp(appContext(), response);

        assertEquals(ApiErrorType.RATE_LIMIT, error.getType());
        assertEquals(429, error.getHttpCode());
    }

    /**
     * Verifica que {@code fromHttp} con código 413 cae en la rama de payload
     * demasiado grande.
     */
    @Test
    public void fromHttp_payloadTooLarge_returnsPayloadType() {
        Response<Object> response = Response.error(413,
                ResponseBody.create("{}", JSON));

        ApiError error = ApiErrorParser.fromHttp(appContext(), response);

        assertEquals(ApiErrorType.PAYLOAD_TOO_LARGE, error.getType());
        assertEquals(413, error.getHttpCode());
    }

    /**
     * Verifica que {@code fromHttp} con código 409 usa el fallback de
     * conflicto cuando el cuerpo no aporta detalle.
     */
    @Test
    public void fromHttp_conflictWithEmptyBody_usesConflictFallback() {
        Response<Object> response = Response.error(409,
                ResponseBody.create("{}", JSON));

        ApiError error = ApiErrorParser.fromHttp(appContext(), response);

        assertEquals(ApiErrorType.CONFLICT, error.getType());
        assertEquals(409, error.getHttpCode());
        assertNotNull(error.getMessage());
    }

    /**
     * Verifica que {@code fromHttp} con un código no mapeado (418) devuelve
     * {@link ApiErrorType#UNKNOWN} sin perder el código HTTP.
     */
    @Test
    public void fromHttp_unmappedCode_returnsUnknownTypeWithRawCode() {
        Response<Object> response = Response.error(418,
                ResponseBody.create("{}", JSON));

        ApiError error = ApiErrorParser.fromHttp(appContext(), response);

        assertEquals(ApiErrorType.UNKNOWN, error.getType());
        assertEquals(418, error.getHttpCode());
    }

    /**
     * Verifica que {@code fromHttp} con sólo {@code error_code} a nivel raíz
     * lo aplica para resolver el mensaje.
     */
    @Test
    public void fromHttp_topLevelErrorCode_resolvesLocalizedMessage() {
        String body = "{\"error_code\":\"username_already_in_use\"}";
        Response<Object> response = Response.error(409,
                ResponseBody.create(body, JSON));

        ApiError error = ApiErrorParser.fromHttp(appContext(), response);

        assertEquals(ApiErrorType.CONFLICT, error.getType());
        assertNotNull(error.getMessage());
    }

    /**
     * Verifica que {@code fromHttp} con {@code errores_campos} legacy extrae
     * los mensajes al mapa de errores por campo.
     */
    @Test
    public void fromHttp_legacyErroresCamposObject_populatesFieldErrors() {
        String body = "{\"errores_campos\":{\"email\":[\"requerido\"],\"password\":[\"corta\"]}}";
        Response<Object> response = Response.error(422,
                ResponseBody.create(body, JSON));

        ApiError error = ApiErrorParser.fromHttp(appContext(), response);

        assertEquals(ApiErrorType.VALIDATION, error.getType());
        assertTrue(error.hasFieldErrors());
        assertNotNull(error.firstFieldMessage("email"));
        assertNotNull(error.firstFieldMessage("password"));
    }

    /**
     * Verifica que {@code fromThrowable} con {@code canceled = true} produce
     * un {@link ApiErrorType#CANCELED}.
     */
    @Test
    public void fromThrowable_cancelled_returnsCanceledError() {
        ApiError error = ApiErrorParser.fromThrowable(
                appContext(), new IOException("boom"), true);

        assertEquals(ApiErrorType.CANCELED, error.getType());
    }

    /**
     * Verifica que {@code fromThrowable} con {@link SocketTimeoutException}
     * cae en {@link ApiErrorType#TIMEOUT}.
     */
    @Test
    public void fromThrowable_socketTimeout_returnsTimeoutError() {
        ApiError error = ApiErrorParser.fromThrowable(
                appContext(), new SocketTimeoutException("slow"), false);

        assertEquals(ApiErrorType.TIMEOUT, error.getType());
    }

    /**
     * Verifica que {@code fromThrowable} con un {@link IOException} genérico
     * cae en {@link ApiErrorType#NETWORK}.
     */
    @Test
    public void fromThrowable_genericIoException_returnsNetworkError() {
        ApiError error = ApiErrorParser.fromThrowable(
                appContext(), new IOException("dns"), false);

        assertEquals(ApiErrorType.NETWORK, error.getType());
    }

    /**
     * Verifica que {@code fromThrowable} con un {@link RuntimeException}
     * arbitrario devuelve un error local sin tipo HTTP específico.
     */
    @Test
    public void fromThrowable_genericThrowable_returnsLocalUnknownError() {
        ApiError error = ApiErrorParser.fromThrowable(
                appContext(), new RuntimeException("oops"), false);

        assertEquals(ApiErrorType.UNKNOWN, error.getType());
        assertEquals(0, error.getHttpCode());
    }
}
