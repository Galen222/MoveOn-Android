package com.proyecto.moveon.data.dto;

import static org.junit.Assert.*;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.proyecto.moveon.data.activities.dto.ActividadResponseDto;
import com.proyecto.moveon.data.activities.dto.ActividadesPageDto;
import com.proyecto.moveon.data.activities.dto.BorrarActividadResponseDto;
import com.proyecto.moveon.data.activities.dto.GuardarActividadResponseDto;
import com.proyecto.moveon.data.profile.dto.ProfileInfoDto;
import com.proyecto.moveon.data.profile.dto.UpdateProfileRequestDto;
import com.proyecto.moveon.data.session.dto.SocialAuthRequestDto;
import com.proyecto.moveon.data.session.dto.SocialRegisterRequestDto;

import org.junit.Test;

import java.util.Collections;

/**
 * Tests de serialización/deserialización para DTOs que no requieren framework Android.
 */
public class DtoSerializationTest {

    private final Gson gson = new Gson();

    /**
     * Verifica que {@link ActividadResponseDto} mapea todos los campos enriquecidos del backend.
     */
    @Test
    public void actividadResponseDto_deserializesAllEnrichedTrackingFields() {
        String json = "{"
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
                + "\"velocidad_max_x100\":1500,"
                + "\"auto_pausas\":1,"
                + "\"pausas_manuales\":2,"
                + "\"alertas_velocidad\":3,"
                + "\"ruta_polilinea\":\"abc\","
                + "\"ruta_mapa_url\":\"map.png\","
                + "\"fecha_ruta\":\"2026-04-25T10:00:00Z\","
                + "\"nuevo_total_puntos\":999"
                + "}";

        ActividadResponseDto dto = gson.fromJson(json, ActividadResponseDto.class);

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
        assertEquals(1500, dto.velocidadMaxKmhX100);
        assertEquals(1, dto.autoPausas);
        assertEquals(2, dto.pausasManuales);
        assertEquals(3, dto.alertasVelocidad);
        assertEquals("abc", dto.rutaPolilinea);
        assertEquals("map.png", dto.rutaMapaUrl);
        assertEquals("2026-04-25T10:00:00Z", dto.fechaRuta);
        assertEquals(999, dto.nuevoTotalPuntos);
    }

    /**
     * Verifica que {@link ActividadesPageDto} conserva paginación y lista de items.
     */
    @Test
    public void actividadesPageDto_serializesPaginationFields() {
        ActividadResponseDto item = new ActividadResponseDto();
        item.id = 1;
        item.tipo = "caminata";
        ActividadesPageDto page = new ActividadesPageDto();
        page.items = Collections.singletonList(item);
        page.total = 10;
        page.skip = 5;
        page.limit = 20;
        page.hasMore = true;

        JsonObject json = gson.fromJson(gson.toJson(page), JsonObject.class);

        assertEquals(1, json.getAsJsonArray("items").size());
        assertEquals(10, json.get("total").getAsInt());
        assertEquals(5, json.get("skip").getAsInt());
        assertEquals(20, json.get("limit").getAsInt());
        assertTrue(json.get("has_more").getAsBoolean());
    }

    /**
     * Verifica que {@link BorrarActividadResponseDto} deserializa mensaje y puntos actualizados.
     */
    @Test
    public void borrarActividadResponseDto_deserializesStatusMessageAndPoints() {
        BorrarActividadResponseDto dto = gson.fromJson(
                "{\"estatus\":\"ok\",\"mensaje\":\"borrada\",\"nuevo_total_puntos\":123}",
                BorrarActividadResponseDto.class
        );

        assertEquals("ok", dto.estatus);
        assertEquals("borrada", dto.mensaje);
        assertEquals(123, dto.nuevoTotalPuntos);
    }

    /**
     * Verifica que {@link GuardarActividadResponseDto} mapea la respuesta completa de alta de actividad.
     */
    @Test
    public void guardarActividadResponseDto_roundTripsEnrichedFields() {
        GuardarActividadResponseDto dto = new GuardarActividadResponseDto();
        dto.id = 9;
        dto.tipo = "bicicleta";
        dto.distancia = 12000;
        dto.duracionTotal = 3600;
        dto.duracionMovimiento = 3400;
        dto.duracionParado = 100;
        dto.duracionPausaManual = 100;
        dto.caloriasQuemadas = 700;
        dto.ritmoMedioMovimiento = 280;
        dto.ritmoMedioTotal = 300;
        dto.ritmoMaximo = 240;
        dto.velocidadMediaKmhX100 = 1200;
        dto.velocidadMaxKmhX100 = 2200;
        dto.autoPausas = 1;
        dto.pausasManuales = 1;
        dto.alertasVelocidad = 0;
        dto.rutaPolilinea = "poly";
        dto.rutaMapaUrl = "map";
        dto.fechaRuta = "2026-04-25T11:00:00Z";
        dto.nuevoTotalPuntos = 500;

        GuardarActividadResponseDto copy = gson.fromJson(gson.toJson(dto), GuardarActividadResponseDto.class);

        assertEquals(9, copy.id);
        assertEquals("bicicleta", copy.tipo);
        assertEquals(12000, copy.distancia);
        assertEquals(3600, copy.duracionTotal);
        assertEquals(3400, copy.duracionMovimiento);
        assertEquals(100, copy.duracionParado);
        assertEquals(100, copy.duracionPausaManual);
        assertEquals(700, copy.caloriasQuemadas);
        assertEquals(280, copy.ritmoMedioMovimiento);
        assertEquals(300, copy.ritmoMedioTotal);
        assertEquals(240, copy.ritmoMaximo);
        assertEquals(1200, copy.velocidadMediaKmhX100);
        assertEquals(2200, copy.velocidadMaxKmhX100);
        assertEquals(1, copy.autoPausas);
        assertEquals(1, copy.pausasManuales);
        assertEquals(0, copy.alertasVelocidad);
        assertEquals("poly", copy.rutaPolilinea);
        assertEquals("map", copy.rutaMapaUrl);
        assertEquals("2026-04-25T11:00:00Z", copy.fechaRuta);
        assertEquals(500, copy.nuevoTotalPuntos);
    }

