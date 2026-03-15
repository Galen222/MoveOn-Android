package com.proyecto.moveon.core.tracking;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.proyecto.moveon.core.settings.AppSettingsManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Centraliza los requisitos necesarios para iniciar el tracking.
 *
 * Requisitos contemplados:
 * - Permiso de ubicación (fine o coarse)
 * - Permiso de reconocimiento de actividad física (API 29+)
 * - Permiso/estado de notificaciones (runtime en API 33+, ajuste de sistema en APIs anteriores)
 * - Ubicación del dispositivo encendida (GPS / servicios de localización)
 */
public final class TrackingRequirementsManager {

    public enum Requirement {
        LOCATION,
        ACTIVITY_RECOGNITION,
        NOTIFICATIONS,
        GPS
    }

    public enum Status {
        ENABLED,
        NEEDS_ACTIVATION,
        BLOCKED
    }

    private TrackingRequirementsManager() {}

    public static boolean hasLocationPermission(@NonNull Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean hasActivityRecognitionPermission(@NonNull Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                || ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean hasNotificationsRequirement(@NonNull Context context) {
        boolean enabledInSystem = NotificationManagerCompat.from(context).areNotificationsEnabled();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return enabledInSystem;
        }
        boolean permissionGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
        return permissionGranted && enabledInSystem;
    }

    public static boolean isDeviceLocationEnabled(@NonNull Context context) {
        LocationManager locationManager =
                (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) return false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return locationManager.isLocationEnabled();
        }

        try {
            return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                    || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean areRuntimeRequirementsSatisfied(@NonNull Context context) {
        return hasLocationPermission(context)
                && hasActivityRecognitionPermission(context)
                && hasNotificationsRequirement(context);
    }

    public static Status getLocationStatus(@NonNull Fragment fragment) {
        Context context = fragment.requireContext();
        if (hasLocationPermission(context)) return Status.ENABLED;
        return isLocationPermissionBlocked(fragment) ? Status.BLOCKED : Status.NEEDS_ACTIVATION;
    }

    public static Status getActivityRecognitionStatus(@NonNull Fragment fragment) {
        Context context = fragment.requireContext();
        if (hasActivityRecognitionPermission(context)) return Status.ENABLED;
        return isActivityRecognitionPermissionBlocked(fragment) ? Status.BLOCKED : Status.NEEDS_ACTIVATION;
    }

    public static Status getNotificationsStatus(@NonNull Fragment fragment) {
        Context context = fragment.requireContext();
        if (hasNotificationsRequirement(context)) return Status.ENABLED;
        return isNotificationsBlocked(fragment) ? Status.BLOCKED : Status.NEEDS_ACTIVATION;
    }

    public static Status getDeviceLocationStatus(@NonNull Context context) {
        return isDeviceLocationEnabled(context) ? Status.ENABLED : Status.NEEDS_ACTIVATION;
    }

    public static boolean isLocationPermissionBlocked(@NonNull Fragment fragment) {
        Context context = fragment.requireContext();
        if (hasLocationPermission(context)) return false;
        if (!AppSettingsManager.wasTrackingLocationPermissionRequested(context)) return false;

        boolean showFine = fragment.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION);
        boolean showCoarse = fragment.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION);
        return !showFine && !showCoarse;
    }

    public static boolean isActivityRecognitionPermissionBlocked(@NonNull Fragment fragment) {
        Context context = fragment.requireContext();
        if (hasActivityRecognitionPermission(context)) return false;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false;
        if (!AppSettingsManager.wasTrackingActivityPermissionRequested(context)) return false;
        return !fragment.shouldShowRequestPermissionRationale(Manifest.permission.ACTIVITY_RECOGNITION);
    }

    public static boolean isNotificationsBlocked(@NonNull Fragment fragment) {
        Context context = fragment.requireContext();
        boolean enabledInSystem = NotificationManagerCompat.from(context).areNotificationsEnabled();

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return !enabledInSystem;
        }

        boolean permissionGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;

        if (permissionGranted) {
            return !enabledInSystem;
        }

        if (!AppSettingsManager.wasTrackingNotificationsPermissionRequested(context)) {
            return false;
        }

