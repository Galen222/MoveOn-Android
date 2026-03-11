package com.proyecto.moveon.ui.profile;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;
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
import com.proyecto.moveon.core.theme.ThemeManager;
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
import java.util.Calendar;
import java.util.Locale;

public class ProfileFragment extends Fragment {

    private static final String PREFS_UI = "profile_ui_prefs";
    private static final String KEY_NOTIFICATIONS_PERMISSION_REQUESTED =
            "notifications_permission_requested";

    private FragmentProfileBinding binding;
    private ProfileViewModel viewModel;

    @Nullable
    private PerfilUsuario perfilActual;

    @Nullable
    private String transientPhotoPreviewPath;

    private final ActivityResultLauncher<Intent> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) return;

                Uri uri = result.getData().getData();
                if (uri == null) return;

                File file = uriToFile(uri);
                if (file == null) {
                    Toast.makeText(requireContext(),
                            getString(R.string.profile_error_photo_read),
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                showTransientPhotoPreview(file);
                viewModel.uploadPhoto(file);
            });

    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (!isAdded() || binding == null) return;

                if (isGranted) {
                    viewModel.setNotificationsEnabled(true);
                } else {
                    viewModel.setNotificationsEnabled(false);
                    Toast.makeText(requireContext(),
                            R.string.profile_notifications_permission_denied,
                            Toast.LENGTH_SHORT).show();
                }

                refreshNotificationsUi();
            });

    private final ActivityResultLauncher<Intent> notificationSettingsLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (!isAdded() || binding == null) return;
                refreshNotificationsUi();
            });

    public ProfileFragment() {
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        viewModel = new ViewModelProvider(this).get(ProfileViewModel.class);

        bindLocalData();
        setupListeners();
        observeViewModel();
        syncThemeToggleWithSavedMode();
        viewModel.loadPerfil();

        return binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        syncThemeToggleWithSavedMode();
        refreshNotificationsUi();
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
        refreshNotificationsUi();
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
                        ? formatFecha(perfil.fechaNacimiento)
                        : notIndicated);
        binding.tvProvincia.setText(
                StringUtils.hasText(perfil.provincia) ? perfil.provincia : notIndicated);
        binding.tvGenero.setText(
                StringUtils.hasText(perfil.genero) ? perfil.genero : notIndicated);
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
        String[] partes = fecha.split("-");
        if (partes.length == 3) {
            return partes[2] + "-" + partes[1] + "-" + partes[0];
        }
        return fecha;
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
            if (checkedId == R.id.btn_theme_light) {
                newMode = ThemeManager.MODE_LIGHT;
            } else if (checkedId == R.id.btn_theme_dark) {
                newMode = ThemeManager.MODE_DARK;
            } else if (checkedId == R.id.btn_theme_system) {
                newMode = ThemeManager.MODE_SYSTEM;
            } else {
                return;
            }

            String currentMode = ThemeManager.getSavedMode(requireContext());
            if (newMode.equals(currentMode)) return;

            ThemeManager.saveAndApply(requireContext(), newMode);
            requireActivity().recreate();
        });

        binding.itemRanking.setOnClickListener(v ->
                Toast.makeText(requireContext(),
                        R.string.common_proximamente,
                        Toast.LENGTH_SHORT).show());

        binding.itemShareRoutes.setOnClickListener(v ->
                Toast.makeText(requireContext(),
                        R.string.common_proximamente,
                        Toast.LENGTH_SHORT).show());

        binding.tvNotificationsOpenSettings.setOnClickListener(v -> openNotificationSettings());

        binding.fabChangePhoto.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("image/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            pickImageLauncher.launch(intent);
        });

        binding.btnLogout.setOnClickListener(v -> viewModel.logout());

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

        binding.itemAltura.setOnClickListener(v -> showEditNumberDialog(
                getString(R.string.profile_label_altura),
                perfilActual != null && perfilActual.altura != null
                        ? String.valueOf(perfilActual.altura)
                        : null,
                true,
                true,
                value -> {
                    if (value.isEmpty()) {
                        viewModel.updatePerfil(new ProfilePatchPayload().altura(null).toJson());
                        return true;
                    }
                    try {
                        int altura = Integer.parseInt(value);
                        viewModel.updatePerfil(new ProfilePatchPayload().altura(altura).toJson());
                        return true;
                    } catch (NumberFormatException e) {
                        Toast.makeText(requireContext(),
                                R.string.dialog_error_invalid_number,
                                Toast.LENGTH_SHORT).show();
                        return false;
                    }
                }
        ));

        binding.itemPeso.setOnClickListener(v -> showEditNumberDialog(
                getString(R.string.profile_label_peso),
                perfilActual != null && perfilActual.peso != null
                        ? String.valueOf(perfilActual.peso)
                        : null,
                false,
                true,
                value -> {
                    if (value.isEmpty()) {
                        viewModel.updatePerfil(new ProfilePatchPayload().peso(null).toJson());
                        return true;
                    }
                    try {
                        double peso = Double.parseDouble(value);
                        viewModel.updatePerfil(new ProfilePatchPayload().peso(peso).toJson());
                        return true;
                    } catch (NumberFormatException e) {
                        Toast.makeText(requireContext(),
                                R.string.dialog_error_invalid_number,
                                Toast.LENGTH_SHORT).show();
                        return false;
                    }
                }
        ));

        refreshNotificationsUi();
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

        dialog.setOnShowListener(d ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    String value = dialogBinding.etField.getText() != null
                            ? dialogBinding.etField.getText().toString().trim()
                            : "";
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
                                      boolean allowEmpty,
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
                : android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(label)
                .setView(dialogBinding.getRoot())
                .setPositiveButton(R.string.dialog_btn_save, null)
                .setNegativeButton(R.string.dialog_btn_cancel, null)
                .create();

        dialog.setOnShowListener(d ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    String value = dialogBinding.etNumber.getText() != null
                            ? dialogBinding.etNumber.getText().toString().trim()
                            : "";
                    if (!allowEmpty && value.isEmpty()) {
                        dialogBinding.tilNumber.setError(getString(R.string.dialog_error_required));
                        return;
                    }
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
                        R.string.profile_error_birthdate_min_age,
                        Toast.LENGTH_SHORT).show();
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

        String[] provincias = getResources().getStringArray(R.array.provincias);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                provincias
        );
        dialogBinding.actvProvincia.setAdapter(adapter);
        dialogBinding.tilProvincia.setHint(getString(R.string.profile_label_provincia));

        if (perfilActual != null && StringUtils.hasText(perfilActual.provincia)) {
            dialogBinding.actvProvincia.setText(perfilActual.provincia, false);
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
                        ? dialogBinding.actvProvincia.getText().toString().trim()
                        : "";

                if (value.isEmpty()) {
                    dialogBinding.tilProvincia.setError(getString(R.string.dialog_error_required));
                    return;
                }

                dialogBinding.tilProvincia.setError(null);
                String provinciaValue = getString(R.string.profile_provincia_no_indicar).equals(value)
                        ? null
                        : value;

                viewModel.updatePerfil(new ProfilePatchPayload()
                        .provincia(provinciaValue)
                        .toJson());

                dialog.dismiss();
            });
        });

        dialog.show();
    }

    private void showGeneroDialog() {
        String[] opciones = getResources().getStringArray(R.array.generos);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.profile_label_genero)
                .setItems(opciones, (d, which) ->
                        viewModel.updatePerfil(new ProfilePatchPayload()
                                .genero(opciones[which])
                                .toJson()))
                .setNegativeButton(R.string.dialog_btn_cancel, null)
                .show();
    }

    private void observeViewModel() {
        viewModel.getPerfilState().observe(getViewLifecycleOwner(), state -> {
            if (state == null || binding == null) return;

            showOverlay(state.loading);

            if (state.data != null) {
                bindPerfilData(state.data);
            } else if (state.error != null) {
                Toast.makeText(requireContext(),
                        state.error.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });

        viewModel.getUpdateState().observe(getViewLifecycleOwner(), state -> {
            if (state == null || binding == null) return;

            showOverlay(state.loading);

            if (state.data != null) {
                String updateStatus = state.data;
                if ("SYNCED".equals(updateStatus)) {
                    Toast.makeText(requireContext(),
                            R.string.profile_update_ok,
                            Toast.LENGTH_SHORT).show();
                } else if ("QUEUED".equals(updateStatus)) {
                    Toast.makeText(requireContext(),
                            "Cambio guardado localmente. Se sincronizará cuando vuelva la conexión.",
                            Toast.LENGTH_SHORT).show();
                }
                viewModel.resetUpdateState();
            } else if (state.error != null) {
                Toast.makeText(requireContext(),
                        state.error.getMessage(),
                        Toast.LENGTH_SHORT).show();
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
                            R.string.profile_photo_ok,
                            Toast.LENGTH_SHORT).show();
                } else if ("QUEUED".equals(state.data)) {
                    Toast.makeText(requireContext(),
                            "Foto guardada localmente. Se subirá cuando vuelva la conexión.",
                            Toast.LENGTH_SHORT).show();
                }
                viewModel.resetPhotoState();
            } else if (state.error != null) {
                transientPhotoPreviewPath = null;
                if (perfilActual != null) {
                    bindPerfilData(perfilActual);
                }
                Toast.makeText(requireContext(),
                        state.error.getMessage(),
                        Toast.LENGTH_SHORT).show();
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
                // Solo si hubo error restauramos el botón
                binding.btnLogout.setEnabled(true);
                binding.btnLogout.setText(getString(R.string.profile_btn_logout));

                Toast.makeText(requireContext(),
                        getString(R.string.profile_error_logout_server),
                        Toast.LENGTH_SHORT).show();
                return;
            }

            goToLogin();
        });
    }

    private boolean hasNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true;
        }
        return ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean areNotificationsEnabledAtSystemLevel() {
        return NotificationManagerCompat.from(requireContext()).areNotificationsEnabled();
    }

    private boolean hasRequestedNotificationPermission() {
        SharedPreferences prefs = requireContext()
                .getSharedPreferences(PREFS_UI, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_NOTIFICATIONS_PERMISSION_REQUESTED, false);
    }

    private void markNotificationPermissionRequested() {
        SharedPreferences prefs = requireContext()
                .getSharedPreferences(PREFS_UI, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_NOTIFICATIONS_PERMISSION_REQUESTED, true).apply();
    }

    private boolean isNotificationPermissionRequestable() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return false;
        }

        return ActivityCompat.shouldShowRequestPermissionRationale(
                requireActivity(),
                Manifest.permission.POST_NOTIFICATIONS
        );
    }

    /**
     * Estado efectivo real: la app puede mostrar notificaciones AHORA.
     */
    private boolean areNotificationsActuallyEnabledForApp() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return areNotificationsEnabledAtSystemLevel();
        }

        return hasNotificationPermission() && areNotificationsEnabledAtSystemLevel();
    }

    /**
     * "Bloqueadas" = la app ya no puede resolverlo sola y hace falta ir a Ajustes.
     *
     * API < 33:
     * - si el sistema las desactiva, están bloqueadas
     *
     * API 33+:
     * - si el permiso está concedido pero el sistema las tiene desactivadas, están bloqueadas
     * - si nunca se pidió el permiso, NO están bloqueadas
     * - si aún se puede volver a mostrar el prompt, NO están bloqueadas
     * - solo están bloqueadas cuando ya no se puede resolver desde la app
     */
    private boolean isNotificationsBlockedBySystem() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return !areNotificationsEnabledAtSystemLevel();
        }

        if (hasNotificationPermission()) {
            return !areNotificationsEnabledAtSystemLevel();
        }

        if (!hasRequestedNotificationPermission()) {
            return false;
        }

        return !isNotificationPermissionRequestable();
    }

    private void refreshNotificationsUi() {
        if (binding == null) return;

        boolean preferredEnabled = viewModel.areNotificationsEnabled();
        boolean actuallyEnabled = areNotificationsActuallyEnabledForApp();
        boolean blockedBySystem = isNotificationsBlockedBySystem();

        // El switch refleja el estado efectivo real, no solo la preferencia guardada.
        boolean effectiveChecked = preferredEnabled && actuallyEnabled;

        binding.switchNotifications.setOnCheckedChangeListener(null);
        binding.switchNotifications.setEnabled(!blockedBySystem);
        binding.switchNotifications.setChecked(effectiveChecked);

        binding.switchNotifications.setOnCheckedChangeListener((btn, isChecked) -> {
            if (!btn.isPressed()) return;

            if (!isChecked) {
                viewModel.setNotificationsEnabled(false);
                refreshNotificationsUi();
                return;
            }

            if (isNotificationsBlockedBySystem()) {
                openNotificationSettings();
                return;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission()) {
                markNotificationPermissionRequested();
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                return;
            }

            if (!areNotificationsEnabledAtSystemLevel()) {
                openNotificationSettings();
                return;
            }

            viewModel.setNotificationsEnabled(true);
            refreshNotificationsUi();
        });

        int blockedVisibility = blockedBySystem ? View.VISIBLE : View.GONE;
        binding.tvNotificationsBlocked.setVisibility(blockedVisibility);
        binding.tvNotificationsOpenSettings.setVisibility(blockedVisibility);
    }

    private void openNotificationSettings() {
        if (!isAdded()) return;

        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().getPackageName());

        try {
            notificationSettingsLauncher.launch(intent);
        } catch (Exception e) {
            Intent fallbackIntent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", requireContext().getPackageName(), null));
            notificationSettingsLauncher.launch(fallbackIntent);
        }
    }

    private void showOverlay(boolean show) {
        if (binding == null) return;
        binding.loadingOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void syncThemeToggleWithSavedMode() {
        if (binding == null) return;

        String mode = ThemeManager.getSavedMode(requireContext());
        int checkedId;

        if (ThemeManager.MODE_LIGHT.equals(mode)) {
            checkedId = R.id.btn_theme_light;
        } else if (ThemeManager.MODE_DARK.equals(mode)) {
            checkedId = R.id.btn_theme_dark;
        } else {
            checkedId = R.id.btn_theme_system;
        }

        if (binding.toggleThemeMode.getCheckedButtonId() != checkedId) {
            binding.toggleThemeMode.check(checkedId);
        }
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
