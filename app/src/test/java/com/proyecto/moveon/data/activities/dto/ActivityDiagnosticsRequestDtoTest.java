package com.proyecto.moveon.data.activities.dto;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * Tests de serialización para {@link ActivityDiagnosticsRequestDto}.
 *
 * <p>Protegen el contrato JSON con el backend para que el payload se siga enviando
 * en {@code snake_case} aunque el modelo Java use nombres en {@code camelCase}.</p>
 */
public final class ActivityDiagnosticsRequestDtoTest {

    private final Gson gson = new Gson();

    /**
     * Verifica que Gson emite las claves esperadas por el backend.
     */
    @Test
    public void toJson_serializesSnakeCaseFields() {
        ActivityDiagnosticsRequestDto dto = new ActivityDiagnosticsRequestDto();
        dto.actividadId = 77;
        dto.actividadLocalId = "local-123";
        dto.sessionStartedAt = "2026-04-05T10:00:00Z";
        dto.sessionFinishedAt = "2026-04-05T11:00:00Z";
        dto.lastTimerTickAt = "2026-04-05T10:59:59Z";
        dto.serviceCreatedAt = "2026-04-05T09:59:55Z";
        dto.serviceDestroyedAt = "2026-04-05T11:00:05Z";
        dto.elapsedSeconds = 3600;
        dto.movingSeconds = 3400;
        dto.stoppedSeconds = 200;
        dto.manualPauseSeconds = 15;
        dto.distanceMeters = 16010;
        dto.averagePaceTotal = 396;
        dto.averagePaceMoving = 389;
        dto.maxPace = 230;
        dto.autoPauses = 1;
        dto.manualPauses = 0;
        dto.speedAlerts = 0;
        dto.runningClassifiedSeconds = 3200;
        dto.walkingClassifiedSeconds = 200;
        dto.serviceRestartCount = 0;
        dto.currentStatus = "FINISHED";
        dto.appVersion = "1.2.3";
        dto.osVersion = "Android 15";
        dto.manufacturer = "Google";
        dto.model = "Pixel";

        Map<String, Object> deviceInfo = new HashMap<>();
        deviceInfo.put("sdk_int", 35);
        deviceInfo.put("brand", "google");
        dto.deviceInfo = deviceInfo;

        ActivityDiagnosticsRequestDto.EventItem eventItem = new ActivityDiagnosticsRequestDto.EventItem();
        eventItem.at = "2026-04-05T10:10:00Z";
        eventItem.tipo = "AUTO_PAUSE";
        eventItem.detalle = "stationary";
        dto.eventLog.add(eventItem);

        JsonObject json = JsonParser.parseString(gson.toJson(dto)).getAsJsonObject();

        assertTrue(json.has("actividad_id"));
        assertTrue(json.has("actividad_local_id"));
        assertTrue(json.has("session_started_at"));
        assertTrue(json.has("session_finished_at"));
        assertTrue(json.has("last_timer_tick_at"));
        assertTrue(json.has("service_created_at"));
        assertTrue(json.has("service_destroyed_at"));
        assertTrue(json.has("elapsed_seconds"));
        assertTrue(json.has("moving_seconds"));
        assertTrue(json.has("stopped_seconds"));
        assertTrue(json.has("manual_pause_seconds"));
        assertTrue(json.has("distance_meters"));
        assertTrue(json.has("average_pace_total"));
        assertTrue(json.has("average_pace_moving"));
        assertTrue(json.has("max_pace"));
        assertTrue(json.has("auto_pauses"));
        assertTrue(json.has("manual_pauses"));
        assertTrue(json.has("speed_alerts"));
        assertTrue(json.has("running_classified_seconds"));
        assertTrue(json.has("walking_classified_seconds"));
        assertTrue(json.has("service_restart_count"));
        assertTrue(json.has("current_status"));
        assertTrue(json.has("app_version"));
        assertTrue(json.has("os_version"));
        assertTrue(json.has("manufacturer"));
        assertTrue(json.has("model"));
        assertTrue(json.has("device_info"));
        assertTrue(json.has("event_log"));

        assertFalse(json.has("actividadId"));
        assertFalse(json.has("actividadLocalId"));
        assertFalse(json.has("sessionStartedAt"));
        assertFalse(json.has("elapsedSeconds"));
        assertFalse(json.has("distanceMeters"));
        assertFalse(json.has("averagePaceTotal"));
        assertFalse(json.has("averagePaceMoving"));
        assertFalse(json.has("currentStatus"));
        assertFalse(json.has("deviceInfo"));
        assertFalse(json.has("eventLog"));

        JsonObject eventJson = json.getAsJsonArray("event_log").get(0).getAsJsonObject();
        assertTrue(eventJson.has("at"));
        assertTrue(eventJson.has("tipo"));
        assertTrue(eventJson.has("detalle"));
    }
}
