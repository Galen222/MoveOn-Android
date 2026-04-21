
package com.proyecto.moveon.core.api;

import android.content.Context;
import android.content.res.Resources;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.proyecto.moveon.R;
import com.proyecto.moveon.core.i18n.AppLanguageManager;
import com.proyecto.moveon.utils.StringUtils;

import java.util.Locale;

/**
 * Clase responsable de backend error localizer.
 */
public final class BackendErrorLocalizer {

    /**
     * Constructor privado: clase de utilidades con sólo métodos estáticos,
     * no está pensada para instanciarse.
     */
    private BackendErrorLocalizer() {}

    @Nullable
    /**
     * Traduce un código de error del backend al mensaje legible en el idioma
     * actual de la app. Normaliza antes el código (minúsculas, guiones bajos)
     * y, si es un {@code rate_limit_exceeded} con {@code retry-after}, usa la
     * plantilla que incluye los segundos de espera.
     *
     * @param context contexto desde el que se resuelve el locale actual.
     * @param errorCode código tal y como llega del backend, admite guiones o mayúsculas.
     * @param retryAfterSeconds segundos sugeridos de espera para el caso {@code rate_limit_exceeded}.
     * @return mensaje localizado, o {@code null} si el código es vacío o no se reconoce.
     */
    public static String localize(@NonNull Context context,
                                  @Nullable String errorCode,
                                  @Nullable String retryAfterSeconds) {
        context = AppLanguageManager.localizedContext(context);
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
    /**
     * Normaliza un código de error a {@code snake_case} puro: recorta, pasa
     * a minúsculas, sustituye cualquier carácter no alfanumérico por
     * {@code _} y colapsa los guiones bajos repetidos. Así el mismo error
     * escrito como {@code "RATE-LIMIT EXCEEDED"} o {@code "rate_limit_exceeded"}
     * acaba resolviéndose a la misma clave de traducción.
     *
     * @param errorCode código a normalizar.
     * @return versión {@code snake_case} del código, sin guiones bajos iniciales ni finales.
     */
    public static String normalizeErrorCode(@NonNull String errorCode) {
        return errorCode
                .trim()
                .toLowerCase(Locale.US)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");
    }
}

