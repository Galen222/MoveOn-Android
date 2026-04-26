package com.proyecto.moveon.core.i18n;

import static org.junit.Assert.*;

import com.proyecto.moveon.R;
import com.proyecto.moveon.testutil.MemoryContext;

import org.junit.Test;

/**
 * Tests JVM de conversión entre valores canónicos de perfil y etiquetas visibles localizadas.
 *
 * <p>Evitan ramas que dependen de {@code Context#getString(...)} final para poder ejecutarse como
 * unit tests JVM sin emulador ni Robolectric.</p>
 */
public class ProfileValueLocalizerTest {

    /**
     * Verifica que el género canónico se transforma en etiqueta visible y que los desconocidos se conservan.
     */
    @Test
    public void displayGenero_mapsCanonicalValueAndKeepsUnknownValues() {
        MemoryContext context = catalogContext();

        assertEquals("Mujer visible", ProfileValueLocalizer.displayGenero(context, "Mujer"));
        assertEquals("No binario", ProfileValueLocalizer.displayGenero(context, "No binario"));
    }

    /**
     * Verifica que el género vuelve a backend value desde label visible, valor canónico o texto desconocido.
     */
    @Test
    public void canonicalGeneroFromLabel_mapsLabelsCanonicalValuesAndFallbacks() {
        MemoryContext context = catalogContext();

        assertNull(ProfileValueLocalizer.canonicalGeneroFromLabel(context, " "));
        assertEquals("Hombre", ProfileValueLocalizer.canonicalGeneroFromLabel(context, "Hombre visible"));
        assertEquals("Otro", ProfileValueLocalizer.canonicalGeneroFromLabel(context, "Otro"));
        assertEquals("No binario", ProfileValueLocalizer.canonicalGeneroFromLabel(context, "No binario"));
    }

    /**
     * Verifica que las provincias canónicas se muestran como labels y que valores desconocidos se conservan.
     */
    @Test
    public void displayProvincia_mapsCanonicalAndUnknownValues() {
        MemoryContext context = catalogContext();

        assertEquals("Madrid visible", ProfileValueLocalizer.displayProvincia(context, "Madrid"));
        assertEquals("Atlantis", ProfileValueLocalizer.displayProvincia(context, "Atlantis"));
    }

    /**
     * Verifica que la primera provincia del catálogo representa ausencia de valor y que unknown se preserva.
     */
    @Test
    public void canonicalProvinciaFromLabel_treatsFirstCatalogEntryAsNull() {
        MemoryContext context = catalogContext();

        assertNull(ProfileValueLocalizer.canonicalProvinciaFromLabel(context, "No indicar visible"));
        assertEquals("Madrid", ProfileValueLocalizer.canonicalProvinciaFromLabel(context, "Madrid visible"));
        assertEquals("Valencia", ProfileValueLocalizer.canonicalProvinciaFromLabel(context, "Valencia"));
        assertEquals("Atlantis", ProfileValueLocalizer.canonicalProvinciaFromLabel(context, "Atlantis"));
    }

    /**
     * Verifica que los tipos de actividad se muestran desde catálogo y devuelven cadena vacía si no hay texto.
     */
    @Test
    public void displayActivityType_mapsKnownTypesAndReturnsEmptyForBlank() {
        MemoryContext context = catalogContext();

        assertEquals("", ProfileValueLocalizer.displayActivityType(context, " "));
        assertEquals("Correr visible", ProfileValueLocalizer.displayActivityType(context, "Correr"));
        assertEquals("Nadar", ProfileValueLocalizer.displayActivityType(context, "Nadar"));
    }

    /**
     * Verifica que los tipos de actividad se normalizan desde labels, backend values y entradas desconocidas.
     */
    @Test
    public void canonicalActivityTypeFromLabel_mapsLabelsCanonicalValuesAndFallbacks() {
        MemoryContext context = catalogContext();

        assertNull(ProfileValueLocalizer.canonicalActivityTypeFromLabel(context, null));
        assertEquals("Caminar", ProfileValueLocalizer.canonicalActivityTypeFromLabel(context, "Caminar visible"));
        assertEquals("Correr", ProfileValueLocalizer.canonicalActivityTypeFromLabel(context, "Correr"));
        assertEquals("Nadar", ProfileValueLocalizer.canonicalActivityTypeFromLabel(context, "Nadar"));
    }

    private static MemoryContext catalogContext() {
        return new MemoryContext()
                .putString(R.string.profile_not_indicated, "No indicado")
                .putStringArray(R.array.generos_backend_values, "Hombre", "Mujer", "Otro")
                .putStringArray(R.array.generos_labels, "Hombre visible", "Mujer visible", "Otro visible")
                .putStringArray(R.array.provincias_backend_values, "No indicar", "Madrid", "Valencia")
                .putStringArray(R.array.provincias_labels, "No indicar visible", "Madrid visible", "Valencia visible")
                .putStringArray(R.array.activity_types_backend_values, "Caminar", "Correr")
                .putStringArray(R.array.activity_types_labels, "Caminar visible", "Correr visible");
    }
}
