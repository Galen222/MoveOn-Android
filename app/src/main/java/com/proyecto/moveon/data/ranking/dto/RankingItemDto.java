package com.proyecto.moveon.data.ranking.dto;

import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

/**
 * DTO de un ítem del ranking.
 * Mapea la respuesta del endpoint GET /ranking/obtener.

 * Campos del backend (schemas.ObtenerRanking):
 *   nombre_usuario  → String
 *   foto_perfil     → String | null
 *   foto_version    → int (timestamp Unix, 0 si no hay foto)
 *   total_puntos    → int
 */
public final class RankingItemDto {

    @SerializedName("nombre_usuario")
    public String nombreUsuario;

    @SerializedName("foto_perfil")
    @Nullable
    public String fotoPerfil;

    @SerializedName("foto_version")
    public int fotoVersion;

    @SerializedName("total_puntos")
    public int totalPuntos;

    @SerializedName("total_metros")
    public int totalMetros;
}