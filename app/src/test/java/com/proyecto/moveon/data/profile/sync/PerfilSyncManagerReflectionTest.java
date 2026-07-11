package com.proyecto.moveon.data.profile.sync;

import static org.junit.Assert.*;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.proyecto.moveon.core.api.ApiError;
import com.proyecto.moveon.core.api.ApiErrorType;
import com.proyecto.moveon.data.local.entity.PerfilCacheEntity;
import com.proyecto.moveon.domain.profile.PerfilUsuario;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Tests de helpers puros de {@link PerfilSyncManager} sin abrir Room ni llamar a red.
 */
public class PerfilSyncManagerReflectionTest {

    /**
     * Verifica que el patch parcial aplica todos los campos soportados y respeta nulos explícitos.
     */
    @Test
    public void applyPatchToCache_updatesPresentFieldsOnly() throws Exception {
        PerfilSyncManager manager = allocate(PerfilSyncManager.class);
        PerfilCacheEntity cache = new PerfilCacheEntity();
        cache.nombreReal = "Anterior";
        cache.email = "old@example.com";
        cache.fechaNacimiento = "1990-01-01";
        cache.genero = "old";
        cache.altura = 170;
        cache.peso = 70.0;
        cache.provincia = "Madrid";
        cache.perfilVisible = true;
        cache.objetivoSemanalMetros = 10L;
        cache.objetivoMensualMetros = 20L;

        JsonObject patch = new JsonObject();
        patch.addProperty("nombre_real", "Nuevo Nombre");
        patch.addProperty("email", "new@example.com");
        patch.addProperty("fecha_nacimiento", "1991-02-03");
        patch.add("genero", JsonNull.INSTANCE);
        patch.add("altura", JsonNull.INSTANCE);
        patch.addProperty("peso", 72.5);
        patch.addProperty("provincia", "Valencia");
        patch.addProperty("perfil_visible", false);
        patch.addProperty("objetivo_semanal_metros", 50000L);
        patch.addProperty("objetivo_mensual_metros", 150000L);

        invoke(manager, "applyPatchToCache", new Class<?>[]{PerfilCacheEntity.class, JsonObject.class}, cache, patch);

        assertEquals("Nuevo Nombre", cache.nombreReal);
        assertEquals("new@example.com", cache.email);
        assertEquals("1991-02-03", cache.fechaNacimiento);
        assertNull(cache.genero);
        assertNull(cache.altura);
        assertEquals(Double.valueOf(72.5), cache.peso);
        assertEquals("Valencia", cache.provincia);
        assertFalse(cache.perfilVisible);
        assertEquals(50_000L, cache.objetivoSemanalMetros);
        assertEquals(150_000L, cache.objetivoMensualMetros);
    }

    /**
     * Verifica que la aplicación de patch no modifica campos ausentes ni sobreescribe email con null.
     */
    @Test
    public void applyPatchToCache_ignoresAbsentFieldsAndNullEmail() throws Exception {
        PerfilSyncManager manager = allocate(PerfilSyncManager.class);
        PerfilCacheEntity cache = new PerfilCacheEntity();
        cache.email = "keep@example.com";
        cache.fechaNacimiento = "1980-05-05";
        cache.perfilVisible = true;
        JsonObject patch = new JsonObject();
        patch.add("email", JsonNull.INSTANCE);
        patch.addProperty("nombre_real", "Solo Nombre");

        invoke(manager, "applyPatchToCache", new Class<?>[]{PerfilCacheEntity.class, JsonObject.class}, cache, patch);

        assertEquals("Solo Nombre", cache.nombreReal);
        assertEquals("keep@example.com", cache.email);
        assertEquals("1980-05-05", cache.fechaNacimiento);
        assertTrue(cache.perfilVisible);
    }

