package com.proyecto.moveon.core.api;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class ApiResult<T> {

    @Nullable public final T data;
    @Nullable public final ApiError error;

    private ApiResult(@Nullable T data, @Nullable ApiError error) {
        this.data = data;
        this.error = error;
    }

    public boolean isSuccess() { return error == null; }

    @NonNull
    public static <T> ApiResult<T> success(@NonNull T data) {
        return new ApiResult<>(data, null);
    }

    @NonNull
    public static ApiResult<Void> successVoid() {
        return new ApiResult<>(null, null);
    }

    @NonNull
    public static <T> ApiResult<T> failure(@NonNull ApiError error) {
        return new ApiResult<>(null, error);
    }
}