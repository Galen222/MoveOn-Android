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
/**
 * Repositorio encargado de centralizar las operaciones de auth.
 */
public class AuthRepository extends BaseRepository {

    private final Context appContext;

    /**
     * Crea el repositorio de autenticación usando siempre el contexto de aplicación.
     *
     * @param context cualquier contexto Android desde el que obtener el {@code applicationContext}.
     */
    public AuthRepository(Context context) {
        this.appContext = context.getApplicationContext();
    }

    /**
     * Callback estándar del repositorio para encapsular éxito o error en un {@link ApiResult}.
     */
    public interface Callback<T> {
        /**
         * Entrega el resultado final de la operación de autenticación.
         *
         * @param result éxito o error de la operación solicitada.
         */
        void onResult(ApiResult<T> result);
    }

    // -------------------------------------------------------------------------
    // Login
    // -------------------------------------------------------------------------

    /**
     * Intenta iniciar sesión con identificador clásico y contraseña.
     *
     * @param identificador email o nombre de usuario ya validado en cliente.
     * @param password contraseña escrita por el usuario.
     * @param callback callback que recibe un {@link LoginSession} o el error correspondiente.
     */
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


    /**
     * Intenta iniciar sesión usando un token emitido por un proveedor social.
     *
     * @param provider identificador del proveedor social.
     * @param token token emitido por el proveedor y validable por el backend.
     * @param callback callback que recibe un {@link LoginSession} o el error correspondiente.
     */
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

    /**
     * Registra una nueva cuenta clásica y devuelve el mensaje final del backend.
     *
     * @param input datos de registro ya normalizados por la capa de dominio.
     * @param callback callback que recibe el mensaje de éxito o el error correspondiente.
     */
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


    /**
     * Completa un alta social y devuelve la sesión autenticada resultante.
     *
     * @param input datos adicionales requeridos para terminar el registro social.
     * @param callback callback que recibe un {@link LoginSession} o el error correspondiente.
     */
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

    /**
     * Solicita al backend un nuevo par de tokens a partir del refresh token actual.
     *
     * @param refreshToken token de refresco vigente.
     * @param callback callback que recibe una nueva {@link LoginSession} o el error correspondiente.
     */
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

    /**
     * Informa al backend del cierre de sesión para invalidar el refresh token actual.
     *
     * @param refreshToken token de refresco que debe revocarse en servidor.
     * @param callback callback que recibe el mensaje final del backend.
     */
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
    // POST /password/solicitar → body: { email, locale }
    // -------------------------------------------------------------------------

    /**
     * Solicita el envío del flujo de recuperación de contraseña para un email concreto.
     *
     * @param email dirección de correo asociada a la cuenta.
     * @param locale locale que el backend puede usar para personalizar el mensaje.
     * @param callback callback que recibe el mensaje final del backend.
     */
    public void solicitarRecuperacion(String email, String locale, Callback<String> callback) {
        Call<MessageResponseDto> call =
                RetrofitProvider.authApi(appContext)
                        .solicitarRecuperacion(new RecuperarPasswordRequestDto(email, locale));

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

    /**
     * Confirma el cambio de contraseña usando el código de recuperación emitido previamente.
     *
     * @param email dirección de correo asociada a la cuenta.
     * @param codigo código temporal recibido por el usuario.
     * @param nuevaPassword nueva contraseña elegida por el usuario.
     * @param callback callback que recibe el mensaje final del backend.
     */
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