        return !fragment.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS);
    }

    @NonNull
    public static List<Requirement> getBlockedRuntimeRequirements(@NonNull Fragment fragment) {
        List<Requirement> blocked = new ArrayList<>();
        if (!hasLocationPermission(fragment.requireContext()) && isLocationPermissionBlocked(fragment)) {
            blocked.add(Requirement.LOCATION);
        }
        if (!hasActivityRecognitionPermission(fragment.requireContext())
                && isActivityRecognitionPermissionBlocked(fragment)) {
            blocked.add(Requirement.ACTIVITY_RECOGNITION);
        }
        if (!hasNotificationsRequirement(fragment.requireContext()) && isNotificationsBlocked(fragment)) {
            blocked.add(Requirement.NOTIFICATIONS);
        }
        return blocked;
    }

    @NonNull
    public static List<Requirement> getRequestableMissingRequirements(@NonNull Fragment fragment) {
        Context context = fragment.requireContext();
        List<Requirement> requestable = new ArrayList<>();
        if (!hasLocationPermission(context) && !isLocationPermissionBlocked(fragment)) {
            requestable.add(Requirement.LOCATION);
        }
        if (!hasActivityRecognitionPermission(context) && !isActivityRecognitionPermissionBlocked(fragment)) {
            requestable.add(Requirement.ACTIVITY_RECOGNITION);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && !hasNotificationsRequirement(context)
                && !isNotificationsBlocked(fragment)) {
            requestable.add(Requirement.NOTIFICATIONS);
        }
        return requestable;
    }

    @NonNull
    public static String[] buildRequestablePermissions(@NonNull Fragment fragment) {
        List<String> permissions = new ArrayList<>();
        List<Requirement> missingRequirements = getRequestableMissingRequirements(fragment);
        for (Requirement requirement : missingRequirements) {
            switch (requirement) {
                case LOCATION:
                    permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
                    permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
                    break;
                case ACTIVITY_RECOGNITION:
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        permissions.add(Manifest.permission.ACTIVITY_RECOGNITION);
                    }
                    break;
                case NOTIFICATIONS:
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissions.add(Manifest.permission.POST_NOTIFICATIONS);
                    }
                    break;
                case GPS:
                    break;
            }
        }
        return permissions.toArray(new String[0]);
    }


    @NonNull
    public static String[] buildRequestablePermissionsForRequirement(@NonNull Fragment fragment,
                                                                      @NonNull Requirement requirement) {
        Context context = fragment.requireContext();
        switch (requirement) {
            case LOCATION:
                if (hasLocationPermission(context) || isLocationPermissionBlocked(fragment)) {
                    return new String[0];
                }
                return new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                };

            case ACTIVITY_RECOGNITION:
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                        || hasActivityRecognitionPermission(context)
                        || isActivityRecognitionPermissionBlocked(fragment)) {
                    return new String[0];
                }
                return new String[]{Manifest.permission.ACTIVITY_RECOGNITION};

            case NOTIFICATIONS:
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                        || hasNotificationsRequirement(context)
                        || isNotificationsBlocked(fragment)) {
                    return new String[0];
                }
                return new String[]{Manifest.permission.POST_NOTIFICATIONS};

            case GPS:
            default:
                return new String[0];
        }
    }

    public static void markPermissionsRequested(@NonNull Context context, @NonNull String[] permissions) {
        boolean location = false;
        boolean activity = false;
        boolean notifications = false;

        for (String permission : permissions) {
            if (Manifest.permission.ACCESS_FINE_LOCATION.equals(permission)
                    || Manifest.permission.ACCESS_COARSE_LOCATION.equals(permission)) {
                location = true;
            } else if (Manifest.permission.ACTIVITY_RECOGNITION.equals(permission)) {
                activity = true;
            } else if (Manifest.permission.POST_NOTIFICATIONS.equals(permission)) {
                notifications = true;
            }
        }

        if (location) {
            AppSettingsManager.setTrackingLocationPermissionRequested(context, true);
        }
        if (activity) {
            AppSettingsManager.setTrackingActivityPermissionRequested(context, true);
        }
        if (notifications) {
            AppSettingsManager.setTrackingNotificationsPermissionRequested(context, true);
        }
    }
}
