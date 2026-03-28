package com.proyecto.moveon.data.session;

import android.content.Context;

import androidx.annotation.NonNull;

import com.proyecto.moveon.R;
import com.proyecto.moveon.core.i18n.AppLanguageManager;
import com.proyecto.moveon.core.api.ApiError;
import com.proyecto.moveon.core.api.ApiErrorParser;
import com.proyecto.moveon.core.api.ApiResult;
import com.proyecto.moveon.data.common.BaseRepository;
import com.proyecto.moveon.data.remote.retrofit.RetrofitProvider;
import com.proyecto.moveon.data.session.dto.LoginResponseDto;
import com.proyecto.moveon.data.session.dto.LogoutRequestDto;
import com.proyecto.moveon.data.session.dto.MessageResponseDto;
import com.proyecto.moveon.data.session.dto.RecuperarPasswordRequestDto;
import com.proyecto.moveon.data.session.dto.RefreshRequestDto;
import com.proyecto.moveon.data.session.dto.RegisterResponseDto;
import com.proyecto.moveon.data.session.dto.ResetearPasswordRequestDto;
import com.proyecto.moveon.data.session.mapper.AuthMapper;
import com.proyecto.moveon.domain.auth.LoginSession;
import com.proyecto.moveon.domain.auth.RegisterInput;
import com.proyecto.moveon.domain.auth.SocialRegisterInput;
import com.proyecto.moveon.utils.StringUtils;

import retrofit2.Call;
import retrofit2.Response;

public class AuthRepository extends BaseRepository {

    private final Context appContext;

