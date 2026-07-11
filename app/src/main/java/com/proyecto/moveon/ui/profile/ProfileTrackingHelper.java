package com.proyecto.moveon.ui.profile;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.proyecto.moveon.R;
import com.proyecto.moveon.core.tracking.TrackingRequirementsManager;
import com.proyecto.moveon.databinding.FragmentProfileBinding;

/**
 * Utilidades para evaluar y solicitar los requisitos de tracking desde la UI de perfil.
 */
public final class ProfileTrackingHelper {

    private final Fragment fragment;
    private final FragmentProfileBinding binding;
    private final ActivityResultLauncher<String[]> permissionLauncher;

    private final BroadcastReceiver deviceLocationStateReceiver = new BroadcastReceiver() {
        /**
         * Reevalúa la UI cuando Android notifica un cambio en el estado global de localización del dispositivo.
         *
         * @param context contexto desde el que se emite el broadcast.
         * @param intent intent del broadcast recibido.
         */
        @Override
        public void onReceive(Context context, Intent intent) {
            updateTrackingRequirementsUi();
        }
    };
    private boolean deviceLocationReceiverRegistered = false;

    /**
     * Crea el helper que sincroniza la UI de perfil con el estado real de los permisos de tracking.
     *
     * @param fragment fragment propietario desde el que se consultan contexto y lifecycle.
     * @param binding binding de la pantalla de perfil que se va a actualizar.
     * @param permissionLauncher launcher usado para pedir permisos en bloque.
     */
    public ProfileTrackingHelper(@NonNull Fragment fragment,
                                 @NonNull FragmentProfileBinding binding,
                                 @NonNull ActivityResultLauncher<String[]> permissionLauncher) {
        this.fragment = fragment;
        this.binding = binding;
        this.permissionLauncher = permissionLauncher;
    }

    /**
     * Recalcula y pinta el estado visible de todos los requisitos necesarios para el tracking.
     */
    public void updateTrackingRequirementsUi() {
        if (!fragment.isAdded()) return;

        bindTrackingRequirementRow(
                binding.tvTrackingLocationStatus,
                binding.tvTrackingLocationAction,
                TrackingRequirementsManager.Requirement.LOCATION,
                TrackingRequirementsManager.getLocationStatus(fragment)
        );

        bindTrackingRequirementRow(
                binding.tvTrackingActivityStatus,
                binding.tvTrackingActivityAction,
                TrackingRequirementsManager.Requirement.ACTIVITY_RECOGNITION,
                TrackingRequirementsManager.getActivityRecognitionStatus(fragment)
        );

        bindTrackingRequirementRow(
                binding.tvTrackingNotificationsStatus,
                binding.tvTrackingNotificationsAction,
                TrackingRequirementsManager.Requirement.NOTIFICATIONS,
                TrackingRequirementsManager.getNotificationsStatus(fragment)
        );

        bindTrackingRequirementRow(
                binding.tvTrackingDeviceLocationStatus,
                binding.tvTrackingDeviceLocationAction,
                TrackingRequirementsManager.Requirement.GPS,
                TrackingRequirementsManager.getDeviceLocationStatus(fragment.requireContext())
        );
    }

    /**
     * Actualiza una fila concreta del bloque de requisitos con su estado y acción disponible.
     *
     * @param statusView vista donde se muestra el estado textual.
     * @param actionView vista de acción asociada al requisito.
     * @param requirement requisito representado por la fila.
     * @param status estado actual calculado para ese requisito.
     */
    private void bindTrackingRequirementRow(@NonNull android.widget.TextView statusView,
                                            @NonNull android.widget.TextView actionView,
                                            @NonNull TrackingRequirementsManager.Requirement requirement,
                                            @NonNull TrackingRequirementsManager.Status status) {
        statusView.setText(getTrackingRequirementStatusText(requirement, status));

        Integer actionTextRes = getTrackingRequirementActionTextRes(requirement, status);
        if (actionTextRes == null) {
            actionView.setVisibility(View.GONE);
            return;
        }

        actionView.setVisibility(View.VISIBLE);
        actionView.setText(actionTextRes);
    }

    /**
     * Resuelve el texto de acción apropiado para un requisito según su estado actual.
     *
     * @param requirement requisito que se está evaluando.
     * @param status estado resultante del requisito.
     * @return recurso de texto para el CTA, o {@code null} si no debe mostrarse acción.
     */
    @Nullable
    private Integer getTrackingRequirementActionTextRes(
            @NonNull TrackingRequirementsManager.Requirement requirement,
            @NonNull TrackingRequirementsManager.Status status) {
        switch (status) {
            case ENABLED:
                return null;
            case NEEDS_ACTIVATION:
                return requirement == TrackingRequirementsManager.Requirement.GPS
                        ? R.string.profile_tracking_status_activate
                        : R.string.profile_tracking_status_request;
            case BLOCKED:
            default:
                return R.string.profile_tracking_status_open_settings;
        }
    }

