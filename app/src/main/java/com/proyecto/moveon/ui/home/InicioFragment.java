
package com.proyecto.moveon.ui.home;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

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
import com.proyecto.moveon.R;
import com.proyecto.moveon.core.settings.AppSettingsManager;
import com.proyecto.moveon.core.settings.PaceDisplayUtils;
import com.proyecto.moveon.core.tracking.TrackingRequirementsManager;
import com.proyecto.moveon.data.session.SecureSessionManager;
import com.proyecto.moveon.databinding.FragmentInicioBinding;
import com.proyecto.moveon.ui.common.TopSnackbar;
import com.proyecto.moveon.ui.home.tracking.TrackingAlert;
import com.proyecto.moveon.ui.home.tracking.TrackingState;
import com.proyecto.moveon.ui.home.tracking.TrackingViewModel;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Pantalla principal de tracking en tiempo real.
 *
 * <p>Combina mapa, métricas, control de sesión y resolución de requisitos del dispositivo.
 * También consume las alertas emitidas por el servicio mediante {@link TrackingAlertBottomSheet}
 * y coordina el guardado o descarte final de la actividad.</p>
 */
public class InicioFragment extends Fragment
        implements OnMapReadyCallback, TrackingAlertBottomSheet.Listener {

    private static final LatLng DEFAULT_LOCATION = new LatLng(40.4168, -3.7038);
    private static final float DEFAULT_ZOOM = 5.5f;
    private static final float USER_ZOOM = 16f;

    private FragmentInicioBinding binding;
    private TrackingViewModel viewModel;

    @Nullable private GoogleMap googleMap;
    @Nullable private Polyline routePolyline;
    @Nullable private TrackingAlertBottomSheet trackingAlertBottomSheet;
    @Nullable private TrackingAlert.Type activeTrackingAlertType;
    @Nullable private AlertDialog stopDialog;
    /**
     * Última alerta de tracking retenida mientras el modal de stop tiene prioridad.
     * 
     * <p>Se usa para reabrir el panel inferior solo si, al cancelar el modal,
     * el estado actual de la sesión sigue justificándolo.</p>
     */
    @Nullable private TrackingAlert pendingTrackingAlertAfterStopDialog;
    private boolean suppressStationarySheetForCurrentActivity = false;
    /**
     * Espejo local del estado del permiso de ubicación conocido por este fragment.
     * 
     * <p>Se usa para detectar la transición concreta "antes no había permiso y ahora sí"
     * cuando el usuario concede el permiso fuera de esta pantalla (por ejemplo, desde Perfil).
     * En ese caso, al volver a Inicio, el mapa debe recentrarse una vez sobre la posición actual.</p>
     */
    private boolean lastKnownLocationPermissionGranted = false;

    /**
     * Launcher propio del flujo de permisos iniciado desde Inicio.
     * 
     * <p>Cuando el permiso se concede desde aquí, este callback sí vuelve a habilitar la capa
     * {@code MyLocation}, recentra el mapa y reevalúa el resto de requisitos antes de arrancar
     * el tracking.</p>
     */
    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestMultiplePermissions(),
                    _ -> onPermissionsRequestCompleted());

    /**
     * Infla la vista principal del tracking y devuelve la raíz asociada al binding.
     * 
     * @param inflater inflator proporcionado por Android.
     * @param container contenedor padre del fragment, o {@code null}.
     * @param savedInstanceState estado restaurado, o {@code null}.
     * @return raíz del layout de inicio.
     */
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentInicioBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    /**
     * Crea el {@link TrackingViewModel}, toma la fotografía inicial del estado
     * de permisos y conecta mapa, botones y observadores del tracking.
     * 
     * @param view vista ya inflada del fragment.
     * @param savedInstanceState estado restaurado, o {@code null}.
     */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(TrackingViewModel.class);

        // Tomamos una fotografía inicial del permiso para poder detectar después
        // si el usuario lo concede desde otra pantalla y vuelve a Inicio.
        lastKnownLocationPermissionGranted =
                TrackingRequirementsManager.hasLocationPermission(requireContext());

        setupMap();
        setupClickListeners();
        observeViewModel();
    }

    /**
     * Re-sincroniza el estado del mapa cada vez que Inicio vuelve al primer plano.
     * 
     * <p>Este punto corrige el caso en el que el permiso de ubicación se concede desde Perfil:
     * el callback de Perfil actualiza su propia UI, pero el mapa de Inicio no recibe ese evento.
     * Al volver a este fragment, comprobamos si el permiso pasó de denegado a concedido y,
     * solo en esa transición, recentramos la cámara automáticamente.</p>
     */
    @Override
    public void onResume() {
        super.onResume();
        syncMapLocationState();
    }

    /**
     * Cierra diálogos y suelta referencias ligadas a la vista para evitar que
     * queden overlays o estados retenidos tras una recreación del fragment.
     */
    @Override
    public void onDestroyView() {
        // Si la vista se destruye, liberamos también cualquier diálogo de stop
        // para no dejar ventanas colgando ni referencias al Fragment antiguo.
        if (stopDialog != null) {
            stopDialog.dismiss();
            stopDialog = null;
        }
        trackingAlertBottomSheet = null;
        activeTrackingAlertType = null;
        pendingTrackingAlertAfterStopDialog = null;
        suppressStationarySheetForCurrentActivity = false;
        binding = null;
        super.onDestroyView();
    }

    /**
     * Localiza el {@link SupportMapFragment} hijo y solicita el callback de mapa listo.
     */
    private void setupMap() {
        SupportMapFragment mapFragment = (SupportMapFragment)
                getChildFragmentManager().findFragmentById(R.id.map_fragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    /**
     * Configura el mapa cuando Google Play Services termina de inicializarlo y
     * decide si debe centrarse en el usuario o en la ubicación por defecto.
     * 
     * @param map instancia ya inicializada del mapa.
     */
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
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(DEFAULT_LOCATION, DEFAULT_ZOOM));
        }
    }

    /**
     * Activa la capa de ubicación del mapa únicamente cuando el permiso sigue
     * concedido en tiempo de ejecución.
     */
    @SuppressWarnings("MissingPermission")
    private void enableMapMyLocation() {
        if (googleMap != null && TrackingRequirementsManager.hasLocationPermission(requireContext())) {
            googleMap.setMyLocationEnabled(true);
        }
    }

    /**
     * Intenta recentrar la cámara sobre la última ubicación conocida del usuario
     * y usa una posición por defecto cuando todavía no hay fix disponible.
     */
    @SuppressWarnings("MissingPermission")
    private void moveCameraToCurrentLocation() {
        if (googleMap == null
                || !TrackingRequirementsManager.hasLocationPermission(requireContext())) {
            return;
        }

        FusedLocationProviderClient fusedClient =
                LocationServices.getFusedLocationProviderClient(requireContext());

        fusedClient.getLastLocation().addOnSuccessListener(location -> {
            if (!isAdded() || googleMap == null) return;

            if (location != null) {
                LatLng userLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, USER_ZOOM));
            } else {
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(DEFAULT_LOCATION, DEFAULT_ZOOM));
            }
        });
    }

    /**
     * Mantiene la capa de ubicación del mapa alineada con el estado real de permisos.
     * 
     * <p>Este método cubre tanto el alta del permiso (habilita la capa y recentra una sola vez)
     * como su posible revocación posterior (deshabilita la capa para evitar inconsistencias).</p>
     */
    private void syncMapLocationState() {
        if (!isAdded()) {
            return;
        }

        boolean hasLocationPermission =
                TrackingRequirementsManager.hasLocationPermission(requireContext());

        if (googleMap != null) {
            if (hasLocationPermission) {
                enableMapMyLocation();

                // Solo recentramos cuando detectamos una concesión nueva del permiso.
                // Así evitamos mover la cámara en cada vuelta a la pestaña Inicio.
                if (!lastKnownLocationPermissionGranted) {
                    moveCameraToCurrentLocation();
                }
            } else if (lastKnownLocationPermissionGranted) {
                // Si el permiso se revoca mientras el fragment no está visible,
                // limpiamos la capa MyLocation al regresar para reflejar el estado real.
                disableMapMyLocation();
            }
        }

        lastKnownLocationPermissionGranted = hasLocationPermission;
    }

    /**
     * Deshabilita la capa {@code MyLocation} de forma segura.
     * 
     * <p>Algunos analizadores estáticos exigen tratar esta llamada como protegida por
     * permiso incluso cuando se usa para desactivar la capa. Además, en ciertos cambios
     * de estado del fragment la API de Maps puede lanzar {@link SecurityException} si el
     * permiso acaba de revocarse. Por eso la encapsulamos en un bloque defensivo.</p>
     */
    private void disableMapMyLocation() {
        if (googleMap == null) {
            return;
        }

        try {
            googleMap.setMyLocationEnabled(false);
        } catch (SecurityException ignored) {
            // Si el permiso se ha revocado mientras el mapa sigue vivo,
            // simplemente dejamos la capa deshabilitada y evitamos que la UI reviente.
        }
    }

    /**
     * Enlaza los controles principales del tracking con sus acciones de play,
     * stop y reset.
     */
    private void setupClickListeners() {
        binding.btnPlay.setOnClickListener(_ -> onPlayClicked());
        binding.btnStop.setOnClickListener(_ -> onStopClicked());
        binding.btnReset.setOnClickListener(_ -> onResetClicked());
    }

    /**
     * Resuelve la acción del botón principal según el estado actual: iniciar,
     * reanudar o pausar la sesión de tracking.
     */
    private void onPlayClicked() {
        TrackingState state = viewModel.getTrackingState().getValue();
        if (state == null) return;

        // La auto-pausa por parada solo debe reanudarse por movimiento real.
        // Si el usuario pulsa el control principal mientras sigue en este estado,
        // ignoramos la acción para evitar una reanudación manual incoherente.
        if (state.getStatus() == TrackingState.Status.AUTO_PAUSED
                && state.getPauseReason() == TrackingState.PauseReason.STATIONARY) {
            return;
        }

        if (state.isIdle() || state.isFinished()) {
            resetStationarySheetSuppressionForCurrentActivity();
            ensureTrackingRequirementsAndStart();
        } else if (state.isPaused()) {
            ensureTrackingRequirementsAndStart();
        } else if (state.isRunning()) {
            viewModel.pauseTracking();
        }
    }

    /**
     * Abre el diálogo de parada y concentra en él las opciones de guardar,
     * cancelar o descartar la actividad actual.
     */
    private void onStopClicked() {
        TrackingState state = viewModel.getTrackingState().getValue();
        if (state == null || !state.isActive()) return;

        // Prioridad al modal de stop: si hay un panel inferior de tracking activo,
        // se cierra antes de abrir el diálogo para evitar dos capas compitiendo.
        dismissTrackingSheetIfShowing();

        if (isStopDialogShowing()) {
            return;
        }

        final boolean canSave = viewModel.canSaveTracking(state);
        String dialogMessage = getString(R.string.tracking_dialog_stop_message);
        String cannotSaveReason = viewModel.getCannotSaveReason(state);
        if (!canSave && cannotSaveReason != null) {
            dialogMessage = dialogMessage + "\n\n" + cannotSaveReason;
        }

        // Orden visual esperado en Android (izquierda a derecha):
        // negativo = Descartar, neutral = Cancelar, positivo = Guardar.
        // Así la acción destructiva queda a la izquierda y la CTA principal a la derecha.
        stopDialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.tracking_dialog_stop_title)
                .setMessage(dialogMessage)
                .setPositiveButton(R.string.tracking_dialog_stop_confirm,
                        (_, _) -> viewModel.stopAndSave())
                .setNeutralButton(R.string.tracking_dialog_stop_cancel,
                        (_, _) -> maybeRestoreTrackingAlertAfterStopCancel())
                .setNegativeButton(R.string.tracking_dialog_reset_confirm, (_, _) -> {
                    viewModel.resetTracking();
                    clearMapRoute();
                    dismissTrackingSheetIfShowing();
                    pendingTrackingAlertAfterStopDialog = null;
                    resetStationarySheetSuppressionForCurrentActivity();
                })
                .create();

        stopDialog.setOnShowListener(_ -> {
            if (stopDialog == null) {
                return;
            }

            Button saveButton = stopDialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (saveButton == null) {
                return;
            }

            saveButton.setEnabled(canSave);
            saveButton.setAlpha(canSave ? 1.0f : 0.5f);
        });
        stopDialog.setOnDismissListener(_ -> stopDialog = null);
        stopDialog.show();
    }

    /**
     * Punto de entrada usado por acciones externas (por ejemplo, la notificación)
     * para abrir el mismo diálogo de detener que se muestra al pulsar el botón en pantalla.
     * 
     * <p>No duplica ninguna lógica de guardado o descarte: simplemente reenruta hacia
     * {@link #onStopClicked()} cuando el fragment y su vista están listos.</p>
     */
    public void requestStopDialogFromExternalAction() {
        if (!isAdded() || binding == null) {
            return;
        }

        binding.getRoot().post(() -> {
            if (!isAdded() || binding == null) {
                return;
            }
            onStopClicked();
        });
    }

    /**
     * Reinicia la actividad actual o prepara una nueva sesión según si el
     * tracking sigue activo o ya estaba detenido.
     */
    private void onResetClicked() {
        TrackingState state = viewModel.getTrackingState().getValue();
        if (state == null || state.isIdle()) return;

        if (viewModel.isTrackingActive()) {
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.tracking_dialog_new_activity_title)
                    .setMessage(R.string.tracking_dialog_new_activity_message)
                    .setPositiveButton(R.string.tracking_dialog_new_activity_confirm, (_, _) -> {
                        viewModel.resetTracking();
                        clearMapRoute();
                        dismissTrackingSheetIfShowing();
                        resetStationarySheetSuppressionForCurrentActivity();
                        ensureTrackingRequirementsAndStart();
                    })
                    .setNegativeButton(R.string.tracking_dialog_new_activity_cancel, null)
                    .show();
            return;
        }

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.tracking_dialog_reset_title)
                .setMessage(R.string.tracking_dialog_reset_message)
                .setPositiveButton(R.string.tracking_dialog_reset_confirm, (_, _) -> {
                    viewModel.resetTracking();
                    clearMapRoute();
                    dismissTrackingSheetIfShowing();
                    resetStationarySheetSuppressionForCurrentActivity();
                })
                .setNegativeButton(R.string.tracking_dialog_reset_cancel, null)
                .show();
    }

    /**
     * Observa el estado del tracking, el guardado y los eventos puntuales para
     * mantener sincronizadas métricas, mapa y feedback visual.
     */
    private void observeViewModel() {
        viewModel.getTrackingState().observe(getViewLifecycleOwner(), this::renderTrackingState);

        viewModel.getSaveState().observe(getViewLifecycleOwner(), uiState -> {
            if (uiState == null || binding == null) return;

            binding.loadingOverlay.setVisibility(uiState.loading ? View.VISIBLE : View.GONE);

            if (!uiState.loading && uiState.data != null) {
                TopSnackbar.success(binding.getRoot(), R.string.tracking_activity_saved_ok);
                clearMapRoute();
                updateMapCamera(null);

                dismissTrackingSheetIfShowing();
                resetStationarySheetSuppressionForCurrentActivity();
            }
        });

        viewModel.getErrorEvent().observe(getViewLifecycleOwner(), event -> {
            if (event == null || binding == null) return;
            String message = event.getContentIfNotHandled();
            if (message != null) {
                TopSnackbar.error(binding.getRoot(), message);
            }
        });

        viewModel.getTrackingAlertEvent().observe(getViewLifecycleOwner(), event -> {
            if (event == null) return;
            TrackingAlert alert = event.getContentIfNotHandled();
            if (alert != null) {
                if (alert.getType() == TrackingAlert.Type.STATIONARY_AUTO_PAUSE
                        && shouldSuppressStationaryAutoPauseSheet()) {
                    pendingTrackingAlertAfterStopDialog = null;
                    dismissTrackingSheetIfShowing();
                    return;
                }

                // Mientras el modal de stop esté abierto, no mostramos paneles
                // inferiores nuevos: el modal manda y concentra la decisión.
                if (isStopDialogShowing()) {
                    pendingTrackingAlertAfterStopDialog = alert;
                    dismissTrackingSheetIfShowing();
                    return;
                }
                showTrackingAlert(alert);
            }
        });
    }

    /**
     * Aplica a la UI una instantánea completa del tracking actualizando alertas,
     * indicadores, botones, métricas y polilínea.
     * 
     * @param state estado consolidado emitido por el servicio de tracking.
     */
    private void renderTrackingState(@NonNull TrackingState state) {
        syncTrackingAlertWithState(state);
        updateActivityIndicator(state.getActivityType());
        updateControlButtons(state);
        updateStatusText(state);
        updateMetrics(state);
        updateMapRoute(state.getRoutePoints(), state.getCurrentLocation());
    }

    /**
     * Sincroniza el panel inferior activo con el estado persistente del tracking.
     * 
     * <p>La alerta de auto-pausa por parada es contextual: cuando la sesión se
     * reactiva sola por movimiento real, el panel deja de tener sentido y debe
     * cerrarse automáticamente. La alerta por velocidad sospechosa, en cambio,
     * se mantiene hasta que el usuario toma una decisión explícita.</p>
     *
     * @param state snapshot actual publicado por el servicio de tracking.
     */
    private void syncTrackingAlertWithState(@NonNull TrackingState state) {
        if (isStopDialogShowing()) {
            dismissTrackingSheetIfShowing();
            return;
        }

        if (trackingAlertBottomSheet == null || activeTrackingAlertType == null) {
            return;
        }

        if (activeTrackingAlertType == TrackingAlert.Type.STATIONARY_AUTO_PAUSE) {
            boolean keepShowing = state.getStatus() == TrackingState.Status.AUTO_PAUSED
                    && state.getPauseReason() == TrackingState.PauseReason.STATIONARY;
            if (!keepShowing) {
                dismissTrackingSheetIfShowing();
            }
        }
    }

    /**
     * Reevalúa si el panel inferior debe reaparecer tras cancelar el modal de stop.
     * 
     * <p>No se remuestra de forma ciega. Solo vuelve si el estado actual sigue en
     * auto-pausa por parado o en pausa por velocidad sospechosa.</p>
     */
    private void maybeRestoreTrackingAlertAfterStopCancel() {
        TrackingState state = viewModel.getTrackingState().getValue();
        TrackingAlert alertToShow = resolveAlertToRestore(state);
        pendingTrackingAlertAfterStopDialog = null;

        if (alertToShow == null) {
            return;
        }

        binding.getRoot().post(() -> {
            if (!isAdded() || binding == null || isStopDialogShowing()) {
                return;
            }
            showTrackingAlert(alertToShow);
        });
    }

    /**
     * Decide qué alerta sigue siendo válida después de cancelar el modal de stop.
     *
     * @param state último estado conocido del tracking o {@code null} si aún no hay snapshot.
     * @return alerta que debe seguir visible o {@code null} cuando ninguna sigue teniendo sentido.
     */
    @Nullable
    private TrackingAlert resolveAlertToRestore(@Nullable TrackingState state) {
        if (state == null) {
            return null;
        }

        if (state.getStatus() == TrackingState.Status.AUTO_PAUSED) {
            if (state.getPauseReason() == TrackingState.PauseReason.STATIONARY) {
                if (shouldSuppressStationaryAutoPauseSheet()) {
                    return null;
                }
                return new TrackingAlert(TrackingAlert.Type.STATIONARY_AUTO_PAUSE);
            }
            if (state.getPauseReason() == TrackingState.PauseReason.SUSPICIOUS_SPEED) {
                return new TrackingAlert(TrackingAlert.Type.SUSPICIOUS_SPEED);
            }
        }

        return pendingTrackingAlertAfterStopDialog;
    }

    /**
     * Marca visualmente si la sesión está clasificada como caminata o carrera.
     * 
     * @param type tipo de actividad actualmente dominante.
     */
    private void updateActivityIndicator(@NonNull TrackingState.ActivityType type) {
        if (type == TrackingState.ActivityType.RUNNING_ACTIVITY) {
            showRunningStatus();
        } else {
            showWalkingStatus();
        }
    }

    /**
     * Ajusta iconos, habilitación y visibilidad de los botones de control según
     * el estado exacto de la sesión.
     * 
     * @param state estado actual del tracking.
     */
    private void updateControlButtons(@NonNull TrackingState state) {
        switch (state.getStatus()) {
            case IDLE, FINISHED -> {
                applyPlayButtonState(R.drawable.play_icon, true, 1f);
                binding.btnStop.setVisibility(View.INVISIBLE);
                binding.btnReset.setVisibility(View.INVISIBLE);
            }
            case RUNNING -> {
                applyPlayButtonState(R.drawable.pause_icon, true, 1f);
                binding.btnStop.setVisibility(View.VISIBLE);
                binding.btnReset.setVisibility(View.INVISIBLE);
            }
            case PAUSED -> {
                applyPlayButtonState(R.drawable.play_icon, true, 1f);
                binding.btnStop.setVisibility(View.VISIBLE);
                binding.btnReset.setVisibility(View.VISIBLE);
            }
            case AUTO_PAUSED -> {
                if (state.getPauseReason() == TrackingState.PauseReason.STATIONARY) {
                    // Mantiene coherencia visual: sigue siendo una sesión activa,
                    // pero temporalmente detenida y pendiente de reactivación
                    // automática por movimiento real. No debe parecer que el
                    // usuario puede reanudarla manualmente pulsando "play".
                    applyPlayButtonState(R.drawable.pause_icon, false, 0.45f);
                } else {
                    // Velocidad sospechosa: la acción correcta vive en el panel.
                    applyPlayButtonState(R.drawable.play_icon, false, 0.45f);
                }
                binding.btnStop.setVisibility(View.VISIBLE);
                binding.btnReset.setVisibility(View.VISIBLE);
            }
        }
    }

    /**
     * Aplica icono, habilitación y opacidad al botón principal.
     *
     * @param iconRes recurso drawable que debe mostrarse.
     * @param enabled indica si el botón debe aceptar pulsaciones.
     * @param alpha opacidad visual usada para comunicar estados bloqueados.
     */
    private void applyPlayButtonState(int iconRes, boolean enabled, float alpha) {
        binding.btnPlay.setIconResource(iconRes);
        binding.btnPlay.setEnabled(enabled);
        binding.btnPlay.setAlpha(alpha);
    }

    /**
     * Actualiza el texto descriptivo y el pill de estado principal a partir del
     * estado y motivo de pausa del tracking.
     * 
     * @param state estado actual del tracking.
     */
    private void updateStatusText(@NonNull TrackingState state) {
        int messageRes = switch (state.getStatus()) {
            case RUNNING -> {
                applyStatusPillStyle(R.drawable.pill_tracking_auto_paused, R.color.greenPrimary);
                yield R.string.tracking_status_running;
            }
            case PAUSED -> {
                applyStatusPillStyle(R.drawable.pill_tracking_auto_paused, R.color.greenPrimary);
                yield R.string.tracking_status_manual_pause;
            }
            case AUTO_PAUSED -> {
                if (state.getPauseReason() == TrackingState.PauseReason.SUSPICIOUS_SPEED) {
                    applyStatusPillStyle(R.drawable.pill_inactive, R.color.textSecondary);
                    yield R.string.tracking_status_suspicious_speed;
                }
                applyStatusPillStyle(R.drawable.pill_tracking_auto_paused, R.color.greenPrimary);
                yield R.string.tracking_status_auto_pause;
            }
            case FINISHED -> {
                applyStatusPillStyle(R.drawable.pill_inactive, R.color.textSecondary);
                yield R.string.tracking_status_finished;
            }
            case IDLE -> {
                applyStatusPillStyle(R.drawable.pill_inactive, R.color.textSecondary);
                yield R.string.tracking_status_idle;
            }
        };
        binding.tvTrackingStatus.setText(messageRes);
    }

    /**
     * Aplica el estilo visual del chip de estado.
     *
     * @param backgroundRes drawable de fondo a usar en el chip.
     * @param textColorRes color del texto del estado.
     */
    private void applyStatusPillStyle(int backgroundRes, int textColorRes) {
        binding.tvTrackingStatus.setBackgroundResource(backgroundRes);
        binding.tvTrackingStatus.setTextColor(
                ContextCompat.getColor(requireContext(), textColorRes)
        );
    }

    /**
     * Vuelca en la cabecera todas las métricas derivadas del tracking usando el
     * modo de ritmo configurado por el usuario.
     * 
     * @param state estado actual del tracking con métricas agregadas.
     */
    private void updateMetrics(@NonNull TrackingState state) {
        binding.tvElapsedTime.setText(formatElapsed(state.getElapsedSeconds()));
        binding.tvMovingTime.setText(formatElapsed(state.getMovingSeconds()));
        binding.tvStoppedTime.setText(formatElapsed(state.getStoppedSeconds()));
        binding.tvDistance.setText(formatDistance(state.getDistanceMeters()));
        binding.tvCalories.setText(getString(R.string.tracking_calories_format, state.getCalories()));
        binding.tvSteps.setText(
                state.isStepSensorAvailable()
                        ? getString(R.string.tracking_steps_format, state.getSteps())
                        : getString(R.string.tracking_steps_unavailable)
        );
        binding.tvPace.setText(
                state.getPace() != null ? state.getPace() : getString(R.string.tracking_default_pace)
        );
        String preferredAveragePace = PaceDisplayUtils.getPreferredAveragePaceText(requireContext(), state);
        binding.tvAveragePace.setText(
                preferredAveragePace != null
                        ? preferredAveragePace
                        : getString(R.string.tracking_default_pace)
        );
        String maxPaceText = state.getMaxPace() != null
                ? state.getMaxPace()
                : getString(R.string.tracking_default_pace);
        binding.tvMaxPaceSummary.setText(
                getString(R.string.tracking_max_pace_inline_format, maxPaceText)
        );
        binding.tvAutoPauseCount.setText(
                getString(R.string.tracking_auto_pause_counter_format, state.getAutoPauseCount())
        );
        binding.tvManualPauseCount.setText(
                getString(R.string.tracking_manual_pause_counter_format, state.getManualPauseCount())
        );
        binding.tvSpeedAlertsCount.setText(
                getString(
                        R.string.tracking_speed_alert_counter_format,
                        state.getSuspiciousSpeedEventCount()
                )
        );
    }

    /**
     * Redibuja la polilínea del recorrido y actualiza el punto final mostrado en el mapa.
     * 
     * @param points lista de puntos aceptados de la ruta.
     * @param currentLocation ubicación instantánea actual, o {@code null}.
     */
    private void updateMapRoute(@NonNull List<LatLng> points, @Nullable LatLng currentLocation) {
        if (googleMap == null) return;

        if (points.isEmpty()) {
            clearMapRoute();
        } else if (routePolyline == null) {
            PolylineOptions options = new PolylineOptions()
                    .color(ContextCompat.getColor(requireContext(), R.color.greenPrimary))
                    .width(getResources().getDimension(R.dimen.tracking_polyline_width))
                    .geodesic(true)
                    .addAll(points);
            routePolyline = googleMap.addPolyline(options);
        } else {
            routePolyline.setPoints(points);
        }

        LatLng cameraTarget = currentLocation;
        if (cameraTarget == null && !points.isEmpty()) {
            cameraTarget = points.listIterator(points.size()).previous();
        }
        updateMapCamera(cameraTarget);
    }

    /**
     * Mueve la cámara del mapa al objetivo solicitado o a la posición por defecto
     * cuando no existe un punto válido.
     * 
     * @param target destino de la cámara, o {@code null} para usar el fallback.
     */
    private void updateMapCamera(@Nullable LatLng target) {
        if (googleMap == null) {
            return;
        }

        if (target == null) {
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(DEFAULT_LOCATION, DEFAULT_ZOOM));
            return;
        }

        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(target, USER_ZOOM));
    }

    /**
     * Elimina del mapa la polilínea dibujada y limpia la referencia retenida.
     */
    private void clearMapRoute() {
        if (routePolyline != null) {
            routePolyline.remove();
            routePolyline = null;
        }
    }

    /**
     * Abre o actualiza el bottom sheet contextual correspondiente a la alerta
     * puntual emitida por el servicio.
     * 
     * @param alert alerta concreta que debe representarse en pantalla.
     */
    private void showTrackingAlert(@NonNull TrackingAlert alert) {
        if (isStopDialogShowing()) {
            return;
        }

        dismissTrackingSheetIfShowing();

        TrackingAlertBottomSheet sheet;
        if (alert.getType() == TrackingAlert.Type.STATIONARY_AUTO_PAUSE) {
            sheet = TrackingAlertBottomSheet.newInstance(
                    alert.getType(),
                    getString(R.string.tracking_stationary_sheet_title),
                    getString(R.string.tracking_stationary_sheet_message),
                    getString(R.string.tracking_stationary_sheet_primary),
                    getString(R.string.tracking_stationary_sheet_secondary),
                    getString(R.string.tracking_stationary_sheet_tertiary),
                    true
            );
        } else {
            sheet = TrackingAlertBottomSheet.newInstance(
                    alert.getType(),
                    getString(R.string.tracking_vehicle_speed_title),
                    getString(R.string.tracking_warning_vehicle_speed),
                    getString(R.string.tracking_vehicle_continue),
                    getString(R.string.tracking_dialog_stop_confirm),
                    null,
                    false
            );
        }

        sheet.setListener(this);
        trackingAlertBottomSheet = sheet;
        activeTrackingAlertType = alert.getType();
        sheet.show(getChildFragmentManager(), TrackingAlertBottomSheet.TAG);
    }

    /**
     * Decide si debe omitirse la alerta de auto-pausa por inactividad para la
     * actividad actual.
     *
     * @return {@code true} si se suprimió para esta actividad o el ajuste global está desactivado.
     */
    private boolean shouldSuppressStationaryAutoPauseSheet() {
        return suppressStationarySheetForCurrentActivity
                || !SecureSessionManager.getInstance(requireContext())
                .shouldShowAutoPauseAlertsByDefault();
    }

    /**
     * Restablece el flag que evita remostrar la auto-pausa por parada al iniciar
     * una actividad nueva o al descartar la anterior.
     */
    private void resetStationarySheetSuppressionForCurrentActivity() {
        suppressStationarySheetForCurrentActivity = false;
    }

    /**
     * Cierra el bottom sheet contextual de tracking si sigue visible y limpia
     * las referencias locales asociadas.
     */
    private void dismissTrackingSheetIfShowing() {
        if (trackingAlertBottomSheet != null) {
            trackingAlertBottomSheet.dismissAllowingStateLoss();
            trackingAlertBottomSheet = null;
        }
        activeTrackingAlertType = null;
    }

    /**
     * Devuelve si el diálogo de stop está actualmente visible.
     * Mientras esto sea true, el modal tiene prioridad sobre cualquier panel inferior.
     *
     * @return {@code true} cuando el diálogo de parada sigue abierto en pantalla.
     */
    private boolean isStopDialogShowing() {
        return stopDialog != null && stopDialog.isShowing();
    }

    /**
     * Gestiona la acción principal elegida en el bottom sheet de tracking.
     *
     * <p>Solo la alerta de velocidad sospechosa exige una confirmación explícita del usuario;
     * la auto-pausa por parada se levanta automáticamente al detectar movimiento real.</p>
     *
     * @param type tipo de alerta desde la que llega la acción.
     */
    @Override
    public void onPrimaryAction(@NonNull TrackingAlert.Type type) {
        if (type == TrackingAlert.Type.SUSPICIOUS_SPEED) {
            // Solo la velocidad sospechosa exige reanudación/confirmación explícita.
            ensureTrackingRequirementsAndStart();
        }
        // La auto-pausa por parada vuelve sola al detectar movimiento real.
    }

    /**
     * Gestiona la acción secundaria del bottom sheet de tracking.
     *
     * <p>En auto-pausa por parada se reusa el mismo flujo de parada visible en pantalla; en
     * velocidad sospechosa se interpreta como una orden directa de guardar la actividad.</p>
     *
     * @param type tipo de alerta desde la que llega la acción.
     */
    @Override
    public void onSecondaryAction(@NonNull TrackingAlert.Type type) {
        if (type == TrackingAlert.Type.STATIONARY_AUTO_PAUSE) {
            // En auto-pausa por parada, "Finalizar" debe seguir exactamente
            // el mismo flujo que el botón principal de stop.
            onStopClicked();
            return;
        }

        // En velocidad sospechosa mantenemos la acción directa de guardado,
        // ya que el botón secundario se presenta explícitamente como "Guardar".
        viewModel.stopAndSave();
    }

    /**
     * Gestiona la acción terciaria del bottom sheet, usada aquí para suprimir
     * la alerta de auto-pausa por parada durante la actividad actual.
     *
     * @param type tipo de alerta desde la que llega la acción.
     */
    @Override
    public void onTertiaryAction(@NonNull TrackingAlert.Type type) {
        if (type == TrackingAlert.Type.STATIONARY_AUTO_PAUSE) {
            suppressStationarySheetForCurrentActivity = true;
        }
    }

    /**
     * Revisa permisos, ajustes del dispositivo y requisitos bloqueantes antes
     * de arrancar o reanudar el tracking.
     *
     * <p>El orden importa: primero requisitos definitivamente bloqueados, después permisos
     * solicitables y por último ajustes globales como la ubicación del dispositivo.</p>
     */
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
        maybeOfferBatteryOptimizationExemption();
    }

    /**
     * Ofrece, una única vez, la exención de optimización de batería justo después
     * de iniciar la primera actividad.
     *
     * <p>Sin la exención, muchos fabricantes matan el proceso —incluido el foreground
     * service— al quitar la app de recientes o tras un rato con la pantalla apagada,
     * lo que interrumpe el tracking y deja la actividad sin métricas. El diálogo no
     * bloquea el inicio: la actividad ya está en marcha cuando se muestra.</p>
     */
    private void maybeOfferBatteryOptimizationExemption() {
        if (!isAdded()) {
            return;
        }

        if (TrackingRequirementsManager.isIgnoringBatteryOptimizations(requireContext())
                || AppSettingsManager.wasTrackingBatteryExemptionRequested(requireContext())) {
            return;
        }

        AppSettingsManager.setTrackingBatteryExemptionRequested(requireContext(), true);

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.tracking_battery_exemption_title)
                .setMessage(R.string.tracking_battery_exemption_message)
                .setPositiveButton(R.string.tracking_battery_exemption_allow,
                        (_, _) -> launchBatteryOptimizationExemption())
                .setNegativeButton(R.string.tracking_battery_exemption_later, null)
                .show();
    }

    /**
     * Lanza el diálogo de sistema de exención de batería con fallback a la
     * pantalla general de ajustes cuando la ROM no expone el intent directo.
     */
    private void launchBatteryOptimizationExemption() {
        if (!isAdded()) {
            return;
        }

        try {
            startActivity(TrackingRequirementsManager
                    .buildBatteryOptimizationExemptionIntent(requireContext()));
        } catch (Exception e) {
            try {
                startActivity(TrackingRequirementsManager
                        .buildBatteryOptimizationSettingsFallbackIntent());
            } catch (Exception ignored) {
                // Si ni siquiera existe la pantalla de ajustes, no interrumpimos la actividad.
            }
        }
    }

    /**
     * Reacciona al resultado del launcher de permisos, resincroniza el mapa y
     * decide si ya puede iniciarse el tracking o todavía faltan requisitos.
     */
    private void onPermissionsRequestCompleted() {
        if (!isAdded()) return;

        if (TrackingRequirementsManager.hasLocationPermission(requireContext())) {
            enableMapMyLocation();
            moveCameraToCurrentLocation();
        }

        // Sincronizamos el espejo local para que futuras vueltas a Inicio no interpreten
        // este mismo permiso como una concesión "nueva" y vuelvan a recentrar el mapa.
        lastKnownLocationPermissionGranted =
                TrackingRequirementsManager.hasLocationPermission(requireContext());

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

    /**
     * Muestra un diálogo informativo con requisitos pendientes que el usuario
     * todavía puede activar desde la propia app o desde ajustes.
     * 
     * @param requirements requisitos que siguen faltando.
     */
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

    /**
     * Muestra un diálogo para requisitos bloqueados cuyo siguiente paso típico
     * es abrir ajustes del sistema.
     * 
     * @param requirements requisitos actualmente bloqueados.
     */
    private void showBlockedRequirementsDialog(
            @NonNull List<TrackingRequirementsManager.Requirement> requirements) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.tracking_requirements_blocked_title)
                .setMessage(getString(
                        R.string.tracking_requirements_blocked_message,
                        buildRequirementsBulletList(requirements)))
                .setPositiveButton(R.string.common_accept, null)
                .setNegativeButton(R.string.tracking_requirements_go_settings,
                        (_, _) -> openBestSettingsForRequirements(requirements))
                .show();
    }

    /**
     * Informa al usuario de que el GPS del dispositivo está desactivado y ofrece
     * acceso directo a la pantalla de ajustes correspondiente.
     */
    private void showDeviceLocationDisabledDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.tracking_device_location_disabled_title)
                .setMessage(R.string.tracking_device_location_disabled_message)
                .setPositiveButton(R.string.common_accept, null)
                .setNegativeButton(R.string.tracking_requirements_go_settings,
                        (_, _) -> openLocationSettings())
                .show();
    }

    /**
     * Convierte la lista de requisitos pendientes a un texto con viñetas apto
     * para diálogos de explicación.
     *
     * @param requirements requisitos que deben representarse.
     * @return cadena multilínea lista para insertarse en el mensaje del diálogo.
     */
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

    /**
     * Resuelve la etiqueta visible de un requisito de tracking.
     *
     * @param requirement requisito cuya etiqueta debe mostrarse.
     * @return texto localizado que describe ese requisito.
     */
    @NonNull
    private String getRequirementLabel(@NonNull TrackingRequirementsManager.Requirement requirement) {
        return switch (requirement) {
            case LOCATION -> getString(R.string.tracking_requirement_location_name);
            case ACTIVITY_RECOGNITION -> getString(R.string.tracking_requirement_activity_name);
            case NOTIFICATIONS -> getString(R.string.tracking_requirement_notifications_name);
            case GPS -> getString(R.string.tracking_requirement_device_location_name);
        };
    }

    /**
     * Abre la pantalla de ajustes más útil según el conjunto de requisitos que
     * permanecen bloqueados.
     * 
     * @param requirements requisitos pendientes o bloqueados.
     */
    @SuppressWarnings("SequencedCollectionMethodCanBeUsed")
    private void openBestSettingsForRequirements(
            @NonNull List<TrackingRequirementsManager.Requirement> requirements) {
        if (requirements.size() == 1
                && requirements.iterator().next() == TrackingRequirementsManager.Requirement.NOTIFICATIONS) {
            openNotificationSettings();
            return;
        }
        openAppSettings();
    }

    /**
     * Lanza la pantalla de información de la app en ajustes del sistema.
     */
    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", requireContext().getPackageName(), null));
        startActivity(intent);
    }

    /**
     * Lanza la pantalla de ajustes de notificaciones de la aplicación.
     */
    private void openNotificationSettings() {
        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().getPackageName());
        startActivity(intent);
    }

    /**
     * Lanza la pantalla de ajustes globales de ubicación del dispositivo.
     */
    private void openLocationSettings() {
        startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
    }

    /**
     * Aplica el estilo visual activo al indicador de caminata y desactiva el de carrera.
     */
    private void showWalkingStatus() {
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

    /**
     * Aplica el estilo visual activo al indicador de carrera y desactiva el de caminata.
     */
    private void showRunningStatus() {
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

    /**
     * Formatea una duración en segundos al patrón mm:ss o h:mm:ss según proceda.
     *
     * @param seconds duración a representar.
     * @return texto formateado para la UI de tracking.
     */
    @NonNull
    private String formatElapsed(long seconds) {
        long h = TimeUnit.SECONDS.toHours(seconds);
        long m = TimeUnit.SECONDS.toMinutes(seconds) % 60L;
        long s = seconds % 60L;
        if (h > 0L) {
            return String.format(Locale.US, "%d:%02d:%02d", h, m, s);
        }
        return String.format(Locale.US, "%02d:%02d", m, s);
    }

    /**
     * Representa la distancia en metros o kilómetros usando los recursos de texto de tracking.
     *
     * @param meters distancia total acumulada.
     * @return texto localizado con la unidad adecuada.
     */
    @NonNull
    private String formatDistance(int meters) {
        if (meters >= 1000) {
            return getString(R.string.tracking_distance_km_format, meters / 1000.0f);
        }
        return getString(R.string.tracking_distance_m_format, meters);
    }
}
