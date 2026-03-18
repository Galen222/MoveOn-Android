package com.proyecto.moveon.ui.profile;

import android.app.Activity;
import android.content.Intent;
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
import com.proyecto.moveon.databinding.FragmentProfileBinding;
import com.proyecto.moveon.domain.profile.PerfilUsuario;
import com.proyecto.moveon.ui.auth.LoginActivity;
import com.proyecto.moveon.ui.common.TopSnackbar;
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

public class ProfileFragment extends Fragment {

    private FragmentProfileBinding binding;
    private ProfileViewModel viewModel;

    @Nullable private PerfilUsuario perfilActual;
    @Nullable private String transientPhotoPreviewPath;

    private ProfileDialogHelper dialogHelper;
    private ProfileTrackingHelper trackingHelper;

    private final ActivityResultLauncher<Intent> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) return;
                Uri uri = result.getData().getData();
                if (uri == null) return;

                File file = uriToFile(uri);
                if (file == null) {
                    showErrorFeedback(getString(R.string.profile_error_photo_read));
                    return;
                }
                showTransientPhotoPreview(file);
                viewModel.uploadPhoto(file);
            });

    private final ActivityResultLauncher<String[]> trackingRequirementPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    result -> {
                        if (trackingHelper != null) trackingHelper.updateTrackingRequirementsUi();
                    });

    public ProfileFragment() {}

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        viewModel = new ViewModelProvider(this).get(ProfileViewModel.class);

        // MEJ-04: Helpers para diálogos y requisitos de tracking.
        dialogHelper = new ProfileDialogHelper(
                this, viewModel, () -> perfilActual, this::showErrorFeedback);
        trackingHelper = new ProfileTrackingHelper(
                this, binding, trackingRequirementPermissionLauncher);

        bindLocalData();        setupListeners();
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
        trackingHelper.updateTrackingRequirementsUi();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        dialogHelper = null;
        trackingHelper = null;
    }

    private void bindLocalData() {
        String username = viewModel.getUsername();
        if (!StringUtils.hasText(username)) {
            username = getString(R.string.profile_default_username);
        }
        binding.tvUserName.setText(username);

        syncLanguageSelectionText();
        trackingHelper.updateTrackingRequirementsUi();
    }

    private void bindPerfilData(@NonNull PerfilUsuario perfil) {
        if (binding == null) return;
        perfilActual = perfil;

        String notIndicated = getString(R.string.profile_not_indicated);

        binding.tvUserName.setText(perfil.nombreUsuario);
        binding.tvUserEmail.setText(perfil.email);
        binding.tvUserPoints.setText(
                getString(R.string.profile_puntos_formato, perfil.totalPuntos));

        binding.tvFullName.setText(
                StringUtils.hasText(perfil.nombreReal) ? perfil.nombreReal : notIndicated);
        binding.tvEmail.setText(perfil.email);
        binding.tvBirthdate.setText(
                StringUtils.hasText(perfil.fechaNacimiento)
                        ? formatFecha(perfil.fechaNacimiento) : notIndicated);
        binding.tvProvincia.setText(ProfileValueLocalizer.displayProvincia(requireContext(), perfil.provincia));
        binding.tvGenero.setText(ProfileValueLocalizer.displayGenero(requireContext(), perfil.genero));
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

        Object photoSource;
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
                    .signature(new ObjectKey(file.getAbsolutePath() + ":" + file.lastModified()));
        } else {
            request = request.diskCacheStrategy(DiskCacheStrategy.ALL);
        }

        request.into(binding.ivProfilePicture);
    }

    private void showTransientPhotoPreview(@NonNull File file) {
        transientPhotoPreviewPath = file.getAbsolutePath();
        loadProfilePhoto(file);
    }

    @NonNull
    private String formatFecha(@NonNull String fecha) {
        try {
            Locale locale = AppLanguageManager.getActiveLocale(requireContext());
            return LocalDate.parse(fecha).format(
                    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
            );
        } catch (DateTimeParseException ignored) {
            return fecha;
        }
    }

    @NonNull
    private String appendPhotoVersion(@NonNull String baseUrl, int version) {
        String separator = baseUrl.contains("?") ? "&" : "?";
        return baseUrl + separator + "v=" + version;
    }

    private void setupListeners() {
        binding.toggleThemeMode.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            String newMode;
            if      (checkedId == R.id.btn_theme_light)  newMode = ThemeManager.MODE_LIGHT;
            else if (checkedId == R.id.btn_theme_dark)   newMode = ThemeManager.MODE_DARK;
            else if (checkedId == R.id.btn_theme_system) newMode = ThemeManager.MODE_SYSTEM;
            else return;

            String currentMode = ThemeManager.getSavedMode(requireContext());
            if (newMode.equals(currentMode)) return;
            dialogHelper.startUiRecreationWithSplash(() -> {
                ThemeManager.saveAndApply(requireContext(), newMode);
                requireActivity().recreate();
            });
        });

        binding.itemRanking.setOnClickListener(v ->
                Toast.makeText(requireContext(), R.string.common_proximamente, Toast.LENGTH_SHORT).show());
        binding.itemShareRoutes.setOnClickListener(v ->
                Toast.makeText(requireContext(), R.string.common_proximamente, Toast.LENGTH_SHORT).show());

        binding.tvTrackingLocationAction.setOnClickListener(
                v -> trackingHelper.handleTrackingRequirementAction(TrackingRequirementsManager.Requirement.LOCATION));
        binding.tvTrackingActivityAction.setOnClickListener(
                v -> trackingHelper.handleTrackingRequirementAction(TrackingRequirementsManager.Requirement.ACTIVITY_RECOGNITION));
        binding.tvTrackingNotificationsAction.setOnClickListener(
                v -> trackingHelper.handleTrackingRequirementAction(TrackingRequirementsManager.Requirement.NOTIFICATIONS));
        binding.tvTrackingDeviceLocationAction.setOnClickListener(
                v -> trackingHelper.handleTrackingRequirementAction(TrackingRequirementsManager.Requirement.GPS));

        binding.fabChangePhoto.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("image/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            pickImageLauncher.launch(intent);
        });

        binding.btnLogout.setOnClickListener(v -> viewModel.logout());
        binding.btnDeleteAccount.setOnClickListener(v -> dialogHelper.showDeleteAccountConfirmationDialog());
        binding.itemLanguage.setOnClickListener(v -> dialogHelper.showLanguageDialog());

        binding.itemFullName.setOnClickListener(v -> dialogHelper.showEditTextDialog(
                getString(R.string.profile_label_fullname),
                perfilActual != null ? perfilActual.nombreReal : null,
                android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS,
                false,
                value -> {
                    AppInputValidator.ValidationResult<String> validation =
                            AppInputValidator.validateRealName(requireContext(), value, false);
                    if (!validation.isValid()) {
                        showErrorFeedback(validationError(validation));
                        return false;
                    }

                    String normalizedValue = validation.getValue();
                    String currentValue = perfilActual != null ? perfilActual.nombreReal : null;
                    if (AppInputValidator.sameText(currentValue, normalizedValue)) {
                        return true;
                    }

                    viewModel.updatePerfil(new ProfilePatchPayload()
                            .nombreReal(StringUtils.hasText(normalizedValue) ? normalizedValue : null)
                            .toJson());
                    return true;
                }
        ));

        binding.itemEmail.setOnClickListener(v -> dialogHelper.showEditTextDialog(
                getString(R.string.profile_label_email),
                perfilActual != null ? perfilActual.email : null,
                android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                true,
                value -> {
                    AppInputValidator.ValidationResult<String> validation =
                            AppInputValidator.validateEmail(requireContext(), value, true);
                    if (!validation.isValid()) {
                        showErrorFeedback(validationError(validation));
                        return false;
                    }

                    String normalizedValue = validation.getValue();
                    String currentValue = perfilActual != null ? perfilActual.email : null;
                    if (AppInputValidator.sameText(currentValue, normalizedValue)) {
                        return true;
                    }

                    viewModel.updatePerfil(new ProfilePatchPayload()
                            .email(normalizedValue)
                            .toJson());
                    return true;
                }
        ));

        binding.itemBirthdate.setOnClickListener(v -> dialogHelper.showBirthdatePicker());
        binding.itemProvincia.setOnClickListener(v -> dialogHelper.showEditProvinciaDialog());
        binding.itemGenero.setOnClickListener(v -> dialogHelper.showGeneroDialog());

        binding.itemAltura.setOnClickListener(v -> dialogHelper.showAlturaPickerDialog());
        binding.itemPeso.setOnClickListener(v -> dialogHelper.showPesoPickerDialog());
    }

    private void syncThemeToggleWithSavedMode() {
        if (binding == null) return;

        String mode = ThemeManager.getSavedMode(requireContext());
        int targetButtonId;
        if (ThemeManager.MODE_LIGHT.equals(mode)) {
            targetButtonId = R.id.btn_theme_light;
        } else if (ThemeManager.MODE_DARK.equals(mode)) {
            targetButtonId = R.id.btn_theme_dark;
        } else {
            targetButtonId = R.id.btn_theme_system;
        }

        if (binding.toggleThemeMode.getCheckedButtonId() != targetButtonId) {
            binding.toggleThemeMode.check(targetButtonId);
        }
    }

    private void syncLanguageSelectionText() {
        if (binding == null || dialogHelper == null) return;

        String mode = viewModel.getAppLanguageMode();
        int index = dialogHelper.findLanguageModeIndex(mode);
        String[] labels = getResources().getStringArray(R.array.app_language_labels);

        if (index >= 0 && index < labels.length) {
            binding.tvLanguageValue.setText(labels[index]);
        }
    }

    private void observeViewModel() {
        // ── perfilState: ÚNICO observer que controla el overlay ──
        // Solo se muestra durante la carga inicial del perfil (loadPerfil).
        // Los patches optimistas y subidas de foto nunca activan el overlay.
        viewModel.getPerfilState().observe(getViewLifecycleOwner(), state -> {
            if (state == null || binding == null) return;
            showOverlay(state.loading);
            if (state.data != null) {
                bindPerfilData(state.data);
            } else if (state.error != null) {
                showApiError(state.error, viewModel::loadPerfil);
            }
        });

        // ── updateState: solo feedback (snackbar), NUNCA overlay ──
        // FIX: Eliminado showOverlay(state.loading).
        // Con el overlay aquí, dos LiveData competían por mostrarlo/ocultarlo:
        // updateState lo ponía y perfilState lo quitaba 5 ms después (por la
        // emisión optimista de Room). El overlay parpadeaba y luego desaparecía,
        // pero el hilo IO seguía bloqueado 8-30 s con backend caído.
        viewModel.getUpdateState().observe(getViewLifecycleOwner(), state -> {
            if (state == null || binding == null) return;
            if (state.data != null) {
                String updateStatus = state.data;
                if (PerfilRepository.UpdateResult.STATUS_SYNCED.equals(updateStatus)) {
                    showSuccessFeedback(getString(R.string.profile_update_ok));
                } else if (PerfilRepository.UpdateResult.STATUS_QUEUED.equals(updateStatus)) {
                    showWarningFeedback(getString(R.string.profile_update_queued));
                }
                viewModel.resetUpdateState();
            } else if (state.error != null) {
                // FIX: Recargar datos para revertir cambios optimistas.
                // Si el servidor rechazó (422, 400), el path FAILED del repository
                // ya hizo fetchPerfilBlocking → mergeRemoteSnapshot, así que Room
                // ya emitió los datos reales. Pero si eso también falló (backend
                // caído), perfilActual tiene el último estado confirmado y
                // bindPerfilData revierte el switch/campo al valor correcto.
                if (perfilActual != null) {
                    bindPerfilData(perfilActual);
                }
                showApiError(state.error, viewModel::retryLastUpdate);
                viewModel.resetUpdateState();
            }
        });

        // ── photoState: solo feedback (snackbar), NUNCA overlay ──
        // FIX: Eliminado showOverlay(state.loading).
        // La foto preview se muestra instantáneamente desde pendingLocalPhotoPath
        // (Room emite → bindPerfilData → Glide carga el archivo local).
        // El overlay bloqueaba la UI 8-60 s con backend caído.
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
                if (perfilActual != null) {
                    bindPerfilData(perfilActual);
                }
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
                binding.btnDeleteAccount.setEnabled(false);
                binding.btnDeleteAccount.setText(R.string.profile_delete_account_deleting);
                showOverlay(true);
                return;
            }
            showOverlay(false);
            if (state.error != null) {
                binding.btnDeleteAccount.setEnabled(true);
                binding.btnDeleteAccount.setText(R.string.profile_btn_delete_account);
                showErrorFeedback(getString(R.string.profile_error_delete_account));
                return;
            }
            goToLogin();
        });
    }


    // ── TopSnackbar helpers ─────────────────────────────────────────────────────

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

    // ── Eliminar cuenta ─────────────────────────────────────────────────────────

    private String validationError(@NonNull AppInputValidator.ValidationResult<?> result) {
        String msg = result.getErrorMessage();
        return msg != null ? msg : getString(R.string.vm_error_generico);
    }

    @Nullable
    private File uriToFile(@NonNull Uri uri) {
        try {
            InputStream inputStream = requireContext().getContentResolver().openInputStream(uri);
            if (inputStream == null) return null;
            File tempFile = File.createTempFile("photo_", ".jpg", requireContext().getCacheDir());
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
