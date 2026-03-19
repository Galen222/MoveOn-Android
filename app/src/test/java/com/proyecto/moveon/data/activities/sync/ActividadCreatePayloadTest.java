package com.proyecto.moveon.data.activities.sync;

import static org.junit.Assert.*;

import com.google.gson.JsonObject;

import org.junit.Test;

public class ActividadCreatePayloadTest {

    @Test
    public void toJson_includesAllRequiredFields() {
        ActividadCreatePayload payload = new ActividadCreatePayload(
                "Correr", 5000, 1800, 350, "encoded_poly", "http://map.png",
                "2025-03-19T10:00:00Z"
        );

        JsonObject json = payload.toJson();

        assertEquals("Correr", json.get("tipo").getAsString());
        assertEquals(5000, json.get("distancia").getAsInt());
        assertEquals(1800, json.get("duracion").getAsInt());
        assertEquals(350, json.get("calorias_quemadas").getAsInt());
        assertEquals("encoded_poly", json.get("ruta_polilinea").getAsString());
        assertEquals("http://map.png", json.get("ruta_mapa_url").getAsString());
        assertEquals("2025-03-19T10:00:00Z", json.get("fecha_ruta").getAsString());
    }

    @Test
    public void toJson_nullPolyline_sendsJsonNull() {
        ActividadCreatePayload payload = new ActividadCreatePayload(
                "Caminar", 1000, 600, 50, null, null,
                "2025-03-19T10:00:00Z"
        );

        JsonObject json = payload.toJson();

        assertTrue(json.get("ruta_polilinea").isJsonNull());
        assertTrue(json.get("ruta_mapa_url").isJsonNull());
    }

    @Test
    public void getters_returnCorrectValues() {
        ActividadCreatePayload payload = new ActividadCreatePayload(
                "Correr", 3000, 900, 200, "poly", "url",
                "2025-01-01T00:00:00Z"
        );

        assertEquals("Correr", payload.getTipo());
        assertEquals(3000, payload.getDistancia());
        assertEquals(900, payload.getDuracion());
        assertEquals(200, payload.getCaloriasQuemadas());
        assertEquals("poly", payload.getRutaPolilinea());
        assertEquals("url", payload.getRutaMapaUrl());
        assertEquals("2025-01-01T00:00:00Z", payload.getFechaRutaIso());
    }

    @Test
    public void toJson_zeroValues_areValid() {
        ActividadCreatePayload payload = new ActividadCreatePayload(
                "Caminar", 0, 0, 0, null, null, "2025-03-19T00:00:00Z"
        );

        JsonObject json = payload.toJson();
        assertEquals(0, json.get("distancia").getAsInt());
        assertEquals(0, json.get("duracion").getAsInt());
        assertEquals(0, json.get("calorias_quemadas").getAsInt());
    }
}
