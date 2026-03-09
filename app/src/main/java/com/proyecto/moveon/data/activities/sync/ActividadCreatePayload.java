package com.proyecto.moveon.data.activities.sync;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.proyecto.moveon.data.local.entity.ActividadEntity;

public final class ActividadCreatePayload {

    private final String tipo;
    private final int distancia;
    private final int duracion;
    private final int caloriasQuemadas;
    @Nullable private final String rutaPolilinea;
    @Nullable private final String rutaMapaUrl;
    private final String fechaRutaIso;

    public ActividadCreatePayload(@NonNull String tipo,
                                  int distancia,
                                  int duracion,
                                  int caloriasQuemadas,
                                  @Nullable String rutaPolilinea,
                                  @Nullable String rutaMapaUrl,
                                  @NonNull String fechaRutaIso) {
        this.tipo = tipo;
        this.distancia = distancia;
        this.duracion = duracion;
        this.caloriasQuemadas = caloriasQuemadas;
        this.rutaPolilinea = rutaPolilinea;
        this.rutaMapaUrl = rutaMapaUrl;
        this.fechaRutaIso = fechaRutaIso;
    }

    @NonNull
    public String getTipo() {
        return tipo;
    }

    public int getDistancia() {
        return distancia;
    }

    public int getDuracion() {
        return duracion;
    }

    public int getCaloriasQuemadas() {
        return caloriasQuemadas;
    }

    @Nullable
    public String getRutaPolilinea() {
        return rutaPolilinea;
    }

    @Nullable
    public String getRutaMapaUrl() {
        return rutaMapaUrl;
    }

    @NonNull
    public String getFechaRutaIso() {
        return fechaRutaIso;
    }

    @NonNull
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("tipo", tipo);
        json.addProperty("distancia", distancia);
        json.addProperty("duracion", duracion);
        json.addProperty("calorias_quemadas", caloriasQuemadas);

        if (rutaPolilinea == null) json.add("ruta_polilinea", JsonNull.INSTANCE);
        else json.addProperty("ruta_polilinea", rutaPolilinea);

        if (rutaMapaUrl == null) json.add("ruta_mapa_url", JsonNull.INSTANCE);
        else json.addProperty("ruta_mapa_url", rutaMapaUrl);

        json.addProperty("fecha_ruta", fechaRutaIso);
        return json;
    }

    @NonNull
    public static ActividadCreatePayload fromEntity(@NonNull ActividadEntity entity) {
        return new ActividadCreatePayload(
                entity.tipo,
                entity.distancia,
                entity.duracion,
                entity.caloriasQuemadas,
                entity.rutaPolilinea,
                entity.rutaMapaUrl,
                entity.fechaRuta
        );
    }
}
