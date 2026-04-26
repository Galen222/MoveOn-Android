package com.proyecto.moveon.ui.home.tracking;

import static org.junit.Assert.*;

import org.junit.Test;

import com.google.android.gms.maps.model.LatLng;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
/**
 * Tests del evento puntual {@link TrackingAlert}.
 */
public class TrackingAlertTest {

    /**
     * Verifica que una alerta de auto-pausa conserva su tipo.
     */
    @Test
    public void getType_returnsStationaryAutoPause() {
        TrackingAlert alert = new TrackingAlert(TrackingAlert.Type.STATIONARY_AUTO_PAUSE);

        assertEquals(TrackingAlert.Type.STATIONARY_AUTO_PAUSE, alert.getType());
    }

    /**
     * Verifica que una alerta de velocidad sospechosa conserva su tipo.
     */
    @Test
    public void getType_returnsSuspiciousSpeed() {
        TrackingAlert alert = new TrackingAlert(TrackingAlert.Type.SUSPICIOUS_SPEED);

        assertEquals(TrackingAlert.Type.SUSPICIOUS_SPEED, alert.getType());
    }
    /**
     * Verifica que TrackingAlert conserva el tipo de alerta recibido.
     */
    @Test
    public void trackingAlert_getTypeReturnsProvidedType() {
        TrackingAlert stationary = new TrackingAlert(TrackingAlert.Type.STATIONARY_AUTO_PAUSE);
        TrackingAlert suspicious = new TrackingAlert(TrackingAlert.Type.SUSPICIOUS_SPEED);

        assertEquals(TrackingAlert.Type.STATIONARY_AUTO_PAUSE, stationary.getType());
        assertEquals(TrackingAlert.Type.SUSPICIOUS_SPEED, suspicious.getType());
    }
}