    /**
     * Verifica que la copia de caché preserva todos los campos relevantes y queda desacoplada del origen.
     */
    @Test
    public void copyOf_copiesFieldsIntoIndependentEntity() throws Exception {
        PerfilSyncManager manager = allocate(PerfilSyncManager.class);
        PerfilCacheEntity source = filledCache();

        PerfilCacheEntity copy = (PerfilCacheEntity) invoke(manager, "copyOf", new Class<?>[]{PerfilCacheEntity.class}, source);
        copy.nombreUsuario = "mutado";

        assertEquals("user", source.nombreUsuario);
        assertEquals("mutado", copy.nombreUsuario);
        assertEquals(source.email, copy.email);
        assertEquals(source.localPhotoPath, copy.localPhotoPath);
        assertEquals(source.objetivoSemanalMetros, copy.objetivoSemanalMetros);
        assertEquals(source.objetivoMensualMetros, copy.objetivoMensualMetros);
        assertEquals(source.lastSyncedAtMs, copy.lastSyncedAtMs);
    }

    /**
     * Verifica que la caché vacía se crea con defaults compatibles con la UI y foto sincronizada.
     */
    @Test
    public void createEmptyCache_usesExpectedDefaults() throws Exception {
        PerfilSyncManager manager = allocate(PerfilSyncManager.class);
        setField(manager, "photoHelper", allocate(PhotoSyncHelper.class));

        PerfilCacheEntity cache = (PerfilCacheEntity) invoke(manager, "createEmptyCache", new Class<?>[]{String.class}, "account-1");

        assertEquals("account-1", cache.accountKey);
        assertEquals("account-1", cache.nombreUsuario);
        assertEquals("", cache.email);
        assertTrue(cache.perfilVisible);
        assertEquals(0, cache.totalPuntos);
        assertEquals(50_000L, cache.objetivoSemanalMetros);
        assertEquals(150_000L, cache.objetivoMensualMetros);
        assertEquals(PhotoSyncHelper.STATE_SYNCED, cache.photoSyncState);
        assertFalse(cache.dirty);
    }

    /**
     * Verifica que el mapper público de entidad a dominio expone todos los datos del perfil.
     */
    @Test
    public void mapEntityToDomain_preservesCacheFields() throws Exception {
        PerfilSyncManager manager = allocate(PerfilSyncManager.class);
        PerfilCacheEntity cache = filledCache();

        PerfilUsuario domain = manager.mapEntityToDomain(cache);

        assertEquals(cache.nombreUsuario, domain.nombreUsuario);
        assertEquals(cache.email, domain.email);
        assertEquals(cache.fechaNacimiento, domain.fechaNacimiento);
        assertEquals(cache.totalPuntos, domain.totalPuntos);
        assertEquals(cache.nombreReal, domain.nombreReal);
        assertEquals(cache.genero, domain.genero);
        assertEquals(cache.altura, domain.altura);
        assertEquals(cache.peso, domain.peso);
        assertEquals(cache.provincia, domain.provincia);
        assertEquals(cache.fotoPerfil, domain.fotoPerfil);
        assertEquals(cache.fotoVersion, domain.fotoVersion);
        assertEquals(cache.localPhotoPath, domain.localPhotoPath);
        assertEquals(cache.pendingLocalPhotoPath, domain.pendingLocalPhotoPath);
        assertEquals(cache.photoSyncState, domain.photoSyncState);
        assertEquals(cache.perfilVisible, domain.perfilVisible);
    }

    /**
     * Verifica la lectura de strings opcionales y la delegación de reintentos a {@link PhotoSyncHelper}.
     */
    @Test
    public void readNullableStringAndIsRetryable_coverSmallHelpers() throws Exception {
        PerfilSyncManager manager = allocate(PerfilSyncManager.class);
        JsonObject object = new JsonObject();
        object.addProperty("value", "texto");
        object.add("nullValue", JsonNull.INSTANCE);

        assertEquals("texto", invoke(manager, "readNullableString", new Class<?>[]{com.google.gson.JsonElement.class}, object.get("value")));
        assertNull(invoke(manager, "readNullableString", new Class<?>[]{com.google.gson.JsonElement.class}, object.get("nullValue")));
        assertTrue(manager.isRetryable(ApiError.typed(ApiErrorType.NETWORK, "red")));
        assertFalse(manager.isRetryable(ApiError.typed(ApiErrorType.VALIDATION, 422, "invalid")));
    }

