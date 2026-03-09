package com.proyecto.moveon.data.activities.dto;

import androidx.annotation.Keep;

import com.google.gson.annotations.SerializedName;

@Keep
public final class ActividadResponseDto {

    @SerializedName("id") public int id;
    @SerializedName("tipo") public String tipo;
    @SerializedName("distancia") public int distancia;
    @SerializedName("duracion") public int duracion;
    @SerializedName("calorias_quemadas") public int caloriasQuemadas;
    @SerializedName("ruta_polilinea") public String rutaPolilinea;
    @SerializedName("ruta_mapa_url") public String rutaMapaUrl;
    @SerializedName("fecha_ruta") public String fechaRuta;
    @SerializedName("nuevo_total_puntos") public Integer nuevoTotalPuntos;
}
