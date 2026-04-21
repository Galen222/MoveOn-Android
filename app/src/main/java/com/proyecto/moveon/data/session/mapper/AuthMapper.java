package com.proyecto.moveon.data.session.mapper;

import com.proyecto.moveon.data.session.dto.LoginRequestDto;
import com.proyecto.moveon.data.session.dto.LoginResponseDto;
import com.proyecto.moveon.data.session.dto.RegisterRequestDto;
import com.proyecto.moveon.data.session.dto.SocialAuthRequestDto;
import com.proyecto.moveon.data.session.dto.SocialRegisterRequestDto;
import com.proyecto.moveon.domain.auth.LoginSession;
import com.proyecto.moveon.domain.auth.RegisterInput;
import com.proyecto.moveon.domain.auth.SocialRegisterInput;
import com.proyecto.moveon.utils.StringUtils;
/**
 * Mapper que transforma los modelos de entrada y salida del módulo de autenticación.
 */
public final class AuthMapper {

    /**
     * Evita instancias de una clase utilitaria compuesta solo por métodos estáticos.
     */
    private AuthMapper() {}

    /**
     * Construye el DTO que consume el endpoint de login clásico.
     *
     * @param identificador nombre de usuario o email introducido por el usuario.
     * @param password contraseña en texto plano validada previamente por la UI.
     * @return {@link LoginRequestDto} listo para enviarse al backend.
     */
    public static LoginRequestDto toLoginRequest(String identificador, String password) {
        return new LoginRequestDto(identificador, password);
    }

    /**
     * Convierte los datos de registro normalizados por la capa de dominio en el DTO del backend.
     *
     * @param input datos de alta recopilados por la pantalla de registro.
     * @return {@link RegisterRequestDto} con los nombres de campo esperados por la API.
     */
    public static RegisterRequestDto toRegisterRequest(RegisterInput input) {
        return new RegisterRequestDto(
                input.nombreUsuario,
                input.email,
                input.password,
                input.fechaNacimiento,
                input.aceptaTerminos,
                input.fechaAceptacionTerminos,
                input.versionTerminos
        );
    }

    /**
     * Crea el payload mínimo necesario para autenticar una identidad social ya validada por el proveedor.
     *
     * @param provider identificador del proveedor social, por ejemplo Google.
     * @param token token emitido por el proveedor y entregado al backend para su verificación.
     * @return {@link SocialAuthRequestDto} listo para el endpoint de login social.
     */
    public static SocialAuthRequestDto toSocialAuthRequest(String provider, String token) {
        return new SocialAuthRequestDto(provider, token);
    }

    /**
     * Convierte el alta social pendiente en el DTO que combina token del proveedor y datos de onboarding.
     *
     * @param input información recopilada durante el registro social complementario.
     * @return {@link SocialRegisterRequestDto} preparado para el endpoint de alta social.
     */
    public static SocialRegisterRequestDto toSocialRegisterRequest(SocialRegisterInput input) {
        return new SocialRegisterRequestDto(
                input.provider,
                input.token,
                input.nombreUsuario,
                input.fechaNacimiento,
                input.aceptaTerminos,
                input.fechaAceptacionTerminos,
                input.versionTerminos
        );
    }

    /**
     * Transforma la respuesta del backend en una sesión de dominio garantizando cadenas no nulas.
     *
     * @param dto respuesta recibida tras login o refresh.
     * @return {@link LoginSession} utilizable por la capa de datos aunque el backend omita algún campo textual.
     */
    public static LoginSession toDomain(LoginResponseDto dto) {
        String u = StringUtils.hasText(dto.nombreUsuario) ? dto.nombreUsuario : "";
        String a = StringUtils.hasText(dto.tokenAcceso) ? dto.tokenAcceso : "";
        String r = StringUtils.hasText(dto.refreshToken) ? dto.refreshToken : "";
        return new LoginSession(u, a, r);
    }
}
