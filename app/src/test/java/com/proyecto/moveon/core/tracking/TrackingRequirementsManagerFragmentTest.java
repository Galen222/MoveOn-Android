package com.proyecto.moveon.core.tracking;

import static org.junit.Assert.*;

import android.Manifest;
import android.content.Context;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.proyecto.moveon.core.settings.AppSettingsManager;
import com.proyecto.moveon.testutil.MemoryContext;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

import java.util.Arrays;
import java.util.List;

/**
 * Tests de matriz de permisos para {@link TrackingRequirementsManager}.
 *
 * <p>Los tests existentes ya cubren banderas simples; estos entran en las
 * ramas de {@link Fragment}: estados bloqueados, permisos requestables y
 * arrays finales que consume la UI.</p>
 */
@RunWith(RobolectricTestRunner.class)
public class TrackingRequirementsManagerFragmentTest {

    /**
     * Verifica que los permisos de ubicación concedidos por separado bastan
     * para satisfacer el requisito de ubicación.
     */
    @Test
    public void hasLocationPermission_acceptsFineOrCoarsePermission() {
        MemoryContext fineContext = new MemoryContext()
                .grantPermission(Manifest.permission.ACCESS_FINE_LOCATION);
        MemoryContext coarseContext = new MemoryContext()
                .grantPermission(Manifest.permission.ACCESS_COARSE_LOCATION);

        assertTrue(TrackingRequirementsManager.hasLocationPermission(fineContext));
        assertTrue(TrackingRequirementsManager.hasLocationPermission(coarseContext));
    }

    /**
     * Verifica que el permiso de actividad física concedido satisface el
     * requisito en SDKs donde dicho permiso aplica.
     */
    @Test
    public void hasActivityRecognitionPermission_returnsTrueWhenPermissionGranted() {
        MemoryContext context = new MemoryContext()
                .grantPermission(Manifest.permission.ACTIVITY_RECOGNITION);

        assertTrue(TrackingRequirementsManager.hasActivityRecognitionPermission(context));
    }

    /**
     * En un fragment fresco, sin flags de bloqueo previos, ubicación, actividad
     * y notificaciones deben ser requisitos solicitables.
     */
    @Test
    public void getRequestableMissingRequirements_freshFragment_returnsRuntimeRequirements() {
        try (ActivityController<FragmentActivity> controller =
                     Robolectric.buildActivity(FragmentActivity.class).setup()) {
            Fragment fragment = attachFragment(controller.get());

            List<TrackingRequirementsManager.Requirement> requirements =
                    TrackingRequirementsManager.getRequestableMissingRequirements(fragment);

            assertTrue(requirements.contains(TrackingRequirementsManager.Requirement.LOCATION));
            assertTrue(requirements.contains(TrackingRequirementsManager.Requirement.ACTIVITY_RECOGNITION));
            assertTrue(requirements.contains(TrackingRequirementsManager.Requirement.NOTIFICATIONS));

        }
    }

    /**
     * Verifica que el array agregado contiene los permisos Android concretos
     * de todos los requisitos runtime pendientes y solicitables.
     */
    @Test
    public void buildRequestablePermissions_freshFragment_containsConcreteAndroidPermissions() {
        try (ActivityController<FragmentActivity> controller =
                     Robolectric.buildActivity(FragmentActivity.class).setup()) {
            Fragment fragment = attachFragment(controller.get());

            List<String> permissions = Arrays.asList(
                    TrackingRequirementsManager.buildRequestablePermissions(fragment));

            assertTrue(permissions.contains(Manifest.permission.ACCESS_FINE_LOCATION));
            assertTrue(permissions.contains(Manifest.permission.ACCESS_COARSE_LOCATION));
            assertTrue(permissions.contains(Manifest.permission.ACTIVITY_RECOGNITION));
            assertTrue(permissions.contains(Manifest.permission.POST_NOTIFICATIONS));

        }
    }

