package com.proyecto.moveon.ui.common;

import static org.junit.Assert.*;

import org.junit.Test;
import com.proyecto.moveon.core.api.ApiError;
import com.proyecto.moveon.core.api.ApiErrorType;
/**
 * Pruebas para validar el comportamiento de event.
 */
public class EventTest {

    /**
     * Verifica el escenario cubierto por {@link #getContentIfNotHandled_returnsContentFirstTime()}.
     */
    @Test
    public void getContentIfNotHandled_returnsContentFirstTime() {
        Event<String> event = new Event<>("session_expired");
        assertEquals("session_expired", event.getContentIfNotHandled());
    }

    /**
     * Verifica el escenario cubierto por {@link #getContentIfNotHandled_returnsNullSecondTime()}.
     */
    @Test
    public void getContentIfNotHandled_returnsNullSecondTime() {
        Event<String> event = new Event<>("session_expired");

        event.getContentIfNotHandled(); // primera vez
        assertNull(event.getContentIfNotHandled()); // segunda vez
    }

    /**
     * Verifica el escenario cubierto por {@link #peekContent_alwaysReturnsContent()}.
     */
    @Test
    public void peekContent_alwaysReturnsContent() {
        Event<String> event = new Event<>("data");

        assertEquals("data", event.peekContent());
        assertEquals("data", event.peekContent());
    }

    /**
     * Verifica el escenario cubierto por {@link #peekContent_worksAfterHandled()}.
     */
    @Test
    public void peekContent_worksAfterHandled() {
        Event<Integer> event = new Event<>(42);

        event.getContentIfNotHandled(); // marca como handled
        assertEquals(Integer.valueOf(42), event.peekContent()); // sigue accesible
    }

    /**
     * Verifica el escenario cubierto por {@link #nullContent_isSupported()}.
     */
    @Test
    public void nullContent_isSupported() {
        Event<String> event = new Event<>(null);

        assertNull(event.getContentIfNotHandled());
        assertNull(event.peekContent());
    }

    /**
     * Verifica el escenario cubierto por {@link #getContentIfNotHandled_thirdCallStillNull()}.
     */
    @Test
    public void getContentIfNotHandled_thirdCallStillNull() {
        Event<String> event = new Event<>("once");

        assertNotNull(event.getContentIfNotHandled());
        assertNull(event.getContentIfNotHandled());
        assertNull(event.getContentIfNotHandled());
    }
    /**
     * Verifica que Event entrega su contenido una única vez y permite consultar el valor sin consumirlo.
     */
    @Test
    public void event_contentIsConsumedOnlyOnceAndPeekDoesNotConsume() {
        Event<String> event = new Event<>("navegar");

        assertEquals("navegar", event.peekContent());
        assertEquals("navegar", event.getContentIfNotHandled());
        assertEquals("navegar", event.peekContent());
        assertNull(event.getContentIfNotHandled());
    }

    /**
     * Verifica que Event también marca como gestionado un contenido nulo.
     */
    @Test
    public void event_nullContentIsStillHandledOnlyOnce() {
        Event<String> event = new Event<>(null);

        assertNull(event.peekContent());
        assertNull(event.getContentIfNotHandled());
        assertNull(event.getContentIfNotHandled());
    }
}