    /**
     * Verifica que los campos no-nullables del patch ignoran JsonNull para no borrar valores consolidados.
     */
    @Test
    public void applyPatchToCache_ignoresJsonNullForNonNullableFields() throws Exception {
        PerfilSyncManager manager = allocate(PerfilSyncManager.class);
        PerfilCacheEntity cache = filledCache();
        JsonObject patch = new JsonObject();
        patch.add("email", JsonNull.INSTANCE);
        patch.add("fecha_nacimiento", JsonNull.INSTANCE);
        patch.add("perfil_visible", JsonNull.INSTANCE);
        patch.add("objetivo_semanal_metros", JsonNull.INSTANCE);
        patch.add("objetivo_mensual_metros", JsonNull.INSTANCE);

        invoke(manager, "applyPatchToCache", new Class<?>[]{PerfilCacheEntity.class, JsonObject.class}, cache, patch);

        assertEquals("user@example.com", cache.email);
        assertEquals("1990-01-01", cache.fechaNacimiento);
        assertFalse(cache.perfilVisible);
        assertEquals(111L, cache.objetivoSemanalMetros);
        assertEquals(222L, cache.objetivoMensualMetros);
    }

    /**
     * Verifica que los campos opcionales se pueden limpiar con JsonNull y que los ausentes se conservan.
     */
    @Test
    public void applyPatchToCache_clearsNullableFieldsAndPreservesAbsentOnes() throws Exception {
        PerfilSyncManager manager = allocate(PerfilSyncManager.class);
        PerfilCacheEntity cache = filledCache();
        JsonObject patch = new JsonObject();
        patch.add("nombre_real", JsonNull.INSTANCE);
        patch.add("genero", JsonNull.INSTANCE);
        patch.add("altura", JsonNull.INSTANCE);
        patch.add("peso", JsonNull.INSTANCE);
        patch.add("provincia", JsonNull.INSTANCE);

        invoke(manager, "applyPatchToCache", new Class<?>[]{PerfilCacheEntity.class, JsonObject.class}, cache, patch);

        assertNull(cache.nombreReal);
        assertNull(cache.genero);
        assertNull(cache.altura);
        assertNull(cache.peso);
        assertNull(cache.provincia);
        assertEquals("user@example.com", cache.email);
        assertEquals(123, cache.totalPuntos);
    }

    /**
     * Verifica que readNullableString conserva cadenas vacías como dato explícito y sólo convierte JsonNull en null.
     */
    @Test
    public void readNullableString_preservesEmptyStringValues() throws Exception {
        PerfilSyncManager manager = allocate(PerfilSyncManager.class);
        JsonObject object = new JsonObject();
        object.addProperty("empty", "");
        object.addProperty("spaces", "   ");

        assertEquals("", invoke(manager, "readNullableString", new Class<?>[]{com.google.gson.JsonElement.class}, object.get("empty")));
        assertEquals("   ", invoke(manager, "readNullableString", new Class<?>[]{com.google.gson.JsonElement.class}, object.get("spaces")));
    }

    private static PerfilCacheEntity filledCache() {
        PerfilCacheEntity cache = new PerfilCacheEntity();
        cache.accountKey = "acc";
        cache.nombreUsuario = "user";
        cache.nombreReal = "Nombre";
        cache.email = "user@example.com";
        cache.fechaNacimiento = "1990-01-01";
        cache.genero = "masculino";
        cache.altura = 180;
        cache.peso = 76.5;
        cache.provincia = "Madrid";
        cache.fotoPerfil = "https://res.cloudinary.com/demo/avatar.jpg";
        cache.fotoVersion = 9;
        cache.localPhotoPath = "/tmp/current.jpg";
        cache.pendingLocalPhotoPath = "/tmp/pending.jpg";
        cache.photoSyncState = PhotoSyncHelper.STATE_PENDING;
        cache.photoLastError = "pendiente";
        cache.perfilVisible = false;
        cache.totalPuntos = 123;
        cache.totalCalorias = 456L;
        cache.objetivoSemanalMetros = 111L;
        cache.objetivoMensualMetros = 222L;
        cache.dirty = true;
        cache.lastFetchedAtMs = 333L;
        cache.lastSyncedAtMs = 444L;
        return cache;
    }

    @SuppressWarnings("unchecked")
    private static <T> T allocate(Class<T> type) throws Exception {
        Field field = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
        field.setAccessible(true);
        Object unsafe = field.get(null);
        Method method = unsafe.getClass().getMethod("allocateInstance", Class.class);
        return (T) method.invoke(unsafe, type);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
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
