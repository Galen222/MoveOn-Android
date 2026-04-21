
package com.proyecto.moveon.ui.profile;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

import com.proyecto.moveon.R;
import com.proyecto.moveon.core.i18n.AppLanguageManager;
import com.proyecto.moveon.app.ServiceLocator;
import com.proyecto.moveon.core.api.ApiError;
import com.proyecto.moveon.data.activities.ActivityRepository;
import com.proyecto.moveon.data.session.SecureSessionManager;
import com.proyecto.moveon.domain.activity.ActividadItem;
import com.proyecto.moveon.ui.common.UiState;

import java.util.Collections;
import java.util.List;

/**
 * ViewModel del bottom sheet de compartir rutas.
 *
 * <p>Se apoya en el mismo repositorio de actividades que ya usa la app para:
 * observar la lista local de actividades y disparar una sincronización con el
 * backend cuando el sheet se abre.</p>
 */
public class ShareRoutesViewModel extends AndroidViewModel {

    private final ActivityRepository activityRepository;
    private final MediatorLiveData<UiState<List<ActividadItem>>> state = new MediatorLiveData<>();

    @Nullable private final String accountKey;
    @Nullable private LiveData<List<ActividadItem>> actividadesSource;

    /**
     * Inicializa el ViewModel pidiendo una instancia propia del repositorio
     * de actividades al {@link ServiceLocator} (para poder cancelarla al
     * destruirse sin afectar a otras pantallas) y la {@code accountKey}
     * del usuario logueado para filtrar por dueño.
     *
     * @param application application usada para localizar los singletons y el repositorio.
     */
    public ShareRoutesViewModel(@NonNull Application application) {
        super(application);
        ServiceLocator locator = ServiceLocator.getInstance(application);
        activityRepository = locator.newActivityRepository();
        accountKey = SecureSessionManager.getInstance(application).getAccountKey();
        attachSource();
    }

    /**
     * Estado observable que consume la UI del bottom sheet.
     */
    @NonNull
    public LiveData<UiState<List<ActividadItem>>> getState() {
        return state;
    }

    /**
     * Carga las rutas del usuario actual.
     *
     * <p>Primero deja que Room pinte el contenido local y además solicita una
     * actualización remota para que la lista se refresque si hay conexión.</p>
     */
    public void load() {
        if (accountKey == null) {
            state.setValue(UiState.error(ApiError.local(
                    AppLanguageManager.getString(getApplication(), R.string.error_no_sesion_activa)
            )));
            return;
        }

        UiState<List<ActividadItem>> current = state.getValue();
        if (current == null || current.data == null) {
            state.setValue(UiState.loading());
        }

        // Se encola sync offline-first por si hay cambios pendientes locales.
        activityRepository.enqueueSync();

        // Además se pide snapshot al servidor para que Room se actualice.
        activityRepository.refreshFromServer(accountKey, error -> {
            UiState<List<ActividadItem>> latest = state.getValue();
            if (error != null && (latest == null || latest.data == null)) {
                state.postValue(UiState.error(error));
            }
        });
    }

    /**
     * Conecta la fuente de Room con el estado del ViewModel.
     */
    private void attachSource() {
        if (accountKey == null) {
            return;
        }

        actividadesSource = activityRepository.observeActividades(accountKey);
        state.addSource(actividadesSource, items -> {
            List<ActividadItem> safeItems = items != null ? items : Collections.emptyList();
            state.setValue(UiState.success(safeItems));
        });
    }

    @Override
    /**
     * Cancela todas las peticiones en vuelo del repositorio de actividades
     * cuando el ViewModel se destruye, para no entregar resultados a una UI
     * que ya no existe.
     */
    protected void onCleared() {
        super.onCleared();
        // Cada consumer usa su propia instancia del repositorio; por eso se cancela aquí.
        activityRepository.cancelAll();
    }
}

