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
        @Override
        public void onReceive(Context context, Intent intent) {
            updateTrackingRequirementsUi();
        }
    };
    private boolean deviceLocationReceiverRegistered = false;

    public ProfileTrackingHelper(@NonNull Fragment fragment,
                                 @NonNull FragmentProfileBinding binding,
                                 @NonNull ActivityResultLauncher<String[]> permissionLauncher) {
        this.fragment = fragment;
        this.binding = binding;
        this.permissionLauncher = permissionLauncher;
    }

    public void updateTrackingRequirementsUi() {
        if (binding == null || !fragment.isAdded()) return;

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

    public void unregisterDeviceLocationReceiver() {
        if (!deviceLocationReceiverRegistered || !fragment.isAdded()) return;
        fragment.requireContext().unregisterReceiver(deviceLocationStateReceiver);
        deviceLocationReceiverRegistered = false;
    }

    private void openAppSettings() {

        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", fragment.requireContext().getPackageName(), null));
        fragment.startActivity(intent);
    }

    private void openNotificationSettings() {
        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, fragment.requireContext().getPackageName());
        fragment.startActivity(intent);
    }

    private void openLocationSettings() {
        fragment.startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
    }

}
