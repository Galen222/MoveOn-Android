package com.proyecto.moveon.ui.common;

import static org.junit.Assert.*;

import org.junit.Test;
/**
 * Pruebas para validar el comportamiento de event.
 */
public class EventTest {

    @Test
    public void getContentIfNotHandled_returnsContentFirstTime() {
        Event<String> event = new Event<>("session_expired");
        assertEquals("session_expired", event.getContentIfNotHandled());
    }

    @Test
    public void getContentIfNotHandled_returnsNullSecondTime() {
        Event<String> event = new Event<>("session_expired");

        event.getContentIfNotHandled(); // primera vez
        assertNull(event.getContentIfNotHandled()); // segunda vez
    }

    @Test
    public void peekContent_alwaysReturnsContent() {
        Event<String> event = new Event<>("data");

        assertEquals("data", event.peekContent());
        assertEquals("data", event.peekContent());
    }

    @Test
    public void peekContent_worksAfterHandled() {
        Event<Integer> event = new Event<>(42);

        event.getContentIfNotHandled(); // marca como handled
        assertEquals(Integer.valueOf(42), event.peekContent()); // sigue accesible
    }

    @Test
    public void nullContent_isSupported() {
        Event<String> event = new Event<>(null);

        assertNull(event.getContentIfNotHandled());
        assertNull(event.peekContent());
    }

    @Test
    public void getContentIfNotHandled_thirdCallStillNull() {
        Event<String> event = new Event<>("once");

        assertNotNull(event.getContentIfNotHandled());
        assertNull(event.getContentIfNotHandled());
        assertNull(event.getContentIfNotHandled());
    }
}