    public AuthRepository(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public interface Callback<T> {
        void onResult(ApiResult<T> result);
    }

    // -------------------------------------------------------------------------
    // Login
    // -------------------------------------------------------------------------

    public void login(String identificador, String password, Callback<LoginSession> callback) {
        Call<LoginResponseDto> call =
                RetrofitProvider.authApi(appContext).login(AuthMapper.toLoginRequest(identificador, password));

        trackCall(call);
        call.enqueue(new retrofit2.Callback<>() {
            @Override
            public void onResponse(@NonNull Call<LoginResponseDto> c,
                                   @NonNull Response<LoginResponseDto> response) {
                untrackCall(call);

                if (!response.isSuccessful()) {
                    callback.onResult(ApiResult.failure(ApiErrorParser.fromHttp(appContext, response)));
                    return;
                }

                LoginResponseDto body = response.body();
                if (body == null || !StringUtils.hasText(body.tokenAcceso) || !StringUtils.hasText(body.refreshToken)) {
                    callback.onResult(ApiResult.failure(ApiError.local(AppLanguageManager.getString(appContext, R.string.api_error_respuesta_invalida))));
                    return;
                }

                callback.onResult(ApiResult.success(AuthMapper.toDomain(body)));
            }

            @Override
            public void onFailure(@NonNull Call<LoginResponseDto> c, @NonNull Throwable t) {
                untrackCall(call);
                if (call.isCanceled()) return;
                callback.onResult(ApiResult.failure(ApiErrorParser.fromThrowable(appContext, t, false)));
            }
        });
    }


    public void loginSocial(String provider, String token, Callback<LoginSession> callback) {
        Call<LoginResponseDto> call =
                RetrofitProvider.authApi(appContext)
                        .loginSocial(AuthMapper.toSocialAuthRequest(provider, token));

        trackCall(call);
        call.enqueue(new retrofit2.Callback<>() {
            @Override
            public void onResponse(@NonNull Call<LoginResponseDto> c,
                                   @NonNull Response<LoginResponseDto> response) {
                untrackCall(call);

                if (!response.isSuccessful()) {
                    callback.onResult(ApiResult.failure(ApiErrorParser.fromHttp(appContext, response)));
                    return;
                }

                LoginResponseDto body = response.body();
                if (body == null || !StringUtils.hasText(body.tokenAcceso) || !StringUtils.hasText(body.refreshToken)) {
                    callback.onResult(ApiResult.failure(ApiError.local(AppLanguageManager.getString(appContext, R.string.api_error_respuesta_invalida))));
                    return;
                }

                callback.onResult(ApiResult.success(AuthMapper.toDomain(body)));
            }

            @Override
            public void onFailure(@NonNull Call<LoginResponseDto> c, @NonNull Throwable t) {
                untrackCall(call);
                if (call.isCanceled()) return;
                callback.onResult(ApiResult.failure(ApiErrorParser.fromThrowable(appContext, t, false)));
            }
        });
    }

    // -------------------------------------------------------------------------
    // Registro
    // -------------------------------------------------------------------------

    public void register(RegisterInput input, Callback<String> callback) {
        Call<RegisterResponseDto> call =
                RetrofitProvider.authApi(appContext).register(AuthMapper.toRegisterRequest(input));

        trackCall(call);
        call.enqueue(new retrofit2.Callback<>() {
            @Override
            public void onResponse(@NonNull Call<RegisterResponseDto> c,
                                   @NonNull Response<RegisterResponseDto> response) {
                untrackCall(call);

                if (!response.isSuccessful()) {
                    callback.onResult(ApiResult.failure(ApiErrorParser.fromHttp(appContext, response)));
                    return;
                }

                RegisterResponseDto body = response.body();
                String msg = (body != null && StringUtils.hasText(body.mensaje))
                        ? body.mensaje : AppLanguageManager.getString(appContext, R.string.repo_registro_completado);

                callback.onResult(ApiResult.success(msg));
            }

            @Override
            public void onFailure(@NonNull Call<RegisterResponseDto> c, @NonNull Throwable t) {
                untrackCall(call);
                if (call.isCanceled()) return;
                callback.onResult(ApiResult.failure(ApiErrorParser.fromThrowable(appContext, t, false)));
            }
        });
    }


    public void registerSocial(SocialRegisterInput input, Callback<LoginSession> callback) {
        Call<LoginResponseDto> call =
                RetrofitProvider.authApi(appContext)
                        .registerSocial(AuthMapper.toSocialRegisterRequest(input));

        trackCall(call);
        call.enqueue(new retrofit2.Callback<>() {
            @Override
            public void onResponse(@NonNull Call<LoginResponseDto> c,
                                   @NonNull Response<LoginResponseDto> response) {
                untrackCall(call);

                if (!response.isSuccessful()) {
                    callback.onResult(ApiResult.failure(ApiErrorParser.fromHttp(appContext, response)));
                    return;
                }

                LoginResponseDto body = response.body();
                if (body == null || !StringUtils.hasText(body.tokenAcceso) || !StringUtils.hasText(body.refreshToken)) {
                    callback.onResult(ApiResult.failure(ApiError.local(AppLanguageManager.getString(appContext, R.string.api_error_respuesta_invalida))));
                    return;
                }

                callback.onResult(ApiResult.success(AuthMapper.toDomain(body)));
            }

            @Override
            public void onFailure(@NonNull Call<LoginResponseDto> c, @NonNull Throwable t) {
                untrackCall(call);
                if (call.isCanceled()) return;
                callback.onResult(ApiResult.failure(ApiErrorParser.fromThrowable(appContext, t, false)));
            }
        });
    }

    // -------------------------------------------------------------------------
    // Refresh de sesión
    // -------------------------------------------------------------------------

    public void refreshSession(String refreshToken, Callback<LoginSession> callback) {
        Call<LoginResponseDto> call =
                RetrofitProvider.authApi(appContext).refresh(new RefreshRequestDto(refreshToken));

        trackCall(call);
        call.enqueue(new retrofit2.Callback<>() {
            @Override
            public void onResponse(@NonNull Call<LoginResponseDto> c,
                                   @NonNull Response<LoginResponseDto> response) {
                untrackCall(call);

                if (!response.isSuccessful()) {
                    callback.onResult(ApiResult.failure(ApiErrorParser.fromHttp(appContext, response)));
                    return;
                }

                LoginResponseDto body = response.body();
                if (body == null || !StringUtils.hasText(body.tokenAcceso) || !StringUtils.hasText(body.refreshToken)) {
                    callback.onResult(ApiResult.failure(ApiError.local(AppLanguageManager.getString(appContext, R.string.api_error_respuesta_invalida))));
                    return;
                }

                callback.onResult(ApiResult.success(AuthMapper.toDomain(body)));
            }

            @Override
            public void onFailure(@NonNull Call<LoginResponseDto> c, @NonNull Throwable t) {
                untrackCall(call);
                if (call.isCanceled()) return;
                callback.onResult(ApiResult.failure(ApiErrorParser.fromThrowable(appContext, t, false)));
            }
        });
    }

    // -------------------------------------------------------------------------
    // Logout
    // -------------------------------------------------------------------------

    public void logout(String refreshToken, Callback<String> callback) {
        Call<MessageResponseDto> call =
                RetrofitProvider.authApi(appContext).logout(new LogoutRequestDto(refreshToken));

        trackCall(call);
        call.enqueue(new retrofit2.Callback<>() {
            @Override
            public void onResponse(@NonNull Call<MessageResponseDto> c,
                                   @NonNull Response<MessageResponseDto> response) {
                untrackCall(call);

                if (!response.isSuccessful()) {
                    callback.onResult(ApiResult.failure(ApiErrorParser.fromHttp(appContext, response)));
                    return;
                }

                MessageResponseDto body = response.body();
                String msg = (body != null && StringUtils.hasText(body.mensaje))
                        ? body.mensaje : AppLanguageManager.getString(appContext, R.string.repo_logout_ok);

                callback.onResult(ApiResult.success(msg));
            }

            @Override
            public void onFailure(@NonNull Call<MessageResponseDto> c, @NonNull Throwable t) {
                untrackCall(call);
                if (call.isCanceled()) return;
                callback.onResult(ApiResult.failure(ApiErrorParser.fromThrowable(appContext, t, false)));
            }
        });
    }

    // -------------------------------------------------------------------------
    // Recuperación de contraseña — Paso 1
    // POST /password/solicitar → body: { email }
    // -------------------------------------------------------------------------

    public void solicitarRecuperacion(String email, Callback<String> callback) {
        Call<MessageResponseDto> call =
                RetrofitProvider.authApi(appContext)
                        .solicitarRecuperacion(new RecuperarPasswordRequestDto(email));

        trackCall(call);
        call.enqueue(new retrofit2.Callback<>() {
            @Override
            public void onResponse(@NonNull Call<MessageResponseDto> c,
                                   @NonNull Response<MessageResponseDto> response) {
                untrackCall(call);

                if (!response.isSuccessful()) {
                    callback.onResult(ApiResult.failure(ApiErrorParser.fromHttp(appContext, response)));
                    return;
                }

                MessageResponseDto body = response.body();
                String msg = (body != null && StringUtils.hasText(body.mensaje))
                        ? body.mensaje : AppLanguageManager.getString(appContext, R.string.repo_recuperacion_enviada);

                callback.onResult(ApiResult.success(msg));
            }

            @Override
            public void onFailure(@NonNull Call<MessageResponseDto> c, @NonNull Throwable t) {
                untrackCall(call);
                if (call.isCanceled()) return;
                callback.onResult(ApiResult.failure(ApiErrorParser.fromThrowable(appContext, t, false)));
            }
        });
    }

    // -------------------------------------------------------------------------
    // Recuperación de contraseña — Paso 2
    // POST /password/confirmar  →  body: { email, codigo, nueva_password }
    // -------------------------------------------------------------------------

    public void resetearPassword(String email, String codigo, String nuevaPassword,
                                 Callback<String> callback) {
        Call<MessageResponseDto> call =
                RetrofitProvider.authApi(appContext)
                        .resetearPassword(new ResetearPasswordRequestDto(email, codigo, nuevaPassword));

        trackCall(call);
        call.enqueue(new retrofit2.Callback<>() {
            @Override
            public void onResponse(@NonNull Call<MessageResponseDto> c,
                                   @NonNull Response<MessageResponseDto> response) {
                untrackCall(call);

                if (!response.isSuccessful()) {
                    callback.onResult(ApiResult.failure(ApiErrorParser.fromHttp(appContext, response)));
                    return;
                }

                MessageResponseDto body = response.body();
                String msg = (body != null && StringUtils.hasText(body.mensaje))
                        ? body.mensaje : AppLanguageManager.getString(appContext, R.string.repo_password_reseteada);

                callback.onResult(ApiResult.success(msg));
            }

            @Override
            public void onFailure(@NonNull Call<MessageResponseDto> c, @NonNull Throwable t) {
                untrackCall(call);
                if (call.isCanceled()) return;
                callback.onResult(ApiResult.failure(ApiErrorParser.fromThrowable(appContext, t, false)));
            }
        });
    }
}


