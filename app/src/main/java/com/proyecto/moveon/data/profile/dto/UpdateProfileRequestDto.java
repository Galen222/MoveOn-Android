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

        public Builder nombreReal(@Nullable String v)      { nombreReal      = v; return this; }
        public Builder email(@Nullable String v)           { email           = v; return this; }
        public Builder fechaNacimiento(@Nullable String v) { fechaNacimiento = v; return this; }
        public Builder genero(@Nullable String v)          { genero          = v; return this; }
        public Builder altura(@Nullable Integer v)         { altura          = v; return this; }
        public Builder peso(@Nullable Double v)            { peso            = v; return this; }
        public Builder provincia(@Nullable String v)       { provincia       = v; return this; }
        public Builder perfilVisible(@Nullable Boolean v)  { perfilVisible   = v; return this; }

        public UpdateProfileRequestDto build() { return new UpdateProfileRequestDto(this); }
    }
}