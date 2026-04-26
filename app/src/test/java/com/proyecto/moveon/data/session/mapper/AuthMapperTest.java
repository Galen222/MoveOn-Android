package com.proyecto.moveon.data.session.mapper;

import static org.junit.Assert.*;

import com.proyecto.moveon.data.session.dto.LoginRequestDto;
import com.proyecto.moveon.data.session.dto.LoginResponseDto;
import com.proyecto.moveon.data.session.dto.RegisterRequestDto;
import com.proyecto.moveon.domain.auth.LoginSession;
import com.proyecto.moveon.domain.auth.RegisterInput;

import org.junit.Test;
import com.proyecto.moveon.data.session.dto.SocialAuthRequestDto;
import com.proyecto.moveon.data.session.dto.SocialRegisterRequestDto;
import com.proyecto.moveon.domain.auth.SocialRegisterInput;
/**
 * Pruebas para validar el comportamiento de auth.
 */
public class AuthMapperTest {

    // ── toLoginRequest ──────────────────────────────────────────────────────

    /**
     * Verifica el escenario cubierto por {@link #toLoginRequest_mapsFieldsCorrectly()}.
     */
    @Test
    public void toLoginRequest_mapsFieldsCorrectly() {
        LoginRequestDto dto = AuthMapper.toLoginRequest("alice", "Pass1234");

        assertEquals("alice", dto.identificador);
        assertEquals("Pass1234", dto.password);
    }

    // ── toRegisterRequest ───────────────────────────────────────────────────

    /**
     * Verifica el escenario cubierto por {@link #toRegisterRequest_mapsAllFields()}.
     */
    @Test
    public void toRegisterRequest_mapsAllFields() {
        RegisterInput input = new RegisterInput(
                "alice",
                "alice@test.com",
                "Pass1234",
                "2000-01-15",
                true,
                "2025-03-19T10:00:00Z",
                "v1.0"
        );

        RegisterRequestDto dto = AuthMapper.toRegisterRequest(input);

        assertEquals("alice", dto.nombreUsuario);
        assertEquals("alice@test.com", dto.email);
        assertEquals("Pass1234", dto.password);
        assertEquals("2000-01-15", dto.fechaNacimiento);
        assertTrue(dto.aceptaTerminos);
        assertEquals("2025-03-19T10:00:00Z", dto.fechaAceptacionTerminos);
        assertEquals("v1.0", dto.versionTerminos);
    }

    // ── toDomain ────────────────────────────────────────────────────────────

    /**
     * Verifica el escenario cubierto por {@link #toDomain_mapsLoginResponse()}.
     */
    @Test
    public void toDomain_mapsLoginResponse() {
        LoginResponseDto dto = new LoginResponseDto();
        dto.nombreUsuario = "alice";
        dto.tokenAcceso = "jwt_token_123";
        dto.refreshToken = "refresh_456";

        LoginSession session = AuthMapper.toDomain(dto);

        assertEquals("alice", session.nombreUsuario);
        assertEquals("jwt_token_123", session.tokenAcceso);
        assertEquals("refresh_456", session.refreshToken);
    }

    /**
     * Verifica el escenario cubierto por {@link #toDomain_nullFields_defaultToEmptyString()}.
     */
    @Test
    public void toDomain_nullFields_defaultToEmptyString() {
        LoginResponseDto dto = new LoginResponseDto();
        dto.nombreUsuario = null;
        dto.tokenAcceso = null;
        dto.refreshToken = null;

        LoginSession session = AuthMapper.toDomain(dto);

        assertEquals("", session.nombreUsuario);
        assertEquals("", session.tokenAcceso);
        assertEquals("", session.refreshToken);
    }

    /**
     * Verifica el escenario cubierto por {@link #toDomain_emptyFields_defaultToEmptyString()}.
     */
    @Test
    public void toDomain_emptyFields_defaultToEmptyString() {
        LoginResponseDto dto = new LoginResponseDto();
        dto.nombreUsuario = "";
        dto.tokenAcceso = "   ";
        dto.refreshToken = "";

        LoginSession session = AuthMapper.toDomain(dto);

        assertEquals("", session.nombreUsuario);
        assertEquals("", session.tokenAcceso);
        assertEquals("", session.refreshToken);
    }
    /**
     * Verifica que el mapper crea el DTO mínimo para login social.
     */
    @Test
    public void toSocialAuthRequest_mapsProviderAndToken() {
        SocialAuthRequestDto dto = AuthMapper.toSocialAuthRequest("google", "id-token");

        assertEquals("google", dto.provider);
        assertEquals("id-token", dto.token);
    }

    /**
     * Verifica que el mapper crea el DTO completo de registro social pendiente.
     */
    @Test
    public void toSocialRegisterRequest_mapsAllOnboardingFields() {
        SocialRegisterInput input = new SocialRegisterInput(
                "google",
                "id-token",
                "alice",
                "2000-01-01",
                true,
                "2026-04-25T10:00:00Z",
                "terms-v1"
        );

        SocialRegisterRequestDto dto = AuthMapper.toSocialRegisterRequest(input);

        assertEquals("google", dto.provider);
        assertEquals("id-token", dto.token);
        assertEquals("alice", dto.nombreUsuario);
        assertEquals("2000-01-01", dto.fechaNacimiento);
        assertTrue(dto.aceptaTerminos);
        assertEquals("2026-04-25T10:00:00Z", dto.fechaAceptacionTerminos);
        assertEquals("terms-v1", dto.versionTerminos);
    }
}
