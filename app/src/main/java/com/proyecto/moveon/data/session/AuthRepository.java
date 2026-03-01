package com.proyecto.moveon.data.session;

import android.content.Context;

import com.proyecto.moveon.R;
import com.proyecto.moveon.core.api.ApiError;
import com.proyecto.moveon.core.api.ApiErrorParser;
import com.proyecto.moveon.core.api.ApiResult;
import com.proyecto.moveon.data.common.BaseRepository;
import com.proyecto.moveon.data.remote.retrofit.RetrofitProvider;
import com.proyecto.moveon.data.session.dto.LogoutRequestDto;
import com.proyecto.moveon.data.session.dto.MessageResponseDto;
import com.proyecto.moveon.data.session.dto.RefreshRequestDto;
import com.proyecto.moveon.data.session.mapper.AuthMapper;
import com.proyecto.moveon.domain.auth.LoginSession;
import com.proyecto.moveon.domain.auth.RegisterInput;
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

    public void login(String identificador, String password, Callback<LoginSession> callback) {
        Call<com.proyecto.moveon.data.session.dto.LoginResponseDto> call =
                RetrofitProvider.authApi(appContext).login(AuthMapper.toLoginRequest(identificador, password));

        trackCall(call);
        call.enqueue(new retrofit2.Callback<com.proyecto.moveon.data.session.dto.LoginResponseDto>() {
            @Override
            public void onResponse(Call<com.proyecto.moveon.data.session.dto.LoginResponseDto> c,
                                   Response<com.proyecto.moveon.data.session.dto.LoginResponseDto> response) {
                untrackCall(call);

                if (!response.isSuccessful()) {
                    callback.onResult(ApiResult.failure(ApiErrorParser.fromHttp(appContext, response)));
                    return;
                }

                com.proyecto.moveon.data.session.dto.LoginResponseDto body = response.body();
                if (body == null || !StringUtils.hasText(body.tokenAcceso) || !StringUtils.hasText(body.refreshToken)) {
                    callback.onResult(ApiResult.failure(ApiError.local(appContext.getString(R.string.api_error_respuesta_invalida))));
                    return;
                }

                callback.onResult(ApiResult.success(AuthMapper.toDomain(body)));
            }

            @Override
            public void onFailure(Call<com.proyecto.moveon.data.session.dto.LoginResponseDto> c, Throwable t) {
                untrackCall(call);
                if (call.isCanceled()) return; // Early return limpio
                callback.onResult(ApiResult.failure(ApiErrorParser.fromThrowable(appContext, t, false)));
            }
        });
    }

    public void register(RegisterInput input, Callback<String> callback) {
        Call<com.proyecto.moveon.data.session.dto.RegisterResponseDto> call =
                RetrofitProvider.authApi(appContext).register(AuthMapper.toRegisterRequest(input));

        trackCall(call);
        call.enqueue(new retrofit2.Callback<com.proyecto.moveon.data.session.dto.RegisterResponseDto>() {
            @Override
            public void onResponse(Call<com.proyecto.moveon.data.session.dto.RegisterResponseDto> c,
                                   Response<com.proyecto.moveon.data.session.dto.RegisterResponseDto> response) {
                untrackCall(call);

                if (!response.isSuccessful()) {
                    callback.onResult(ApiResult.failure(ApiErrorParser.fromHttp(appContext, response)));
                    return;
                }

                com.proyecto.moveon.data.session.dto.RegisterResponseDto body = response.body();
                String msg = (body != null && StringUtils.hasText(body.mensaje))
                        ? body.mensaje : appContext.getString(R.string.repo_registro_completado);

                callback.onResult(ApiResult.success(msg));
            }

            @Override
            public void onFailure(Call<com.proyecto.moveon.data.session.dto.RegisterResponseDto> c, Throwable t) {
                untrackCall(call);
                if (call.isCanceled()) return; // Early return limpio
                callback.onResult(ApiResult.failure(ApiErrorParser.fromThrowable(appContext, t, false)));
            }
        });
    }

    public void refreshSession(String refreshToken, Callback<LoginSession> callback) {
        Call<com.proyecto.moveon.data.session.dto.LoginResponseDto> call =
                RetrofitProvider.authApi(appContext).refresh(new RefreshRequestDto(refreshToken));

        trackCall(call);
        call.enqueue(new retrofit2.Callback<com.proyecto.moveon.data.session.dto.LoginResponseDto>() {
            @Override
            public void onResponse(Call<com.proyecto.moveon.data.session.dto.LoginResponseDto> c,
                                   Response<com.proyecto.moveon.data.session.dto.LoginResponseDto> response) {
                untrackCall(call);

                if (!response.isSuccessful()) {
                    callback.onResult(ApiResult.failure(ApiErrorParser.fromHttp(appContext, response)));
                    return;
                }

                com.proyecto.moveon.data.session.dto.LoginResponseDto body = response.body();
                if (body == null || !StringUtils.hasText(body.tokenAcceso) || !StringUtils.hasText(body.refreshToken)) {
                    callback.onResult(ApiResult.failure(ApiError.local(appContext.getString(R.string.api_error_respuesta_invalida))));
                    return;
                }

                callback.onResult(ApiResult.success(AuthMapper.toDomain(body)));
            }

            @Override
            public void onFailure(Call<com.proyecto.moveon.data.session.dto.LoginResponseDto> c, Throwable t) {
                untrackCall(call);
                if (call.isCanceled()) return; // Early return limpio
                callback.onResult(ApiResult.failure(ApiErrorParser.fromThrowable(appContext, t, false)));
            }
        });
    }

    public void logout(String refreshToken, Callback<String> callback) {
        Call<MessageResponseDto> call =
                RetrofitProvider.authApi(appContext).logout(new LogoutRequestDto(refreshToken));

        trackCall(call);
        call.enqueue(new retrofit2.Callback<MessageResponseDto>() {
            @Override
            public void onResponse(Call<MessageResponseDto> c, Response<MessageResponseDto> response) {
                untrackCall(call);

                if (!response.isSuccessful()) {
                    callback.onResult(ApiResult.failure(ApiErrorParser.fromHttp(appContext, response)));
                    return;
                }

                MessageResponseDto body = response.body();
                String msg = (body != null && StringUtils.hasText(body.mensaje)) ? body.mensaje : appContext.getString(R.string.repo_logout_ok);

                callback.onResult(ApiResult.success(msg));
            }

            @Override
            public void onFailure(Call<MessageResponseDto> c, Throwable t) {
                untrackCall(call);
                if (call.isCanceled()) return; // Early return limpio
                callback.onResult(ApiResult.failure(ApiErrorParser.fromThrowable(appContext, t, false)));
            }
        });
    }
}