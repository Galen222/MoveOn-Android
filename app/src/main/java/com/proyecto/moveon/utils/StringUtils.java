package com.proyecto.moveon.utils;

import androidx.annotation.Nullable;

/**
 * Utilidades pequeñas para trabajar con {@link String} y {@link CharSequence} de forma segura.
 */
public final class StringUtils {

    /**
     * Constructor privado: clase de utilidades con sólo métodos estáticos,
     * no está pensada para instanciarse.
     */
    private StringUtils() {
        // Utility class
    }

    /**
     * Convierte un {@link CharSequence} a {@link String} seguro (nunca null) y aplica trim().
     *
     * @param text texto potencialmente nulo procedente de UI u otra fuente.
     * @return cadena no nula ya recortada en ambos extremos.
     */
    public static String textOf(@Nullable CharSequence text) {
        return (text == null) ? "" : text.toString().trim();
    }

    /**
     * Indica si un {@link String} contiene algún carácter útil tras aplicar trim().
     *
     * @param text cadena a comprobar.
     * @return {@code true} cuando la cadena no es nula y conserva contenido visible.
     */
    public static boolean hasText(@Nullable String text) {
        return text != null && !text.trim().isEmpty();
    }

    /**
     * Sobrecarga para {@link CharSequence} procedente de widgets como {@code TextView} o {@code EditText}.
     *
     * @param text texto a comprobar.
     * @return {@code true} cuando {@link #textOf(CharSequence)} produce contenido no vacío.
     */
    public static boolean hasText(@Nullable CharSequence text) {
        return hasText(textOf(text));
    }
}