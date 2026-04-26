package com.proyecto.moveon.data.profile.dto;

import static org.junit.Assert.*;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.junit.Test;

/**
 * Tests de serialización/deserialización de DTOs de perfil.
 */
public class ProfileDtoSerializationTest {

    private final Gson gson = new Gson();

    /**
     * Verifica que {@link ProfileInfoDto} mapea todos los campos snake_case del backend.
     */
    @Test
    public void profileInfo_deserializesSnakeCaseFields() {
        String raw = "{"
                + "\"nombre_usuario\":\"alice\","
                + "\"nombre_real\":\"Alice Runner\","
                + "\"email\":\"alice@example.com\","
                + "\"fecha_nacimiento\":\"2000-01-01\","
                + "\"genero\":\"female\","
                + "\"altura\":170,"
                + "\"peso\":62.5,"
                + "\"provincia\":\"Madrid\","
                + "\"foto_perfil\":\"photo.png\","
                + "\"foto_version\":3,"
                + "\"perfil_visible\":true,"
                + "\"total_puntos\":99,"
                + "\"total_calorias\":1234,"
                + "\"objetivo_semanal_metros\":50000,"
                + "\"objetivo_mensual_metros\":150000"
                + "}";

        ProfileInfoDto dto = gson.fromJson(raw, ProfileInfoDto.class);

        assertEquals("alice", dto.nombreUsuario);
        assertEquals("Alice Runner", dto.nombreReal);
        assertEquals("alice@example.com", dto.email);
        assertEquals("2000-01-01", dto.fechaNacimiento);
        assertEquals("female", dto.genero);
        assertEquals(Integer.valueOf(170), dto.altura);
        assertEquals(Double.valueOf(62.5), dto.peso);
        assertEquals("Madrid", dto.provincia);
        assertEquals("photo.png", dto.fotoPerfil);
        assertEquals(3, dto.fotoVersion);
        assertTrue(dto.perfilVisible);
        assertEquals(99, dto.totalPuntos);
        assertEquals(1234L, dto.totalCalorias);
        assertEquals(50000L, dto.objetivoSemanalMetros);
        assertEquals(150000L, dto.objetivoMensualMetros);
    }

    /**
     * Verifica que el builder de {@link UpdateProfileRequestDto} serializa campos configurados.
     */
    @Test
    public void updateProfileRequest_builderSerializesConfiguredFields() {
        UpdateProfileRequestDto dto = new UpdateProfileRequestDto.Builder()
                .nombreReal("Alice Runner")
                .email("alice@example.com")
                .fechaNacimiento("2000-01-01")
                .genero("female")
                .altura(170)
                .peso(62.5)
                .provincia("Madrid")
                .perfilVisible(false)
                .build();

        JsonObject json = gson.fromJson(gson.toJson(dto), JsonObject.class);

        assertEquals("Alice Runner", json.get("nombre_real").getAsString());
        assertEquals("alice@example.com", json.get("email").getAsString());
        assertEquals("2000-01-01", json.get("fecha_nacimiento").getAsString());
        assertEquals("female", json.get("genero").getAsString());
        assertEquals(170, json.get("altura").getAsInt());
        assertEquals(62.5, json.get("peso").getAsDouble(), 0.0001);
        assertEquals("Madrid", json.get("provincia").getAsString());
        assertFalse(json.get("perfil_visible").getAsBoolean());
    }

    /**
     * Verifica que los campos no establecidos en el builder no se emiten en JSON.
     */
    @Test
    public void updateProfileRequest_omitsUnsetFields() {
        UpdateProfileRequestDto dto = new UpdateProfileRequestDto.Builder()
                .email("alice@example.com")
                .build();

        JsonObject json = gson.fromJson(gson.toJson(dto), JsonObject.class);

        assertTrue(json.has("email"));
        assertFalse(json.has("nombre_real"));
        assertFalse(json.has("altura"));
        assertFalse(json.has("perfil_visible"));
    }
}
