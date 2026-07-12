package com.proyecto.moveon.data.session.dto;

import static org.junit.Assert.*;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.junit.Test;
/**
 * Pruebas para validar el comportamiento de session serialization.
 */
public class SessionDtoSerializationTest {

    private final Gson gson = new Gson();

    // ── LoginRequestDto ─────────────────────────────────────────────────────

    @Test
    public void loginRequest_serialization() {
        LoginRequestDto dto = new LoginRequestDto("alice", "Pass1234");

        JsonObject json = gson.fromJson(gson.toJson(dto), JsonObject.class);

        assertEquals("alice", json.get("identificador").getAsString());
        assertEquals("Pass1234", json.get("password").getAsString());
    }

    // ── LogoutRequestDto ────────────────────────────────────────────────────

    @Test
    public void logoutRequest_serialization() {
        LogoutRequestDto dto = new LogoutRequestDto("refresh_token_abc");

        JsonObject json = gson.fromJson(gson.toJson(dto), JsonObject.class);

        assertEquals("refresh_token_abc", json.get("refresh_token").getAsString());
    }

    // ── RefreshRequestDto ───────────────────────────────────────────────────

    @Test
    public void refreshRequest_serialization() {
        RefreshRequestDto dto = new RefreshRequestDto("rt_xyz");

        JsonObject json = gson.fromJson(gson.toJson(dto), JsonObject.class);

        assertEquals("rt_xyz", json.get("refresh_token").getAsString());
    }

    // ── RecuperarPasswordRequestDto ─────────────────────────────────────────

    @Test
    public void recuperarPasswordRequest_serialization() {
        RecuperarPasswordRequestDto dto =
                new RecuperarPasswordRequestDto("test@email.com", "en");

        JsonObject json = gson.fromJson(gson.toJson(dto), JsonObject.class);

        assertEquals("test@email.com", json.get("email").getAsString());
        assertEquals("en", json.get("locale").getAsString());
    }

    // ── ResetearPasswordRequestDto ──────────────────────────────────────────

    @Test
    public void resetearPasswordRequest_serialization() {
        ResetearPasswordRequestDto dto = new ResetearPasswordRequestDto(
                "test@email.com", "123456", "NewPass1234");

        JsonObject json = gson.fromJson(gson.toJson(dto), JsonObject.class);

        assertEquals("test@email.com", json.get("email").getAsString());
        assertEquals("123456", json.get("codigo").getAsString());
        assertEquals("NewPass1234", json.get("nueva_password").getAsString());
    }

    // ── RegisterRequestDto ──────────────────────────────────────────────────

    @Test
    public void registerRequest_serialization() {
        RegisterRequestDto dto = new RegisterRequestDto(
                "alice", "alice@test.com", "Pass1234",
                "2000-01-15", true, "2025-03-19T10:00:00Z", "v1.0");

        JsonObject json = gson.fromJson(gson.toJson(dto), JsonObject.class);

        assertEquals("alice", json.get("nombre_usuario").getAsString());
        assertEquals("alice@test.com", json.get("email").getAsString());
        assertEquals("Pass1234", json.get("password").getAsString());
        assertEquals("2000-01-15", json.get("fecha_nacimiento").getAsString());
        assertTrue(json.get("acepta_terminos").getAsBoolean());
        assertEquals("2025-03-19T10:00:00Z", json.get("fecha_aceptacion_terminos").getAsString());
        assertEquals("v1.0", json.get("version_terminos").getAsString());
    }

    // ── Deserialization tests ────────────────────────────────────────────────

    @Test
    public void loginResponse_deserialization() {
        String json = "{\"nombre_usuario\":\"alice\",\"token_acceso\":\"jwt123\",\"refresh_token\":\"rt456\"}";
        LoginResponseDto dto = gson.fromJson(json, LoginResponseDto.class);

        assertEquals("alice", dto.nombreUsuario);
        assertEquals("jwt123", dto.tokenAcceso);
        assertEquals("rt456", dto.refreshToken);
    }

