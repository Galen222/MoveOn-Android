package com.proyecto.moveon.ui.profile;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.signature.ObjectKey;
import com.proyecto.moveon.R;
import com.proyecto.moveon.core.api.ApiError;
import com.proyecto.moveon.core.api.ApiErrorType;
import com.proyecto.moveon.core.i18n.AppLanguageManager;
import com.proyecto.moveon.core.i18n.ProfileValueLocalizer;
import com.proyecto.moveon.core.theme.ThemeManager;
import com.proyecto.moveon.core.tracking.TrackingRequirementsManager;
import com.proyecto.moveon.core.validation.AppInputValidator;
import com.proyecto.moveon.data.profile.PerfilRepository;
import com.proyecto.moveon.data.profile.sync.ProfilePatchPayload;
import com.proyecto.moveon.data.session.SecureSessionManager;
import com.proyecto.moveon.databinding.FragmentProfileBinding;
import com.proyecto.moveon.domain.profile.PerfilUsuario;
import com.proyecto.moveon.ui.auth.LoginActivity;
import com.proyecto.moveon.ui.common.TopSnackbar;
import com.proyecto.moveon.ui.ranking.RankingFragment;
import com.proyecto.moveon.utils.NavigationUtils;
import com.proyecto.moveon.utils.StringUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.FormatStyle;
import java.util.Locale;

/**
 * Pantalla de perfil del usuario.
 *
 * <p>Este archivo sustituye el placeholder anterior de “Compartir rutas” por el
 * flujo real que abre un bottom sheet con todas las rutas del usuario.</p>
 */
public class ProfileFragment extends Fragment {

    private FragmentProfileBinding binding;
    private ProfileViewModel viewModel;

    @Nullable private PerfilUsuario perfilActual;
    @Nullable private String transientPhotoPreviewPath;

    private ProfileDialogHelper dialogHelper;
    private ProfileTrackingHelper trackingHelper;
    private SecureSessionManager secureSessionManager;

    @Nullable private DeleteAccountBottomSheet deleteAccountSheet;

    // ── ActivityResult launchers ──────────────────────────────────────────────
    // Se registran en onCreate() según el ciclo de vida de Fragment.
    //
    // pickImageLauncher usa GetContent: el contrato correcto para seleccionar
    // imágenes. Recibe el MIME type como String y devuelve la Uri seleccionada,
    // evitando completamente StartActivityForResult.
    private ActivityResultLauncher<String> pickImageLauncher;
    private ActivityResultLauncher<String[]> trackingRequirementPermissionLauncher;

    public ProfileFragment() {}

