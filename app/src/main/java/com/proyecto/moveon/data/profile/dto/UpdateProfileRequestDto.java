package com.proyecto.moveon.data.profile.dto;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;
/**
 * DTO utilizado para enviar la solicitud de update profile.
 */
@Keep
public final class UpdateProfileRequestDto {

    @Nullable @SerializedName("nombre_real")      public final String  nombreReal;
    @Nullable @SerializedName("email")            public final String  email;
    @Nullable @SerializedName("fecha_nacimiento") public final String  fechaNacimiento;
    @Nullable @SerializedName("genero")           public final String  genero;
    @Nullable @SerializedName("altura")           public final Integer altura;
    @Nullable @SerializedName("peso")             public final Double  peso;
    @Nullable @SerializedName("provincia")        public final String  provincia;
    @Nullable @SerializedName("perfil_visible")   public final Boolean perfilVisible;

    /**
     * Constructor privado invocado por {@link Builder#build()}. Copia el
     * estado acumulado en el builder a campos finales del DTO para que sea
     * inmutable una vez construido.
     *
     * @param b builder con los campos que el llamador ha decidido enviar en el PATCH.
     */
    private UpdateProfileRequestDto(Builder b) {
        this.nombreReal      = b.nombreReal;
        this.email           = b.email;
        this.fechaNacimiento = b.fechaNacimiento;
        this.genero          = b.genero;
        this.altura          = b.altura;
        this.peso            = b.peso;
        this.provincia       = b.provincia;
        this.perfilVisible   = b.perfilVisible;
    }

    public static final class Builder {
        private String  nombreReal;
        private String  email;
        private String  fechaNacimiento;
        private String  genero;
        private Integer altura;
        private Double  peso;
        private String  provincia;
        private Boolean perfilVisible;

        /**
         * Establece el nombre real (no el nombre de usuario). {@code null}
         * significa que este campo no se enviará en el PATCH.
         *
         * @param v nuevo nombre real, o {@code null} para no incluirlo en el PATCH.
         * @return el propio builder para encadenar llamadas.
         */
        public Builder nombreReal(@Nullable String v)      { nombreReal      = v; return this; }
        /**
         * Establece el email del perfil. {@code null} significa que no se
         * incluirá en el PATCH (el backend ignora los campos no presentes).
         *
         * @param v nuevo email, o {@code null} para no tocarlo.
         * @return el propio builder para encadenar llamadas.
         */
        public Builder email(@Nullable String v)           { email           = v; return this; }
        /**
         * Establece la fecha de nacimiento ({@code yyyy-MM-dd}). {@code null}
         * indica que no se enviará en el PATCH.
         *
         * @param v nueva fecha, o {@code null} para no tocarla.
         * @return el propio builder para encadenar llamadas.
         */
        public Builder fechaNacimiento(@Nullable String v) { fechaNacimiento = v; return this; }
        /**
         * Establece el género del perfil. {@code null} deja el valor previo
         * en el backend sin cambios.
         *
         * @param v nuevo valor de género, o {@code null} para no tocarlo.
         * @return el propio builder para encadenar llamadas.
         */
        public Builder genero(@Nullable String v)          { genero          = v; return this; }
        /**
         * Establece la altura en centímetros. {@code null} significa que no
         * se envía en el PATCH.
         *
         * @param v altura en cm, o {@code null} para no tocarla.
         * @return el propio builder para encadenar llamadas.
         */
        public Builder altura(@Nullable Integer v)         { altura          = v; return this; }
        /**
         * Establece el peso en kilogramos (admite decimales). {@code null}
         * indica que no se incluye en el PATCH.
         *
         * @param v peso en kg, o {@code null} para no tocarlo.
         * @return el propio builder para encadenar llamadas.
         */
        public Builder peso(@Nullable Double v)            { peso            = v; return this; }
        /**
         * Establece la provincia asociada al perfil. {@code null} significa
         * que no se modifica en el PATCH.
         *
         * @param v nueva provincia, o {@code null} para no tocarla.
         * @return el propio builder para encadenar llamadas.
         */
        public Builder provincia(@Nullable String v)       { provincia       = v; return this; }
        /**
         * Ajusta la visibilidad pública del perfil en el ranking y en búsquedas.
         * {@code null} deja el valor actual intacto en el backend.
         *
         * @param v {@code true}/{@code false} para visible/oculto, o {@code null} para no tocarlo.
         * @return el propio builder para encadenar llamadas.
         */
        public Builder perfilVisible(@Nullable Boolean v)  { perfilVisible   = v; return this; }

        /**
         * Materializa el builder en un DTO inmutable listo para serializar y
         * enviar al endpoint {@code PATCH /perfil}.
         *
         * @return DTO con únicamente los campos que se hayan establecido en el builder.
         */
        public UpdateProfileRequestDto build() { return new UpdateProfileRequestDto(this); }
    }
}