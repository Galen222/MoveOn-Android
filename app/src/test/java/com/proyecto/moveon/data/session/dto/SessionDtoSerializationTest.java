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
}
