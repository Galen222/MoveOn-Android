package com.proyecto.moveon.ui.profile;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.gson.JsonObject;
import com.proyecto.moveon.R;
import com.proyecto.moveon.core.api.ApiError;
import com.proyecto.moveon.core.i18n.AppLanguageManager;
import com.proyecto.moveon.core.settings.AppSettingsManager;
import com.proyecto.moveon.data.profile.PerfilRepository;
import com.proyecto.moveon.data.session.AuthRepository;
import com.proyecto.moveon.data.session.SecureSessionManager;
import com.proyecto.moveon.domain.profile.PerfilUsuario;
import com.proyecto.moveon.ui.common.UiState;
import com.proyecto.moveon.utils.OfflineSessionCleaner;
import com.proyecto.moveon.utils.StringUtils;

import java.io.File;

public class ProfileViewModel extends AndroidViewModel {

    private final AuthRepository authRepository;
    private final SecureSessionManager sessionManager;
    private final PerfilRepository perfilRepository;

    private final MediatorLiveData<UiState<PerfilUsuario>> perfilState = new MediatorLiveData<>();
    private final MutableLiveData<UiState<String>> updateState = new MutableLiveData<>();
    private final MutableLiveData<UiState<String>> photoState  = new MutableLiveData<>();
    private final MutableLiveData<UiState<String>> logoutState = new MutableLiveData<>();

    @Nullable private final String accountKey;
    @Nullable private LiveData<PerfilUsuario> perfilSource;

    public ProfileViewModel(@NonNull Application application) {
        super(application);
        authRepository = new AuthRepository(application);
        sessionManager = SecureSessionManager.getInstance(application);
        perfilRepository = new PerfilRepository(application);
        accountKey = sessionManager.getAccountKey();

        attachPerfilSource();
    }

    public LiveData<UiState<PerfilUsuario>> getPerfilState() { return perfilState; }
    public LiveData<UiState<String>> getUpdateState() { return updateState; }
    public LiveData<UiState<String>> getPhotoState()  { return photoState; }
    public LiveData<UiState<String>> getLogoutState() { return logoutState; }

    public String getUsername() {
        String u = sessionManager.getUsername();
        return StringUtils.hasText(u) ? u : null;
    }

    @NonNull
    public String getAppLanguageMode() {
        return AppLanguageManager.getSelectedMode(getApplication());
    }

    public boolean hasManualAppLanguageSelection() {
        return AppLanguageManager.hasManualSelection(getApplication());
    }

    public void setAppLanguageMode(@NonNull String mode) {
        AppLanguageManager.saveAndApply(getApplication(), mode);
    }

    public void loadPerfil() {
        if (accountKey == null) {
            perfilState.setValue(UiState.error(ApiError.local(getApplication().getString(R.string.error_no_sesion_activa))));
            return;
        }

        UiState<PerfilUsuario> current = perfilState.getValue();
        if (current == null || current.data == null) {
            perfilState.setValue(UiState.loading());
        }

        perfilRepository.refreshPerfil(accountKey, error -> {
            UiState<PerfilUsuario> latest = perfilState.getValue();
            if (error != null && (latest == null || latest.data == null)) {
                perfilState.postValue(UiState.error(error));
            }
        });
    }

    public void updatePerfil(@NonNull JsonObject patchJson) {
        if (accountKey == null) {
            updateState.setValue(UiState.error(ApiError.local(getApplication().getString(R.string.error_no_sesion_activa))));
            return;
        }

        if (patchJson.isEmpty()) {
            updateState.setValue(UiState.error(ApiError.local(getApplication().getString(R.string.error_no_hay_cambios))));
            return;
        }

        updateState.setValue(UiState.loading());
        perfilRepository.applyLocalPatchAndEnqueue(accountKey, patchJson, result -> {
            if (PerfilRepository.UpdateResult.STATUS_FAILED.equals(result.status)) {
                updateState.postValue(UiState.error(
                        result.error != null ? result.error : ApiError.local(getApplication().getString(R.string.vm_error_generico))));
            } else {
                updateState.postValue(UiState.success(result.status));
            }
        });
    }

    public void uploadPhoto(@NonNull File file) {
        if (accountKey == null) {
            photoState.setValue(UiState.error(ApiError.local(getApplication().getString(R.string.error_no_sesion_activa))));
            return;
        }

        photoState.setValue(UiState.loading());
        perfilRepository.uploadPhotoLocalFirst(accountKey, file, result -> {
            if (PerfilRepository.UpdateResult.STATUS_FAILED.equals(result.status)) {
                photoState.postValue(UiState.error(
                        result.error != null ? result.error : ApiError.local(getApplication().getString(R.string.vm_error_generico))));
            } else {
                photoState.postValue(UiState.success(result.status));
            }
        });
    }

    public void resetUpdateState() { updateState.setValue(null); }
    public void resetPhotoState()  { photoState.setValue(null); }

    public void logout() {
        String refreshToken = sessionManager.getRefreshToken();
        if (!StringUtils.hasText(refreshToken)) {
            OfflineSessionCleaner.clearSessionAndLocalDataBlocking(getApplication());
            logoutState.setValue(UiState.success("Local"));
            return;
        }
        logoutState.setValue(UiState.loading());
        authRepository.logout(refreshToken, result -> {
            OfflineSessionCleaner.clearSessionAndLocalDataBlocking(getApplication());
            if (result.isSuccess()) {
                logoutState.postValue(UiState.success(
                        result.data != null ? result.data : "OK"));
            } else {
                logoutState.postValue(UiState.error(
                        result.error != null ? result.error : ApiError.local(getApplication().getString(R.string.vm_error_generico))));
            }
        });
    }

    private void attachPerfilSource() {
        if (accountKey == null) return;
        perfilSource = perfilRepository.observePerfil(accountKey);
        perfilState.addSource(perfilSource, perfil -> {
            if (perfil != null) perfilState.setValue(UiState.success(perfil));
        });
    }

    @Override
    protected void onCleared() {
        authRepository.cancelAll();
        if (perfilSource != null) {
            perfilState.removeSource(perfilSource);
        }
        perfilRepository.cancelOngoing();
        super.onCleared();
    }
}

