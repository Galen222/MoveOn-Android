package com.proyecto.moveon.data.ranking;

import static org.junit.Assert.*;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.proyecto.moveon.data.ranking.dto.RankingItemDto;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Tests de parsing y construcción de URL privados de {@link RankingRepository} sin instanciar Android {@code Context}.
 */
public class RankingRepositoryPrivateParsingTest {

    /**
     * Verifica que la URL nacional no añade query cuando la provincia está vacía o ausente.
     */
    @Test
    public void buildUrl_withoutProvinceReturnsBaseEndpoint() throws Exception {
        RankingRepository repository = allocateRepository();

        assertEquals("ranking/obtener", invoke(repository, "buildUrl", new Class<?>[]{String.class}, new Object[]{null}));
        assertEquals("ranking/obtener", invoke(repository, "buildUrl", new Class<?>[]{String.class}, "   "));
    }

    /**
     * Verifica que la provincia se recorta y codifica en UTF-8 para la query del ranking.
     */
    @Test
    public void buildUrl_withProvinceTrimsAndEncodesUtf8() throws Exception {
        RankingRepository repository = allocateRepository();

        assertEquals("ranking/obtener?provincia=A+Coru%C3%B1a",
                invoke(repository, "buildUrl", new Class<?>[]{String.class}, "  A Coruña  "));
        assertEquals("ranking/obtener?provincia=Castell%C3%B3n%2FCastell%C3%B3",
                invoke(repository, "buildUrl", new Class<?>[]{String.class}, "Castellón/Castelló"));
    }

    /**
     * Verifica que el parser de ranking transforma arrays JSON y rechaza nodos que no cumplen contrato.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void parseRanking_acceptsArraysAndRejectsOtherPayloads() throws Exception {
        RankingRepository repository = allocateRepository();
        JsonElement array = JsonParser.parseString("[{\"posicion\":2,\"nombre_usuario\":\"ana\",\"foto_perfil\":null,\"foto_version\":0,\"total_puntos\":40,\"total_metros\":2000}]");
        JsonElement object = JsonParser.parseString("{\"mensaje\":\"no es array\"}");

        List<RankingItemDto> ranking = (List<RankingItemDto>) invoke(repository, "parseRanking", new Class<?>[]{JsonElement.class}, array);

        assertNotNull(ranking);
        assertEquals(1, ranking.size());
        assertEquals(2, ranking.getFirst().posicion);
        assertEquals("ana", ranking.getFirst().nombreUsuario);
        assertNull(invoke(repository, "parseRanking", new Class<?>[]{JsonElement.class}, object));
        assertNull(invoke(repository, "parseRanking", new Class<?>[]{JsonElement.class}, new Object[]{null}));
    }

    /**
     * Verifica que el parser de mensaje usa el campo mensaje cuando existe y cae a OK en payloads incompletos.
     */
    @Test
    public void parseMensaje_usesMensajeOrOkFallback() throws Exception {
        RankingRepository repository = allocateRepository();
        JsonElement withMessage = JsonParser.parseString("{\"mensaje\":\"Reporte recibido\"}");
        JsonElement nullMessage = JsonParser.parseString("{\"mensaje\":null}");
        JsonElement array = JsonParser.parseString("[]");

        assertEquals("Reporte recibido", invoke(repository, "parseMensaje", new Class<?>[]{JsonElement.class}, withMessage));
        assertEquals("OK", invoke(repository, "parseMensaje", new Class<?>[]{JsonElement.class}, nullMessage));
        assertEquals("OK", invoke(repository, "parseMensaje", new Class<?>[]{JsonElement.class}, array));
        assertEquals("OK", invoke(repository, "parseMensaje", new Class<?>[]{JsonElement.class}, new Object[]{null}));
    }

    private static RankingRepository allocateRepository() throws Exception {
        Field field = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
        field.setAccessible(true);
        Object unsafe = java.util.Objects.requireNonNull(field.get(null), "Unsafe no disponible");
        Method method = unsafe.getClass().getMethod("allocateInstance", Class.class);
        return (RankingRepository) method.invoke(unsafe, RankingRepository.class);
    }

    private static Object invoke(Object target, String methodName, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw e;
        }
    }
}
