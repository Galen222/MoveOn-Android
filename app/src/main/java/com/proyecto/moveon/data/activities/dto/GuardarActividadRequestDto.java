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

    @SerializedName("pasos")
    @Nullable
    public final Integer pasos;

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

    /**
     * Construye el DTO con todas las métricas ya calculadas por el módulo de
     * tracking, listas para enviarse al endpoint {@code POST /actividad/guardar}
     * o para quedarse en la cola offline hasta que haya red.
     *
     * <p>Las duraciones se envían desglosadas (total / movimiento / parado /
     * pausa manual) para que el historial y el ranking sean consistentes con
     * lo que el propio módulo de tracking calculó en el dispositivo, sin que
     * el backend tenga que reconstruirlas.</p>
     *
     * @param tipo tipo de actividad (p. ej. {@code "carrera"}, {@code "caminata"}).
     * @param distancia distancia total en metros.
     * @param duracionTotal duración total en segundos, incluyendo paradas y pausas.
     * @param duracionMovimiento segundos clasificados como movimiento real.
     * @param duracionParado segundos con el usuario parado pero sin pausar manualmente.
     * @param duracionPausaManual segundos que el usuario pausó manualmente.
     * @param caloriasQuemadas calorías quemadas estimadas por el dispositivo.
     * @param pasos pasos detectados, o {@code null} si el dispositivo no tiene sensor compatible.
     * @param ritmoMedioMovimiento ritmo medio en movimiento en segundos por kilómetro.
     * @param ritmoMedioTotal ritmo medio total (incluye paradas) en segundos por kilómetro.
     * @param ritmoMaximo mejor ritmo sostenido válido en segundos por kilómetro.
     * @param velocidadMediaKmhX100 velocidad media en km/h multiplicada por 100 para evitar decimales.
     * @param velocidadMaxKmhX100 velocidad máxima en km/h multiplicada por 100.
     * @param autoPausas número de auto-pausas disparadas por el detector de inactividad.
     * @param pausasManuales número de pausas iniciadas a mano por el usuario.
     * @param alertasVelocidad número de alertas por velocidad anómala.
     * @param rutaPolilinea polilínea codificada de la ruta, o {@code null} si no se trackeó.
     * @param fechaRuta fecha/hora de la actividad en formato ISO-8601.
     */
    public GuardarActividadRequestDto(
            @NonNull String tipo,
            int distancia,
            int duracionTotal,
            int duracionMovimiento,
            int duracionParado,
            int duracionPausaManual,
            int caloriasQuemadas,
            @Nullable Integer pasos,
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
        this.pasos = pasos;
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
