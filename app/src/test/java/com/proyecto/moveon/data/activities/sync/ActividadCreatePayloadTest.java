package com.proyecto.moveon.data.activities.sync;

import static org.junit.Assert.*;

import com.google.gson.JsonObject;

import org.junit.Test;

/**
 * Tests del payload JSON de sincronización de actividad con métricas extendidas.
 */
public class ActividadCreatePayloadTest {

    @Test
    public void toJson_includesAllRequiredFields() {
        ActividadCreatePayload payload = new ActividadCreatePayload(
                "Correr",
                5000,
                1800,
                1500,
                240,
                60,
                350,
                330,
                360,
                280,
                987,
                1450,
                2,
                1,
                3,
                "encoded_poly",
                "http://map.png",
                "2025-03-19T10:00:00Z"
        );

        JsonObject json = payload.toJson();

        assertEquals("Correr", json.get("tipo").getAsString());
        assertEquals(5000, json.get("distancia").getAsInt());
        assertEquals(1800, json.get("duracion_total").getAsInt());
        assertEquals(1500, json.get("duracion_movimiento").getAsInt());
        assertEquals(240, json.get("duracion_parado").getAsInt());
        assertEquals(60, json.get("duracion_pausa_manual").getAsInt());
        assertEquals(350, json.get("calorias_quemadas").getAsInt());
        assertEquals(330, json.get("ritmo_medio_movimiento").getAsInt());
        assertEquals(360, json.get("ritmo_medio_total").getAsInt());
        assertEquals(280, json.get("ritmo_maximo").getAsInt());
        assertEquals(987, json.get("velocidad_media_x100").getAsInt());
        assertEquals(1450, json.get("velocidad_max_x100").getAsInt());
        assertEquals(2, json.get("auto_pausas").getAsInt());
        assertEquals(1, json.get("pausas_manuales").getAsInt());
        assertEquals(3, json.get("alertas_velocidad").getAsInt());
        assertEquals("encoded_poly", json.get("ruta_polilinea").getAsString());
        assertEquals("http://map.png", json.get("ruta_mapa_url").getAsString());
        assertEquals("2025-03-19T10:00:00Z", json.get("fecha_ruta").getAsString());
    }

    @Test
    public void toJson_nullRouteFields_sendJsonNull() {
        ActividadCreatePayload payload = new ActividadCreatePayload(
                "Caminar",
                1000,
                600,
                520,
                50,
                30,
                50,
                720,
                900,
                420,
                650,
                800,
                1,
                1,
                0,
                null,
                null,
                "2025-03-19T10:00:00Z"
        );

        JsonObject json = payload.toJson();

        assertTrue(json.get("ruta_polilinea").isJsonNull());
        assertTrue(json.get("ruta_mapa_url").isJsonNull());
    }

    @Test
    public void toJson_usesCurrentFieldNames() {
        ActividadCreatePayload payload = new ActividadCreatePayload(
                "Correr",
                3000,
                900,
                780,
                90,
                30,
                200,
                300,
                360,
                1000,
                1200,
                1400,
                1,
                1,
                2,
                "poly",
                "url",
                "2025-01-01T00:00:00Z"
        );

        JsonObject json = payload.toJson();

        assertTrue(json.has("duracion_total"));
        assertTrue(json.has("duracion_movimiento"));
        assertTrue(json.has("duracion_parado"));
        assertTrue(json.has("duracion_pausa_manual"));
        assertTrue(json.has("ritmo_medio_movimiento"));
        assertTrue(json.has("ritmo_medio_total"));
        assertTrue(json.has("ritmo_maximo"));
        assertTrue(json.has("velocidad_media_x100"));
        assertTrue(json.has("velocidad_max_x100"));
        assertTrue(json.has("auto_pausas"));
        assertTrue(json.has("pausas_manuales"));
        assertTrue(json.has("alertas_velocidad"));
        assertTrue(json.has("ruta_mapa_url"));

        assertFalse(json.has("duracion"));
    }

    @Test
    public void toJson_zeroValues_areValid() {
        ActividadCreatePayload payload = new ActividadCreatePayload(
                "Caminar",
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                null,
                null,
                "2025-03-19T00:00:00Z"
        );

        JsonObject json = payload.toJson();
        assertEquals(0, json.get("distancia").getAsInt());
        assertEquals(0, json.get("duracion_total").getAsInt());
        assertEquals(0, json.get("duracion_movimiento").getAsInt());
        assertEquals(0, json.get("duracion_parado").getAsInt());
        assertEquals(0, json.get("duracion_pausa_manual").getAsInt());
        assertEquals(0, json.get("calorias_quemadas").getAsInt());
        assertEquals(0, json.get("ritmo_maximo").getAsInt());
        assertEquals(0, json.get("velocidad_max_x100").getAsInt());
        assertTrue(json.get("ruta_mapa_url").isJsonNull());
    }
}
