package com.proyecto.moveon.ui.ranking;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.proyecto.moveon.app.ServiceLocator;
import com.proyecto.moveon.data.ranking.RankingRepository;
import com.proyecto.moveon.data.ranking.dto.RankingItemDto;
import com.proyecto.moveon.ui.common.UiState;

import java.util.List;

/**
 * ViewModel del ranking.
 *
 * <p>Este ViewModel expone un {@link UiState} con la lista del ranking nacional
 * o provincial y protege la UI frente a respuestas tardías de peticiones sucesivas.</p>
 *
 * <p>Para ello aplica dos defensas:</p>
 * <ol>
 *     <li>Cancela las peticiones anteriores del repositorio antes de lanzar una nueva.</li>
 *     <li>Asigna un identificador incremental a cada carga e ignora cualquier callback
 *     tardío que ya no corresponda a la petición activa.</li>
 * </ol>
 */
public final class RankingViewModel extends AndroidViewModel {

    /** Repositorio del módulo de ranking. */
    @NonNull
    private final RankingRepository repository;

    /**
     * Estado observable consumido por la UI.
     *
     * <p>Se inicializa con éxito vacío para que el observer del fragment pueda decidir
     * cuándo mostrar loading, lista, vacío o error sin disparar una vista residual.</p>
     */
    @NonNull
    private final MutableLiveData<UiState<List<RankingItemDto>>> rankingState =
            new MutableLiveData<>(UiState.success(null));

    /** Provincia actualmente seleccionada. {@code null} implica ranking España. */
    @Nullable
    private String provinciaActual = null;

    /**
     * Secuencia monótona de solicitudes de ranking.
     *
     * <p>Cada vez que se pide un ranking nuevo se incrementa este contador y el valor
     * generado pasa a ser la única respuesta considerada válida.</p>
     */
    private int requestSequence = 0;

    /** Identificador de la solicitud activa más reciente. */
    private int activeRequestId = 0;

    /**
     * @param application contexto de aplicación necesario para construir el repositorio.
     */
    public RankingViewModel(@NonNull Application application) {
        super(application);
        repository = ServiceLocator.getInstance(application).newRankingRepository();

        // Carga inicial del ranking nacional al abrir el bottom sheet.
        cargarRanking(null);
    }

    /**
     * Estado observable del ranking consumido por el fragment.
     *
     * @return LiveData con loading, datos o error.
     */
    @NonNull
    public LiveData<UiState<List<RankingItemDto>>> getRankingState() {
        return rankingState;
    }

    /**
     * Carga el ranking para España o para una provincia concreta.
     *
     * <p>Esta versión invalida explícitamente cualquier respuesta anterior. Si una petición
     * vieja devuelve tarde, su callback se ignora y no puede reintroducir una lista obsoleta
     * en pantalla.</p>
     *
     * @param provincia provincia opcional; {@code null} o vacío implica ranking nacional.
     */
    public void cargarRanking(@Nullable String provincia) {
        provinciaActual = provincia;

        // Se genera un nuevo token lógico de petición antes de lanzar la request.
        final int requestId = ++requestSequence;
        activeRequestId = requestId;

        // Se entra en loading inmediatamente para que la UI oculte cualquier contenido previo.
        rankingState.setValue(UiState.loading());

        // Defensa 1: cancelar llamadas anteriores en vuelo asociadas a este repositorio.
        repository.cancelAll();

        repository.obtenerRanking(provincia, result -> {
            // Defensa 2: ignorar callbacks tardíos de requests ya obsoletas.
            if (requestId != activeRequestId) {
                return;
            }

            if (result.isSuccess()) {
                rankingState.postValue(UiState.success(result.data));
            } else {
                rankingState.postValue(UiState.error(result.error));
            }
        });
    }

    /**
     * Repite la última carga usando el filtro actualmente activo.
     *
     * <p>Delega en {@link #cargarRanking(String)} reutilizando la provincia seleccionada en la
     * carga más reciente para que la UI pueda ofrecer un simple botón de reintento.</p>
     */
    public void recargar() {
        cargarRanking(provinciaActual);
    }

    @Override
    /**
     * Cancela todas las peticiones en curso del repositorio antes de que
     * el ViewModel muera. Cada ViewModel recibe su propia instancia del
     * repositorio, así que esta cancelación no afecta a otras pantallas.
     */
    protected void onCleared() {
        repository.cancelAll();
    }
}
