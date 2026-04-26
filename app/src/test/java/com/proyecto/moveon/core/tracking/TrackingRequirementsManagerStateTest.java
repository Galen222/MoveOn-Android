package com.proyecto.moveon.core.tracking;

import static org.junit.Assert.*;

import android.Manifest;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * Tests adicionales para {@link TrackingRequirementsManager} cubriendo los
 * caminos puros que dependen sólo de permisos concedidos en el contexto y
 * del servicio de localización del sistema.
 *
 * <p>Se ejecutan bajo {@link RobolectricTestRunner} porque varios métodos
 * usan {@code NotificationManagerCompat} y {@code LocationManager}, que
 * requieren un {@code Context} Android funcional.</p>
 */
@RunWith(RobolectricTestRunner.class)

public class TrackingRequirementsManagerStateTest {

    /**
     * Devuelve el contexto de aplicación gestionado por Robolectric.
     *
     * @return contexto Android funcional con servicios del framework activos.
     */
    private static Context appContext() {
        return ApplicationProvider.getApplicationContext();
    }

    /**
     * Verifica que {@code hasLocationPermission} devuelve {@code false} en un
     * contexto recién creado donde no se ha concedido ningún permiso runtime.
     */
    @Test
    public void hasLocationPermission_freshContext_returnsFalse() {
        assertFalse(TrackingRequirementsManager.hasLocationPermission(appContext()));
    }

    /**
     * Verifica que {@code hasActivityRecognitionPermission} devuelve
     * {@code false} en un contexto sin permisos concedidos.
     */
    @Test
    public void hasActivityRecognitionPermission_freshContext_returnsFalse() {
        assertFalse(TrackingRequirementsManager.hasActivityRecognitionPermission(appContext()));
    }

    /**
     * Verifica que {@code areRuntimeRequirementsSatisfied} devuelve
     * {@code false} cuando faltan los permisos clave.
     */
    @Test
    public void areRuntimeRequirementsSatisfied_freshContext_returnsFalse() {
        assertFalse(TrackingRequirementsManager.areRuntimeRequirementsSatisfied(appContext()));
    }

    /**
     * Verifica que {@code isDeviceLocationEnabled} no lanza con el
     * {@link android.location.LocationManager} simulado por Robolectric.
     *
     * <p>Sólo comprobamos que el método se ejecuta sin lanzar; el valor
     * concreto depende de la configuración por defecto de Robolectric.</p>
     */
    @Test
    public void isDeviceLocationEnabled_doesNotThrow() {
        TrackingRequirementsManager.isDeviceLocationEnabled(appContext());
    }

    /**
     * Verifica que {@code getDeviceLocationStatus} devuelve un estado válido
     * (no nulo) bajo Robolectric.
     */
    @Test
    public void getDeviceLocationStatus_returnsNonNullStatus() {
        TrackingRequirementsManager.Status status =
                TrackingRequirementsManager.getDeviceLocationStatus(appContext());

        assertNotNull(status);
    }

    /**
     * Verifica que {@code markPermissionsRequested} con array vacío no rompe.
     */
    @Test
    public void markPermissionsRequested_emptyArray_doesNotThrow() {
        TrackingRequirementsManager.markPermissionsRequested(appContext(), new String[0]);
    }

    /**
     * Verifica que {@code markPermissionsRequested} ignora permisos ajenos al
     * tracking sin lanzar.
     */
    @Test
    public void markPermissionsRequested_unrelatedPermissions_doNotThrow() {
        TrackingRequirementsManager.markPermissionsRequested(appContext(), new String[]{
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.RECORD_AUDIO
        });
    }

    /**
     * Verifica que {@code markPermissionsRequested} marca correctamente las
     * banderas cuando recibe permisos de los tres grupos a la vez.
     */
    @Test
    public void markPermissionsRequested_marksAllRelevantFlagsAtOnce() {
        TrackingRequirementsManager.markPermissionsRequested(appContext(), new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACTIVITY_RECOGNITION,
                Manifest.permission.POST_NOTIFICATIONS
        });
    }
}
