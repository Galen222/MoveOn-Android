package com.proyecto.moveon.data.activities.dto;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

/**
 * DTO de respuesta para {@code DELETE /actividad/borrar/{id}}.
 * El backend devuelve el estado, un mensaje y los puntos actualizados.
 */
@Keep
public final class BorrarActividadResponseDto {

    @SerializedName("estatus")
    @Nullable
    public String estatus;

    @SerializedName("mensaje")
    @Nullable
    public String mensaje;

    @SerializedName("nuevo_total_puntos")
    public int nuevoTotalPuntos;
}