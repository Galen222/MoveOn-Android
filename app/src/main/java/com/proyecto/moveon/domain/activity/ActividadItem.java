package com.proyecto.moveon.domain.activity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.proyecto.moveon.data.activities.ActivitySyncState;

/**
 * Modelo de dominio de actividad usado por la UI y los cálculos agregados.
 */
@SuppressWarnings("ClassCanBeRecord")
public final class ActividadItem {

    @NonNull public final String localId;
    @Nullable public final Integer remoteId;
    @NonNull public final String tipo;
    public final int distanciaMetros;
    public final int duracionSegundos;
    public final int duracionMovimientoSegundos;
    public final int duracionParadoSegundos;
    public final int duracionPausaManualSegundos;
    public final int caloriasQuemadas;
    @Nullable public final Integer pasos;
    public final int ritmoMedioMovimientoSegKm;
    public final int ritmoMedioTotalSegKm;
    public final int ritmoMaximoSegKm;
    public final int velocidadMediaKmhX100;
    public final int velocidadMaxKmhX100;
    public final int autoPausas;
    public final int pausasManuales;
    public final int alertasVelocidad;
    @Nullable public final String rutaPolilinea;
    @Nullable public final String rutaMapaUrl;
    @NonNull public final String fechaRutaIso;
    @NonNull public final String syncState;
    @Nullable public final String lastError;

    /**
     * Construye un ítem de actividad con todas sus métricas ya calculadas.
     *
     * <p>{@code localId} es siempre obligatorio y actúa como clave primaria
     * mientras la actividad no se haya subido; {@code remoteId} se asigna
     * cuando el backend confirma el alta y permite deduplicar en
     * sincronizaciones posteriores.</p>
     *
     * @param localId UUID local estable generado en el dispositivo.
     * @param remoteId id asignado por el backend, o {@code null} si aún no se ha sincronizado.
     * @param tipo tipo de actividad (p. ej. {@code "carrera"}, {@code "caminata"}).
     * @param distanciaMetros distancia total en metros.
     * @param duracionSegundos duración total en segundos (incluye paradas y pausas).
     * @param duracionMovimientoSegundos segundos clasificados como movimiento real.
     * @param duracionParadoSegundos segundos parado sin pausar manualmente.
     * @param duracionPausaManualSegundos segundos en pausa manual del usuario.
     * @param caloriasQuemadas calorías quemadas estimadas.
     * @param pasos pasos detectados, o {@code null} si el móvil no podía medirlos.
     * @param ritmoMedioMovimientoSegKm ritmo medio en movimiento en segundos por kilómetro.
     * @param ritmoMedioTotalSegKm ritmo medio total en segundos por kilómetro.
     * @param ritmoMaximoSegKm mejor ritmo sostenido válido en segundos por kilómetro.
     * @param velocidadMediaKmhX100 velocidad media en km/h multiplicada por 100.
     * @param velocidadMaxKmhX100 velocidad máxima en km/h multiplicada por 100.
     * @param autoPausas número de auto-pausas disparadas.
     * @param pausasManuales número de pausas manuales iniciadas por el usuario.
     * @param alertasVelocidad número de alertas por velocidad sospechosa.
     * @param rutaPolilinea polilínea codificada de la ruta, o {@code null} si no se guardó.
     * @param rutaMapaUrl URL de la imagen estática del mapa, o {@code null} si aún no se generó.
     * @param fechaRutaIso fecha/hora en formato ISO-8601.
     * @param syncState estado de sincronización actual (pendiente, sincronizado, error…).
     * @param lastError último error de sincronización localizado, o {@code null} si no hay.
     */
    public ActividadItem(
            @NonNull String localId,
            @Nullable Integer remoteId,
            @NonNull String tipo,
            int distanciaMetros,
            int duracionSegundos,
            int duracionMovimientoSegundos,
            int duracionParadoSegundos,
            int duracionPausaManualSegundos,
            int caloriasQuemadas,
            @Nullable Integer pasos,
            int ritmoMedioMovimientoSegKm,
            int ritmoMedioTotalSegKm,
            int ritmoMaximoSegKm,
            int velocidadMediaKmhX100,
            int velocidadMaxKmhX100,
            int autoPausas,
            int pausasManuales,
            int alertasVelocidad,
            @Nullable String rutaPolilinea,
            @Nullable String rutaMapaUrl,
            @NonNull String fechaRutaIso,
            @NonNull String syncState,
            @Nullable String lastError) {
        this.localId = localId;
        this.remoteId = remoteId;
        this.tipo = tipo;
        this.distanciaMetros = distanciaMetros;
        this.duracionSegundos = duracionSegundos;
        this.duracionMovimientoSegundos = duracionMovimientoSegundos;
        this.duracionParadoSegundos = duracionParadoSegundos;
        this.duracionPausaManualSegundos = duracionPausaManualSegundos;
        this.caloriasQuemadas = caloriasQuemadas;
        this.pasos = pasos;
        this.ritmoMedioMovimientoSegKm = ritmoMedioMovimientoSegKm;
        this.ritmoMedioTotalSegKm = ritmoMedioTotalSegKm;
        this.ritmoMaximoSegKm = ritmoMaximoSegKm;
        this.velocidadMediaKmhX100 = velocidadMediaKmhX100;
        this.velocidadMaxKmhX100 = velocidadMaxKmhX100;
        this.autoPausas = autoPausas;
        this.pausasManuales = pausasManuales;
        this.alertasVelocidad = alertasVelocidad;
        this.rutaPolilinea = rutaPolilinea;
        this.rutaMapaUrl = rutaMapaUrl;
        this.fechaRutaIso = fechaRutaIso;
        this.syncState = syncState;
        this.lastError = lastError;
    }

    /**
     * Indica si esta actividad aún no ha llegado al backend y sigue en la
     * cola offline. La lista del historial y el indicador visual de
     * "pendiente de subir" se apoyan en este flag.
     *
     * @return {@code true} si {@link ActivitySyncState#isPending(String)} considera pendiente al estado actual.
     */
    public boolean isPendingSync() {
        return ActivitySyncState.isPending(syncState);
    }
}