    /**
     * Verifica que {@link ProfileInfoDto} deserializa datos de perfil y objetivos.
     */
    @Test
    public void profileInfoDto_deserializesProfileAndGoals() {
        String json = "{"
                + "\"nombre_usuario\":\"alice\","
                + "\"nombre_real\":\"Alice\","
                + "\"email\":\"alice@example.com\","
                + "\"fecha_nacimiento\":\"2000-01-01\","
                + "\"genero\":\"female\","
                + "\"altura\":170,"
                + "\"peso\":62.5,"
                + "\"provincia\":\"Madrid\","
                + "\"foto_perfil\":\"photo.png\","
                + "\"foto_version\":3,"
                + "\"perfil_visible\":true,"
                + "\"total_puntos\":1000,"
                + "\"total_calorias\":2222,"
                + "\"objetivo_semanal_metros\":50000,"
                + "\"objetivo_mensual_metros\":150000"
                + "}";

        ProfileInfoDto dto = gson.fromJson(json, ProfileInfoDto.class);

        assertEquals("alice", dto.nombreUsuario);
        assertEquals("Alice", dto.nombreReal);
        assertEquals("alice@example.com", dto.email);
        assertEquals("2000-01-01", dto.fechaNacimiento);
        assertEquals("female", dto.genero);
        assertEquals(Integer.valueOf(170), dto.altura);
        assertEquals(Double.valueOf(62.5), dto.peso);
        assertEquals("Madrid", dto.provincia);
        assertEquals("photo.png", dto.fotoPerfil);
        assertEquals(3, dto.fotoVersion);
        assertTrue(dto.perfilVisible);
        assertEquals(1000, dto.totalPuntos);
        assertEquals(2222L, dto.totalCalorias);
        assertEquals(50000L, dto.objetivoSemanalMetros);
        assertEquals(150000L, dto.objetivoMensualMetros);
    }

    /**
     * Verifica que el builder de {@link UpdateProfileRequestDto} conserva todos los campos establecidos.
     */
    @Test
    public void updateProfileRequestDto_builderPreservesAllFields() {
        UpdateProfileRequestDto dto = new UpdateProfileRequestDto.Builder()
                .nombreReal("Alice")
                .email("alice@example.com")
                .fechaNacimiento("2000-01-01")
                .genero("female")
                .altura(170)
                .peso(62.5)
                .provincia("Madrid")
                .perfilVisible(false)
                .build();

        assertEquals("Alice", dto.nombreReal);
        assertEquals("alice@example.com", dto.email);
        assertEquals("2000-01-01", dto.fechaNacimiento);
        assertEquals("female", dto.genero);
        assertEquals(Integer.valueOf(170), dto.altura);
        assertEquals(Double.valueOf(62.5), dto.peso);
        assertEquals("Madrid", dto.provincia);
        assertEquals(Boolean.FALSE, dto.perfilVisible);
    }

    /**
     * Verifica que el builder permite expresar un PATCH vacío sin inventar valores por defecto.
     */
    @Test
    public void updateProfileRequestDto_emptyBuilderKeepsFieldsNull() {
        UpdateProfileRequestDto dto = new UpdateProfileRequestDto.Builder().build();

        assertNull(dto.nombreReal);
        assertNull(dto.email);
        assertNull(dto.fechaNacimiento);
        assertNull(dto.genero);
        assertNull(dto.altura);
        assertNull(dto.peso);
        assertNull(dto.provincia);
        assertNull(dto.perfilVisible);
    }

    /**
     * Verifica la serialización del DTO de login social.
     */
    @Test
    public void socialAuthRequestDto_serializesProviderAndToken() {
        SocialAuthRequestDto dto = new SocialAuthRequestDto("google", "id-token");

        JsonObject json = gson.fromJson(gson.toJson(dto), JsonObject.class);

        assertEquals("google", json.get("provider").getAsString());
        assertEquals("id-token", json.get("token").getAsString());
    }

    /**
     * Verifica la serialización completa del DTO de registro social.
     */
    @Test
    public void socialRegisterRequestDto_serializesOnboardingFields() {
        SocialRegisterRequestDto dto = new SocialRegisterRequestDto(
                "google",
                "id-token",
                "alice",
                "2000-01-01",
                true,
                "2026-04-25T10:00:00Z",
                "terms-v1"
        );

        JsonObject json = gson.fromJson(gson.toJson(dto), JsonObject.class);

        assertEquals("google", json.get("provider").getAsString());
        assertEquals("id-token", json.get("token").getAsString());
        assertEquals("alice", json.get("nombre_usuario").getAsString());
        assertEquals("2000-01-01", json.get("fecha_nacimiento").getAsString());
        assertTrue(json.get("acepta_terminos").getAsBoolean());
        assertEquals("2026-04-25T10:00:00Z", json.get("fecha_aceptacion_terminos").getAsString());
        assertEquals("terms-v1", json.get("version_terminos").getAsString());
    }
}