    // ── Ciclo de vida ─────────────────────────────────────────────────────────

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri == null) return;
                    File file = uriToFile(uri);
                    if (file == null) {
                        showErrorFeedback(getString(R.string.profile_error_photo_read));
                        return;
                    }
                    showTransientPhotoPreview(file);
                    viewModel.uploadPhoto(file);
                });

        trackingRequirementPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    if (trackingHelper != null) trackingHelper.updateTrackingRequirementsUi();
                });
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        viewModel = new ViewModelProvider(this).get(ProfileViewModel.class);
        secureSessionManager = SecureSessionManager.getInstance(requireContext());

        dialogHelper = new ProfileDialogHelper(
                this, viewModel, () -> perfilActual, this::showErrorFeedback);
        trackingHelper = new ProfileTrackingHelper(
                this, binding, trackingRequirementPermissionLauncher);

        bindLocalData();
        setupListeners();
        observeViewModel();
        syncThemeToggleWithSavedMode();
        syncLanguageSelectionText();
        viewModel.loadPerfil();

        return binding.getRoot();
    }

    @Override
    public void onStart() {
        super.onStart();
        trackingHelper.registerDeviceLocationReceiver();
    }

    @Override
    public void onStop() {
        trackingHelper.unregisterDeviceLocationReceiver();
        super.onStop();
    }

    @Override
    public void onResume() {
        super.onResume();
        syncThemeToggleWithSavedMode();
        syncLanguageSelectionText();
        syncAutoPauseAlertsToggle();
        trackingHelper.updateTrackingRequirementsUi();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        dialogHelper = null;
        trackingHelper = null;
        secureSessionManager = null;
        deleteAccountSheet = null;
    }

    // ── Datos locales (sin esperar red) ───────────────────────────────────────

    private void bindLocalData() {
        String username = viewModel.getUsername();
        if (!StringUtils.hasText(username)) {
            username = getString(R.string.profile_default_username);
        }
        binding.tvUserName.setText(username);
        syncLanguageSelectionText();
        syncAutoPauseAlertsToggle();
        trackingHelper.updateTrackingRequirementsUi();
    }

    private void syncAutoPauseAlertsToggle() {
        if (binding == null || secureSessionManager == null) return;

        binding.switchAutoPauseAlerts.setOnCheckedChangeListener(null);
        binding.switchAutoPauseAlerts.setChecked(
                secureSessionManager.shouldShowAutoPauseAlertsByDefault()
        );
        binding.switchAutoPauseAlerts.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!buttonView.isPressed() || secureSessionManager == null) return;
            secureSessionManager.setShowAutoPauseAlertsByDefault(isChecked);
        });
    }

    private void bindPerfilData(@NonNull PerfilUsuario perfil) {
        if (binding == null) return;
        perfilActual = perfil;

        final String notIndicated = getString(R.string.profile_not_indicated);

        binding.tvUserName.setText(perfil.nombreUsuario);
        binding.tvUserEmail.setText(perfil.email);
        binding.tvUserPoints.setText(
                getString(R.string.profile_puntos_formato, perfil.totalPuntos));

        binding.tvFullName.setText(
                StringUtils.hasText(perfil.nombreReal) ? perfil.nombreReal : notIndicated);
        binding.tvEmail.setText(perfil.email);
        binding.tvBirthdate.setText(
                StringUtils.hasText(perfil.fechaNacimiento)
                        ? formatFecha(perfil.fechaNacimiento)
                        : notIndicated);
        binding.tvProvincia.setText(
                ProfileValueLocalizer.displayProvincia(requireContext(), perfil.provincia));
        binding.tvGenero.setText(
                ProfileValueLocalizer.displayGenero(requireContext(), perfil.genero));
        binding.tvAltura.setText(
                perfil.altura != null
                        ? getString(R.string.profile_altura_formato, perfil.altura)
                        : notIndicated);
        binding.tvPeso.setText(
                perfil.peso != null
                        ? getString(R.string.profile_peso_formato, perfil.peso)
                        : notIndicated);

        binding.switchPublicProfile.setOnCheckedChangeListener(null);
        binding.switchPublicProfile.setChecked(perfil.perfilVisible);
        binding.switchPublicProfile.setOnCheckedChangeListener((btn, isChecked) -> {
            if (!btn.isPressed()) return;
            viewModel.updatePerfil(new ProfilePatchPayload()
                    .perfilVisible(isChecked)
                    .toJson());
        });

        final Object photoSource;
        if (StringUtils.hasText(perfil.pendingLocalPhotoPath)
                && new File(perfil.pendingLocalPhotoPath).exists()) {
            transientPhotoPreviewPath = null;
            photoSource = new File(perfil.pendingLocalPhotoPath);
        } else if (StringUtils.hasText(transientPhotoPreviewPath)
                && new File(transientPhotoPreviewPath).exists()) {
            photoSource = new File(transientPhotoPreviewPath);
        } else if (StringUtils.hasText(perfil.localPhotoPath)
                && new File(perfil.localPhotoPath).exists()) {
            transientPhotoPreviewPath = null;
            photoSource = new File(perfil.localPhotoPath);
        } else if (StringUtils.hasText(perfil.fotoPerfil)) {
            transientPhotoPreviewPath = null;
            photoSource = appendPhotoVersion(perfil.fotoPerfil, perfil.fotoVersion);
        } else {
            photoSource = R.drawable.default_profile;
        }

        loadProfilePhoto(photoSource);
    }

    private void loadProfilePhoto(@NonNull Object photoSource) {
        if (binding == null) return;

        com.bumptech.glide.RequestBuilder<?> request = Glide.with(this)
                .load(photoSource)
                .placeholder(R.drawable.default_profile)
                .error(R.drawable.default_profile)
                .circleCrop();

        if (photoSource instanceof File) {
            File file = (File) photoSource;
            request = request
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(true)
                    .signature(new ObjectKey(
                            file.getAbsolutePath() + ":" + file.lastModified()));
        } else {
            request = request.diskCacheStrategy(DiskCacheStrategy.ALL);
        }

        request.into(binding.ivProfilePicture);
    }

    private void showTransientPhotoPreview(@NonNull File file) {
        transientPhotoPreviewPath = file.getAbsolutePath();
        loadProfilePhoto(file);
    }

    // ── Listeners ─────────────────────────────────────────────────────────────

    private void setupListeners() {
        binding.toggleThemeMode.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            final String newMode;
            if (checkedId == R.id.btn_theme_light)       newMode = ThemeManager.MODE_LIGHT;
            else if (checkedId == R.id.btn_theme_dark)   newMode = ThemeManager.MODE_DARK;
            else if (checkedId == R.id.btn_theme_system) newMode = ThemeManager.MODE_SYSTEM;
            else return;

            if (newMode.equals(ThemeManager.getSavedMode(requireContext()))) return;
            dialogHelper.startUiRecreationWithSplash(() -> {
                ThemeManager.saveAndApply(requireContext(), newMode);
                requireActivity().recreate();
            });
        });

        binding.itemRanking.setOnClickListener(v -> {
            String provincia = perfilActual != null ? perfilActual.provincia : null;
            RankingFragment.newInstance(provincia)
                    .show(getChildFragmentManager(), RankingFragment.TAG);
        });

        // Sustituye el placeholder anterior por el bottom sheet real de compartir rutas.
        binding.itemShareRoutes.setOnClickListener(v -> showShareRoutesBottomSheet());

        binding.tvTrackingLocationAction.setOnClickListener(v ->
                trackingHelper.handleTrackingRequirementAction(
                        TrackingRequirementsManager.Requirement.LOCATION));
        binding.tvTrackingActivityAction.setOnClickListener(v ->
                trackingHelper.handleTrackingRequirementAction(
                        TrackingRequirementsManager.Requirement.ACTIVITY_RECOGNITION));
        binding.tvTrackingNotificationsAction.setOnClickListener(v ->
                trackingHelper.handleTrackingRequirementAction(
                        TrackingRequirementsManager.Requirement.NOTIFICATIONS));
        binding.tvTrackingDeviceLocationAction.setOnClickListener(v ->
                trackingHelper.handleTrackingRequirementAction(
                        TrackingRequirementsManager.Requirement.GPS));

        // GetContent lanza el selector del sistema con el MIME type indicado
        binding.fabChangePhoto.setOnClickListener(v -> pickImageLauncher.launch("image/*"));

        binding.btnLogout.setOnClickListener(v -> viewModel.logout());
        binding.tvDeleteAccount.setOnClickListener(v -> showDeleteAccountBottomSheet());
        binding.itemLanguage.setOnClickListener(v -> dialogHelper.showLanguageDialog());

        binding.itemFullName.setOnClickListener(v -> dialogHelper.showEditTextDialog(
                getString(R.string.profile_label_fullname),
                perfilActual != null ? perfilActual.nombreReal : null,
                android.text.InputType.TYPE_CLASS_TEXT
                        | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS,
                false,
                value -> {
                    AppInputValidator.ValidationResult<String> validation =
                            AppInputValidator.validateRealName(requireContext(), value, false);
                    if (!validation.isValid()) {
                        showErrorFeedback(validationError(validation));
                        return false;
                    }
                    String normalized = validation.getValue();
                    String current = perfilActual != null ? perfilActual.nombreReal : null;
                    if (AppInputValidator.sameText(current, normalized)) return true;
                    viewModel.updatePerfil(new ProfilePatchPayload()
                            .nombreReal(StringUtils.hasText(normalized) ? normalized : null)
                            .toJson());
                    return true;
                }));

        binding.itemEmail.setOnClickListener(v -> dialogHelper.showEditTextDialog(
                getString(R.string.profile_label_email),
                perfilActual != null ? perfilActual.email : null,
                android.text.InputType.TYPE_CLASS_TEXT
                        | android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                true,
                value -> {
                    AppInputValidator.ValidationResult<String> validation =
                            AppInputValidator.validateEmail(requireContext(), value, true);
                    if (!validation.isValid()) {
                        showErrorFeedback(validationError(validation));
                        return false;
                    }
                    String normalized = validation.getValue();
                    String current = perfilActual != null ? perfilActual.email : null;
                    if (AppInputValidator.sameText(current, normalized)) return true;
                    viewModel.updatePerfil(new ProfilePatchPayload()
                            .email(normalized)
                            .toJson());
                    return true;
                }));

        binding.itemBirthdate.setOnClickListener(v -> dialogHelper.showBirthDatePicker());
        binding.itemProvincia.setOnClickListener(v -> dialogHelper.showEditProvinciaDialog());
        binding.itemGenero.setOnClickListener(v -> dialogHelper.showGeneroDialog());
        binding.itemAltura.setOnClickListener(v -> dialogHelper.showAlturaPickerDialog());
        binding.itemPeso.setOnClickListener(v -> dialogHelper.showPesoPickerDialog());
    }

    /**
     * Abre el bottom sheet de compartir rutas.
     */
    private void showShareRoutesBottomSheet() {
        if (!isAdded()) return;
        if (getChildFragmentManager().isStateSaved()) return;

        ShareRoutesBottomSheet.newInstance()
                .show(getChildFragmentManager(), ShareRoutesBottomSheet.TAG);
    }

    // ── Observadores ──────────────────────────────────────────────────────────

    private void observeViewModel() {
        viewModel.getPerfilState().observe(getViewLifecycleOwner(), state -> {
            if (state == null || binding == null) return;
            showOverlay(state.loading);
            if (state.data != null) {
                bindPerfilData(state.data);
            } else if (state.error != null) {
                showApiError(state.error, viewModel::loadPerfil);
            }
        });

        viewModel.getUpdateState().observe(getViewLifecycleOwner(), state -> {
            if (state == null || binding == null) return;
            if (state.data != null) {
                if (PerfilRepository.UpdateResult.STATUS_SYNCED.equals(state.data)) {
                    showSuccessFeedback(getString(R.string.profile_update_ok));
                } else if (PerfilRepository.UpdateResult.STATUS_QUEUED.equals(state.data)) {
                    showWarningFeedback(getString(R.string.profile_update_queued));
                }
                viewModel.resetUpdateState();
            } else if (state.error != null) {
                if (perfilActual != null) bindPerfilData(perfilActual);
                showApiError(state.error, viewModel::retryLastUpdate);
                viewModel.resetUpdateState();
            }
        });

        viewModel.getPhotoState().observe(getViewLifecycleOwner(), state -> {
            if (state == null || binding == null) return;
            if (state.data != null) {
                if (PerfilRepository.UpdateResult.STATUS_SYNCED.equals(state.data)) {
                    transientPhotoPreviewPath = null;
                    showSuccessFeedback(getString(R.string.profile_photo_ok));
                } else if (PerfilRepository.UpdateResult.STATUS_QUEUED.equals(state.data)) {
                    showWarningFeedback(getString(R.string.profile_photo_queued));
                }
                viewModel.resetPhotoState();
            } else if (state.error != null) {
                transientPhotoPreviewPath = null;
                if (perfilActual != null) bindPerfilData(perfilActual);
                showApiError(state.error, viewModel::retryLastPhotoUpload);
                viewModel.resetPhotoState();
            }
        });

        viewModel.getLogoutState().observe(getViewLifecycleOwner(), state -> {
            if (state == null || binding == null) return;
            if (state.loading) {
                binding.btnLogout.setEnabled(false);
                binding.btnLogout.setText(getString(R.string.profile_btn_logging_out));
                return;
            }
            if (state.error != null) {
                Toast.makeText(requireContext(),
                        getString(R.string.profile_error_logout_server),
                        Toast.LENGTH_LONG).show();
            }
            goToLogin();
        });

        viewModel.getDeleteAccountState().observe(getViewLifecycleOwner(), state -> {
            if (state == null || binding == null) return;
            if (state.loading) {
                if (deleteAccountSheet != null) deleteAccountSheet.setLoading(true);
                showOverlay(true);
                return;
            }
            showOverlay(false);
            if (state.error != null) {
                if (deleteAccountSheet != null) {
                    deleteAccountSheet.setError(
                            getString(R.string.profile_error_delete_account));
                } else {
                    showErrorFeedback(getString(R.string.profile_error_delete_account));
                }
                return;
            }
            if (deleteAccountSheet != null) {
                deleteAccountSheet.dismiss();
                deleteAccountSheet = null;
            }
            goToLogin();
        });
    }

    // ── Eliminar cuenta ───────────────────────────────────────────────────────

    private void showDeleteAccountBottomSheet() {
        deleteAccountSheet = DeleteAccountBottomSheet.newInstance();
        deleteAccountSheet.setOnDeleteConfirmedListener(() -> viewModel.deleteAccount());
        deleteAccountSheet.show(getChildFragmentManager(), DeleteAccountBottomSheet.TAG);
    }

    // ── Sincronización de UI con ajustes guardados ────────────────────────────

    private void syncThemeToggleWithSavedMode() {
        if (binding == null) return;
        final String mode = ThemeManager.getSavedMode(requireContext());
        final int targetId;
        if (ThemeManager.MODE_LIGHT.equals(mode))       targetId = R.id.btn_theme_light;
        else if (ThemeManager.MODE_DARK.equals(mode))   targetId = R.id.btn_theme_dark;
        else                                            targetId = R.id.btn_theme_system;
        if (binding.toggleThemeMode.getCheckedButtonId() != targetId) {
            binding.toggleThemeMode.check(targetId);
        }
    }

    private void syncLanguageSelectionText() {
        if (binding == null || dialogHelper == null) return;
        final String mode = viewModel.getAppLanguageMode();
        final int index = dialogHelper.findLanguageModeIndex(mode);
        final String[] labels = getResources().getStringArray(R.array.app_language_labels);
        if (index >= 0 && index < labels.length) {
            binding.tvLanguageValue.setText(labels[index]);
        }
    }

    // ── Feedback helpers ──────────────────────────────────────────────────────

    private void showSuccessFeedback(@NonNull CharSequence message) {
        if (binding == null) return;
        TopSnackbar.success(binding.getRoot(), message);
    }

    private void showWarningFeedback(@NonNull CharSequence message) {
        if (binding == null) return;
        TopSnackbar.warning(binding.getRoot(), message);
    }

    private void showErrorFeedback(@NonNull CharSequence message) {
        if (binding == null) return;
        TopSnackbar.error(binding.getRoot(), message);
    }

    private void showApiError(@NonNull ApiError error, @Nullable Runnable retryAction) {
        if (binding == null) return;
        if (retryAction != null && isRetryable(error)) {
            TopSnackbar.error(binding.getRoot(), error.getMessage(),
                    getString(R.string.stats_btn_retry), retryAction);
        } else {
            TopSnackbar.error(binding.getRoot(), error.getMessage());
        }
    }

    private boolean isRetryable(@Nullable ApiError error) {
        if (error == null) return false;
        ApiErrorType type = error.getType();
        return type == ApiErrorType.NETWORK
                || type == ApiErrorType.TIMEOUT
                || type == ApiErrorType.SERVER
                || type == ApiErrorType.RATE_LIMIT
                || type == ApiErrorType.CANCELED;
    }

    private void showOverlay(boolean show) {
        if (binding == null) return;
        binding.loadingOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void goToLogin() {
        if (!isAdded()) return;
        NavigationUtils.goToActivityAndClearTask(requireActivity(), LoginActivity.class);
    }

    // ── Utilidades ────────────────────────────────────────────────────────────

    @NonNull
    private String formatFecha(@NonNull String fecha) {
        try {
            Locale locale = AppLanguageManager.getActiveLocale(requireContext());
            return LocalDate.parse(fecha)
                    .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                            .withLocale(locale));
        } catch (DateTimeParseException ignored) {
            return fecha;
        }
    }

    @NonNull
    private String appendPhotoVersion(@NonNull String baseUrl, int version) {
        String separator = baseUrl.contains("?") ? "&" : "?";
        return baseUrl + separator + "v=" + version;
    }

    @NonNull
    private String validationError(@NonNull AppInputValidator.ValidationResult<?> result) {
        String msg = result.getErrorMessage();
        return msg != null ? msg : getString(R.string.vm_error_generico);
    }

    @Nullable
    private File uriToFile(@NonNull Uri uri) {
        try {
            InputStream inputStream =
                    requireContext().getContentResolver().openInputStream(uri);
            if (inputStream == null) return null;
            File tempFile = File.createTempFile(
                    "photo_", ".jpg", requireContext().getCacheDir());
            try (FileOutputStream out = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }
            inputStream.close();
            return tempFile;
        } catch (IOException e) {
            return null;
        }
    }
}
