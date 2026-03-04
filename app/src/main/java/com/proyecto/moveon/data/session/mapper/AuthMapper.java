package com.proyecto.moveon.data.session.mapper;

import com.proyecto.moveon.data.session.dto.LoginRequestDto;
import com.proyecto.moveon.data.session.dto.LoginResponseDto;
import com.proyecto.moveon.data.session.dto.RegisterRequestDto;
import com.proyecto.moveon.domain.auth.LoginSession;
import com.proyecto.moveon.domain.auth.RegisterInput;
import com.proyecto.moveon.utils.StringUtils;

public final class AuthMapper {

    private AuthMapper() {}

    public static LoginRequestDto toLoginRequest(String identificador, String password) {
        return new LoginRequestDto(identificador, password);
    }

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

    public static LoginSession toDomain(LoginResponseDto dto) {
        String u = StringUtils.hasText(dto.nombreUsuario) ? dto.nombreUsuario : "";
        String a = StringUtils.hasText(dto.tokenAcceso) ? dto.tokenAcceso : "";
        String r = StringUtils.hasText(dto.refreshToken) ? dto.refreshToken : "";
        return new LoginSession(u, a, r);
    }
}