package com.proyecto.moveon.data.profile.dto;

import androidx.annotation.Keep;

import com.google.gson.annotations.SerializedName;

@Keep
public final class ProfileInfoDto {

    @SerializedName("nombre_usuario")   public String  nombreUsuario;
    @SerializedName("nombre_real")      public String  nombreReal;
    @SerializedName("email")            public String  email;
    @SerializedName("fecha_nacimiento") public String  fechaNacimiento;
    @SerializedName("genero")           public String  genero;
    @SerializedName("altura")           public Integer altura;
    @SerializedName("peso")             public Double  peso;
    @SerializedName("provincia")        public String  provincia;
    @SerializedName("foto_perfil")      public String  fotoPerfil;
    @SerializedName("foto_version")     public int     fotoVersion;
    @SerializedName("perfil_visible")   public boolean perfilVisible;
    @SerializedName("total_puntos")     public int     totalPuntos;
    @SerializedName("total_calorias")          public long    totalCalorias;
    @SerializedName("objetivo_semanal_metros") public long    objetivoSemanalMetros;
    @SerializedName("objetivo_mensual_metros") public long    objetivoMensualMetros;
}
