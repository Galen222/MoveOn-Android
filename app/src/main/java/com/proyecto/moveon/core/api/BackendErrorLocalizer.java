package com.proyecto.moveon.core.api;

import android.content.Context;
import android.content.res.Resources;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.proyecto.moveon.R;
import com.proyecto.moveon.utils.StringUtils;

import java.util.Locale;

public final class BackendErrorLocalizer {

    private BackendErrorLocalizer() {}

    @Nullable
    public static String localize(@NonNull Context context,
                                  @Nullable String errorCode,
                                  @Nullable String retryAfterSeconds) {
        if (!StringUtils.hasText(errorCode)) return null;

        String normalized = normalizeErrorCode(errorCode);
        if (!StringUtils.hasText(normalized)) return null;

        if ("rate_limit_exceeded".equals(normalized) && StringUtils.hasText(retryAfterSeconds)) {
            return context.getString(R.string.api_error_rate_limit_retry, retryAfterSeconds);
        }

        Resources res = context.getResources();
        String packageName = context.getPackageName();
        int resId = res.getIdentifier("backend_error_" + normalized, "string", packageName);
        if (resId == 0) return null;
        return context.getString(resId);
    }

    @NonNull
    public static String normalizeErrorCode(@NonNull String errorCode) {
        return errorCode
                .trim()
                .toLowerCase(Locale.US)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");
    }
}
