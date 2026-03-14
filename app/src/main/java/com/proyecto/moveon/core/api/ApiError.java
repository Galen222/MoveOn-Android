
package com.proyecto.moveon.core.api;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ApiError {

    @NonNull private final ApiErrorType type;
    private final int httpCode;
    @NonNull private final String message;
    @Nullable private final String errorCode;

    // fieldErrors: {"email": ["Email inválido"], "password": ["..."] }
    @NonNull private final Map<String, List<String>> fieldErrors;

    @Nullable private final String raw;

    public ApiError(@NonNull ApiErrorType type,
                    int httpCode,
                    @NonNull String message,
                    @Nullable String errorCode,
                    @Nullable Map<String, List<String>> fieldErrors,
                    @Nullable String raw) {
        this.type = type;
        this.httpCode = httpCode;
        this.message = message;
        this.errorCode = errorCode;
        this.fieldErrors = fieldErrors != null ? fieldErrors : Collections.emptyMap();
        this.raw = raw;
    }

    @NonNull public ApiErrorType getType() { return type; }
    public int getHttpCode() { return httpCode; }
    @NonNull public String getMessage() { return message; }
    @Nullable public String getErrorCode() { return errorCode; }
    @NonNull public Map<String, List<String>> getFieldErrors() { return fieldErrors; }
    @Nullable public String getRaw() { return raw; }

    public boolean hasFieldErrors() { return !fieldErrors.isEmpty(); }

    @Nullable
    public String firstFieldMessage(@NonNull String... keys) {
        if (keys.length == 0) return null;
        for (String k : keys) {
            if (k == null) continue;
            List<String> msgs = fieldErrors.get(k);
            if (msgs != null && !msgs.isEmpty()) {
                String v = msgs.get(0);
                if (v != null && v.trim().length() > 0) return v;
            }
        }
        return null;
    }

    @NonNull
    public static ApiError local(@NonNull String message) {
        return new ApiError(ApiErrorType.UNKNOWN, 0, message, null, null, null);
    }

    @NonNull
    public static ApiError typed(@NonNull ApiErrorType type, @NonNull String message) {
        return new ApiError(type, 0, message, null, null, null);
    }

    @NonNull
    public static ApiError typed(@NonNull ApiErrorType type, int httpCode, @NonNull String message) {
        return new ApiError(type, httpCode, message, null, null, null);
    }

    @NonNull
    public static ApiError typed(@NonNull ApiErrorType type,
                                 int httpCode,
                                 @NonNull String message,
                                 @Nullable String errorCode) {
        return new ApiError(type, httpCode, message, errorCode, null, null);
    }

    @NonNull
    public ApiError withFieldError(@NonNull String key, @NonNull String value) {
        Map<String, List<String>> m = new HashMap<>(this.fieldErrors);
        List<String> list = m.get(key);
        if (list == null) list = new ArrayList<>();
        list.add(value);
        m.put(key, list);
        return new ApiError(this.type, this.httpCode, this.message, this.errorCode, m, this.raw);
    }
}
