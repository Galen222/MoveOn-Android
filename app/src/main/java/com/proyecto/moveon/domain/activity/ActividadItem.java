package com.proyecto.moveon.domain.activity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class ActividadItem {

    @NonNull public final String localId;
    @Nullable public final Integer remoteId;
    @NonNull public final String tipo;
    public final int distanciaMetros;
    public final int duracionSegundos;
    public final int caloriasQuemadas;
    @Nullable public final String rutaPolilinea;
    @Nullable public final String rutaMapaUrl;
    @NonNull public final String fechaRutaIso;
    @NonNull public final String syncState;
    @Nullable public final String lastError;

    public ActividadItem(@NonNull String localId,
                         @Nullable Integer remoteId,
                         @NonNull String tipo,
                         int distanciaMetros,
                         int duracionSegundos,
                         int caloriasQuemadas,
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
        this.caloriasQuemadas = caloriasQuemadas;
        this.rutaPolilinea = rutaPolilinea;
        this.rutaMapaUrl = rutaMapaUrl;
        this.fechaRutaIso = fechaRutaIso;
        this.syncState = syncState;
        this.lastError = lastError;
    }

    public boolean isPendingSync() {
        return !"SYNCED".equals(syncState);
    }
}
