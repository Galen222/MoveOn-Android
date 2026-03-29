package com.proyecto.moveon.data.activities.dto;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

/**
 * DTO para guardar una actividad ya calculada por el módulo de tracking.
 *
 * <p>Además de las métricas históricas clásicas, incluye tiempo en movimiento,
 * tiempo parado y valores medios necesarios para el historial y la sincronización
 * offline consistente.</p>
 */
@Keep
public final class GuardarActividadRequestDto {

    @SerializedName("tipo")
    @NonNull
    public final String tipo;

    @SerializedName("distancia")
    public final int distancia;

    @SerializedName("duracion_total")
    public final int duracionTotal;

    @SerializedName("duracion_movimiento")
    public final int duracionMovimiento;

    @SerializedName("duracion_parado")
    public final int duracionParado;

    @SerializedName("duracion_pausa_manual")
    public final int duracionPausaManual;

    @SerializedName("calorias_quemadas")
    public final int caloriasQuemadas;

    @SerializedName("ritmo_medio_movimiento")
    public final int ritmoMedioMovimiento;

    @SerializedName("ritmo_medio_total")
    public final int ritmoMedioTotal;

    /** Mejor ritmo sostenido válido de la actividad, en segundos por kilómetro. */
    @SerializedName("ritmo_maximo")
    public final int ritmoMaximo;

    @SerializedName("velocidad_media_x100")
    public final int velocidadMediaKmhX100;

    @SerializedName("velocidad_max_x100")
    public final int velocidadMaxKmhX100;

    @SerializedName("auto_pausas")
    public final int autoPausas;

    @SerializedName("pausas_manuales")
    public final int pausasManuales;

    @SerializedName("alertas_velocidad")
    public final int alertasVelocidad;

    @SerializedName("ruta_polilinea")
    @Nullable
    public final String rutaPolilinea;

    @SerializedName("fecha_ruta")
    @NonNull
    public final String fechaRuta;

    public GuardarActividadRequestDto(
            @NonNull String tipo,
            int distancia,
            int duracionTotal,
            int duracionMovimiento,
            int duracionParado,
            int duracionPausaManual,
            int caloriasQuemadas,
            int ritmoMedioMovimiento,
            int ritmoMedioTotal,
            int ritmoMaximo,
            int velocidadMediaKmhX100,
            int velocidadMaxKmhX100,
            int autoPausas,
            int pausasManuales,
            int alertasVelocidad,
            @Nullable String rutaPolilinea,
            @NonNull String fechaRuta) {
        this.tipo = tipo;
        this.distancia = distancia;
        this.duracionTotal = duracionTotal;
        this.duracionMovimiento = duracionMovimiento;
        this.duracionParado = duracionParado;
        this.duracionPausaManual = duracionPausaManual;
        this.caloriasQuemadas = caloriasQuemadas;
        this.ritmoMedioMovimiento = ritmoMedioMovimiento;
        this.ritmoMedioTotal = ritmoMedioTotal;
        this.ritmoMaximo = ritmoMaximo;
        this.velocidadMediaKmhX100 = velocidadMediaKmhX100;
        this.velocidadMaxKmhX100 = velocidadMaxKmhX100;
        this.autoPausas = autoPausas;
        this.pausasManuales = pausasManuales;
        this.alertasVelocidad = alertasVelocidad;
        this.rutaPolilinea = rutaPolilinea;
        this.fechaRuta = fechaRuta;
    }
}
