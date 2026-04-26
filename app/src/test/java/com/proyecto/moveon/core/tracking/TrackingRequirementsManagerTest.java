package com.proyecto.moveon.core.tracking;

import static org.junit.Assert.*;

import android.Manifest;

import com.proyecto.moveon.core.settings.AppSettingsManager;
import com.proyecto.moveon.testutil.MemoryContext;


import org.junit.Test;

/**
 * Tests de ramas de tracking que sólo dependen de flags locales de permisos.
 */
public class TrackingRequirementsManagerTest {


    /**
     * Verifica que la localización del dispositivo se considera desactivada cuando no hay LocationManager.
     */
    @Test
    public void isDeviceLocationEnabled_returnsFalseWhenLocationManagerIsMissing() {
        MemoryContext context = new MemoryContext();

        assertFalse(TrackingRequirementsManager.isDeviceLocationEnabled(context));
        assertEquals(TrackingRequirementsManager.Status.NEEDS_ACTIVATION,
                TrackingRequirementsManager.getDeviceLocationStatus(context));
    }

    /**
     * Verifica que marcar permisos de ubicación activa una única bandera aunque lleguen fine y coarse juntos.
     */
    @Test
    public void markPermissionsRequested_marksLocationFlagForFineOrCoarsePermissions() {
        MemoryContext context = new MemoryContext();

        TrackingRequirementsManager.markPermissionsRequested(context, new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        });

        assertTrue(AppSettingsManager.wasTrackingLocationPermissionRequested(context));
        assertFalse(AppSettingsManager.wasTrackingActivityPermissionRequested(context));
        assertFalse(AppSettingsManager.wasTrackingNotificationsPermissionRequested(context));
    }

    /**
     * Verifica que marcar el permiso de actividad física sólo activa su bandera específica.
     */
    @Test
    public void markPermissionsRequested_marksActivityRecognitionFlagOnly() {
        MemoryContext context = new MemoryContext();

        TrackingRequirementsManager.markPermissionsRequested(context, new String[]{
                Manifest.permission.ACTIVITY_RECOGNITION
        });

        assertFalse(AppSettingsManager.wasTrackingLocationPermissionRequested(context));
        assertTrue(AppSettingsManager.wasTrackingActivityPermissionRequested(context));
        assertFalse(AppSettingsManager.wasTrackingNotificationsPermissionRequested(context));
    }

    /**
     * Verifica que marcar el permiso de notificaciones sólo activa la bandera de notificaciones.
     */
    @Test
    public void markPermissionsRequested_marksNotificationsFlagOnly() {
        MemoryContext context = new MemoryContext();

        TrackingRequirementsManager.markPermissionsRequested(context, new String[]{
                Manifest.permission.POST_NOTIFICATIONS
        });

        assertFalse(AppSettingsManager.wasTrackingLocationPermissionRequested(context));
        assertFalse(AppSettingsManager.wasTrackingActivityPermissionRequested(context));
        assertTrue(AppSettingsManager.wasTrackingNotificationsPermissionRequested(context));
    }

    /**
     * Verifica que permisos desconocidos no alteran ninguna bandera de tracking.
     */
    @Test
    public void markPermissionsRequested_ignoresUnknownPermissions() {
        MemoryContext context = new MemoryContext();

        TrackingRequirementsManager.markPermissionsRequested(context, new String[]{
                "com.proyecto.moveon.PERMISO_DESCONOCIDO"
        });

        assertFalse(AppSettingsManager.wasTrackingLocationPermissionRequested(context));
        assertFalse(AppSettingsManager.wasTrackingActivityPermissionRequested(context));
        assertFalse(AppSettingsManager.wasTrackingNotificationsPermissionRequested(context));
    }

    /**
     * Verifica que el conjunto completo de permisos activa todas las banderas persistidas.
     */
    @Test
    public void markPermissionsRequested_marksEveryRecognizedPermissionGroup() {
        MemoryContext context = new MemoryContext();

        TrackingRequirementsManager.markPermissionsRequested(context, new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACTIVITY_RECOGNITION,
                Manifest.permission.POST_NOTIFICATIONS
        });

        assertTrue(AppSettingsManager.wasTrackingLocationPermissionRequested(context));
        assertTrue(AppSettingsManager.wasTrackingActivityPermissionRequested(context));
        assertTrue(AppSettingsManager.wasTrackingNotificationsPermissionRequested(context));
    }
}
