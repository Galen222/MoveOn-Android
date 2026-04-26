package com.proyecto.moveon.core.api;

import static org.junit.Assert.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Tests JVM de ramas HTTP puras de {@link ApiErrorParser}.
 *
 * <p>Evitan resolver recursos Android con {@code Context#getString(...)} porque ese método es
 * final en el SDK y no se puede simular de forma fiable en unit tests JVM sin Robolectric.</p>
 */
public class ApiErrorParserHttpTest {

    /**
     * Verifica que los códigos HTTP conocidos se clasifican en el tipo de error esperado.
     */
    @Test
    public void mapHttpToType_coversHttpStatusFamiliesAndFallback() throws Exception {
        assertEquals(ApiErrorType.UNAUTHORIZED, invoke("mapHttpToType", new Class<?>[]{int.class}, 401));
        assertEquals(ApiErrorType.FORBIDDEN, invoke("mapHttpToType", new Class<?>[]{int.class}, 403));
        assertEquals(ApiErrorType.NOT_FOUND, invoke("mapHttpToType", new Class<?>[]{int.class}, 404));
        assertEquals(ApiErrorType.TIMEOUT, invoke("mapHttpToType", new Class<?>[]{int.class}, 408));
        assertEquals(ApiErrorType.CONFLICT, invoke("mapHttpToType", new Class<?>[]{int.class}, 409));
        assertEquals(ApiErrorType.PAYLOAD_TOO_LARGE, invoke("mapHttpToType", new Class<?>[]{int.class}, 413));
        assertEquals(ApiErrorType.RATE_LIMIT, invoke("mapHttpToType", new Class<?>[]{int.class}, 429));
        assertEquals(ApiErrorType.VALIDATION, invoke("mapHttpToType", new Class<?>[]{int.class}, 400));
        assertEquals(ApiErrorType.VALIDATION, invoke("mapHttpToType", new Class<?>[]{int.class}, 422));
        assertEquals(ApiErrorType.SERVER, invoke("mapHttpToType", new Class<?>[]{int.class}, 500));
        assertEquals(ApiErrorType.UNKNOWN, invoke("mapHttpToType", new Class<?>[]{int.class}, 418));
    }

    /**
     * Verifica que los mensajes genéricos del framework se detectan y los mensajes útiles se conservan.
     */
    @Test
    public void isGenericFrameworkMessage_detectsOnlyGenericMessages() throws Exception {
        assertEquals(Boolean.TRUE, invoke("isGenericFrameworkMessage", new Class<?>[]{String.class}, new Object[]{null}));
        assertEquals(Boolean.TRUE, invoke("isGenericFrameworkMessage", new Class<?>[]{String.class}, " "));
        assertEquals(Boolean.TRUE, invoke("isGenericFrameworkMessage", new Class<?>[]{String.class}, "Not Found"));
        assertEquals(Boolean.TRUE, invoke("isGenericFrameworkMessage", new Class<?>[]{String.class}, "bad request"));
        assertEquals(Boolean.TRUE, invoke("isGenericFrameworkMessage", new Class<?>[]{String.class}, "solicitud inválida"));
        assertEquals(Boolean.FALSE, invoke("isGenericFrameworkMessage", new Class<?>[]{String.class}, "Email ya registrado"));
    }

    /**
     * Verifica que la limpieza de mensajes elimina el prefijo técnico sin alterar mensajes ya limpios.
     */
    @Test
    public void cleanBackendMsg_removesErrorPrefixAndTrimsMessage() throws Exception {
        assertEquals("Email repetido",
                invoke("cleanBackendMsg", new Class<?>[]{String.class}, " Error: Email repetido "));
        assertEquals("Mensaje limpio",
                invoke("cleanBackendMsg", new Class<?>[]{String.class}, "Mensaje limpio"));
    }

    /**
     * Verifica que la lectura segura de propiedades JSON descarta ausentes, blancos y no primitivos.
     */
    @Test
    public void getString_returnsOnlyUsefulPrimitiveValues() throws Exception {
        JsonObject object = JsonParser.parseString("{\"message\":\" hola \",\"blank\":\"   \",\"nested\":{}}").getAsJsonObject();

        assertEquals(" hola ", invoke("getString", new Class<?>[]{JsonObject.class, String.class}, object, "message"));
        assertNull(invoke("getString", new Class<?>[]{JsonObject.class, String.class}, object, "blank"));
        assertNull(invoke("getString", new Class<?>[]{JsonObject.class, String.class}, object, "nested"));
        assertNull(invoke("getString", new Class<?>[]{JsonObject.class, String.class}, object, "missing"));
    }

    /**
     * Verifica que se selecciona el primer texto útil respetando el orden de prioridad.
     */
    @Test
    public void firstNonEmpty_returnsFirstUsefulCandidate() throws Exception {
        assertEquals("segundo",
                invoke("firstNonEmpty", new Class<?>[]{String[].class},
                        (Object) new String[]{null, " ", "segundo", "tercero"}));
        assertNull(invoke("firstNonEmpty", new Class<?>[]{String[].class}, new Object[]{null}));
        assertNull(invoke("firstNonEmpty", new Class<?>[]{String[].class}, (Object) new String[]{" ", null}));
    }

    /**
     * Verifica que el último elemento primitivo de una localización FastAPI se usa como campo.
     */
    @Test
    public void lastLocAsFieldName_extractsLastPrimitiveFieldName() throws Exception {
        JsonArray loc = JsonParser.parseString("[\"body\",\"password\"]").getAsJsonArray();
        JsonArray blank = JsonParser.parseString("[\"body\",\"   \"]").getAsJsonArray();
        JsonArray nested = JsonParser.parseString("[\"body\",{}]").getAsJsonArray();

        assertEquals("password", invoke("lastLocAsFieldName", new Class<?>[]{JsonArray.class}, loc));
        assertNull(invoke("lastLocAsFieldName", new Class<?>[]{JsonArray.class}, blank));
        assertNull(invoke("lastLocAsFieldName", new Class<?>[]{JsonArray.class}, nested));
        assertNull(invoke("lastLocAsFieldName", new Class<?>[]{JsonArray.class}, new Object[]{null}));
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
