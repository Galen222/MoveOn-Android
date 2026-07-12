package com.proyecto.moveon.data.activities.sync;

import static org.junit.Assert.*;

import com.google.gson.JsonObject;

import org.junit.Test;

import com.proyecto.moveon.data.local.entity.ActividadEntity;
/**
 * Tests del payload JSON de sincronización de actividad con métricas extendidas.
 */
public class ActividadCreatePayloadTest {

    @Test
    public void toJson_includesAllRequiredFields() {
        JsonObject json = jsonWithAllRequiredFields();

        assertEquals("local-1", json.get("client_local_id").getAsString());
        assertEquals("Correr", json.get("tipo").getAsString());
        assertEquals(5000, json.get("distancia").getAsInt());
        assertEquals(1800, json.get("duracion_total").getAsInt());
        assertEquals(1500, json.get("duracion_movimiento").getAsInt());
        assertEquals(240, json.get("duracion_parado").getAsInt());
        assertEquals(60, json.get("duracion_pausa_manual").getAsInt());
        assertEquals(350, json.get("calorias_quemadas").getAsInt());
        assertEquals(4_321, json.get("pasos").getAsInt());
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
        JsonObject json = jsonWithNullRouteFields();

        assertTrue(json.get("ruta_polilinea").isJsonNull());
        assertTrue(json.get("ruta_mapa_url").isJsonNull());
        assertTrue(json.get("pasos").isJsonNull());
    }

    @Test
    public void toJson_usesCurrentFieldNames() {
        JsonObject json = jsonWithCurrentFieldNames();

        assertTrue(json.has("client_local_id"));
        assertTrue(json.has("duracion_total"));
        assertTrue(json.has("duracion_movimiento"));
        assertTrue(json.has("duracion_parado"));
        assertTrue(json.has("duracion_pausa_manual"));
        assertTrue(json.has("pasos"));
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
        JsonObject json = jsonWithZeroValues();

        assertEquals(0, json.get("distancia").getAsInt());
        assertEquals(0, json.get("duracion_total").getAsInt());
        assertEquals(0, json.get("duracion_movimiento").getAsInt());
        assertEquals(0, json.get("duracion_parado").getAsInt());
        assertEquals(0, json.get("duracion_pausa_manual").getAsInt());
        assertEquals(0, json.get("calorias_quemadas").getAsInt());
        assertEquals(0, json.get("pasos").getAsInt());
        assertEquals(0, json.get("ritmo_maximo").getAsInt());
        assertEquals(0, json.get("velocidad_max_x100").getAsInt());
        assertTrue(json.get("ruta_mapa_url").isJsonNull());
    }
    /**
     * Verifica que el constructor serializa todos los campos de tracking con nombres snake_case.
     */
    @Test
    public void toJson_serializesAllFields() {
        JsonObject json = jsonWithAllSerializedFields();

        assertEquals("local-1", json.get("client_local_id").getAsString());
        assertEquals("carrera", json.get("tipo").getAsString());
        assertEquals(5_000, json.get("distancia").getAsInt());
        assertEquals(1_800, json.get("duracion_total").getAsInt());
        assertEquals(1_700, json.get("duracion_movimiento").getAsInt());
        assertEquals(80, json.get("duracion_parado").getAsInt());
        assertEquals(20, json.get("duracion_pausa_manual").getAsInt());
        assertEquals(350, json.get("calorias_quemadas").getAsInt());
        assertEquals(4_321, json.get("pasos").getAsInt());
        assertEquals(340, json.get("ritmo_medio_movimiento").getAsInt());
        assertEquals(360, json.get("ritmo_medio_total").getAsInt());
        assertEquals(300, json.get("ritmo_maximo").getAsInt());
        assertEquals(1_000, json.get("velocidad_media_x100").getAsInt());
        assertEquals(1_400, json.get("velocidad_max_x100").getAsInt());
        assertEquals(2, json.get("auto_pausas").getAsInt());
        assertEquals(1, json.get("pausas_manuales").getAsInt());
        assertEquals(3, json.get("alertas_velocidad").getAsInt());
        assertEquals("poly", json.get("ruta_polilinea").getAsString());
        assertEquals("map.png", json.get("ruta_mapa_url").getAsString());
        assertEquals("2026-04-25T10:00:00Z", json.get("fecha_ruta").getAsString());
    }

    /**
     * Verifica que la ruta y el mapa nulos se serializan explícitamente como JsonNull.
     */
    @Test
    public void toJson_serializesNullableRouteFieldsAsJsonNull() {
        JsonObject json = jsonWithNullableSerializedRouteFields();

        assertTrue(json.get("ruta_polilinea").isJsonNull());
        assertTrue(json.get("ruta_mapa_url").isJsonNull());
        assertTrue(json.get("pasos").isJsonNull());
    }

