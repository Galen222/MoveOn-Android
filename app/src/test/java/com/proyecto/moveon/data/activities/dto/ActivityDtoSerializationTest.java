package com.proyecto.moveon.data.activities.dto;

import static org.junit.Assert.*;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.junit.Test;

import java.util.Collections;

/**
 * Tests de serialización/deserialización para DTOs de actividades.
 */
public class ActivityDtoSerializationTest {

    private final Gson gson = new Gson();

    /**
     * Verifica que {@link ActividadResponseDto} mapea todos los campos enriquecidos del backend.
     */
    @Test
    public void actividadResponse_deserializesAllTrackingFields() {
        String raw = "{"
                + "\"id\":7,"
                + "\"tipo\":\"carrera\","
                + "\"distancia\":5000,"
                + "\"duracion_total\":1800,"
                + "\"duracion_movimiento\":1700,"
                + "\"duracion_parado\":80,"
                + "\"duracion_pausa_manual\":20,"
                + "\"calorias_quemadas\":350,"
                + "\"ritmo_medio_movimiento\":340,"
                + "\"ritmo_medio_total\":360,"
                + "\"ritmo_maximo\":300,"
                + "\"velocidad_media_x100\":1000,"
                + "\"velocidad_max_x100\":1400,"
                + "\"auto_pausas\":2,"
                + "\"pausas_manuales\":1,"
                + "\"alertas_velocidad\":3,"
                + "\"ruta_polilinea\":\"poly\","
                + "\"ruta_mapa_url\":\"map.png\","
                + "\"fecha_ruta\":\"2026-04-25T10:00:00Z\","
                + "\"nuevo_total_puntos\":99"
                + "}";

        ActividadResponseDto dto = gson.fromJson(raw, ActividadResponseDto.class);

        assertEquals(7, dto.id);
        assertEquals("carrera", dto.tipo);
        assertEquals(5000, dto.distancia);
        assertEquals(1800, dto.duracionTotal);
        assertEquals(1700, dto.duracionMovimiento);
        assertEquals(80, dto.duracionParado);
        assertEquals(20, dto.duracionPausaManual);
        assertEquals(350, dto.caloriasQuemadas);
        assertEquals(340, dto.ritmoMedioMovimiento);
        assertEquals(360, dto.ritmoMedioTotal);
        assertEquals(300, dto.ritmoMaximo);
        assertEquals(1000, dto.velocidadMediaKmhX100);
        assertEquals(1400, dto.velocidadMaxKmhX100);
        assertEquals(2, dto.autoPausas);
        assertEquals(1, dto.pausasManuales);
        assertEquals(3, dto.alertasVelocidad);
        assertEquals("poly", dto.rutaPolilinea);
        assertEquals("map.png", dto.rutaMapaUrl);
        assertEquals("2026-04-25T10:00:00Z", dto.fechaRuta);
        assertEquals(99, dto.nuevoTotalPuntos);
    }

    /**
     * Verifica que {@link GuardarActividadResponseDto} se deserializa con los nombres esperados.
     */
    @Test
    public void guardarActividadResponse_deserializesAllFields() {
        String raw = "{"
                + "\"id\":8,"
                + "\"tipo\":\"caminata\","
                + "\"distancia\":3000,"
                + "\"duracion_total\":1600,"
                + "\"duracion_movimiento\":1500,"
                + "\"duracion_parado\":100,"
                + "\"duracion_pausa_manual\":0,"
                + "\"calorias_quemadas\":200,"
                + "\"ritmo_medio_movimiento\":500,"
                + "\"ritmo_medio_total\":520,"
                + "\"ritmo_maximo\":480,"
                + "\"velocidad_media_x100\":700,"
                + "\"velocidad_max_x100\":900,"
                + "\"auto_pausas\":1,"
                + "\"pausas_manuales\":0,"
                + "\"alertas_velocidad\":0,"
                + "\"ruta_polilinea\":\"poly2\","
                + "\"ruta_mapa_url\":\"map2.png\","
                + "\"fecha_ruta\":\"2026-04-25T11:00:00Z\","
                + "\"nuevo_total_puntos\":111"
                + "}";

        GuardarActividadResponseDto dto = gson.fromJson(raw, GuardarActividadResponseDto.class);

        assertEquals(8, dto.id);
        assertEquals("caminata", dto.tipo);
        assertEquals(3000, dto.distancia);
        assertEquals(1600, dto.duracionTotal);
        assertEquals(1500, dto.duracionMovimiento);
        assertEquals(100, dto.duracionParado);
        assertEquals(0, dto.duracionPausaManual);
        assertEquals(200, dto.caloriasQuemadas);
        assertEquals(500, dto.ritmoMedioMovimiento);
        assertEquals(520, dto.ritmoMedioTotal);
        assertEquals(480, dto.ritmoMaximo);
        assertEquals(700, dto.velocidadMediaKmhX100);
        assertEquals(900, dto.velocidadMaxKmhX100);
        assertEquals(1, dto.autoPausas);
        assertEquals(0, dto.pausasManuales);
        assertEquals(0, dto.alertasVelocidad);
        assertEquals("poly2", dto.rutaPolilinea);
        assertEquals("map2.png", dto.rutaMapaUrl);
        assertEquals("2026-04-25T11:00:00Z", dto.fechaRuta);
        assertEquals(111, dto.nuevoTotalPuntos);
    }

