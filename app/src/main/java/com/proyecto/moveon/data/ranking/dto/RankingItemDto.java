package com.proyecto.moveon.data.ranking.dto;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

/**
 * DTO de un ítem del ranking.
 *
 * <p>Mapea la respuesta del endpoint {@code GET /ranking/obtener}.</p>
 *
 * <p>En la versión corregida del contrato, el backend envía también la
 * {@code posicion} real del usuario dentro del ámbito consultado
 * (España o provincia). La UI ya no debe inferirla a partir del índice
 * del RecyclerView, porque ese enfoque puede dejar posiciones obsoletas
 * cuando un mismo usuario aparece en listados distintos.</p>
 */
@Keep
public final class RankingItemDto {

    /** Posición real del usuario dentro del ranking actual. */
    @SerializedName("posicion")
    public int posicion;

    /** Nombre de usuario público mostrado en el ranking. */
    @SerializedName("nombre_usuario")
    public String nombreUsuario;

    /** URL absoluta de la foto de perfil, o {@code null} si no tiene. */
    @SerializedName("foto_perfil")
    @Nullable
    public String fotoPerfil;

    /** Versión numérica usada para invalidar la caché de la foto. */
    @SerializedName("foto_version")
    public int fotoVersion;

    /** Puntos totales calculados por backend a partir de los metros acumulados. */
    @SerializedName("total_puntos")
    public int totalPuntos;

    /** Metros totales acumulados del usuario. */
    @SerializedName("total_metros")
    public int totalMetros;
}
