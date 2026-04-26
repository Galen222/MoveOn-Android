package com.proyecto.moveon.core.theme;

import static org.junit.Assert.*;

import androidx.appcompat.app.AppCompatDelegate;

import com.proyecto.moveon.testutil.MemoryContext;

import org.junit.Test;

/**
 * Tests de persistencia y aplicación del modo de tema de la app.
 */
public class ThemeManagerTest {

    /**
     * Verifica que el modo guardado parte de sistema cuando no existe preferencia explícita.
     */
    @Test
    public void getSavedMode_defaultsToSystemMode() {
        MemoryContext context = new MemoryContext();

        assertEquals(ThemeManager.MODE_SYSTEM, ThemeManager.getSavedMode(context));
    }

    /**
     * Verifica que guardar el modo oscuro persiste la preferencia y aplica night mode YES.
     */
    @Test
    public void saveAndApply_darkPersistsAndAppliesNightModeYes() {
        MemoryContext context = new MemoryContext();

        ThemeManager.saveAndApply(context, ThemeManager.MODE_DARK);

        assertEquals(ThemeManager.MODE_DARK, ThemeManager.getSavedMode(context));
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, AppCompatDelegate.getDefaultNightMode());
    }

    /**
     * Verifica que guardar el modo claro persiste la preferencia y aplica night mode NO.
     */
    @Test
    public void saveAndApply_lightPersistsAndAppliesNightModeNo() {
        MemoryContext context = new MemoryContext();

        ThemeManager.saveAndApply(context, ThemeManager.MODE_LIGHT);

        assertEquals(ThemeManager.MODE_LIGHT, ThemeManager.getSavedMode(context));
        assertEquals(AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.getDefaultNightMode());
    }

    /**
     * Verifica que cualquier modo desconocido se normaliza a sistema al guardar y aplicar.
     */
    @Test
    public void saveAndApply_unknownModePersistsSystemAndAppliesFollowSystem() {
        MemoryContext context = new MemoryContext();

        ThemeManager.saveAndApply(context, "solarized");

        assertEquals(ThemeManager.MODE_SYSTEM, ThemeManager.getSavedMode(context));
        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, AppCompatDelegate.getDefaultNightMode());
    }

    /**
     * Verifica que applyMode normaliza entradas inválidas sin tocar preferencias.
     */
    @Test
    public void applyMode_invalidValueFallsBackToSystemWithoutPersisting() {
        MemoryContext context = new MemoryContext();

        ThemeManager.applyMode("invalid");

        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, AppCompatDelegate.getDefaultNightMode());
        assertEquals(ThemeManager.MODE_SYSTEM, ThemeManager.getSavedMode(context));
    }
}
