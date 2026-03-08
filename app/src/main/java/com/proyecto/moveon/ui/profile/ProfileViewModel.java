package com.proyecto.moveon.ui.profile;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.proyecto.moveon.core.api.ApiError;
import com.proyecto.moveon.data.profile.dto.ProfileInfoDto;
import com.proyecto.moveon.data.profile.dto.UpdateProfileRequestDto;
import com.proyecto.moveon.data.remote.AuthenticatedApiClient;
import com.proyecto.moveon.data.session.AuthRepository;
import com.proyecto.moveon.data.session.SecureSessionManager;
import com.proyecto.moveon.domain.profile.PerfilUsuario;
import com.proyecto.moveon.ui.common.UiState;
import com.proyecto.moveon.utils.StringUtils;

import java.io.File;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;

public class ProfileViewModel extends AndroidViewModel {

    private static final String PREFS_NAME        = "user_prefs";
    private static final String KEY_NOTIFICATIONS = "notifications_enabled";
    private static final String ENDPOINT_PROFILE  = "perfil/informacion";
    private static final String ENDPOINT_UPDATE   = "perfil/actualizar";
    private static final String ENDPOINT_PHOTO    = "perfil/foto";

    private final AuthRepository         authRepository;
    private final SecureSessionManager   sessionManager;
    private final AuthenticatedApiClient apiClient;
    private final SharedPreferences      prefs;
    private final Gson                   gson = new Gson();

    private final MutableLiveData<UiState<PerfilUsuario>> perfilState  = new MutableLiveData<>();
    private final MutableLiveData<UiState<String>>        updateState  = new MutableLiveData<>();
    private final MutableLiveData<UiState<String>>        photoState   = new MutableLiveData<>();
    private final MutableLiveData<UiState<String>>        logoutState  = new MutableLiveData<>();

    public ProfileViewModel(@NonNull Application application) {
        super(application);
        authRepository = new AuthRepository(application);
        sessionManager = new SecureSessionManager(application);
        apiClient      = new AuthenticatedApiClient(application);
        prefs          = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ── LiveData expuesto ─────────────────────────────────────────────────────

    public LiveData<UiState<PerfilUsuario>> getPerfilState() { return perfilState; }
    public LiveData<UiState<String>>        getUpdateState() { return updateState; }
    public LiveData<UiState<String>>        getPhotoState()  { return photoState; }
    public LiveData<UiState<String>>        getLogoutState() { return logoutState; }

    // ── Username local ────────────────────────────────────────────────────────

    public String getUsername() {
        String u = sessionManager.getUsername();
        return StringUtils.hasText(u) ? u : null;
    }

    // ── Perfil ────────────────────────────────────────────────────────────────

    public void loadPerfil() {
        perfilState.setValue(UiState.loading());
        apiClient.get(ENDPOINT_PROFILE,
                json -> mapToDomain(gson.fromJson(json, ProfileInfoDto.class)),
                result -> {
                    if (result.isSuccess()) {
                        perfilState.postValue(UiState.success(result.data));
                    } else {
                        perfilState.postValue(UiState.error(
                                result.error != null ? result.error : ApiError.local("Error")));
                    }
                });
    }

    public void updatePerfil(@NonNull UpdateProfileRequestDto dto) {
        updateState.setValue(UiState.loading());
        apiClient.patchJson(ENDPOINT_UPDATE, gson.toJsonTree(dto),
                json -> {
                    JsonObject obj = json.getAsJsonObject();
                    return obj.has("mensaje") ? obj.get("mensaje").getAsString() : "OK";
                },
                result -> {
                    if (result.isSuccess()) {
                        updateState.postValue(UiState.success(
                                result.data != null ? result.data : "OK"));
                    } else {
                        updateState.postValue(UiState.error(
                                result.error != null ? result.error : ApiError.local("Error")));
                    }
                });
    }

    public void uploadPhoto(@NonNull File file) {
        photoState.setValue(UiState.loading());
        RequestBody requestBody = RequestBody.create(MediaType.parse("image/jpeg"), file);
        MultipartBody.Part part = MultipartBody.Part.createFormData(
                "archivo", file.getName(), requestBody);
        apiClient.postMultipart(ENDPOINT_PHOTO, part,
                json -> {
                    JsonObject obj = json.getAsJsonObject();
                    return obj.has("mensaje") ? obj.get("mensaje").getAsString() : "OK";
                },
                result -> {
                    if (result.isSuccess()) {
                        photoState.postValue(UiState.success(
                                result.data != null ? result.data : "OK"));
                    } else {
                        photoState.postValue(UiState.error(
                                result.error != null ? result.error : ApiError.local("Error")));
                    }
                });
    }

    public void resetUpdateState() { updateState.setValue(null); }
    public void resetPhotoState()  { photoState.setValue(null); }

    // ── Notificaciones (local) ────────────────────────────────────────────────

    public boolean areNotificationsEnabled() {
        return prefs.getBoolean(KEY_NOTIFICATIONS, false);
    }

    public void setNotificationsEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_NOTIFICATIONS, enabled).apply();
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    public void logout() {
        String refreshToken = sessionManager.getRefreshToken();
        if (!StringUtils.hasText(refreshToken)) {
            sessionManager.logout();
            logoutState.setValue(UiState.success("Local"));
            return;
        }
        logoutState.setValue(UiState.loading());
        authRepository.logout(refreshToken, result -> {
            sessionManager.logout();
            if (result.isSuccess()) {
                logoutState.postValue(UiState.success(
                        result.data != null ? result.data : "OK"));
            } else {
                logoutState.postValue(UiState.error(
                        result.error != null ? result.error : ApiError.local("Error")));
            }
        });
    }

    // ── Mapper DTO → Dominio ──────────────────────────────────────────────────

    @NonNull
    private PerfilUsuario mapToDomain(@NonNull ProfileInfoDto dto) {
        return new PerfilUsuario(
                dto.nombreUsuario,
                dto.email,
                dto.fechaNacimiento,
                dto.totalPuntos,
                dto.nombreReal,
                dto.genero,
                dto.altura,
                dto.peso,
                dto.provincia,
                dto.fotoPerfil,
                dto.perfilVisible
        );
    }

    @Override
    protected void onCleared() {
        authRepository.cancelAll();
        apiClient.cancelAll();
        super.onCleared();
    }
}