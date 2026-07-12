package com.proyecto.moveon.data.ranking;

import static org.junit.Assert.*;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonParser;
import com.proyecto.moveon.data.ranking.dto.RankingItemDto;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Tests de lógica pura privada de {@link RankingRepository} sin red ni contexto Android.
 */
public class RankingRepositoryPureTest {

    /**
     * Verifica que buildUrl conserva el endpoint nacional para provincia nula o vacía.
     */
    @Test
    public void buildUrl_nullOrBlankProvinceReturnsBaseEndpoint() throws Exception {
        RankingRepository repository = allocateRepository();

        assertEquals("ranking/obtener", invokeBuildUrl(repository, null));
        assertEquals("ranking/obtener", invokeBuildUrl(repository, "   "));
    }

    /**
     * Verifica que buildUrl recorta y codifica correctamente provincias con espacios y caracteres no ASCII.
     */
    @Test
    public void buildUrl_nonBlankProvinceIsTrimmedAndUrlEncoded() throws Exception {
        RankingRepository repository = allocateRepository();

        String url = invokeBuildUrl(repository, " A Coruña ");

        assertEquals("ranking/obtener?provincia=A+Coru%C3%B1a", url);
    }

    /**
     * Verifica que parseRanking transforma arrays JSON válidos en DTOs de ranking.
     */
    @Test
    public void parseRanking_validArrayReturnsDtoList() throws Exception {
        RankingRepository repository = allocateRepository();
        JsonElement json = JsonParser.parseString("[{\"posicion\":2,\"nombre_usuario\":\"ana\",\"foto_perfil\":\"https://cdn/f.jpg\",\"foto_version\":7,\"total_puntos\":91,\"total_metros\":12345}]");

        List<RankingItemDto> items = invokeParseRanking(repository, json);

        assertNotNull(items);
        assertEquals(1, items.size());
        RankingItemDto item = items.getFirst();
        assertEquals(2, item.posicion);
        assertEquals("ana", item.nombreUsuario);
        assertEquals("https://cdn/f.jpg", item.fotoPerfil);
        assertEquals(7, item.fotoVersion);
        assertEquals(91, item.totalPuntos);
        assertEquals(12345, item.totalMetros);
    }

    /**
     * Verifica que parseRanking devuelve null para payloads que no son arrays.
     */
    @Test
    public void parseRanking_nonArrayPayloadsReturnNull() throws Exception {
        RankingRepository repository = allocateRepository();

        assertNull(invokeParseRanking(repository, null));
        assertNull(invokeParseRanking(repository, JsonNull.INSTANCE));
        assertNull(invokeParseRanking(repository, JsonParser.parseString("{\"items\":[]}")));
    }

    /**
     * Verifica que parseMensaje extrae el mensaje de backend y usa OK como fallback defensivo.
     */
    @Test
    public void parseMensaje_readsMessageOrReturnsOkFallback() throws Exception {
        RankingRepository repository = allocateRepository();

        assertEquals("Reporte recibido",
                invokeParseMensaje(repository, JsonParser.parseString("{\"mensaje\":\"Reporte recibido\"}")));
        assertEquals("OK",
                invokeParseMensaje(repository, JsonParser.parseString("{\"mensaje\":null}")));
        assertEquals("OK",
                invokeParseMensaje(repository, JsonParser.parseString("{}")));
        assertEquals("OK",
                invokeParseMensaje(repository, JsonParser.parseString("[]")));
        assertEquals("OK",
                invokeParseMensaje(repository, null));
    }

    private static String invokeBuildUrl(RankingRepository repository, String provincia) throws Exception {
        Method method = RankingRepository.class.getDeclaredMethod("buildUrl", String.class);
        method.setAccessible(true);
        return (String) method.invoke(repository, provincia);
    }

    @SuppressWarnings("unchecked")
    private static List<RankingItemDto> invokeParseRanking(RankingRepository repository, JsonElement json) throws Exception {
        Method method = RankingRepository.class.getDeclaredMethod("parseRanking", JsonElement.class);
        method.setAccessible(true);
        return (List<RankingItemDto>) method.invoke(repository, json);
    }

    private static String invokeParseMensaje(RankingRepository repository, JsonElement json) throws Exception {
        Method method = RankingRepository.class.getDeclaredMethod("parseMensaje", JsonElement.class);
        method.setAccessible(true);
        return (String) method.invoke(repository, json);
    }

    private static RankingRepository allocateRepository() throws Exception {
        Field field = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
        field.setAccessible(true);
        Object unsafe = java.util.Objects.requireNonNull(field.get(null), "Unsafe no disponible");
        Method method = unsafe.getClass().getMethod("allocateInstance", Class.class);
        return (RankingRepository) method.invoke(unsafe, RankingRepository.class);
    }
}