    @Test
    public void registerResponse_deserialization() {
        String json = "{\"mensaje\":\"Cuenta creada exitosamente\"}";
        RegisterResponseDto dto = gson.fromJson(json, RegisterResponseDto.class);

        assertEquals("Cuenta creada exitosamente", dto.mensaje);
    }

    @Test
    public void messageResponse_deserialization() {
        String json = "{\"mensaje\":\"Código enviado\"}";
        MessageResponseDto dto = gson.fromJson(json, MessageResponseDto.class);

        assertEquals("Código enviado", dto.mensaje);
    }

    @Test
    public void appSessionResponse_deserialization() {
        String json = "{\"app_session_token\":\"session_xyz\"}";
        AppSessionResponseDto dto = gson.fromJson(json, AppSessionResponseDto.class);

        assertEquals("session_xyz", dto.appSession);
    }
    /**
     * Verifica que LoginRequestDto serializa identificador y password con los nombres esperados por backend.
     */
    @Test
    public void loginRequestDto_serializesIdentifierAndPassword() {
        LoginRequestDto dto = new LoginRequestDto("alice", "secret");

        JsonObject json = gson.fromJson(gson.toJson(dto), JsonObject.class);

        assertEquals("alice", json.get("identificador").getAsString());
        assertEquals("secret", json.get("password").getAsString());
    }

    /**
     * Verifica que RegisterRequestDto serializa todos los campos legales y de alta de cuenta.
     */
    @Test
    public void registerRequestDto_serializesAccountAndTermsFields() {
        RegisterRequestDto dto = new RegisterRequestDto(
                "alice",
                "alice@example.com",
                "Secret123",
                "2000-01-02",
                true,
                "2026-04-25T10:00:00Z",
                "terms-v1"
        );

        JsonObject json = gson.fromJson(gson.toJson(dto), JsonObject.class);

        assertEquals("alice", json.get("nombre_usuario").getAsString());
        assertEquals("alice@example.com", json.get("email").getAsString());
        assertEquals("Secret123", json.get("password").getAsString());
        assertEquals("2000-01-02", json.get("fecha_nacimiento").getAsString());
        assertTrue(json.get("acepta_terminos").getAsBoolean());
        assertEquals("2026-04-25T10:00:00Z", json.get("fecha_aceptacion_terminos").getAsString());
        assertEquals("terms-v1", json.get("version_terminos").getAsString());
    }

    /**
     * Verifica que RecuperarPasswordRequestDto normaliza español exacto y cualquier otro locale a inglés.
     */
    @Test
    public void recuperarPasswordRequestDto_normalizesLocaleToSupportedBackendValues() {
        RecuperarPasswordRequestDto spanish = new RecuperarPasswordRequestDto("a@example.com", " es ");
        RecuperarPasswordRequestDto english = new RecuperarPasswordRequestDto("b@example.com", "en-US");
        RecuperarPasswordRequestDto unknown = new RecuperarPasswordRequestDto("c@example.com", "fr");

        assertEquals("a@example.com", spanish.email);
        assertEquals("es", spanish.locale);
        assertEquals("en", english.locale);
        assertEquals("en", unknown.locale);
    }

    /**
     * Verifica que ResetearPasswordRequestDto usa el nombre nueva_password del contrato FastAPI.
     */
    @Test
    public void resetearPasswordRequestDto_serializesBackendPasswordFieldName() {
        ResetearPasswordRequestDto dto = new ResetearPasswordRequestDto(
                "alice@example.com",
                "123456",
                "NewSecret123"
        );

        JsonObject json = gson.fromJson(gson.toJson(dto), JsonObject.class);

        assertEquals("alice@example.com", json.get("email").getAsString());
        assertEquals("123456", json.get("codigo").getAsString());
        assertEquals("NewSecret123", json.get("nueva_password").getAsString());
    }

