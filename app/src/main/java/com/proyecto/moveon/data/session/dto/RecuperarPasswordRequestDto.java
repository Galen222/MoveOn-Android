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

    /**
     * Construye el cuerpo de la solicitud de recuperación. El {@code locale}
     * se normaliza antes de guardarlo para que el backend reciba siempre un
     * valor cerrado ({@code es} o {@code en}) y pueda elegir la plantilla
     * del correo sin lógica adicional.
     *
     * @param email email de la cuenta que desea recuperar la contraseña.
     * @param locale código de idioma preferido; se filtra a {@code es} o {@code en}.
     */
    public RecuperarPasswordRequestDto(@NonNull String email, @NonNull String locale) {
        this.email = email;
        this.locale = normalizeLocale(locale);
    }

    /**
     * Reduce el locale recibido a uno de los dos únicos valores que el
     * backend acepta para elegir la plantilla del email de recuperación.
     *
     * @param raw locale tal y como llega de {@link java.util.Locale} o de la configuración de la app.
     * @return {@code "es"} si el locale es español; {@code "en"} en cualquier otro caso.
     */
    @NonNull
    private static String normalizeLocale(@NonNull String raw) {
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return "es".equals(normalized) ? "es" : "en";
    }
}
