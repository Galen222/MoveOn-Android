package com.proyecto.moveon.ui.profile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

/**
 * Tests JVM seguros para {@link ShareRouteFormatter}.
 *
 * <p>Estos tests evitan dependencias de {@code android.test.mock.MockContext}, Robolectric o
 * instrumentación Android. Por eso se centran en ramas puras del formateador y en validar que
 * las ramas que requieren recursos Android fallan rápido cuando no se les pasa contexto.</p>
 */
public class ShareRouteFormatterTest {

    /**
     * Verifica que el constructor privado mantiene la clase como utilidad estática.
     *
     * @throws Exception si la reflexión no puede acceder al constructor privado.
     */
    @Test
    public void constructor_isPrivateUtilityConstructor() throws Exception {
        Constructor<ShareRouteFormatter> constructor = ShareRouteFormatter.class.getDeclaredConstructor();

        assertTrue(Modifier.isPrivate(constructor.getModifiers()));
        constructor.setAccessible(true);
        assertNotNull(constructor.newInstance());
    }

    /**
     * Verifica que un ritmo válido se formatea en patrón minutos-segundos sin consultar recursos Android.
     */
    @Test
    public void formatPace_validValueUsesMinuteSecondPatternWithoutContextLookup() {
        assertEquals("4'05\"", ShareRouteFormatter.formatPace(null, 245));
        assertEquals("1'05\"", ShareRouteFormatter.formatPace(null, 65));
        assertEquals("0'59\"", ShareRouteFormatter.formatPace(null, 59));
    }

    /**
     * Verifica que la variante opcional reutiliza el formateo estándar para ritmos válidos.
     */
    @Test
    public void formatOptionalPace_validValueDelegatesToFormatPace() {
        assertEquals("3'30\"", ShareRouteFormatter.formatOptionalPace(null, 210));
        assertEquals("10'00\"", ShareRouteFormatter.formatOptionalPace(null, 600));
    }

    /**
     * Verifica que los ritmos no válidos necesitan contexto para resolver el placeholder localizado.
     */
    @Test
    public void formatPace_invalidValueRequiresContextPlaceholder() {
        assertThrows(NullPointerException.class, () -> ShareRouteFormatter.formatPace(null, 0));
        assertThrows(NullPointerException.class, () -> ShareRouteFormatter.formatPace(null, -1));
    }

    /**
     * Verifica que el texto de compartir necesita contexto Android para resolver su recurso localizado.
     */
    @Test
    public void buildShareText_nullContextFailsFast() {
        assertThrows(NullPointerException.class, () -> ShareRouteFormatter.buildShareText(null, null));
    }
}