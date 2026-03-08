package com.proyecto.moveon.data.activities.dto;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

/**
 * DTO de respuesta de {@code POST /actividad/guardar}.

 * El backend devuelve el objeto de actividad creado junto con
 * los puntos totales actualizados del usuario.
 */
@Keep
@SuppressWarnings("unused")
public final class GuardarActividadResponseDto {

    @SerializedName("id")
    public int id;

    @SerializedName("tipo")
    @Nullable
    public String tipo;

    @SerializedName("distancia")
    public int distancia;

    @SerializedName("duracion")
    public int duracion;

    @SerializedName("calorias_quemadas")
    public int caloriasQuemadas;

    @SerializedName("ruta_polilinea")
    @Nullable
    public String rutaPolilinea;

    @SerializedName("ruta_mapa_url")
    @Nullable
    public String rutaMapaUrl;

    @SerializedName("fecha_ruta")
    @Nullable
    public String fechaRuta;

    /** Puntos totales del usuario tras guardar la actividad. */
    @SerializedName("nuevo_total_puntos")
    public int nuevoTotalPuntos;
}