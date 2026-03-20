package com.proyecto.moveon.domain.activity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.proyecto.moveon.data.activities.ActivitySyncState;

/**
 * Modelo de dominio de actividad usado por la UI y los cálculos agregados.
 */
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
    public final int ritmoMedioMovimientoSegKm;
    public final int ritmoMedioTotalSegKm;
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
            int ritmoMedioMovimientoSegKm,
            int ritmoMedioTotalSegKm,
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
        this.ritmoMedioMovimientoSegKm = ritmoMedioMovimientoSegKm;
        this.ritmoMedioTotalSegKm = ritmoMedioTotalSegKm;
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

    public boolean isPendingSync() {
        return ActivitySyncState.isPending(syncState);
    }
}