    /**
     * Devuelve el texto visible que resume el estado actual de un requisito de tracking.
     *
     * @param requirement requisito que se está representando.
     * @param status estado calculado para ese requisito.
     * @return cadena localizada adecuada para la fila.
     */
    @NonNull
    private String getTrackingRequirementStatusText(
            @NonNull TrackingRequirementsManager.Requirement requirement,
            @NonNull TrackingRequirementsManager.Status status) {
        switch (status) {
            case ENABLED:
                return fragment.getString(R.string.profile_tracking_status_enabled);
            case NEEDS_ACTIVATION:
                return requirement == TrackingRequirementsManager.Requirement.GPS
                        ? fragment.getString(R.string.profile_tracking_status_disabled)
                        : fragment.getString(R.string.profile_tracking_status_needs_activation);
            case BLOCKED:
            default:
                return fragment.getString(R.string.profile_tracking_status_blocked);
        }
    }

    /**
     * Ejecuta la acción adecuada para desbloquear o activar un requisito concreto.
     *
     * @param requirement requisito cuya acción ha pulsado el usuario.
     */
    public void handleTrackingRequirementAction(
            @NonNull TrackingRequirementsManager.Requirement requirement) {
        TrackingRequirementsManager.Status status = getTrackingRequirementStatus(requirement);
        if (status == TrackingRequirementsManager.Status.ENABLED) {
            return;
        }

        if (status == TrackingRequirementsManager.Status.BLOCKED) {
            openSettingsForRequirement(requirement);
            return;
        }

        if (requirement == TrackingRequirementsManager.Requirement.GPS) {
            openLocationSettings();
            return;
        }

        String[] permissions = TrackingRequirementsManager
                .buildRequestablePermissionsForRequirement(fragment, requirement);
        if (permissions.length == 0) {
            updateTrackingRequirementsUi();
            return;
        }

        TrackingRequirementsManager.markPermissionsRequested(fragment.requireContext(), permissions);
        permissionLauncher.launch(permissions);
    }

    /**
     * Consulta el estado actual de un requisito usando {@link TrackingRequirementsManager}.
     *
     * @param requirement requisito cuyo estado se desea obtener.
     * @return estado actual del requisito.
     */
    @NonNull
    private TrackingRequirementsManager.Status getTrackingRequirementStatus(
            @NonNull TrackingRequirementsManager.Requirement requirement) {
        switch (requirement) {
            case LOCATION:
                return TrackingRequirementsManager.getLocationStatus(fragment);
            case ACTIVITY_RECOGNITION:
                return TrackingRequirementsManager.getActivityRecognitionStatus(fragment);
            case NOTIFICATIONS:
                return TrackingRequirementsManager.getNotificationsStatus(fragment);
            case GPS:
            default:
                return TrackingRequirementsManager.getDeviceLocationStatus(fragment.requireContext());
        }
    }

    /**
     * Abre la pantalla de ajustes más adecuada para resolver un requisito bloqueado.
     *
     * @param requirement requisito que debe resolverse desde ajustes del sistema o de la app.
     */
    private void openSettingsForRequirement(
            @NonNull TrackingRequirementsManager.Requirement requirement) {
        if (requirement == TrackingRequirementsManager.Requirement.NOTIFICATIONS) {
            openNotificationSettings();
        } else if (requirement == TrackingRequirementsManager.Requirement.GPS) {
            openLocationSettings();
        } else {
            openAppSettings();
        }
    }

    /**
     * Registra un receiver temporal para refrescar la UI cuando el usuario activa o desactiva la ubicación del dispositivo.
     */
    public void registerDeviceLocationReceiver() {
        if (deviceLocationReceiverRegistered || !fragment.isAdded()) return;

        IntentFilter filter = new IntentFilter(LocationManager.MODE_CHANGED_ACTION);
        filter.addAction(LocationManager.PROVIDERS_CHANGED_ACTION);

        Context context = fragment.requireContext();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(deviceLocationStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            context.registerReceiver(deviceLocationStateReceiver, filter);
        }
        deviceLocationReceiverRegistered = true;
    }

    /**
     * Desregistra el receiver del estado de ubicación si seguía activo.
     */
    public void unregisterDeviceLocationReceiver() {
        if (!deviceLocationReceiverRegistered || !fragment.isAdded()) return;
        fragment.requireContext().unregisterReceiver(deviceLocationStateReceiver);
        deviceLocationReceiverRegistered = false;
    }

    /**
     * Abre los ajustes de la aplicación para que el usuario revise permisos denegados manualmente.
     */
    private void openAppSettings() {

        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", fragment.requireContext().getPackageName(), null));
        fragment.startActivity(intent);
    }

    /**
     * Abre la pantalla del sistema para gestionar las notificaciones de la app.
     */
    private void openNotificationSettings() {
        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, fragment.requireContext().getPackageName());
        fragment.startActivity(intent);
    }

    /**
     * Abre los ajustes globales de ubicación del dispositivo.
     */
    private void openLocationSettings() {
        fragment.startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
    }

}
