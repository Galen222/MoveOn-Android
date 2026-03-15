package com.proyecto.moveon.core.i18n;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.proyecto.moveon.R;
import com.proyecto.moveon.utils.StringUtils;

/**
 * Separa los valores canónicos que viajan al backend de sus labels visibles
 * localizados en la UI.
 */
public final class ProfileValueLocalizer {

    private ProfileValueLocalizer() {}

    @NonNull
    public static String displayGenero(@NonNull Context context, @Nullable String canonicalValue) {
        if (!StringUtils.hasText(canonicalValue)) {
            return context.getString(R.string.profile_not_indicated);
        }
        int index = indexOf(context.getResources().getStringArray(R.array.generos_backend_values), canonicalValue);
        if (index >= 0) {
            return context.getResources().getStringArray(R.array.generos_labels)[index];
        }
        return canonicalValue;
    }

    @Nullable
    public static String canonicalGeneroFromLabel(@NonNull Context context, @Nullable String label) {
        if (!StringUtils.hasText(label)) return null;

        String[] labels = context.getResources().getStringArray(R.array.generos_labels);
        String[] backendValues = context.getResources().getStringArray(R.array.generos_backend_values);

        int index = indexOf(labels, label);
        if (index >= 0) return backendValues[index];

        index = indexOf(backendValues, label);
        return index >= 0 ? backendValues[index] : label;
    }

    @NonNull
    public static String displayProvincia(@NonNull Context context, @Nullable String canonicalValue) {
        if (!StringUtils.hasText(canonicalValue)) {
            return context.getString(R.string.profile_not_indicated);
        }
        int index = indexOf(context.getResources().getStringArray(R.array.provincias_backend_values), canonicalValue);
        if (index >= 0) {
            return context.getResources().getStringArray(R.array.provincias_labels)[index];
        }
        return canonicalValue;
    }

    @Nullable
    public static String canonicalProvinciaFromLabel(@NonNull Context context, @Nullable String label) {
        if (!StringUtils.hasText(label)) return null;

        String[] labels = context.getResources().getStringArray(R.array.provincias_labels);
        String[] backendValues = context.getResources().getStringArray(R.array.provincias_backend_values);

        int index = indexOf(labels, label);
        if (index < 0) {
            index = indexOf(backendValues, label);
        }
        if (index < 0) {
            return label;
        }
        return index == 0 ? null : backendValues[index];
    }

    @NonNull
    public static String displayActivityType(@NonNull Context context, @Nullable String canonicalValue) {
        if (!StringUtils.hasText(canonicalValue)) return "";

        int index = indexOf(context.getResources().getStringArray(R.array.activity_types_backend_values), canonicalValue);
        if (index >= 0) {
            return context.getResources().getStringArray(R.array.activity_types_labels)[index];
        }
        return canonicalValue;
    }

    @Nullable
    public static String canonicalActivityTypeFromLabel(@NonNull Context context, @Nullable String label) {
        if (!StringUtils.hasText(label)) return null;

        String[] labels = context.getResources().getStringArray(R.array.activity_types_labels);
        String[] backendValues = context.getResources().getStringArray(R.array.activity_types_backend_values);

        int index = indexOf(labels, label);
        if (index >= 0) return backendValues[index];

        index = indexOf(backendValues, label);
        return index >= 0 ? backendValues[index] : label;
    }

    private static int indexOf(@NonNull String[] values, @Nullable String needle) {
        if (!StringUtils.hasText(needle)) return -1;
        for (int i = 0; i < values.length; i++) {
            if (needle.equals(values[i])) return i;
        }
        return -1;
    }
}