    /**
     * Verifica que una entidad local se convierte a payload remoto sin perder métricas.
     */
    @Test
    public void fromEntity_copiesEntityFields() {
        JsonObject json = jsonFromRepresentativeEntity();

        assertEquals("local-3", json.get("client_local_id").getAsString());
        assertEquals("carrera", json.get("tipo").getAsString());
        assertEquals(100, json.get("distancia").getAsInt());
        assertEquals(200, json.get("duracion_total").getAsInt());
        assertEquals(2_345, json.get("pasos").getAsInt());
        assertEquals("poly", json.get("ruta_polilinea").getAsString());
        assertEquals("map.png", json.get("ruta_mapa_url").getAsString());
        assertEquals("2026-04-25T10:00:00Z", json.get("fecha_ruta").getAsString());
    }
    /**
     * Verifica que el payload serializa todos los campos enriquecidos con nombres snake_case.
     */
    @Test
    public void toJson_serializesEveryEnrichedTrackingField() {
        JsonObject json = jsonWithEnrichedTrackingFields();

        assertEquals("local-1", json.get("client_local_id").getAsString());
        assertEquals("carrera", json.get("tipo").getAsString());
        assertEquals(5000, json.get("distancia").getAsInt());
        assertEquals(1800, json.get("duracion_total").getAsInt());
        assertEquals(1700, json.get("duracion_movimiento").getAsInt());
        assertEquals(80, json.get("duracion_parado").getAsInt());
        assertEquals(20, json.get("duracion_pausa_manual").getAsInt());
        assertEquals(350, json.get("calorias_quemadas").getAsInt());
        assertEquals(4_321, json.get("pasos").getAsInt());
        assertEquals(340, json.get("ritmo_medio_movimiento").getAsInt());
        assertEquals(360, json.get("ritmo_medio_total").getAsInt());
        assertEquals(300, json.get("ritmo_maximo").getAsInt());
        assertEquals(1000, json.get("velocidad_media_x100").getAsInt());
        assertEquals(1500, json.get("velocidad_max_x100").getAsInt());
        assertEquals(1, json.get("auto_pausas").getAsInt());
        assertEquals(2, json.get("pausas_manuales").getAsInt());
        assertEquals(3, json.get("alertas_velocidad").getAsInt());
        assertEquals("poly", json.get("ruta_polilinea").getAsString());
        assertEquals("map.png", json.get("ruta_mapa_url").getAsString());
        assertEquals("2026-04-25T10:00:00Z", json.get("fecha_ruta").getAsString());
    }

    /**
     * Verifica que los campos opcionales de ruta se escriben como nulos JSON explícitos.
     */
    @Test
    public void toJson_withNullRouteFields_writesJsonNulls() {
        JsonObject json = jsonWithNullEnrichedRouteFields();

        assertTrue(json.get("ruta_polilinea").isJsonNull());
        assertTrue(json.get("ruta_mapa_url").isJsonNull());
    }

    /**
     * Verifica que {@link ActividadCreatePayload#fromEntity(ActividadEntity)} copia todos los campos persistidos.
     */
    @Test
    public void fromEntity_copiesAllEntityFieldsToPayloadJson() {
        JsonObject json = jsonFromFullyPopulatedEntity();

        assertEquals("local-entity", json.get("client_local_id").getAsString());
        assertEquals("caminata", json.get("tipo").getAsString());
        assertEquals(2500, json.get("distancia").getAsInt());
        assertEquals(900, json.get("duracion_total").getAsInt());
        assertEquals(850, json.get("duracion_movimiento").getAsInt());
        assertEquals(40, json.get("duracion_parado").getAsInt());
        assertEquals(10, json.get("duracion_pausa_manual").getAsInt());
        assertEquals(120, json.get("calorias_quemadas").getAsInt());
        assertEquals(3_210, json.get("pasos").getAsInt());
        assertEquals(400, json.get("ritmo_medio_movimiento").getAsInt());
        assertEquals(430, json.get("ritmo_medio_total").getAsInt());
        assertEquals(350, json.get("ritmo_maximo").getAsInt());
        assertEquals(800, json.get("velocidad_media_x100").getAsInt());
        assertEquals(1000, json.get("velocidad_max_x100").getAsInt());
        assertEquals(1, json.get("auto_pausas").getAsInt());
        assertEquals(0, json.get("pausas_manuales").getAsInt());
        assertEquals(2, json.get("alertas_velocidad").getAsInt());
        assertEquals("entity-poly", json.get("ruta_polilinea").getAsString());
        assertEquals("entity-map", json.get("ruta_mapa_url").getAsString());
        assertEquals("2026-04-25T12:00:00Z", json.get("fecha_ruta").getAsString());
    }

