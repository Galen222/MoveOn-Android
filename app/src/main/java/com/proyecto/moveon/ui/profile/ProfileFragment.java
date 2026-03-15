package com.proyecto.moveon.ui.profile;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.NumberPicker;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.signature.ObjectKey;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointBackward;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.proyecto.moveon.R;
import com.proyecto.moveon.core.i18n.AppLanguageManager;
import com.proyecto.moveon.core.i18n.ProfileValueLocalizer;
import com.proyecto.moveon.core.theme.ThemeManager;
import com.proyecto.moveon.core.tracking.TrackingRequirementsManager;
import com.proyecto.moveon.data.profile.sync.ProfilePatchPayload;
import com.proyecto.moveon.databinding.DialogEditFieldBinding;
import com.proyecto.moveon.databinding.DialogEditNumberBinding;
import com.proyecto.moveon.databinding.DialogEditProvinciaBinding;
import com.proyecto.moveon.databinding.FragmentProfileBinding;
import com.proyecto.moveon.domain.profile.PerfilUsuario;
import com.proyecto.moveon.ui.auth.LoginActivity;
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
import java.util.Calendar;
import java.util.Locale;

public class ProfileFragment extends Fragment {

    private FragmentProfileBinding binding;
    private ProfileViewModel viewModel;

    @Nullable private PerfilUsuario perfilActual;
    @Nullable private String transientPhotoPreviewPath;


