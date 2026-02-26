package com.proyecto.moveon.ui.common;

import androidx.annotation.Nullable;

public class UiState<T> {
    public final boolean loading;
    @Nullable public final T data;
    @Nullable public final String error;

    private UiState(boolean loading, @Nullable T data, @Nullable String error) {
        this.loading = loading;
        this.data = data;
        this.error = error;
    }

    public static <T> UiState<T> loading() { return new UiState<>(true, null, null); }
    public static <T> UiState<T> success(T data) { return new UiState<>(false, data, null); }
    public static <T> UiState<T> error(String msg) { return new UiState<>(false, null, msg); }
}