    private static JsonObject jsonFromRepresentativeEntity() {
        ActividadEntity entity = new ActividadEntity();
        entity.localId = "local-3";
        entity.tipo = "carrera";
        entity.distancia = 100;
        entity.duracionTotal = 200;
        entity.duracionMovimiento = 180;
        entity.duracionParado = 15;
        entity.duracionPausaManual = 5;
        entity.caloriasQuemadas = 30;
        entity.pasos = 2_345;
        entity.ritmoMedioMovimiento = 400;
        entity.ritmoMedioTotal = 420;
        entity.ritmoMaximo = 350;
        entity.velocidadMediaKmhX100 = 900;
        entity.velocidadMaxKmhX100 = 1_200;
        entity.autoPausas = 1;
        entity.pausasManuales = 2;
        entity.alertasVelocidad = 3;
        entity.rutaPolilinea = "poly";
        entity.rutaMapaUrl = "map.png";
        entity.fechaRuta = "2026-04-25T10:00:00Z";
        return ActividadCreatePayload.fromEntity(entity).toJson();
    }

    private static JsonObject jsonWithNullRouteFields() {
        return new ActividadCreatePayload(
                "local-2",
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
                null,
                "2025-03-19T10:00:00Z"
        ).toJson();
    }

    private static JsonObject jsonWithZeroValues() {
        return new ActividadCreatePayload(
                "local-4",
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
                0,
                null,
                null,
                "2025-03-19T00:00:00Z"
        ).toJson();
    }

    private static JsonObject jsonWithNullableSerializedRouteFields() {
        return new ActividadCreatePayload(
                "local-2",
                "caminata",
                1,
                2,
                3,
                4,
                5,
                6,
                null,
                7,
                8,
                9,
                10,
                11,
                12,
                13,
                14,
                null,
                null,
                "2026-04-25T10:00:00Z"
        ).toJson();
    }

    private static JsonObject jsonWithNullEnrichedRouteFields() {
        return new ActividadCreatePayload(
                "local-1", "carrera", 1, 2, 3, 4, 5,
                6, null, 7, 8, 9, 10, 11, 12, 13, 14,
                null, null, "2026-04-25T10:00:00Z"
        ).toJson();
    }

    private static JsonObject jsonWithAllRequiredFields() {
        return new ActividadCreatePayload(
                "local-1", "Correr", 5000, 1800, 1500, 240, 60, 350,
                4_321, 330, 360, 280, 987, 1450, 2, 1, 3,
                "encoded_poly", "http://map.png", "2025-03-19T10:00:00Z"
        ).toJson();
    }

    private static JsonObject jsonWithCurrentFieldNames() {
        return new ActividadCreatePayload(
                "local-3", "Correr", 3000, 900, 780, 90, 30, 200,
                2_345, 300, 360, 1000, 1200, 1400, 1, 1, 2,
                "poly", "url", "2025-01-01T00:00:00Z"
        ).toJson();
    }

    private static JsonObject jsonWithAllSerializedFields() {
        return new ActividadCreatePayload(
                "local-1", "carrera", 5_000, 1_800, 1_700, 80, 20, 350,
                4_321, 340, 360, 300, 1_000, 1_400, 2, 1, 3,
                "poly", "map.png", "2026-04-25T10:00:00Z"
        ).toJson();
    }

    private static JsonObject jsonWithEnrichedTrackingFields() {
        return new ActividadCreatePayload(
                "local-1", "carrera", 5000, 1800, 1700, 80, 20, 350,
                4_321, 340, 360, 300, 1000, 1500, 1, 2, 3,
                "poly", "map.png", "2026-04-25T10:00:00Z"
        ).toJson();
    }

    private static JsonObject jsonFromFullyPopulatedEntity() {
        return ActividadCreatePayload.fromEntity(fullyPopulatedEntity()).toJson();
    }

    private static ActividadEntity fullyPopulatedEntity() {
        ActividadEntity entity = new ActividadEntity();
        entity.localId = "local-entity";
        entity.tipo = "caminata";
        entity.distancia = 2500;
        entity.duracionTotal = 900;
        entity.duracionMovimiento = 850;
        entity.duracionParado = 40;
        entity.duracionPausaManual = 10;
        entity.caloriasQuemadas = 120;
        entity.pasos = 3_210;
        entity.ritmoMedioMovimiento = 400;
        entity.ritmoMedioTotal = 430;
        entity.ritmoMaximo = 350;
        entity.velocidadMediaKmhX100 = 800;
        entity.velocidadMaxKmhX100 = 1000;
        entity.autoPausas = 1;
        entity.pausasManuales = 0;
        entity.alertasVelocidad = 2;
        entity.rutaPolilinea = "entity-poly";
        entity.rutaMapaUrl = "entity-map";
        entity.fechaRuta = "2026-04-25T12:00:00Z";
        return entity;
    }

}