    private final ActivityResultLauncher<Intent> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) return;
                Uri uri = result.getData().getData();
                if (uri == null) return;

                File file = uriToFile(uri);
                if (file == null) {
                    Toast.makeText(requireContext(),
                            getString(R.string.profile_error_photo_read), Toast.LENGTH_SHORT).show();
                    return;
                }
                showTransientPhotoPreview(file);
                viewModel.uploadPhoto(file);
            });

    private final ActivityResultLauncher<String[]> trackingRequirementPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    result -> updateTrackingRequirementsUi());

    private final BroadcastReceiver deviceLocationStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateTrackingRequirementsUi();
        }
    };

    private boolean deviceLocationReceiverRegistered = false;

    public ProfileFragment() {}

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        viewModel = new ViewModelProvider(this).get(ProfileViewModel.class);

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
        registerDeviceLocationReceiver();
    }

    @Override
    public void onStop() {
        unregisterDeviceLocationReceiver();
        super.onStop();
    }

    @Override
    public void onResume() {
        super.onResume();
        syncThemeToggleWithSavedMode();
        syncLanguageSelectionText();
        updateTrackingRequirementsUi();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void bindLocalData() {
        String username = viewModel.getUsername();
        if (!StringUtils.hasText(username)) {
            username = getString(R.string.profile_default_username);
        }
        binding.tvUserName.setText(username);

        syncLanguageSelectionText();
        updateTrackingRequirementsUi();
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
            ThemeManager.saveAndApply(requireContext(), newMode);
            requireActivity().recreate();
        });

        binding.itemRanking.setOnClickListener(v ->
                Toast.makeText(requireContext(), R.string.common_proximamente, Toast.LENGTH_SHORT).show());
        binding.itemShareRoutes.setOnClickListener(v ->
                Toast.makeText(requireContext(), R.string.common_proximamente, Toast.LENGTH_SHORT).show());

        binding.tvTrackingLocationAction.setOnClickListener(
                v -> handleTrackingRequirementAction(TrackingRequirementsManager.Requirement.LOCATION));
        binding.tvTrackingActivityAction.setOnClickListener(
                v -> handleTrackingRequirementAction(TrackingRequirementsManager.Requirement.ACTIVITY_RECOGNITION));
        binding.tvTrackingNotificationsAction.setOnClickListener(
                v -> handleTrackingRequirementAction(TrackingRequirementsManager.Requirement.NOTIFICATIONS));
        binding.tvTrackingDeviceLocationAction.setOnClickListener(
                v -> handleTrackingRequirementAction(TrackingRequirementsManager.Requirement.GPS));

        binding.fabChangePhoto.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("image/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            pickImageLauncher.launch(intent);
        });

        binding.btnLogout.setOnClickListener(v -> viewModel.logout());
        binding.itemLanguage.setOnClickListener(v -> showLanguageDialog());

        binding.itemFullName.setOnClickListener(v -> showEditTextDialog(
                getString(R.string.profile_label_fullname),
                perfilActual != null ? perfilActual.nombreReal : null,
                android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS,
                false,
                value -> {
                    viewModel.updatePerfil(new ProfilePatchPayload()
                            .nombreReal(value.isEmpty() ? null : value)
                            .toJson());
                    return true;
                }
        ));

        binding.itemEmail.setOnClickListener(v -> showEditTextDialog(
                getString(R.string.profile_label_email),
                perfilActual != null ? perfilActual.email : null,
                android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                true,
                value -> {
                    viewModel.updatePerfil(new ProfilePatchPayload()
                            .email(value)
                            .toJson());
                    return true;
                }
        ));

        binding.itemBirthdate.setOnClickListener(v -> showBirthdatePicker());
        binding.itemProvincia.setOnClickListener(v -> showEditProvinciaDialog());
        binding.itemGenero.setOnClickListener(v -> showGeneroDialog());

        binding.itemAltura.setOnClickListener(v -> showAlturaPickerDialog());
        binding.itemPeso.setOnClickListener(v -> showPesoPickerDialog());
    }

    // ── Picker de Altura (100–220 cm, enteros) ────────────────────────────────

    private void showAlturaPickerDialog() {
        final int minAltura = 50;
        final int maxAltura = 300;

        NumberPicker picker = new NumberPicker(requireContext());
        picker.setMinValue(minAltura);
        picker.setMaxValue(maxAltura);
        picker.setWrapSelectorWheel(false);

        int initialAltura = 170; // dentro del nuevo rango 50–300
        if (perfilActual != null && perfilActual.altura != null) {
            int current = perfilActual.altura;
            if (current >= minAltura && current <= maxAltura) {
                initialAltura = current;
            }
        }
        picker.setValue(initialAltura);

        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        picker.setPadding(pad, pad, pad, pad);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.profile_label_altura)
                .setView(picker)
                .setNegativeButton(R.string.dialog_btn_cancel, null)
                .setPositiveButton(R.string.dialog_btn_save, (dialog, which) -> {
                    int altura = picker.getValue();
                    viewModel.updatePerfil(new ProfilePatchPayload().altura(altura).toJson());
                })
                .show();
    }

    // ── Picker de Peso (40–200 kg, pasos de 0.5) ─────────────────────────────

    private void showPesoPickerDialog() {
        final double pesoMin  = 20.0;
        final double pesoMax  = 300.0;
        final double pesoStep = 0.5;
        final int totalItems  = (int) ((pesoMax - pesoMin) / pesoStep) + 1;

        String[] labels = new String[totalItems];
        for (int i = 0; i < totalItems; i++) {
            double val = pesoMin + i * pesoStep;
            labels[i] = String.format(Locale.getDefault(), "%.1f kg", val);
        }

        NumberPicker picker = new NumberPicker(requireContext());
        picker.setMinValue(0);
        picker.setMaxValue(totalItems - 1);
        picker.setDisplayedValues(labels);
        picker.setWrapSelectorWheel(false);

        int initialIndex = (int) ((70.0 - pesoMin) / pesoStep); // 70 kg, dentro del nuevo rango 20–300
        if (perfilActual != null && perfilActual.peso != null) {
            double current = perfilActual.peso;
            if (current >= pesoMin && current <= pesoMax) {
                initialIndex = (int) Math.round((current - pesoMin) / pesoStep);
            }
        }
        picker.setValue(initialIndex);

        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        picker.setPadding(pad, pad, pad, pad);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.profile_label_peso)
                .setView(picker)
                .setNegativeButton(R.string.dialog_btn_cancel, null)
                .setPositiveButton(R.string.dialog_btn_save, (dialog, which) -> {
                    double peso = pesoMin + picker.getValue() * pesoStep;
                    peso = Math.round(peso * 10.0) / 10.0;
                    viewModel.updatePerfil(new ProfilePatchPayload().peso(peso).toJson());
                })
                .show();
    }

    private void showEditTextDialog(@NonNull String label,
                                    @Nullable String currentValue,
                                    int inputType,
                                    boolean required,
                                    @NonNull OnValueSavedListener listener) {
        DialogEditFieldBinding dialogBinding =
                DialogEditFieldBinding.inflate(LayoutInflater.from(requireContext()));

        dialogBinding.tilField.setHint(label);
        if (StringUtils.hasText(currentValue)) {
            dialogBinding.etField.setText(currentValue);
            dialogBinding.etField.setSelection(currentValue.length());
        }
        dialogBinding.etField.setInputType(inputType);

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(label)
                .setView(dialogBinding.getRoot())
                .setPositiveButton(R.string.dialog_btn_save, null)
                .setNegativeButton(R.string.dialog_btn_cancel, null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = dialogBinding.etField.getText() != null
                    ? dialogBinding.etField.getText().toString().trim() : "";
            dialogBinding.tilField.setError(null);

            if (required && value.isEmpty()) {
                dialogBinding.tilField.setError(getString(R.string.dialog_error_required));
                return;
            }

            boolean close = listener.onSaved(value);
            if (close) dialog.dismiss();
        }));

        dialog.show();
    }

    private void showEditNumberDialog(@NonNull String label,
                                      @Nullable String currentValue,
                                      boolean isInteger,
                                      @NonNull OnValueSavedListener listener) {
        DialogEditNumberBinding dialogBinding =
                DialogEditNumberBinding.inflate(LayoutInflater.from(requireContext()));

        dialogBinding.tilNumber.setHint(label);
        if (StringUtils.hasText(currentValue)) {
            dialogBinding.etNumber.setText(currentValue);
            dialogBinding.etNumber.setSelection(currentValue.length());
        }
        dialogBinding.etNumber.setInputType(isInteger
                ? android.text.InputType.TYPE_CLASS_NUMBER
                : android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(label)
                .setView(dialogBinding.getRoot())
                .setPositiveButton(R.string.dialog_btn_save, null)
                .setNegativeButton(R.string.dialog_btn_cancel, null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = dialogBinding.etNumber.getText() != null
                    ? dialogBinding.etNumber.getText().toString().trim() : "";
            dialogBinding.tilNumber.setError(null);
            boolean close = listener.onSaved(value);
            if (close) dialog.dismiss();
        }));

        dialog.show();
    }

    private void showBirthdatePicker() {
        Calendar maxDate = Calendar.getInstance();
        maxDate.add(Calendar.YEAR, -18);

        CalendarConstraints constraints = new CalendarConstraints.Builder()
                .setEnd(maxDate.getTimeInMillis())
                .setValidator(DateValidatorPointBackward.before(maxDate.getTimeInMillis()))
                .build();

        long initialSelection;
        if (perfilActual != null && StringUtils.hasText(perfilActual.fechaNacimiento)) {
            try {
                String[] parts = perfilActual.fechaNacimiento.split("-");
                Calendar cal = Calendar.getInstance();
                cal.set(Integer.parseInt(parts[0]),
                        Integer.parseInt(parts[1]) - 1,
                        Integer.parseInt(parts[2]));
                initialSelection = cal.getTimeInMillis();
            } catch (Exception e) {
                initialSelection = maxDate.getTimeInMillis();
            }
        } else {
            initialSelection = maxDate.getTimeInMillis();
        }

        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(R.string.profile_label_birthdate)
                .setInputMode(MaterialDatePicker.INPUT_MODE_CALENDAR)
                .setCalendarConstraints(constraints)
                .setSelection(initialSelection)
                .build();

        picker.addOnPositiveButtonClickListener(selection -> {
            Calendar selected = Calendar.getInstance();
            selected.setTimeInMillis(selection);

            Calendar minRequired = Calendar.getInstance();
            minRequired.add(Calendar.YEAR, -18);
            if (selected.after(minRequired)) {
                Toast.makeText(requireContext(),
                        R.string.profile_error_birthdate_min_age, Toast.LENGTH_SHORT).show();
                return;
            }

            String fecha = String.format(Locale.getDefault(), "%04d-%02d-%02d",
                    selected.get(Calendar.YEAR),
                    selected.get(Calendar.MONTH) + 1,
                    selected.get(Calendar.DAY_OF_MONTH));

            viewModel.updatePerfil(new ProfilePatchPayload()
                    .fechaNacimiento(fecha)
                    .toJson());
        });

        picker.show(getParentFragmentManager(), "birthdate_picker");
    }

    private void showEditProvinciaDialog() {
        DialogEditProvinciaBinding dialogBinding =
                DialogEditProvinciaBinding.inflate(LayoutInflater.from(requireContext()));

        String[] provincias = getResources().getStringArray(R.array.provincias_labels);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_dropdown_item_1line, provincias);
        dialogBinding.actvProvincia.setAdapter(adapter);
        dialogBinding.tilProvincia.setHint(getString(R.string.profile_label_provincia));

        if (perfilActual != null && StringUtils.hasText(perfilActual.provincia)) {
            dialogBinding.actvProvincia.setText(
                    ProfileValueLocalizer.displayProvincia(requireContext(), perfilActual.provincia),
                    false
            );
        }

        dialogBinding.actvProvincia.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) dialogBinding.actvProvincia.showDropDown();
        });
        dialogBinding.actvProvincia.setOnClickListener(v ->
                dialogBinding.actvProvincia.showDropDown());

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.profile_label_provincia)
                .setView(dialogBinding.getRoot())
                .setPositiveButton(R.string.dialog_btn_save, null)
                .setNegativeButton(R.string.dialog_btn_cancel, null)
                .create();

        dialog.setOnShowListener(d -> {
            dialogBinding.actvProvincia.post(() -> {
                dialogBinding.actvProvincia.requestFocus();
                dialogBinding.actvProvincia.showDropDown();
            });

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String value = dialogBinding.actvProvincia.getText() != null
                        ? dialogBinding.actvProvincia.getText().toString().trim() : "";
                if (value.isEmpty()) {
                    dialogBinding.tilProvincia.setError(getString(R.string.dialog_error_required));
                    return;
                }
                dialogBinding.tilProvincia.setError(null);
                String provinciaValue = ProfileValueLocalizer.canonicalProvinciaFromLabel(requireContext(), value);
                viewModel.updatePerfil(new ProfilePatchPayload()
                        .provincia(provinciaValue)
                        .toJson());
                dialog.dismiss();
            });
        });

        dialog.show();
    }

    private void showGeneroDialog() {
        String[] opciones = getResources().getStringArray(R.array.generos_labels);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.profile_label_genero)
                .setItems(opciones, (d, which) ->
                        viewModel.updatePerfil(new ProfilePatchPayload()
                                .genero(ProfileValueLocalizer.canonicalGeneroFromLabel(requireContext(), opciones[which]))
                                .toJson()))
                .setNegativeButton(R.string.dialog_btn_cancel, null)
                .show();
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
        if (binding == null) return;

        String mode = viewModel.getAppLanguageMode();
        int index = findLanguageModeIndex(mode);
        String[] labels = getResources().getStringArray(R.array.app_language_labels);

        if (index >= 0 && index < labels.length) {
            binding.tvLanguageValue.setText(labels[index]);
        }
    }

    private void showLanguageDialog() {
        String[] modes = getResources().getStringArray(R.array.app_language_modes);
        String[] labels = getResources().getStringArray(R.array.app_language_labels);

        int checkedItem = findLanguageModeIndex(viewModel.getAppLanguageMode());

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.profile_language_selector_title)
                .setSingleChoiceItems(labels, checkedItem, (dialog, which) -> {
                    String selectedMode = modes[which];
                    if (!selectedMode.equals(viewModel.getAppLanguageMode())) {
                        AppLanguageManager.saveAndApply(requireContext(), selectedMode);
                    }
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.dialog_btn_cancel, null)
                .show();
    }

    private int findLanguageModeIndex(@Nullable String mode) {
        String normalizedMode = AppLanguageManager.sanitizeSelectableMode(mode);
        String[] modes = getResources().getStringArray(R.array.app_language_modes);
        for (int i = 0; i < modes.length; i++) {
            if (modes[i].equals(normalizedMode)) return i;
        }
        return 0;
    }

    private void observeViewModel() {
        viewModel.getPerfilState().observe(getViewLifecycleOwner(), state -> {
            if (state == null || binding == null) return;
            showOverlay(state.loading);
            if (state.data != null) {
                bindPerfilData(state.data);
            } else if (state.error != null) {
                Toast.makeText(requireContext(),
                        state.error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        viewModel.getUpdateState().observe(getViewLifecycleOwner(), state -> {
            if (state == null || binding == null) return;
            showOverlay(state.loading);
            if (state.data != null) {
                String updateStatus = state.data;
                if ("SYNCED".equals(updateStatus)) {
                    Toast.makeText(requireContext(),
                            R.string.profile_update_ok, Toast.LENGTH_SHORT).show();
                } else if ("QUEUED".equals(updateStatus)) {
                    Toast.makeText(requireContext(),
                            getString(R.string.profile_update_queued),
                            Toast.LENGTH_SHORT).show();
                }
                viewModel.resetUpdateState();
            } else if (state.error != null) {
                Toast.makeText(requireContext(),
                        state.error.getMessage(), Toast.LENGTH_SHORT).show();
                viewModel.resetUpdateState();
            }
        });

        viewModel.getPhotoState().observe(getViewLifecycleOwner(), state -> {
            if (state == null || binding == null) return;
            showOverlay(state.loading);
            if (state.data != null) {
                if ("SYNCED".equals(state.data)) {
                    transientPhotoPreviewPath = null;
                    Toast.makeText(requireContext(),
                            R.string.profile_photo_ok, Toast.LENGTH_SHORT).show();
                } else if ("QUEUED".equals(state.data)) {
                    Toast.makeText(requireContext(),
                            getString(R.string.profile_photo_queued),
                            Toast.LENGTH_SHORT).show();
                }
                viewModel.resetPhotoState();
            } else if (state.error != null) {
                transientPhotoPreviewPath = null;
                if (perfilActual != null) {
                    bindPerfilData(perfilActual);
                }
                Toast.makeText(requireContext(),
                        state.error.getMessage(), Toast.LENGTH_SHORT).show();
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
                        Toast.LENGTH_SHORT).show();
            }
            goToLogin();
        });
    }

    private void showOverlay(boolean show) {
        if (binding == null) return;
        binding.loadingOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void updateTrackingRequirementsUi() {
        if (binding == null || !isAdded()) return;

        bindTrackingRequirementRow(
                binding.tvTrackingLocationStatus,
                binding.tvTrackingLocationAction,
                TrackingRequirementsManager.Requirement.LOCATION,
                TrackingRequirementsManager.getLocationStatus(this)
        );

        bindTrackingRequirementRow(
                binding.tvTrackingActivityStatus,
                binding.tvTrackingActivityAction,
                TrackingRequirementsManager.Requirement.ACTIVITY_RECOGNITION,
                TrackingRequirementsManager.getActivityRecognitionStatus(this)
        );

        bindTrackingRequirementRow(
                binding.tvTrackingNotificationsStatus,
                binding.tvTrackingNotificationsAction,
                TrackingRequirementsManager.Requirement.NOTIFICATIONS,
                TrackingRequirementsManager.getNotificationsStatus(this)
        );

        bindTrackingRequirementRow(
                binding.tvTrackingDeviceLocationStatus,
                binding.tvTrackingDeviceLocationAction,
                TrackingRequirementsManager.Requirement.GPS,
                TrackingRequirementsManager.getDeviceLocationStatus(requireContext())
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
                return getString(R.string.profile_tracking_status_enabled);
            case NEEDS_ACTIVATION:
                return requirement == TrackingRequirementsManager.Requirement.GPS
                        ? getString(R.string.profile_tracking_status_disabled)
                        : getString(R.string.profile_tracking_status_needs_activation);
            case BLOCKED:
            default:
                return getString(R.string.profile_tracking_status_blocked);
        }
    }

    private void handleTrackingRequirementAction(
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
                .buildRequestablePermissionsForRequirement(this, requirement);
        if (permissions.length == 0) {
            updateTrackingRequirementsUi();
            return;
        }

        TrackingRequirementsManager.markPermissionsRequested(requireContext(), permissions);
        trackingRequirementPermissionLauncher.launch(permissions);
    }

    @NonNull
    private TrackingRequirementsManager.Status getTrackingRequirementStatus(
            @NonNull TrackingRequirementsManager.Requirement requirement) {
        switch (requirement) {
            case LOCATION:
                return TrackingRequirementsManager.getLocationStatus(this);
            case ACTIVITY_RECOGNITION:
                return TrackingRequirementsManager.getActivityRecognitionStatus(this);
            case NOTIFICATIONS:
                return TrackingRequirementsManager.getNotificationsStatus(this);
            case GPS:
            default:
                return TrackingRequirementsManager.getDeviceLocationStatus(requireContext());
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

    private void registerDeviceLocationReceiver() {
        if (deviceLocationReceiverRegistered || !isAdded()) return;

        IntentFilter filter = new IntentFilter(LocationManager.MODE_CHANGED_ACTION);
        filter.addAction(LocationManager.PROVIDERS_CHANGED_ACTION);

        Context context = requireContext();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(deviceLocationStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            context.registerReceiver(deviceLocationStateReceiver, filter);
        }
        deviceLocationReceiverRegistered = true;
    }

    private void unregisterDeviceLocationReceiver() {
        if (!deviceLocationReceiverRegistered || !isAdded()) return;
        requireContext().unregisterReceiver(deviceLocationStateReceiver);
        deviceLocationReceiverRegistered = false;
    }

    private void openAppSettings() {

        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", requireContext().getPackageName(), null));
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

    private void goToLogin() {
        if (!isAdded()) return;
        NavigationUtils.goToActivityAndClearTask(requireActivity(), LoginActivity.class);
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

    private interface OnValueSavedListener {
        boolean onSaved(@NonNull String value);
    }
}
