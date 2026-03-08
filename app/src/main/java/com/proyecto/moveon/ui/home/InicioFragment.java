package com.proyecto.moveon.ui.home;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.proyecto.moveon.R;
import com.proyecto.moveon.databinding.FragmentInicioBinding;
import com.proyecto.moveon.ui.home.tracking.TrackingState;
import com.proyecto.moveon.ui.home.tracking.TrackingViewModel;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;

/**
 * Fragmento principal de tracking.
 * Flujo de estados:
 *   IDLE ──[Play]──► RUNNING ──[Pausa]──► PAUSED ──[Play]──► RUNNING
 *                        └──[Stop]──► FINISHED → guarda → IDLE
 */
public class InicioFragment extends Fragment implements OnMapReadyCallback {

    // -------------------------------------------------------------------------
    // ViewBinding y ViewModel
    // -------------------------------------------------------------------------

    private FragmentInicioBinding binding;
    private TrackingViewModel      viewModel;

    // -------------------------------------------------------------------------
    // Mapa
    // -------------------------------------------------------------------------

    @Nullable private GoogleMap googleMap;
    @Nullable private Polyline  routePolyline;

    // -------------------------------------------------------------------------
    // Permisos
    // -------------------------------------------------------------------------

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestMultiplePermissions(),
                    permissions -> {
                        boolean locationGranted =
                                Boolean.TRUE.equals(permissions.get(Manifest.permission.ACCESS_FINE_LOCATION))
                                        || Boolean.TRUE.equals(permissions.get(Manifest.permission.ACCESS_COARSE_LOCATION));
                        if (locationGranted) {
                            enableMapMyLocation();
                            viewModel.startTracking();
                        } else {
                            Toast.makeText(requireContext(),
                                    R.string.tracking_permission_denied, Toast.LENGTH_LONG).show();
                        }
                    });

    // -------------------------------------------------------------------------
    // Ciclo de vida
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // Mapa
    // -------------------------------------------------------------------------

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

        if (hasLocationPermission()) {
            enableMapMyLocation();
        }
    }

    @SuppressWarnings("MissingPermission")
    private void enableMapMyLocation() {
        if (googleMap != null) {
            googleMap.setMyLocationEnabled(true);
        }
    }

    // -------------------------------------------------------------------------
    // Click listeners
    // -------------------------------------------------------------------------

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
            requestPermissionsAndStart();
        } else if (state.isRunning()) {
            viewModel.pauseTracking();
        } else if (state.isPaused()) {
            viewModel.startTracking();
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
                        requestPermissionsAndStart();
                    })
                    .setNegativeButton(R.string.tracking_dialog_new_activity_cancel, null)
                    .show();
        } else {
            requestPermissionsAndStart();
        }
    }

    // -------------------------------------------------------------------------
    // Observadores
    // -------------------------------------------------------------------------

    private void observeViewModel() {
        viewModel.getTrackingState().observe(getViewLifecycleOwner(),
                this::renderTrackingState);

        viewModel.getSaveState().observe(getViewLifecycleOwner(), uiState -> {
            if (uiState == null) return;

            binding.loadingOverlay.setVisibility(
                    uiState.loading ? View.VISIBLE : View.GONE);

            if (!uiState.loading && uiState.data != null) {
                Toast.makeText(requireContext(),
                        R.string.tracking_activity_saved_ok, Toast.LENGTH_SHORT).show();
                clearMapRoute();
            }
        });

        viewModel.getErrorEvent().observe(getViewLifecycleOwner(), event -> {
            if (event == null) return;
            String msg = event.getContentIfNotHandled();
            if (msg != null) {
                Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    // -------------------------------------------------------------------------
    // Renderizado de estado
    // -------------------------------------------------------------------------

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
    }

    // -------------------------------------------------------------------------
    // Mapa — polilínea de ruta
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // Permisos
    // -------------------------------------------------------------------------

    private void requestPermissionsAndStart() {
        if (hasLocationPermission() && hasActivityRecognitionPermission()) {
            viewModel.startTracking();
        } else {
            permissionLauncher.launch(buildRequiredPermissions());
        }
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasActivityRecognitionPermission() {
        return ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED;
    }

    @NonNull
    private String[] buildRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACTIVITY_RECOGNITION,
                    Manifest.permission.POST_NOTIFICATIONS
            };
        }
        return new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACTIVITY_RECOGNITION
        };
    }

    // -------------------------------------------------------------------------
    // Indicadores de actividad (conservados del original)
    // -------------------------------------------------------------------------

    private void showWalkingStatus() {
        if (binding == null) return;

        binding.statusWalking.setBackgroundColor(
                ContextCompat.getColor(requireContext(), R.color.greenPrimary));
        binding.tvWalking.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.textOnGreen));
        binding.ivWalking.setColorFilter(
                ContextCompat.getColor(requireContext(), R.color.textOnGreen));

        binding.statusRunning.setBackgroundColor(Color.TRANSPARENT);
        binding.tvRunning.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.textSecondary));
        binding.ivRunning.setColorFilter(
                ContextCompat.getColor(requireContext(), R.color.textSecondary));
    }

    private void showRunningStatus() {
        if (binding == null) return;

        binding.statusRunning.setBackgroundColor(
                ContextCompat.getColor(requireContext(), R.color.greenPrimary));
        binding.tvRunning.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.textOnGreen));
        binding.ivRunning.setColorFilter(
                ContextCompat.getColor(requireContext(), R.color.textOnGreen));

        binding.statusWalking.setBackgroundColor(Color.TRANSPARENT);
        binding.tvWalking.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.textSecondary));
        binding.ivWalking.setColorFilter(
                ContextCompat.getColor(requireContext(), R.color.textSecondary));
    }

    // -------------------------------------------------------------------------
    // Formatters
    // -------------------------------------------------------------------------

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