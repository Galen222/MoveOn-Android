package com.proyecto.moveon.ui.home;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.proyecto.moveon.ui.common.TopSnackbar;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.material.snackbar.Snackbar;
import com.proyecto.moveon.R;
import com.proyecto.moveon.core.tracking.TrackingRequirementsManager;
import com.proyecto.moveon.databinding.FragmentInicioBinding;
import com.proyecto.moveon.ui.home.tracking.TrackingState;
import com.proyecto.moveon.ui.home.tracking.TrackingViewModel;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class InicioFragment extends Fragment implements OnMapReadyCallback {

    private static final LatLng DEFAULT_LOCATION = new LatLng(40.4168, -3.7038);
    private static final float  DEFAULT_ZOOM     = 5.5f;
    private static final float  USER_ZOOM        = 16f;

    private FragmentInicioBinding binding;
    private TrackingViewModel viewModel;

    @Nullable private GoogleMap googleMap;
    @Nullable private Polyline routePolyline;

    /** {@code true} tras centrar la cámara en la ubicación real del usuario.
     *  Evita re-centrar innecesariamente en cada onResume (ej. al cambiar de tab). */
    private boolean userLocated = false;

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestMultiplePermissions(),
                    permissions -> onPermissionsRequestCompleted());

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentInicioBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(TrackingViewModel.class);

        setupMap();
        setupClickListeners();
        observeViewModel();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Si los permisos se concedieron desde otro sitio (ej. ProfileFragment o
        // Ajustes del sistema) mientras este Fragment estaba oculto, onMapReady
        // ya pasó y el mapa sigue centrado en España. Aquí detectamos esa situación
        // y centramos la cámara en el usuario.
        if (googleMap != null
                && !userLocated
                && TrackingRequirementsManager.hasLocationPermission(requireContext())) {
            enableMapMyLocation();
            moveCameraToCurrentLocation();
        }
    }

    private void setupMap() {
        SupportMapFragment mapFragment = (SupportMapFragment)
                getChildFragmentManager().findFragmentById(R.id.map_fragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        googleMap = map;
        googleMap.getUiSettings().setZoomControlsEnabled(false);
        googleMap.getUiSettings().setCompassEnabled(true);
        googleMap.getUiSettings().setMyLocationButtonEnabled(false);

        if (TrackingRequirementsManager.hasLocationPermission(requireContext())) {
            enableMapMyLocation();
            moveCameraToCurrentLocation();
        } else {
            googleMap.moveCamera(
                    CameraUpdateFactory.newLatLngZoom(DEFAULT_LOCATION, DEFAULT_ZOOM));
        }
    }

    @SuppressWarnings("MissingPermission")
    private void enableMapMyLocation() {
        if (googleMap != null && TrackingRequirementsManager.hasLocationPermission(requireContext())) {
            googleMap.setMyLocationEnabled(true);
        }
    }

    @SuppressWarnings("MissingPermission")
    private void moveCameraToCurrentLocation() {
        if (googleMap == null
                || !TrackingRequirementsManager.hasLocationPermission(requireContext())) return;

        FusedLocationProviderClient fusedClient =
                LocationServices.getFusedLocationProviderClient(requireContext());

        fusedClient.getLastLocation().addOnSuccessListener(location -> {
            if (!isAdded() || googleMap == null) return;

            if (location != null) {
                LatLng userLatLng = new LatLng(
                        location.getLatitude(), location.getLongitude());
                googleMap.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(userLatLng, USER_ZOOM));
                userLocated = true;
            } else {
                googleMap.moveCamera(
                        CameraUpdateFactory.newLatLngZoom(DEFAULT_LOCATION, DEFAULT_ZOOM));
            }
        });
    }

    private void setupClickListeners() {
        binding.btnPlay.setOnClickListener(v  -> onPlayClicked());
        binding.btnStop.setOnClickListener(v  -> onStopClicked());
        binding.btnReset.setOnClickListener(v -> onResetClicked());
        binding.btnAdd.setOnClickListener(v   -> onAddClicked());
    }

    private void onPlayClicked() {
        TrackingState state = viewModel.getTrackingState().getValue();
        if (state == null) return;

        if (state.isIdle() || state.isFinished()) {
            ensureTrackingRequirementsAndStart();
        } else if (state.isRunning()) {
            viewModel.pauseTracking();
        } else if (state.isPaused()) {
            ensureTrackingRequirementsAndStart();
        }
    }

    private void onStopClicked() {
        TrackingState state = viewModel.getTrackingState().getValue();
        if (state == null || !state.isActive()) return;

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.tracking_dialog_stop_title)
                .setMessage(R.string.tracking_dialog_stop_message)
                .setPositiveButton(R.string.tracking_dialog_stop_confirm,
                        (d, w) -> viewModel.stopAndSave())
                .setNegativeButton(R.string.tracking_dialog_stop_cancel, null)
                .show();
    }

    private void onResetClicked() {
        TrackingState state = viewModel.getTrackingState().getValue();
        if (state == null || state.isIdle()) return;

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.tracking_dialog_reset_title)
                .setMessage(R.string.tracking_dialog_reset_message)
                .setPositiveButton(R.string.tracking_dialog_reset_confirm, (d, w) -> {
                    viewModel.resetTracking();
                    clearMapRoute();
                })
                .setNegativeButton(R.string.tracking_dialog_reset_cancel, null)
                .show();
    }

    private void onAddClicked() {
        if (viewModel.isTrackingActive()) {
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.tracking_dialog_new_activity_title)
                    .setMessage(R.string.tracking_dialog_new_activity_message)
                    .setPositiveButton(R.string.tracking_dialog_new_activity_confirm, (d, w) -> {
                        viewModel.resetTracking();
                        clearMapRoute();
                        ensureTrackingRequirementsAndStart();
                    })
                    .setNegativeButton(R.string.tracking_dialog_new_activity_cancel, null)
                    .show();
        } else {
            ensureTrackingRequirementsAndStart();
        }
    }

    private void observeViewModel() {
        viewModel.getTrackingState().observe(getViewLifecycleOwner(),
                this::renderTrackingState);

        viewModel.getSaveState().observe(getViewLifecycleOwner(), uiState -> {
            if (uiState == null) return;

            binding.loadingOverlay.setVisibility(
                    uiState.loading ? View.VISIBLE : View.GONE);

            if (!uiState.loading && uiState.data != null) {
                TopSnackbar.success(binding.getRoot(),
                        getString(R.string.tracking_activity_saved_ok));
                clearMapRoute();
            }
        });

        viewModel.getErrorEvent().observe(getViewLifecycleOwner(), event -> {
            if (event == null) return;
            String msg = event.getContentIfNotHandled();
            if (msg != null) {
                TopSnackbar.error(binding.getRoot(), msg);
            }
        });

        viewModel.getVehicleSpeedEvent().observe(getViewLifecycleOwner(), event -> {
            if (event == null) return;
            String msg = event.getContentIfNotHandled();
            if (msg == null) return;
            Snackbar.make(binding.getRoot(), msg, Snackbar.LENGTH_LONG)
                    .setAnchorView(binding.cardControls)
                    .setBackgroundTint(
                            ContextCompat.getColor(requireContext(), R.color.surfaceContainerHigh))
                    .setTextColor(
                            ContextCompat.getColor(requireContext(), R.color.textPrimary))
                    .show();
        });
    }

    private void renderTrackingState(@NonNull TrackingState state) {
        updateActivityIndicator(state.getActivityType());
        updateControlButtons(state);
        updateMetrics(state);
        updateMapRoute(state.getRoutePoints());
    }

    private void updateActivityIndicator(@NonNull TrackingState.ActivityType type) {
        if (type == TrackingState.ActivityType.RUNNING_ACTIVITY) {
            showRunningStatus();
        } else {
            showWalkingStatus();
        }
    }

    private void updateControlButtons(@NonNull TrackingState state) {
        switch (state.getStatus()) {
            case IDLE:
            case FINISHED:
                binding.btnPlay.setIconResource(R.drawable.play_icon);
                binding.btnStop.setVisibility(View.INVISIBLE);
                binding.btnReset.setVisibility(View.INVISIBLE);
                break;

            case RUNNING:
                binding.btnPlay.setIconResource(R.drawable.pause_icon);
                binding.btnStop.setVisibility(View.VISIBLE);
                binding.btnReset.setVisibility(View.INVISIBLE);
                break;

            case PAUSED:
                binding.btnPlay.setIconResource(R.drawable.play_icon);
                binding.btnStop.setVisibility(View.VISIBLE);
                binding.btnReset.setVisibility(View.VISIBLE);
                break;
        }
    }

    private void updateMetrics(@NonNull TrackingState state) {
        binding.tvElapsedTime.setText(formatElapsed(state.getElapsedSeconds()));
        binding.tvDistance.setText(formatDistance(state.getDistanceMeters()));
        binding.tvCalories.setText(
                getString(R.string.tracking_calories_format, state.getCalories()));

        String pace = state.getPace();
        binding.tvPace.setText(pace != null ? pace : getString(R.string.tracking_default_pace));
    }

    private void updateMapRoute(@NonNull List<LatLng> points) {
        if (googleMap == null || points.isEmpty()) return;

        if (routePolyline == null) {
            PolylineOptions options = new PolylineOptions()
                    .color(ContextCompat.getColor(requireContext(), R.color.greenPrimary))
                    .width(getResources().getDimension(R.dimen.tracking_polyline_width))
                    .geodesic(true)
                    .addAll(points);
            routePolyline = googleMap.addPolyline(options);
        } else {
            routePolyline.setPoints(points);
        }

        LatLng last = points.get(points.size() - 1);
        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(last, 17f));
    }

    private void clearMapRoute() {
        if (routePolyline != null) {
            routePolyline.remove();
            routePolyline = null;
        }
    }

    private void ensureTrackingRequirementsAndStart() {
        List<TrackingRequirementsManager.Requirement> blockedRequirements =
                TrackingRequirementsManager.getBlockedRuntimeRequirements(this);
        if (!blockedRequirements.isEmpty()) {
            showBlockedRequirementsDialog(blockedRequirements);
            return;
        }

        String[] permissionsToRequest = TrackingRequirementsManager.buildRequestablePermissions(this);
        if (permissionsToRequest.length > 0) {
            TrackingRequirementsManager.markPermissionsRequested(requireContext(), permissionsToRequest);
            permissionLauncher.launch(permissionsToRequest);
            return;
        }

        if (!TrackingRequirementsManager.isDeviceLocationEnabled(requireContext())) {
            showDeviceLocationDisabledDialog();
            return;
        }

        enableMapMyLocation();
        viewModel.startTracking();
    }

    private void onPermissionsRequestCompleted() {
        if (!isAdded()) return;

        if (TrackingRequirementsManager.hasLocationPermission(requireContext())) {
            enableMapMyLocation();
            moveCameraToCurrentLocation();
        }

        List<TrackingRequirementsManager.Requirement> blockedRequirements =
                TrackingRequirementsManager.getBlockedRuntimeRequirements(this);
        if (!blockedRequirements.isEmpty()) {
            showBlockedRequirementsDialog(blockedRequirements);
            return;
        }

        List<TrackingRequirementsManager.Requirement> missingRequestable =
                TrackingRequirementsManager.getRequestableMissingRequirements(this);
        if (!missingRequestable.isEmpty()) {
            showNeedsActivationDialog(missingRequestable);
            return;
        }

        if (!TrackingRequirementsManager.isDeviceLocationEnabled(requireContext())) {
            showDeviceLocationDisabledDialog();
            return;
        }

        viewModel.startTracking();
    }

    private void showNeedsActivationDialog(
            @NonNull List<TrackingRequirementsManager.Requirement> requirements) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.tracking_requirements_needed_title)
                .setMessage(getString(
                        R.string.tracking_requirements_needed_message,
                        buildRequirementsBulletList(requirements)))
                .setPositiveButton(R.string.common_accept, null)
                .show();
    }

    private void showBlockedRequirementsDialog(
            @NonNull List<TrackingRequirementsManager.Requirement> requirements) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.tracking_requirements_blocked_title)
                .setMessage(getString(
                        R.string.tracking_requirements_blocked_message,
                        buildRequirementsBulletList(requirements)))
                .setPositiveButton(R.string.common_accept, null)
                .setNegativeButton(R.string.tracking_requirements_go_settings,
                        (dialog, which) -> openBestSettingsForRequirements(requirements))
                .show();
    }

    private void showDeviceLocationDisabledDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.tracking_device_location_disabled_title)
                .setMessage(R.string.tracking_device_location_disabled_message)
                .setPositiveButton(R.string.common_accept, null)
                .setNegativeButton(R.string.tracking_requirements_go_settings,
                        (dialog, which) -> openLocationSettings())
                .show();
    }

    @NonNull
    private String buildRequirementsBulletList(
            @NonNull List<TrackingRequirementsManager.Requirement> requirements) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < requirements.size(); i++) {
            if (i > 0) builder.append("\n");
            builder.append("• ").append(getRequirementLabel(requirements.get(i)));
        }
        return builder.toString();
    }

    @NonNull
    private String getRequirementLabel(@NonNull TrackingRequirementsManager.Requirement requirement) {
        switch (requirement) {
            case LOCATION:
                return getString(R.string.tracking_requirement_location_name);
            case ACTIVITY_RECOGNITION:
                return getString(R.string.tracking_requirement_activity_name);
            case NOTIFICATIONS:
                return getString(R.string.tracking_requirement_notifications_name);
            case GPS:
            default:
                return getString(R.string.tracking_requirement_device_location_name);
        }
    }

    private void openBestSettingsForRequirements(
            @NonNull List<TrackingRequirementsManager.Requirement> requirements) {
        if (requirements.size() == 1
                && requirements.get(0) == TrackingRequirementsManager.Requirement.NOTIFICATIONS) {
            openNotificationSettings();
            return;
        }
        openAppSettings();
    }

    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", requireContext().getPackageName(), null));
        startActivity(intent);
    }

    private void openNotificationSettings() {
        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().getPackageName());
        startActivity(intent);
    }

    private void openLocationSettings() {
        startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
    }

    private void showWalkingStatus() {
        if (binding == null) return;

        binding.statusWalking.setBackground(
                ContextCompat.getDrawable(requireContext(), R.drawable.pill_active));
        binding.tvWalking.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.textOnGreen));
        binding.ivWalking.setColorFilter(
                ContextCompat.getColor(requireContext(), R.color.textOnGreen));

        binding.statusRunning.setBackground(
                ContextCompat.getDrawable(requireContext(), R.drawable.pill_inactive));
        binding.tvRunning.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.textSecondary));
        binding.ivRunning.setColorFilter(
                ContextCompat.getColor(requireContext(), R.color.textSecondary));
    }

    private void showRunningStatus() {
        if (binding == null) return;

        binding.statusRunning.setBackground(
                ContextCompat.getDrawable(requireContext(), R.drawable.pill_active));
        binding.tvRunning.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.textOnGreen));
        binding.ivRunning.setColorFilter(
                ContextCompat.getColor(requireContext(), R.color.textOnGreen));

        binding.statusWalking.setBackground(
                ContextCompat.getDrawable(requireContext(), R.drawable.pill_inactive));
        binding.tvWalking.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.textSecondary));
        binding.ivWalking.setColorFilter(
                ContextCompat.getColor(requireContext(), R.color.textSecondary));
    }

    @NonNull
    private String formatElapsed(long seconds) {
        long h = TimeUnit.SECONDS.toHours(seconds);
        long m = TimeUnit.SECONDS.toMinutes(seconds) % 60;
        long s = seconds % 60;
        if (h > 0) {
            return String.format(Locale.US, "%d:%02d:%02d", h, m, s);
        }
        return String.format(Locale.US, "%02d:%02d", m, s);
    }

    @NonNull
    private String formatDistance(int meters) {
        if (meters >= 1000) {
            return getString(R.string.tracking_distance_km_format, meters / 1000.0f);
        }
        return getString(R.string.tracking_distance_m_format, meters);
    }
}
