package com.proyecto.moveon.core.api;

import static org.junit.Assert.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tests de ramas privadas puras de {@link ApiErrorParser} que no necesitan Android {@code Context}.
 */
public class ApiErrorParserPrivateBranchesTest {

    /**
     * Verifica el mapeo completo de códigos HTTP conocidos a tipos de dominio.
     */
    @Test
    public void mapHttpToType_coversKnownAndFallbackCodes() throws Exception {
        assertEquals(ApiErrorType.UNAUTHORIZED, invoke("mapHttpToType", new Class<?>[]{int.class}, 401));
        assertEquals(ApiErrorType.FORBIDDEN, invoke("mapHttpToType", new Class<?>[]{int.class}, 403));
        assertEquals(ApiErrorType.NOT_FOUND, invoke("mapHttpToType", new Class<?>[]{int.class}, 404));
        assertEquals(ApiErrorType.TIMEOUT, invoke("mapHttpToType", new Class<?>[]{int.class}, 408));
        assertEquals(ApiErrorType.CONFLICT, invoke("mapHttpToType", new Class<?>[]{int.class}, 409));
        assertEquals(ApiErrorType.PAYLOAD_TOO_LARGE, invoke("mapHttpToType", new Class<?>[]{int.class}, 413));
        assertEquals(ApiErrorType.RATE_LIMIT, invoke("mapHttpToType", new Class<?>[]{int.class}, 429));
        assertEquals(ApiErrorType.VALIDATION, invoke("mapHttpToType", new Class<?>[]{int.class}, 400));
        assertEquals(ApiErrorType.VALIDATION, invoke("mapHttpToType", new Class<?>[]{int.class}, 422));
        assertEquals(ApiErrorType.SERVER, invoke("mapHttpToType", new Class<?>[]{int.class}, 503));
        assertEquals(ApiErrorType.UNKNOWN, invoke("mapHttpToType", new Class<?>[]{int.class}, 418));
    }

    /**
     * Verifica que los mensajes genéricos de framework se detectan y los específicos se conservan.
     */
    @Test
    public void isGenericFrameworkMessage_detectsFrameworkMessagesOnly() throws Exception {
        for (String generic : Arrays.asList(null, " ", "Not Found", "forbidden.", "UNAUTHORIZED", "bad request", "solicitud inválida")) {
            assertEquals("Debe considerarse genérico: " + generic,
                    Boolean.TRUE,
                    invoke("isGenericFrameworkMessage", new Class<?>[]{String.class}, new Object[]{generic}));
        }

        assertEquals(Boolean.FALSE,
                invoke("isGenericFrameworkMessage", new Class<?>[]{String.class}, "Email ya registrado"));
    }

    /**
     * Verifica que la limpieza de mensaje elimina el prefijo técnico y conserva mensajes ya limpios.
     */
    @Test
    public void cleanBackendMsg_removesTechnicalPrefixOnlyWhenPresent() throws Exception {
        assertEquals("Credenciales incorrectas",
                invoke("cleanBackendMsg", new Class<?>[]{String.class}, " Error: Credenciales incorrectas "));
        assertEquals("Ya estaba limpio",
                invoke("cleanBackendMsg", new Class<?>[]{String.class}, "Ya estaba limpio"));
    }

    /**
     * Comprueba que la lectura segura de strings JSON ignora ausentes, blancos y no primitivos.
     */
    @Test
    public void getString_returnsOnlyUsefulPrimitiveStrings() throws Exception {
        JsonObject object = JsonParser.parseString("{\"ok\":\" valor \",\"blank\":\"   \",\"nested\":{}}").getAsJsonObject();

        assertEquals(" valor ", invoke("getString", new Class<?>[]{JsonObject.class, String.class}, object, "ok"));
        assertNull(invoke("getString", new Class<?>[]{JsonObject.class, String.class}, object, "blank"));
        assertNull(invoke("getString", new Class<?>[]{JsonObject.class, String.class}, object, "nested"));
        assertNull(invoke("getString", new Class<?>[]{JsonObject.class, String.class}, object, "missing"));
    }

    /**
     * Verifica que la selección de primer texto útil respeta orden y tolera arrays nulos.
     */
    @Test
    public void firstNonEmpty_returnsFirstUsefulValue() throws Exception {
        assertEquals("segundo", invoke("firstNonEmpty", new Class<?>[]{String[].class}, (Object) new String[]{null, " ", "segundo", "tercero"}));
        assertNull(invoke("firstNonEmpty", new Class<?>[]{String[].class}, new Object[]{null}));
        assertNull(invoke("firstNonEmpty", new Class<?>[]{String[].class}, (Object) new String[]{" ", null}));
    }

    /**
     * Verifica que añadir errores por campo crea listas, acumula mensajes y descarta entradas vacías.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void addFieldError_accumulatesValidMessagesOnly() throws Exception {
        Map<String, List<String>> map = new HashMap<>();

        invoke("addFieldError", new Class<?>[]{Map.class, String.class, String.class}, map, "email", "Obligatorio");
        invoke("addFieldError", new Class<?>[]{Map.class, String.class, String.class}, map, "email", "Inválido");
        invoke("addFieldError", new Class<?>[]{Map.class, String.class, String.class}, map, " ", "Ignorado");
        invoke("addFieldError", new Class<?>[]{Map.class, String.class, String.class}, map, "password", " ");

        assertEquals(Arrays.asList("Obligatorio", "Inválido"), map.get("email"));
        assertFalse(map.containsKey("password"));
    }

    /**
     * Verifica que la fusión de errores preserva mensajes válidos y descarta listas nulas o textos vacíos.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void mergeFieldErrors_preservesOnlyUsefulMessages() throws Exception {
        Map<String, List<String>> target = new HashMap<>();
        Map<String, List<String>> source = new HashMap<>();
        source.put("email", Arrays.asList("Uno", " ", "Dos"));
        source.put("ignored", null);

        invoke("mergeFieldErrors", new Class<?>[]{Map.class, Map.class}, target, source);

        assertEquals(Arrays.asList("Uno", "Dos"), target.get("email"));
        assertFalse(target.containsKey("ignored"));
    }

    /**
     * Verifica que el campo de una ubicación tipo FastAPI se extrae desde el último elemento útil.
     */
    @Test
    public void lastLocAsFieldName_usesLastPrimitiveElement() throws Exception {
        JsonArray loc = JsonParser.parseString("[\"body\",\"email\"]").getAsJsonArray();
        JsonArray blank = JsonParser.parseString("[\"body\",\"   \"]").getAsJsonArray();

        assertEquals("email", invoke("lastLocAsFieldName", new Class<?>[]{JsonArray.class}, loc));
        assertNull(invoke("lastLocAsFieldName", new Class<?>[]{JsonArray.class}, blank));
        assertNull(invoke("lastLocAsFieldName", new Class<?>[]{JsonArray.class}, new Object[]{null}));
    }


    /**
     * Verifica que lastLocAsFieldName ignora arrays vacíos o cuyo último elemento no es primitivo.
     */
    @Test
    public void lastLocAsFieldName_returnsNullForEmptyOrNonPrimitiveLastElement() throws Exception {
        JsonArray empty = new JsonArray();
        JsonArray nested = JsonParser.parseString("[\"body\",{}]").getAsJsonArray();

        assertNull(invoke("lastLocAsFieldName", new Class<?>[]{JsonArray.class}, empty));
        assertNull(invoke("lastLocAsFieldName", new Class<?>[]{JsonArray.class}, nested));
    }

    private static Object invoke(String methodName, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = ApiErrorParser.class.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        try {
            return method.invoke(null, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw e;
        }
    }
}
