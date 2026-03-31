package com.proyecto.moveon.data.ranking.dto;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.google.gson.Gson;

import org.junit.Test;

/**
 * Tests unitarios del DTO del ranking.
 *
 * <p>Verifican que el campo {@code posicion} se deserializa correctamente desde
 * el backend y que el resto del contrato del ranking se mantiene intacto.</p>
 */
public class RankingItemDtoTest {

    private final Gson gson = new Gson();

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
}
