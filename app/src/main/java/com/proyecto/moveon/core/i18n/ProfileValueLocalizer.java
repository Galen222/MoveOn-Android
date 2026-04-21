package com.proyecto.moveon.core.i18n;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.proyecto.moveon.R;
import com.proyecto.moveon.utils.StringUtils;

/**
 * Convierte entre los valores canónicos del perfil y sus etiquetas visibles localizadas.
 *
 * <p>Esta capa evita acoplar la UI a los textos mostrados al usuario y mantiene estable
 * el contrato con backend aunque cambien las traducciones o el copy de pantalla.</p>
 */
public final class ProfileValueLocalizer {

    /**
     * Evita instancias de una clase utilitaria dedicada a conversiones de etiquetas de perfil.
     */
    private ProfileValueLocalizer() {}

    /**
     * Convierte el valor canónico de género al texto localizado que debe mostrarse en pantalla.
     *
     * @param context contexto desde el que resolver arrays y recursos.
     * @param canonicalValue valor persistido o recibido del backend.
     * @return etiqueta visible para la UI.
     */
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

    /**
     * Obtiene el valor canónico de género a partir de una etiqueta visible o de un valor ya normalizado.
     *
     * @param context contexto desde el que resolver catálogos localizados.
     * @param label etiqueta visible o valor ya canónico.
     * @return valor canónico de género o {@code null} si no hay entrada útil.
     */
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

    /**
     * Traduce la provincia persistida al label localizado que utiliza la interfaz.
     *
     * @param context contexto desde el que resolver arrays y recursos.
     * @param canonicalValue valor persistido o recibido del backend.
     * @return etiqueta visible para la UI.
     */
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

    /**
     * Resuelve el valor canónico de provincia partiendo de una etiqueta visible o de un backend value.
     *
     * <p>El primer elemento del catálogo representa "no indicada", por eso se devuelve {@code null}
     * cuando la coincidencia cae en esa posición.</p>
     *
     * @param context contexto desde el que resolver catálogos localizados.
     * @param label etiqueta visible o valor ya canónico.
     * @return valor canónico de provincia o {@code null} cuando representa "no indicada".
     */
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

    /**
     * Devuelve la etiqueta localizada del tipo de actividad almacenado en backend.
     *
     * @param context contexto desde el que resolver arrays y recursos.
     * @param canonicalValue valor persistido o recibido del backend.
     * @return etiqueta visible del tipo de actividad.
     */
    @NonNull
    public static String displayActivityType(@NonNull Context context, @Nullable String canonicalValue) {
        if (!StringUtils.hasText(canonicalValue)) return "";

        int index = indexOf(context.getResources().getStringArray(R.array.activity_types_backend_values), canonicalValue);
        if (index >= 0) {
            return context.getResources().getStringArray(R.array.activity_types_labels)[index];
        }
        return canonicalValue;
    }

    /**
     * Convierte una etiqueta de tipo de actividad al valor canónico que espera la API.
     *
     * @param context contexto desde el que resolver catálogos localizados.
     * @param label etiqueta visible o valor ya canónico.
     * @return valor canónico del tipo de actividad o {@code null} si no hay entrada útil.
     */
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

    /**
     * Busca una coincidencia exacta dentro del catálogo recibido y devuelve su índice o {@code -1}.
     *
     * @param values catálogo en el que buscar.
     * @param needle texto a localizar.
     * @return índice de coincidencia o {@code -1} cuando no existe.
     */
    private static int indexOf(@NonNull String[] values, @Nullable String needle) {
        if (!StringUtils.hasText(needle)) return -1;
        for (int i = 0; i < values.length; i++) {
            if (needle.equals(values[i])) return i;
        }
        return -1;
    }
}
