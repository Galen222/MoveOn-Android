package com.proyecto.moveon.data.activities.dto;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

/**
 * DTO de petición para {@code POST /actividad/guardar}.

 * Campos requeridos por el backend (FastAPI — GuardarActividad):
 * - tipo → "Caminar" | "Correr"
 * - distancia → metros enteros positivos
 * - duracion → segundos enteros positivos
 * - calorias_quemadas → entero positivo
 * - ruta_polilinea → encoded polyline (opcional)
 * - fecha_ruta → ISO-8601 con zona horaria
 */
@Keep
public final class GuardarActividadRequestDto {

    @SerializedName("tipo")
    @NonNull
    public final String tipo;

    @SerializedName("distancia")
    public final int distancia;

    @SerializedName("duracion")
    public final int duracion;

    @SerializedName("calorias_quemadas")
    public final int caloriasQuemadas;

    @SerializedName("ruta_polilinea")
    @Nullable
    public final String rutaPolilinea;

    @SerializedName("fecha_ruta")
    @NonNull
    public final String fechaRuta;

    public GuardarActividadRequestDto(
            @NonNull  String tipo,
            int             distancia,
            int             duracion,
            int             caloriasQuemadas,
            @Nullable String rutaPolilinea,
            @NonNull  String fechaRuta) {

        this.tipo             = tipo;
        this.distancia        = distancia;
        this.duracion         = duracion;
        this.caloriasQuemadas = caloriasQuemadas;
        this.rutaPolilinea    = rutaPolilinea;
        this.fechaRuta        = fechaRuta;
    }
}