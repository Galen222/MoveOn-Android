package com.proyecto.moveon.data.local.entity;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Tests de entidades Room como contenedores de datos locales.
 */
public class LocalEntitiesTest {

    /**
     * Verifica que {@link ActividadEntity} conserva las métricas de tracking asignadas.
     */
    @Test
    public void actividadEntity_preservesAssignedFields() {
        ActividadEntity entity = new ActividadEntity();
        entity.localId = "local";
        entity.accountKey = "account";
        entity.remoteId = 7;
        entity.tipo = "carrera";
        entity.distancia = 1_000;
        entity.duracionTotal = 600;
        entity.duracionMovimiento = 500;
        entity.duracionParado = 80;
        entity.duracionPausaManual = 20;
        entity.caloriasQuemadas = 70;
        entity.pasos = 1_234;
        entity.ritmoMedioMovimiento = 360;
        entity.ritmoMedioTotal = 400;
        entity.ritmoMaximo = 300;
        entity.velocidadMediaKmhX100 = 1_000;
        entity.velocidadMaxKmhX100 = 1_400;
        entity.autoPausas = 1;
        entity.pausasManuales = 2;
        entity.alertasVelocidad = 3;
        entity.rutaPolilinea = "poly";
        entity.rutaMapaUrl = "map.png";
        entity.fechaRuta = "2026-04-25T10:00:00Z";
        entity.syncState = "SYNCED";
        entity.lastError = "none";
        entity.createdAtMs = 10L;
        entity.updatedAtMs = 20L;

        assertEquals("local", entity.localId);
        assertEquals("account", entity.accountKey);
        assertEquals(Integer.valueOf(7), entity.remoteId);
        assertEquals(1_000, entity.distancia);
        assertEquals(Integer.valueOf(1_234), entity.pasos);
        assertEquals(20L, entity.updatedAtMs);
    }

    /**
     * Verifica que {@link PerfilCacheEntity} conserva datos cacheados y flags de sincronización.
     */
    @Test
    public void perfilCacheEntity_preservesAssignedFields() {
        PerfilCacheEntity entity = new PerfilCacheEntity();
        entity.accountKey = "account";
        entity.nombreUsuario = "alice";
        entity.nombreReal = "Alice";
        entity.email = "alice@example.com";
        entity.fechaNacimiento = "2000-01-01";
        entity.genero = "female";
        entity.altura = 170;
        entity.peso = 62.5;
        entity.provincia = "Madrid";
        entity.fotoPerfil = "photo.png";
        entity.fotoVersion = 2;
        entity.localPhotoPath = "/photo.png";
        entity.pendingLocalPhotoPath = "/pending.png";
        entity.photoSyncState = "PENDING";
        entity.perfilVisible = true;
        entity.totalPuntos = 123;
        entity.totalCalorias = 456L;
        entity.objetivoSemanalMetros = 50_000L;
        entity.objetivoMensualMetros = 150_000L;
        entity.dirty = true;
        entity.lastFetchedAtMs = 1L;
        entity.lastSyncedAtMs = 2L;

        assertEquals("account", entity.accountKey);
        assertEquals("alice", entity.nombreUsuario);
        assertEquals("photo.png", entity.fotoPerfil);
        assertEquals(2, entity.fotoVersion);
        assertTrue(entity.perfilVisible);
        assertTrue(entity.dirty);
    }

    /**
     * Verifica que {@link PerfilPendingPatchEntity} conserva payload, intentos y estado.
     */
    @Test
    public void perfilPendingPatchEntity_preservesAssignedFields() {
        PerfilPendingPatchEntity entity = new PerfilPendingPatchEntity();
        entity.operationId = "op";
        entity.accountKey = "account";
        entity.payloadJson = "{\"email\":\"a@b.com\"}";
        entity.createdAtMs = 123L;
        entity.attempts = 2;
        entity.lastError = "timeout";
        entity.state = "PENDING";

        assertEquals("op", entity.operationId);
        assertEquals("account", entity.accountKey);
        assertEquals("{\"email\":\"a@b.com\"}", entity.payloadJson);
        assertEquals(2, entity.attempts);
        assertEquals("timeout", entity.lastError);
        assertEquals("PENDING", entity.state);
    }

    /**
     * Verifica que {@link UserPrefsEntity} conserva objetivos y timestamp.
     */
    @Test
    public void userPrefsEntity_preservesAssignedFields() {
        UserPrefsEntity entity = new UserPrefsEntity();
        entity.accountKey = "account";
        entity.weeklyGoalMeters = 50_000L;
        entity.monthlyGoalMeters = 150_000L;
        entity.updatedAtMs = 999L;

        assertEquals("account", entity.accountKey);
        assertEquals(50_000L, entity.weeklyGoalMeters);
        assertEquals(150_000L, entity.monthlyGoalMeters);
        assertEquals(999L, entity.updatedAtMs);
    }
}
