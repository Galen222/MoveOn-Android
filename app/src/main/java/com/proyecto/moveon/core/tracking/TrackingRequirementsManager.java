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
 * Requisitos contemplados:
 * - Permiso de ubicación (fine o coarse)
 * - Permiso de reconocimiento de actividad física (API 29+)
 * - Permiso/estado de notificaciones (runtime en API 33+, ajuste de sistema en APIs anteriores)
 * - Ubicación del dispositivo encendida (GPS / servicios de localización)
 */
public final class TrackingRequirementsManager {

    private static final String POST_NOTIFICATIONS_PERMISSION =
            "android.permission.POST_NOTIFICATIONS";

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

    /**
     * Evita instancias de una clase utilitaria de comprobación de requisitos.
     */
    private TrackingRequirementsManager() {}

    /**
     * Comprueba si la app dispone de al menos uno de los permisos de ubicación necesarios para trackear.
     *
     * @param context contexto usado para consultar permisos concedidos.
     * @return {@code true} cuando existe permiso de ubicación fine o coarse.
     */
    public static boolean hasLocationPermission(@NonNull Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Indica si el permiso de reconocimiento de actividad está concedido.
     *
     * @param context contexto usado para consultar el permiso runtime.
     * @return {@code true} cuando el permiso está concedido.
     */
    public static boolean hasActivityRecognitionPermission(@NonNull Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Verifica que las notificaciones puedan usarse tanto a nivel de sistema como de permiso runtime cuando corresponde.
     *
     * @param context contexto usado para consultar el permiso y el ajuste global de notificaciones.
     * @return {@code true} cuando el flujo de tracking puede mostrar notificaciones sin restricciones.
     */
    public static boolean hasNotificationsRequirement(@NonNull Context context) {
        boolean enabledInSystem = NotificationManagerCompat.from(context).areNotificationsEnabled();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return enabledInSystem;
        }
        boolean permissionGranted = ContextCompat.checkSelfPermission(context, POST_NOTIFICATIONS_PERMISSION)
                == PackageManager.PERMISSION_GRANTED;
        return permissionGranted && enabledInSystem;
    }

    /**
     * Comprueba si los servicios de localización del dispositivo están realmente activados.
     *
     * @param context contexto usado para consultar el {@link LocationManager}.
     * @return {@code true} cuando GPS o localización del sistema están activos.
     */
    public static boolean isDeviceLocationEnabled(@NonNull Context context) {
        LocationManager locationManager =
                (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) return false;

        return locationManager.isLocationEnabled();
    }

    /**
     * Resume si todos los requisitos runtime previos al tracking están satisfechos.
     *
     * @param context contexto desde el que verificar permisos y notificaciones.
     * @return {@code true} cuando la app ya puede iniciar tracking sin pedir más permisos runtime.
     */
    public static boolean areRuntimeRequirementsSatisfied(@NonNull Context context) {
        return hasLocationPermission(context)
                && hasActivityRecognitionPermission(context)
                && hasNotificationsRequirement(context);
    }

    /**
     * Calcula el estado del requisito de ubicación distinguiendo entre concedido, solicitables o bloqueado.
     *
     * @param fragment fragmento desde el que se evalúa el estado y las rationales del permiso.
     * @return estado actual del requisito de ubicación para la UI.
     */
    public static Status getLocationStatus(@NonNull Fragment fragment) {
        Context context = fragment.requireContext();
        if (hasLocationPermission(context)) return Status.ENABLED;
        return isLocationPermissionBlocked(fragment) ? Status.BLOCKED : Status.NEEDS_ACTIVATION;
    }

    /**
     * Calcula el estado del permiso de reconocimiento de actividad para el fragmento actual.
     *
     * @param fragment fragmento desde el que se consulta el permiso y su rationale.
     * @return estado actual del requisito de reconocimiento de actividad.
     */
    public static Status getActivityRecognitionStatus(@NonNull Fragment fragment) {
        Context context = fragment.requireContext();
        if (hasActivityRecognitionPermission(context)) return Status.ENABLED;
        return isActivityRecognitionPermissionBlocked(fragment) ? Status.BLOCKED : Status.NEEDS_ACTIVATION;
    }

    /**
     * Devuelve el estado del requisito de notificaciones teniendo en cuenta permiso runtime y ajuste del sistema.
     *
     * @param fragment fragmento desde el que se consultan permisos y estado de notificaciones.
     * @return estado actual del requisito de notificaciones para iniciar tracking.
     */
    public static Status getNotificationsStatus(@NonNull Fragment fragment) {
        Context context = fragment.requireContext();
        if (hasNotificationsRequirement(context)) return Status.ENABLED;
        return isNotificationsBlocked(fragment) ? Status.BLOCKED : Status.NEEDS_ACTIVATION;
    }

    /**
     * Resume el estado de la localización del dispositivo como requisito previo no-runtime.
     *
     * @param context contexto usado para consultar el estado del sistema.
     * @return {@link Status#ENABLED} cuando la localización está activa o {@link Status#NEEDS_ACTIVATION} en caso contrario.
     */
    public static Status getDeviceLocationStatus(@NonNull Context context) {
        return isDeviceLocationEnabled(context) ? Status.ENABLED : Status.NEEDS_ACTIVATION;
    }

    /**
     * Determina si el permiso de ubicación quedó bloqueado tras un intento previo y ya no puede pedirse directamente.
     *
     * @param fragment fragmento desde el que se consultan la rationale y las marcas persistidas en ajustes.
     * @return {@code true} cuando la app ya pidió ubicación, sigue sin tenerla y Android no muestra rationale.
     */
    public static boolean isLocationPermissionBlocked(@NonNull Fragment fragment) {
        Context context = fragment.requireContext();
        if (hasLocationPermission(context)) return false;
        if (!AppSettingsManager.wasTrackingLocationPermissionRequested(context)) return false;

        boolean showFine = fragment.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION);
        boolean showCoarse = fragment.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION);
        return !showFine && !showCoarse;
    }

    /**
     * Indica si el permiso de actividad quedó bloqueado después de haber sido solicitado al menos una vez.
     *
     * @param fragment fragmento desde el que se consultan la rationale y el histórico de petición.
     * @return {@code true} cuando el permiso sigue faltando y el usuario ya lo dejó bloqueado.
     */
    public static boolean isActivityRecognitionPermissionBlocked(@NonNull Fragment fragment) {
        Context context = fragment.requireContext();
        if (hasActivityRecognitionPermission(context)) return false;
        if (!AppSettingsManager.wasTrackingActivityPermissionRequested(context)) return false;
        return !fragment.shouldShowRequestPermissionRationale(Manifest.permission.ACTIVITY_RECOGNITION);
    }

    /**
     * Determina si las notificaciones están efectivamente bloqueadas para el flujo de tracking.
     *
     * @param fragment fragmento desde el que se consulta el permiso runtime y el ajuste global del sistema.
     * @return {@code true} cuando la app no puede notificar y tampoco puede pedirlo directamente otra vez.
     */
    public static boolean isNotificationsBlocked(@NonNull Fragment fragment) {
        Context context = fragment.requireContext();
        boolean enabledInSystem = NotificationManagerCompat.from(context).areNotificationsEnabled();

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return !enabledInSystem;
        }

        boolean permissionGranted = ContextCompat.checkSelfPermission(context, POST_NOTIFICATIONS_PERMISSION)
                == PackageManager.PERMISSION_GRANTED;

        if (permissionGranted) {
            return !enabledInSystem;
        }

        if (!AppSettingsManager.wasTrackingNotificationsPermissionRequested(context)) {
            return false;
        }

        return !fragment.shouldShowRequestPermissionRationale(POST_NOTIFICATIONS_PERMISSION);
    }

    /**
     * Lista los requisitos runtime que faltan y además han quedado bloqueados para petición directa.
     *
     * @param fragment fragmento desde el que se evalúan permisos concedidos y rationales.
     * @return lista de {@link Requirement} que sólo pueden resolverse yendo a ajustes.
     */
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

    /**
     * Devuelve los requisitos aún pendientes que el fragmento puede solicitar en este momento.
     *
     * @param fragment fragmento que lanzará la petición de permisos al usuario.
     * @return lista de requisitos faltantes que siguen siendo solicitables por runtime permission.
     */
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

    /**
     * Construye el array de permisos Android a solicitar a partir de los requisitos pendientes y no bloqueados.
     *
     * @param fragment fragmento desde el que se va a lanzar {@code requestPermissions}.
     * @return array de permisos Android listo para pasarse a la petición runtime.
     */
    @NonNull
    public static String[] buildRequestablePermissions(@NonNull Fragment fragment) {
        List<String> permissions = new ArrayList<>();
        List<Requirement> missingRequirements = getRequestableMissingRequirements(fragment);
        for (Requirement requirement : missingRequirements) {
            switch (requirement) {
                case LOCATION -> {
                    permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
                    permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
                }
                case ACTIVITY_RECOGNITION ->
                        permissions.add(Manifest.permission.ACTIVITY_RECOGNITION);
                case NOTIFICATIONS -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissions.add(POST_NOTIFICATIONS_PERMISSION);
                    }
                }
                case GPS -> {
                    // No requiere permiso runtime.
                }
            }
        }
        return permissions.toArray(new String[0]);
    }


    /**
     * Genera los permisos concretos necesarios para un requisito concreto si todavía puede pedirse.
     *
     * @param fragment fragmento que evaluará el estado actual del permiso.
     * @param requirement requisito funcional cuyo permiso runtime se quiere materializar.
     * @return array con los permisos Android asociados al requisito o vacío si ya no procede pedirlos.
     */
    @NonNull
    public static String[] buildRequestablePermissionsForRequirement(@NonNull Fragment fragment,
                                                                      @NonNull Requirement requirement) {
        Context context = fragment.requireContext();
        return switch (requirement) {
            case LOCATION -> {
                if (hasLocationPermission(context) || isLocationPermissionBlocked(fragment)) {
                    yield new String[0];
                }
                yield new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                };
            }
            case ACTIVITY_RECOGNITION -> {
                if (hasActivityRecognitionPermission(context)
                        || isActivityRecognitionPermissionBlocked(fragment)) {
                    yield new String[0];
                }
                yield new String[]{Manifest.permission.ACTIVITY_RECOGNITION};
            }
            case NOTIFICATIONS -> {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                        || hasNotificationsRequirement(context)
                        || isNotificationsBlocked(fragment)) {
                    yield new String[0];
                }
                yield new String[]{POST_NOTIFICATIONS_PERMISSION};
            }
            case GPS -> new String[0];
        };
    }

    /**
     * Marca en preferencias qué grupos de permisos ya fueron solicitados al usuario en el intento actual.
     *
     * @param context contexto usado para persistir las banderas en {@link AppSettingsManager}.
     * @param permissions permisos concretos que acaban de pedirse al usuario.
     */
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
            } else if (POST_NOTIFICATIONS_PERMISSION.equals(permission)) {
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
