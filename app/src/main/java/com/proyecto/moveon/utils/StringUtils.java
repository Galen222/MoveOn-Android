package com.proyecto.moveon.utils;

import androidx.annotation.Nullable;

/**
 * Utilidades pequeñas para trabajar con Strings/CharSequence de forma segura.
 */
public final class StringUtils {

    private StringUtils() {
        // Utility class
    }

    /**
     * Convierte un CharSequence a String seguro (nunca null) y aplica trim().
     */
    public static String textOf(@Nullable CharSequence text) {
        return (text == null) ? "" : text.toString().trim();
    }

    /**
     * true si el texto no es null y, tras hacer trim(), no queda vacío.
     */
    public static boolean hasText(@Nullable String text) {
        return text != null && !text.trim().isEmpty();
    }

    /**
     * Sobrecarga para CharSequence (TextView/EditText.getText()).
     */
    public static boolean hasText(@Nullable CharSequence text) {
        return hasText(textOf(text));
    }
}