    /**
     * Verifica que {@link ActividadesPageDto} mapea paginación, totales y bandera has_more.
     */
    @Test
    public void actividadesPage_deserializesPaginationFields() {
        String raw = "{"
                + "\"items\":[{\"id\":1,\"tipo\":\"carrera\"}],"
                + "\"total\":50,"
                + "\"skip\":10,"
                + "\"limit\":20,"
                + "\"has_more\":true"
                + "}";

        ActividadesPageDto dto = gson.fromJson(raw, ActividadesPageDto.class);

        assertEquals(1, dto.items.size());
        assertEquals(1, dto.items.get(0).id);
        assertEquals("carrera", dto.items.get(0).tipo);
        assertEquals(50, dto.total);
        assertEquals(10, dto.skip);
        assertEquals(20, dto.limit);
        assertTrue(dto.hasMore);
    }

    /**
     * Verifica que {@link BorrarActividadResponseDto} mapea el estado y los puntos actualizados.
     */
    @Test
    public void borrarActividadResponse_deserializesResponseFields() {
        String raw = "{"
                + "\"estatus\":\"ok\","
                + "\"mensaje\":\"Actividad borrada\","
                + "\"nuevo_total_puntos\":123"
                + "}";

        BorrarActividadResponseDto dto = gson.fromJson(raw, BorrarActividadResponseDto.class);

        assertEquals("ok", dto.estatus);
        assertEquals("Actividad borrada", dto.mensaje);
        assertEquals(123, dto.nuevoTotalPuntos);
    }

    /**
     * Verifica que {@link ActivityDiagnosticsRequestDto} serializa sus métricas y el log de eventos.
     */
    @Test
    public void activityDiagnostics_serializesMetricsAndEvents() {
        ActivityDiagnosticsRequestDto dto = new ActivityDiagnosticsRequestDto();
        dto.actividadId = 7;
        dto.actividadLocalId = "local-7";
        dto.sessionStartedAt = "2026-04-25T10:00:00Z";
        dto.sessionFinishedAt = "2026-04-25T10:30:00Z";
        dto.elapsedSeconds = 1800;
        dto.movingSeconds = 1700;
        dto.stoppedSeconds = 100;
        dto.manualPauseSeconds = 0;
        dto.distanceMeters = 5000;
        dto.averagePaceTotal = 360;
        dto.averagePaceMoving = 340;
        dto.maxPace = 300;
        dto.autoPauses = 2;
        dto.manualPauses = 1;
        dto.speedAlerts = 3;
        dto.runningClassifiedSeconds = 1200;
        dto.walkingClassifiedSeconds = 500;
        dto.serviceRestartCount = 1;
        dto.currentStatus = "FINISHED";
        dto.appVersion = "1.0.5";
        dto.osVersion = "Android";
        dto.manufacturer = "Google";
        dto.model = "Pixel";
        dto.deviceInfo = Collections.<String, Object>singletonMap("sdk", 36);

        ActivityDiagnosticsRequestDto.EventItem event = new ActivityDiagnosticsRequestDto.EventItem();
        event.at = "2026-04-25T10:15:00Z";
        event.tipo = "AUTO_PAUSE";
        event.detalle = "stationary";
        dto.eventLog.add(event);

        JsonObject json = gson.fromJson(gson.toJson(dto), JsonObject.class);

        assertEquals(7, json.get("actividad_id").getAsInt());
        assertEquals("local-7", json.get("actividad_local_id").getAsString());
        assertEquals(1800, json.get("elapsed_seconds").getAsInt());
        assertEquals(5000, json.get("distance_meters").getAsInt());
        assertEquals(1, json.getAsJsonArray("event_log").size());
        assertEquals("AUTO_PAUSE", json.getAsJsonArray("event_log").get(0).getAsJsonObject().get("tipo").getAsString());
        assertEquals(36.0, json.getAsJsonObject("device_info").get("sdk").getAsDouble(), 0.0001);
    }
}