    /**
     * Verifica que LogoutRequestDto y RefreshRequestDto comparten el nombre refresh_token.
     */
    @Test
    public void tokenRequests_serializeRefreshTokenConsistently() {
        LogoutRequestDto logout = new LogoutRequestDto("refresh-a");
        RefreshRequestDto refresh = new RefreshRequestDto("refresh-b");

        JsonObject logoutJson = gson.fromJson(gson.toJson(logout), JsonObject.class);
        JsonObject refreshJson = gson.fromJson(gson.toJson(refresh), JsonObject.class);

        assertEquals("refresh-a", logoutJson.get("refresh_token").getAsString());
        assertEquals("refresh-b", refreshJson.get("refresh_token").getAsString());
    }

    /**
     * Verifica la deserialización de la respuesta de login completa.
     */
    @Test
    public void loginResponseDto_deserializesTokensAndUsername() {
        LoginResponseDto dto = gson.fromJson(
                "{"
                        + "\"nombre_usuario\":\"alice\","
                        + "\"token_acceso\":\"access\","
                        + "\"refresh_token\":\"refresh\""
                        + "}",
                LoginResponseDto.class
        );

        assertEquals("alice", dto.nombreUsuario);
        assertEquals("access", dto.tokenAcceso);
        assertEquals("refresh", dto.refreshToken);
    }

    /**
     * Verifica la deserialización de respuestas simples de mensaje.
     */
    @Test
    public void messageAndRegisterResponses_deserializeMensajeField() {
        MessageResponseDto message = gson.fromJson("{\"mensaje\":\"ok\"}", MessageResponseDto.class);
        RegisterResponseDto register = gson.fromJson("{\"mensaje\":\"registrado\"}", RegisterResponseDto.class);

        assertEquals("ok", message.mensaje);
        assertEquals("registrado", register.mensaje);
    }

    /**
     * Verifica que AppSessionResponseDto lee el token de sesión de aplicación del campo snake_case.
     */
    @Test
    public void appSessionResponseDto_deserializesAppSessionToken() {
        AppSessionResponseDto dto = gson.fromJson(
                "{\"app_session_token\":\"app-token\"}",
                AppSessionResponseDto.class
        );

        assertEquals("app-token", dto.appSession);
    }
    /**
     * Verifica que {@link LoginResponseDto} deserializa los tokens con los nombres de backend.
     */
    @Test
    public void loginResponse_deserializesSnakeCaseFields() {
        LoginResponseDto dto = gson.fromJson(
                "{\"nombre_usuario\":\"alice\",\"token_acceso\":\"access\",\"refresh_token\":\"refresh\"}",
                LoginResponseDto.class
        );

        assertEquals("alice", dto.nombreUsuario);
        assertEquals("access", dto.tokenAcceso);
        assertEquals("refresh", dto.refreshToken);
    }

    /**
     * Verifica que {@link RegisterResponseDto} deserializa el mensaje de registro.
     */
    @Test
    public void registerResponse_deserializesMessage() {
        RegisterResponseDto dto = gson.fromJson("{\"mensaje\":\"creado\"}", RegisterResponseDto.class);

        assertEquals("creado", dto.mensaje);
    }

    /**
     * Verifica que {@link MessageResponseDto} deserializa respuestas genéricas de mensaje.
     */
    @Test
    public void messageResponse_deserializesMessage() {
        MessageResponseDto dto = gson.fromJson("{\"mensaje\":\"ok\"}", MessageResponseDto.class);

        assertEquals("ok", dto.mensaje);
    }

    /**
     * Verifica que {@link AppSessionResponseDto} mapea el token técnico de app.
     */
    @Test
    public void appSessionResponse_deserializesAppSessionToken() {
        AppSessionResponseDto dto = gson.fromJson(
                "{\"app_session_token\":\"app-session\"}",
                AppSessionResponseDto.class
        );

        assertEquals("app-session", dto.appSession);
    }
}
