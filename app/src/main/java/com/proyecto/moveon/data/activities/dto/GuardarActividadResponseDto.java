package com.proyecto.moveon.data.activities.dto;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

/**
 * DTO de respuesta para {@code POST /actividad/guardar}.
 */
@Keep
@SuppressWarnings("unused")
public final class GuardarActividadResponseDto {

    @SerializedName("id") public int id;
    @SerializedName("tipo") @Nullable public String tipo;
    @SerializedName("distancia") public int distancia;
    @SerializedName("duracion_total") public int duracionTotal;
    @SerializedName("duracion_movimiento") public int duracionMovimiento;
    @SerializedName("duracion_parado") public int duracionParado;
    @SerializedName("duracion_pausa_manual") public int duracionPausaManual;
    @SerializedName("calorias_quemadas") public int caloriasQuemadas;
    @SerializedName("pasos") @Nullable public Integer pasos;
    @SerializedName("ritmo_medio_movimiento") public int ritmoMedioMovimiento;
    @SerializedName("ritmo_medio_total") public int ritmoMedioTotal;
    @SerializedName("ritmo_maximo") public int ritmoMaximo;
    @SerializedName("velocidad_media_x100") public int velocidadMediaKmhX100;
    @SerializedName("velocidad_max_x100") public int velocidadMaxKmhX100;
    @SerializedName("auto_pausas") public int autoPausas;
    @SerializedName("pausas_manuales") public int pausasManuales;
    @SerializedName("alertas_velocidad") public int alertasVelocidad;
    @SerializedName("ruta_polilinea") @Nullable public String rutaPolilinea;
    @SerializedName("ruta_mapa_url") @Nullable public String rutaMapaUrl;
    @SerializedName("fecha_ruta") @Nullable public String fechaRuta;
    @SerializedName("nuevo_total_puntos") public int nuevoTotalPuntos;
}
