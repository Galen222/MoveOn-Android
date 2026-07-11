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
import com.proyecto.moveon.app.ServiceLocator;
import com.proyecto.moveon.core.api.ApiError;
import com.proyecto.moveon.core.concurrency.MoveOnExecutors;
import com.proyecto.moveon.core.i18n.AppLanguageManager;
import com.proyecto.moveon.data.profile.PerfilRepository;
import com.proyecto.moveon.data.session.AuthRepository;
import com.proyecto.moveon.data.session.SecureSessionManager;
import com.proyecto.moveon.domain.profile.PerfilUsuario;
import com.proyecto.moveon.ui.common.UiState;
import com.proyecto.moveon.utils.OfflineSessionCleaner;
import com.proyecto.moveon.utils.StringUtils;

import java.io.File;
/**
 * ViewModel que expone el estado y las acciones de profile.
 */
public class ProfileViewModel extends AndroidViewModel {

    private final AuthRepository authRepository;
    private final SecureSessionManager sessionManager;
    private final PerfilRepository perfilRepository;

    private final MediatorLiveData<UiState<PerfilUsuario>> perfilState       = new MediatorLiveData<>();
    private final MutableLiveData<UiState<String>>         updateState       = new MutableLiveData<>();
    private final MutableLiveData<UiState<String>>         photoState        = new MutableLiveData<>();
    private final MutableLiveData<UiState<String>>         logoutState       = new MutableLiveData<>();
    private final MutableLiveData<UiState<String>>         deleteAccountState = new MutableLiveData<>();

    @Nullable private final String accountKey;
    @Nullable private LiveData<PerfilUsuario> perfilSource;
    @Nullable private JsonObject lastFailedPatchJson;
    @Nullable private String lastFailedPhotoPath;

    /**
     * Inicializa el ViewModel de perfil y engancha la fuente reactiva del perfil cacheado.
     *
     * @param application aplicación usada para resolver dependencias y recursos.
     */
    public ProfileViewModel(@NonNull Application application) {
        super(application);
        // La creación centralizada vía ServiceLocator mantiene las dependencias alineadas.
        ServiceLocator locator = ServiceLocator.getInstance(application);
        authRepository = locator.newAuthRepository();
        sessionManager = SecureSessionManager.getInstance(application);
        perfilRepository = locator.newPerfilRepository();
        accountKey = sessionManager.getAccountKey();

        // La carga inicial del caché se hace en hilo IO para evitar
        // accesos síncronos a Room desde el main thread.
        // En instalación limpia, Room puede crear tablas en esta llamada y
        // ese trabajo no debe ejecutarse en la UI.
        if (accountKey != null) {
            MoveOnExecutors.executeIo(() -> {
                PerfilUsuario cached = perfilRepository.getCachedPerfilNow(accountKey);
                if (cached != null) {
                    perfilState.postValue(UiState.success(cached));
                }
            });
        }

        attachPerfilSource();
    }

    /**
     * Expone el estado observable del perfil cargado.
     *
     * @return {@link LiveData} con el perfil actual, su carga o sus errores.
     */
    public LiveData<UiState<PerfilUsuario>> getPerfilState()        { return perfilState; }
    /**
     * Expone el estado de las actualizaciones textuales del perfil.
     *
     * @return {@link LiveData} del último update lanzado.
     */
    public LiveData<UiState<String>>        getUpdateState()        { return updateState; }
    /**
     * Expone el estado de subida y sincronización de la foto de perfil.
     *
     * @return {@link LiveData} asociado a la foto.
     */
    public LiveData<UiState<String>>        getPhotoState()         { return photoState; }
    /**
     * Expone el resultado del logout actual.
     *
     * @return {@link LiveData} del flujo de cierre de sesión.
     */
    public LiveData<UiState<String>>        getLogoutState()        { return logoutState; }
    /**
     * Expone el estado del borrado de cuenta.
     *
     * @return {@link LiveData} del flujo de eliminación.
     */
    public LiveData<UiState<String>>        getDeleteAccountState() { return deleteAccountState; }

    /**
     * Devuelve el username actual de la sesión si sigue disponible.
     *
     * @return nombre de usuario o {@code null} si no hay sesión utilizable.
     */
    public String getUsername() {
        String u = sessionManager.getUsername();
        return StringUtils.hasText(u) ? u : null;
    }

    /**
     * Devuelve el modo de idioma actualmente seleccionado en la app.
     *
     * @return modo de idioma persistido.
     */
    @NonNull
    public String getAppLanguageMode() {
        return AppLanguageManager.getSelectedMode(getApplication());
    }

    // API pública: selector de idioma en perfil (pendiente de UI)
    /**
     * Indica si el usuario seleccionó manualmente el idioma de la app.
     *
     * @return {@code true} si existe una selección explícita guardada.
     */
    @SuppressWarnings("unused")
    public boolean hasManualAppLanguageSelection() {
        return AppLanguageManager.hasManualSelection(getApplication());
    }

    // API pública: selector de idioma en perfil (pendiente de UI)
    /**
     * Guarda y aplica un nuevo modo de idioma de aplicación.
     *
     * @param mode modo seleccionado por el usuario.
     */
    @SuppressWarnings("unused")
    public void setAppLanguageMode(@NonNull String mode) {
        AppLanguageManager.saveAndApply(getApplication(), mode);
    }

