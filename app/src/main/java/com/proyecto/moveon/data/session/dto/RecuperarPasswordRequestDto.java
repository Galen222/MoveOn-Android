package com.proyecto.moveon.data.session.dto;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;

import com.google.gson.annotations.SerializedName;

import java.util.Locale;
/**
 * DTO utilizado para enviar la solicitud de recuperar password.
 */
@Keep
public final class RecuperarPasswordRequestDto {

    @SerializedName("email")
    @NonNull
    public final String email;

    /** Idioma efectivo de la UI para que el backend elija la plantilla del correo. */
    @SerializedName("locale")
    @NonNull
    public final String locale;

    public RecuperarPasswordRequestDto(@NonNull String email, @NonNull String locale) {
        this.email = email;
        this.locale = normalizeLocale(locale);
    }

    @NonNull
    private static String normalizeLocale(@NonNull String raw) {
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return "es".equals(normalized) ? "es" : "en";
    }
}
