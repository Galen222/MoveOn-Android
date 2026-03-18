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

public final class RankingViewModel extends AndroidViewModel {

    private final RankingRepository repository;

    private final MutableLiveData<UiState<List<RankingItemDto>>> rankingState =
            new MutableLiveData<>(UiState.success(null));

    @Nullable
    private String provinciaActual = null;

    @NonNull
    public LiveData<UiState<List<RankingItemDto>>> getRankingState() {
        return rankingState;
    }

    public RankingViewModel(@NonNull Application application) {
        super(application);
        repository = ServiceLocator.getInstance(application).newRankingRepository();
        cargarRanking(null);
    }

    public void cargarRanking(@Nullable String provincia) {
        provinciaActual = provincia;
        rankingState.setValue(UiState.loading());
        repository.obtenerRanking(provincia, result -> {
            if (result.isSuccess()) {
                rankingState.postValue(UiState.success(result.data));
            } else {
                rankingState.postValue(UiState.error(result.error));
            }
        });
    }

    public void recargar() {
        cargarRanking(provinciaActual);
    }

    @Override
    protected void onCleared() {
        repository.cancelAll();
        super.onCleared();
    }
}