    /**
     * Verifica los arrays individuales usados cuando la UI pide resolver un
     * requisito concreto desde el bottom sheet.
     */
    @Test
    public void buildRequestablePermissionsForRequirement_freshFragment_returnsExpectedArrays() {
        try (ActivityController<FragmentActivity> controller =
                     Robolectric.buildActivity(FragmentActivity.class).setup()) {
            Fragment fragment = attachFragment(controller.get());

            assertArrayEquals(new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    TrackingRequirementsManager.buildRequestablePermissionsForRequirement(
                            fragment, TrackingRequirementsManager.Requirement.LOCATION));
            assertArrayEquals(new String[]{Manifest.permission.ACTIVITY_RECOGNITION},
                    TrackingRequirementsManager.buildRequestablePermissionsForRequirement(
                            fragment, TrackingRequirementsManager.Requirement.ACTIVITY_RECOGNITION));
            assertArrayEquals(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    TrackingRequirementsManager.buildRequestablePermissionsForRequirement(
                            fragment, TrackingRequirementsManager.Requirement.NOTIFICATIONS));
            assertArrayEquals(new String[0],
                    TrackingRequirementsManager.buildRequestablePermissionsForRequirement(
                            fragment, TrackingRequirementsManager.Requirement.GPS));

        }
    }

    /**
     * Si la app ya pidió permisos y Android no muestra rationale, los tres
     * requisitos runtime quedan bloqueados y desaparecen de los requestables.
     */
    @Test
    public void requestedPermissionsWithoutRationale_areReportedAsBlocked() {
        try (ActivityController<FragmentActivity> controller =
                     Robolectric.buildActivity(FragmentActivity.class).setup()) {
            Fragment fragment = attachFragment(controller.get());
            Context context = fragment.requireContext();
            TrackingRequirementsManager.markPermissionsRequested(context, new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACTIVITY_RECOGNITION,
                    Manifest.permission.POST_NOTIFICATIONS
            });

            assertEquals(TrackingRequirementsManager.Status.BLOCKED,
                    TrackingRequirementsManager.getLocationStatus(fragment));
            assertEquals(TrackingRequirementsManager.Status.BLOCKED,
                    TrackingRequirementsManager.getActivityRecognitionStatus(fragment));
            assertEquals(TrackingRequirementsManager.Status.BLOCKED,
                    TrackingRequirementsManager.getNotificationsStatus(fragment));

            List<TrackingRequirementsManager.Requirement> blocked =
                    TrackingRequirementsManager.getBlockedRuntimeRequirements(fragment);
            assertTrue(blocked.contains(TrackingRequirementsManager.Requirement.LOCATION));
            assertTrue(blocked.contains(TrackingRequirementsManager.Requirement.ACTIVITY_RECOGNITION));
            assertTrue(blocked.contains(TrackingRequirementsManager.Requirement.NOTIFICATIONS));

            assertFalse(TrackingRequirementsManager.getRequestableMissingRequirements(fragment)
                    .contains(TrackingRequirementsManager.Requirement.LOCATION));
            assertArrayEquals(new String[0],
                    TrackingRequirementsManager.buildRequestablePermissionsForRequirement(
                            fragment, TrackingRequirementsManager.Requirement.LOCATION));
            assertArrayEquals(new String[0],
                    TrackingRequirementsManager.buildRequestablePermissionsForRequirement(
                            fragment, TrackingRequirementsManager.Requirement.ACTIVITY_RECOGNITION));
            assertArrayEquals(new String[0],
                    TrackingRequirementsManager.buildRequestablePermissionsForRequirement(
                            fragment, TrackingRequirementsManager.Requirement.NOTIFICATIONS));

        }
    }

    private static Fragment attachFragment(FragmentActivity activity) {
        AppSettingsManager.clearTrackingPermissionRequestFlags(activity);
        Fragment fragment = new Fragment();
        activity.getSupportFragmentManager()
                .beginTransaction()
                .add(fragment, "tracking-test")
                .commitNow();
        return fragment;
    }
}
