package com.proyecto.moveon.data.activities.dto;

import static org.junit.Assert.*;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.junit.Test;

public class GuardarActividadRequestDtoTest {

    private final Gson gson = new Gson();

    @Test
    public void constructor_setsAllFields() {
        GuardarActividadRequestDto dto = new GuardarActividadRequestDto(
                "Correr", 5000, 1800, 350, "polyline_encoded", "2025-03-19T10:00:00Z");

        assertEquals("Correr", dto.tipo);
        assertEquals(5000, dto.distancia);
        assertEquals(1800, dto.duracion);
        assertEquals(350, dto.caloriasQuemadas);
        assertEquals("polyline_encoded", dto.rutaPolilinea);
        assertEquals("2025-03-19T10:00:00Z", dto.fechaRuta);
    }

    @Test
    public void constructor_nullPolyline() {
        GuardarActividadRequestDto dto = new GuardarActividadRequestDto(
                "Caminar", 1000, 600, 50, null, "2025-03-19T10:00:00Z");

        assertNull(dto.rutaPolilinea);
    }

    @Test
    public void serialization_usesSerializedNames() {
        GuardarActividadRequestDto dto = new GuardarActividadRequestDto(
                "Correr", 3000, 900, 200, null, "2025-01-01T00:00:00Z");

        String json = gson.toJson(dto);
        JsonObject obj = gson.fromJson(json, JsonObject.class);

        assertTrue(obj.has("tipo"));
        assertTrue(obj.has("distancia"));
        assertTrue(obj.has("duracion"));
        assertTrue(obj.has("calorias_quemadas"));
        assertTrue(obj.has("ruta_polilinea"));
        assertTrue(obj.has("fecha_ruta"));

        // Verifica que NO usa nombres Java camelCase
        assertFalse(obj.has("caloriasQuemadas"));
        assertFalse(obj.has("rutaPolilinea"));
        assertFalse(obj.has("fechaRuta"));
    }

    @Test
    public void serialization_valuesAreCorrect() {
        GuardarActividadRequestDto dto = new GuardarActividadRequestDto(
                "Caminar", 2500, 1200, 100, "abc", "2025-06-15T12:00:00Z");

        String json = gson.toJson(dto);
        JsonObject obj = gson.fromJson(json, JsonObject.class);

        assertEquals("Caminar", obj.get("tipo").getAsString());
        assertEquals(2500, obj.get("distancia").getAsInt());
        assertEquals(1200, obj.get("duracion").getAsInt());
        assertEquals(100, obj.get("calorias_quemadas").getAsInt());
        assertEquals("abc", obj.get("ruta_polilinea").getAsString());
        assertEquals("2025-06-15T12:00:00Z", obj.get("fecha_ruta").getAsString());
    }
}