    /**
     * Fuerza una recarga del perfil desde red manteniendo el caché visible si ya existe.
     */
    public void loadPerfil() {
        if (accountKey == null) {
            perfilState.setValue(UiState.error(ApiError.local(AppLanguageManager.getString(getApplication(), R.string.error_no_sesion_activa))));
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

    /**
     * Aplica un patch optimista sobre el perfil y lo encola para sincronización.
     *
     * @param patchJson cambios a enviar al backend.
     */
    public void updatePerfil(@NonNull JsonObject patchJson) {
        if (accountKey == null) {
            updateState.setValue(UiState.error(ApiError.local(AppLanguageManager.getString(getApplication(), R.string.error_no_sesion_activa))));
            return;
        }

        if (patchJson.isEmpty()) {
            updateState.setValue(UiState.error(ApiError.local(AppLanguageManager.getString(getApplication(), R.string.error_no_hay_cambios))));
            return;
        }

        lastFailedPatchJson = patchJson.deepCopy();
        // Los campos se aplican de forma optimista en Room y la UI se
        // actualiza instantáneamente vía perfilState (Room LiveData).
        // Activar loading aquí dispararía el overlay fullscreen (clickable=true),
        // que se cancelaba 5 ms después por la emisión de perfilState y luego
        // no se quitaba hasta que el timeout de red terminaba (8-30 s con
        // backend caído). Resultado: overlay bloqueante o parpadeo confuso.
        perfilRepository.applyLocalPatchAndEnqueue(accountKey, patchJson, result -> {
            if (PerfilRepository.UpdateResult.STATUS_FAILED.equals(result.status)) {
                updateState.postValue(UiState.error(
                        result.error != null ? result.error : ApiError.local(AppLanguageManager.getString(getApplication(), R.string.vm_error_generico))));
            } else {
                lastFailedPatchJson = null;
                updateState.postValue(UiState.success(result.status));
            }
        });
    }

    /**
     * Sube una nueva foto de perfil priorizando la previsualización local inmediata.
     *
     * @param file fichero de imagen seleccionado.
     */
    public void uploadPhoto(@NonNull File file) {
        if (accountKey == null) {
            photoState.setValue(UiState.error(ApiError.local(AppLanguageManager.getString(getApplication(), R.string.error_no_sesion_activa))));
            return;
        }

        lastFailedPhotoPath = file.getAbsolutePath();
        // La preview de la foto se muestra de inmediato desde pendingLocalPhotoPath
        // gracias al flujo optimista uploadPhotoLocalFirst → savePendingPhoto
        // → saveCache → Room emite → bindPerfilData muestra la preview.
        // El overlay bloqueaba la UI 8-60 s (writeTimeout) con backend caído.
        perfilRepository.uploadPhotoLocalFirst(accountKey, file, result -> {
            if (PerfilRepository.UpdateResult.STATUS_FAILED.equals(result.status)) {
                photoState.postValue(UiState.error(
                        result.error != null ? result.error : ApiError.local(AppLanguageManager.getString(getApplication(), R.string.vm_error_generico))));
            } else {
                lastFailedPhotoPath = null;
                photoState.postValue(UiState.success(result.status));
            }
        });
    }

    /**
     * Reintenta el último patch fallido del perfil si todavía existe una copia almacenada.
     */
    public void retryLastUpdate() {
        if (lastFailedPatchJson != null) {
            updatePerfil(lastFailedPatchJson.deepCopy());
        }
    }

    /**
     * Reintenta la última subida de foto fallida si el archivo local sigue existiendo.
     */
    public void retryLastPhotoUpload() {
        if (lastFailedPhotoPath == null) return;
        File file = new File(lastFailedPhotoPath);
        if (file.exists()) {
            uploadPhoto(file);
        }
    }

    /**
     * Limpia el estado observable del último update de perfil.
     */
    public void resetUpdateState() { updateState.setValue(null); }
    /**
     * Limpia el estado observable de la última subida de foto.
     */
    public void resetPhotoState()  { photoState.setValue(null); }

    /**
     * Inicia el cierre de sesión remoto y local, o limpia solo local si falta refresh token.
     */
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
                        result.error != null ? result.error : ApiError.local(AppLanguageManager.getString(getApplication(), R.string.vm_error_generico))));
            }
        });
    }

    /**
     * Solicita la eliminación definitiva de la cuenta y limpia la sesión solo cuando backend la confirma.
     */
    public void deleteAccount() {
        deleteAccountState.setValue(UiState.loading());
        perfilRepository.eliminarCuenta(result -> {
            if (result.isSuccess()) {
                // Solo limpiamos la sesión local cuando el servidor confirma el borrado.
                // Si falla (red, servidor), la cuenta sigue existiendo y el usuario
                // necesita la sesión intacta para poder reintentar.
                OfflineSessionCleaner.clearSessionAndLocalDataBlocking(getApplication());
                deleteAccountState.postValue(UiState.success(
                        result.data != null ? result.data : "OK"));
            } else {
                deleteAccountState.postValue(UiState.error(
                        result.error != null ? result.error
                                : ApiError.local(AppLanguageManager.getString(getApplication(), R.string.vm_error_generico))));
            }
        });
    }

    /**
     * Enlaza el perfil cacheado de Room con el estado expuesto por el ViewModel.
     */
    private void attachPerfilSource() {
        if (accountKey == null) return;
        perfilSource = perfilRepository.observePerfil(accountKey);
        perfilState.addSource(perfilSource, perfil -> {
            if (perfil != null) perfilState.setValue(UiState.success(perfil));
        });
    }

    /**
     * Cancela operaciones en curso y desengancha fuentes observadas al destruirse el ViewModel.
     */
    @Override
    protected void onCleared() {
        authRepository.cancelAll();
        if (perfilSource != null) {
            perfilState.removeSource(perfilSource);
        }
        perfilRepository.cancelOngoing();
    }
}

