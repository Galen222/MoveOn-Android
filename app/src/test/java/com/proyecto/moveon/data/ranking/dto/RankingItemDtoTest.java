package com.proyecto.moveon.data.ranking.dto;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.google.gson.Gson;

import org.junit.Test;

import static org.junit.Assert.*;
/**
 * Tests unitarios del DTO del ranking.
 *
 * <p>Verifican que el campo {@code posicion} se deserializa correctamente desde
 * el backend y que el resto del contrato del ranking se mantiene intacto.</p>
 */
public class RankingItemDtoTest {

    private final Gson gson = new Gson();

    /**
     * Verifica que Gson mapea correctamente los nombres {@code snake_case} del
     * backend ({@code total_puntos}, {@code total_metros}, {@code foto_version},
     * etc.) a los campos {@code camelCase} de {@link com.proyecto.moveon.data.ranking.dto.RankingItemDto}.
     *
     * <p>Este test existe para que cualquier cambio accidental en los
     * {@code @SerializedName} rompa la build antes de llegar a producción.</p>
     */
    @Test
    public void deserialization_mapsPositionAndMetrics() {
        String json = "{"
                + "\"posicion\":1,"
                + "\"nombre_usuario\":\"andrea18\","
                + "\"foto_perfil\":null,"
                + "\"foto_version\":0,"
                + "\"total_puntos\":123,"
                + "\"total_metros\":123456"
                + "}";

        RankingItemDto dto = gson.fromJson(json, RankingItemDto.class);

        assertEquals(1, dto.posicion);
        assertEquals("andrea18", dto.nombreUsuario);
        assertNull(dto.fotoPerfil);
        assertEquals(0, dto.fotoVersion);
        assertEquals(123, dto.totalPuntos);
        assertEquals(123456, dto.totalMetros);
    }
    /**
     * Verifica que el DTO del ranking deserializa posición real, foto y métricas.
     */
    @Test
    public void rankingItem_deserializesAllFields() {
        String raw = "{"
                + "\"posicion\":3,"
                + "\"nombre_usuario\":\"alice\","
                + "\"foto_perfil\":\"photo.png\","
                + "\"foto_version\":4,"
                + "\"total_puntos\":900,"
                + "\"total_metros\":45000"
                + "}";

        RankingItemDto dto = new Gson().fromJson(raw, RankingItemDto.class);

        assertEquals(3, dto.posicion);
        assertEquals("alice", dto.nombreUsuario);
        assertEquals("photo.png", dto.fotoPerfil);
        assertEquals(4, dto.fotoVersion);
        assertEquals(900, dto.totalPuntos);
        assertEquals(45_000, dto.totalMetros);
    }

    /**
     * Verifica que la foto de perfil puede venir nula sin impedir la deserialización.
     */
    @Test
    public void rankingItem_allowsNullProfilePhoto() {
        RankingItemDto dto = new Gson().fromJson(
                "{\"posicion\":1,\"nombre_usuario\":\"bob\",\"foto_perfil\":null,\"foto_version\":0,\"total_puntos\":1,\"total_metros\":2}",
                RankingItemDto.class
        );

        assertEquals("bob", dto.nombreUsuario);
        assertNull(dto.fotoPerfil);
        assertEquals(0, dto.fotoVersion);
    }
}
