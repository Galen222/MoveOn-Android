package com.proyecto.moveon.data.activities.dto;

import static org.junit.Assert.*;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.junit.Test;

/**
 * Tests del DTO de guardado de actividad con el modelo extendido de métricas.
 */
public class GuardarActividadRequestDtoTest {

    private final Gson gson = new Gson();

    /**
     * Verifica que el constructor copia cada parámetro al campo
     * correspondiente. Es un test regresivo: un refactor accidental que
     * cambiase el orden de asignación rompería el DTO que el backend
     * recibe y esta prueba lo captura.
     */
    @Test
    public void constructor_setsAllFields() {
        GuardarActividadRequestDto dto = new GuardarActividadRequestDto(
                "Correr",
                5000,
                1800,
                1500,
                240,
                60,
                350,
                4_321,
                330,
                360,
                280,
                987,
                1450,
                2,
                1,
                3,
                "polyline_encoded",
                "2025-03-19T10:00:00Z"
        );

        assertEquals("Correr", dto.tipo);
        assertEquals(5000, dto.distancia);
        assertEquals(1800, dto.duracionTotal);
        assertEquals(1500, dto.duracionMovimiento);
        assertEquals(240, dto.duracionParado);
        assertEquals(60, dto.duracionPausaManual);
        assertEquals(350, dto.caloriasQuemadas);
        assertEquals(Integer.valueOf(4_321), dto.pasos);
        assertEquals(330, dto.ritmoMedioMovimiento);
        assertEquals(360, dto.ritmoMedioTotal);
        assertEquals(280, dto.ritmoMaximo);
        assertEquals(987, dto.velocidadMediaKmhX100);
        assertEquals(1450, dto.velocidadMaxKmhX100);
        assertEquals(2, dto.autoPausas);
        assertEquals(1, dto.pausasManuales);
        assertEquals(3, dto.alertasVelocidad);
        assertEquals("polyline_encoded", dto.rutaPolilinea);
        assertEquals("2025-03-19T10:00:00Z", dto.fechaRuta);
    }

    /**
     * Documenta que {@code rutaPolilinea == null} es un caso válido
     * (actividades sin GPS o con permisos denegados). El DTO debe aceptarlo
     * sin lanzar excepción y preservarlo en el campo.
     */
    @Test
    public void constructor_nullPolyline_isAllowed() {
        GuardarActividadRequestDto dto = new GuardarActividadRequestDto(
                "Caminar",
                1000,
                600,
                520,
                50,
                30,
                50,
                null,
                720,
                900,
                420,
                650,
                800,
                1,
                1,
                0,
                null,
                "2025-03-19T10:00:00Z"
        );

        assertNull(dto.rutaPolilinea);
    }

    @Test
    public void serialization_usesCurrentSerializedNames() {
        GuardarActividadRequestDto dto = new GuardarActividadRequestDto(
                "Correr",
                3000,
                900,
                780,
                90,
                30,
                200,
                2_345,
                300,
                360,
                480,
                1000,
                1200,
                1,
                1,
                2,
                "polyline_encoded",
                "2025-01-01T00:00:00Z"
        );

        String json = gson.toJson(dto);
        JsonObject obj = gson.fromJson(json, JsonObject.class);

        assertTrue(obj.has("tipo"));
        assertTrue(obj.has("distancia"));
        assertTrue(obj.has("duracion_total"));
        assertTrue(obj.has("duracion_movimiento"));
        assertTrue(obj.has("duracion_parado"));
        assertTrue(obj.has("duracion_pausa_manual"));
        assertTrue(obj.has("calorias_quemadas"));
        assertTrue(obj.has("pasos"));
        assertTrue(obj.has("ritmo_medio_movimiento"));
        assertTrue(obj.has("ritmo_medio_total"));
        assertTrue(obj.has("ritmo_maximo"));
        assertTrue(obj.has("velocidad_media_x100"));
        assertTrue(obj.has("velocidad_max_x100"));
        assertTrue(obj.has("auto_pausas"));
        assertTrue(obj.has("pausas_manuales"));
        assertTrue(obj.has("alertas_velocidad"));
        assertTrue(obj.has("ruta_polilinea"));
        assertTrue(obj.has("fecha_ruta"));

        // No debe usar nombres camelCase ni el nombre legacy "duracion".
        assertFalse(obj.has("duracion"));
        assertFalse(obj.has("duracionTotal"));
        assertFalse(obj.has("duracionMovimiento"));
        assertFalse(obj.has("duracionParado"));
        assertFalse(obj.has("duracionPausaManual"));
        assertFalse(obj.has("caloriasQuemadas"));
        assertFalse(obj.has("rutaPolilinea"));
        assertFalse(obj.has("fechaRuta"));
    }

    @Test
    public void serialization_valuesAreCorrect() {
        GuardarActividadRequestDto dto = new GuardarActividadRequestDto(
                "Caminar",
                2500,
                1200,
                1000,
                150,
                50,
                100,
                3_210,
                480,
                520,
                640,
                760,
                980,
                2,
                1,
                1,
                "abc",
                "2025-06-15T12:00:00Z"
        );

        String json = gson.toJson(dto);
        JsonObject obj = gson.fromJson(json, JsonObject.class);

        assertEquals("Caminar", obj.get("tipo").getAsString());
        assertEquals(2500, obj.get("distancia").getAsInt());
        assertEquals(1200, obj.get("duracion_total").getAsInt());
        assertEquals(1000, obj.get("duracion_movimiento").getAsInt());
        assertEquals(150, obj.get("duracion_parado").getAsInt());
        assertEquals(50, obj.get("duracion_pausa_manual").getAsInt());
        assertEquals(100, obj.get("calorias_quemadas").getAsInt());
        assertEquals(3_210, obj.get("pasos").getAsInt());
        assertEquals(480, obj.get("ritmo_medio_movimiento").getAsInt());
        assertEquals(520, obj.get("ritmo_medio_total").getAsInt());
        assertEquals(640, obj.get("ritmo_maximo").getAsInt());
        assertEquals(760, obj.get("velocidad_media_x100").getAsInt());
        assertEquals(980, obj.get("velocidad_max_x100").getAsInt());
        assertEquals(2, obj.get("auto_pausas").getAsInt());
        assertEquals(1, obj.get("pausas_manuales").getAsInt());
        assertEquals(1, obj.get("alertas_velocidad").getAsInt());
        assertEquals("abc", obj.get("ruta_polilinea").getAsString());
        assertEquals("2025-06-15T12:00:00Z", obj.get("fecha_ruta").getAsString());
    }
}
