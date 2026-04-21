package com.proyecto.moveon.ui.profile;

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.widget.ArrayAdapter;
import android.widget.NumberPicker;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointBackward;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.proyecto.moveon.R;
import com.proyecto.moveon.core.i18n.AppLanguageManager;
import com.proyecto.moveon.core.i18n.ProfileValueLocalizer;
import com.proyecto.moveon.core.settings.AppSettingsManager;
import com.proyecto.moveon.core.validation.AppInputValidator;
import com.proyecto.moveon.data.profile.sync.ProfilePatchPayload;
import com.proyecto.moveon.databinding.DialogEditFieldBinding;
import com.proyecto.moveon.databinding.DialogEditProvinciaBinding;
import com.proyecto.moveon.domain.profile.PerfilUsuario;
import com.proyecto.moveon.ui.main.MainActivity;
import com.proyecto.moveon.utils.StringUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Agrupa los diálogos de edición del perfil que usa {@link ProfileFragment}.
 *
 * <p>La eliminación de cuenta se ha movido a {@link DeleteAccountBottomSheet}.</p>
 */
public final class ProfileDialogHelper {

    public interface OnValueSavedListener {
        /**
         * Recibe el valor confirmado en un diálogo de edición textual.
         *
         * @param value valor ya trimado introducido por el usuario.
         * @return {@code true} si el diálogo puede cerrarse; {@code false} si debe permanecer abierto.
         */
        boolean onSaved(@NonNull String value);
    }

    private final Fragment fragment;
    private final ProfileViewModel viewModel;
    private final Supplier<PerfilUsuario> perfilSupplier;
    private final Consumer<String> onError;

    /**
     * Crea el helper que centraliza los diálogos de edición del perfil.
     *
     * @param fragment fragment dueño de los diálogos.
     * @param viewModel ViewModel de perfil que ejecuta las actualizaciones.
     * @param perfilSupplier proveedor del perfil actual visible.
     * @param onError callback para mostrar errores de validación.
     */
    public ProfileDialogHelper(@NonNull Fragment fragment,
                               @NonNull ProfileViewModel viewModel,
                               @NonNull Supplier<PerfilUsuario> perfilSupplier,
                               @NonNull Consumer<String> onError) {
        this.fragment = fragment;
        this.viewModel = viewModel;
        this.perfilSupplier = perfilSupplier;
        this.onError = onError;
    }

    // ── Picker de Altura (100–220 cm, enteros) ────────────────────────────────

    /**
     * Muestra el selector numérico de altura y envía el cambio si pasa validación.
     */
    public void showAlturaPickerDialog() {
        final int minAltura = 50;
        final int maxAltura = 300;

        NumberPicker picker = new NumberPicker(fragment.requireContext());
        picker.setMinValue(minAltura);
        picker.setMaxValue(maxAltura);
        picker.setWrapSelectorWheel(false);

        int initialAltura = 170;
        PerfilUsuario perfilAltura = perfilSupplier.get();
        if (perfilAltura != null && perfilAltura.altura != null) {
            int current = perfilAltura.altura;
            if (current >= minAltura && current <= maxAltura) {
                initialAltura = current;
            }
        }
        picker.setValue(initialAltura);

        int pad = (int) (16 * fragment.getResources().getDisplayMetrics().density);
        picker.setPadding(pad, pad, pad, pad);

        new MaterialAlertDialogBuilder(fragment.requireContext())
                .setTitle(R.string.profile_label_altura)
                .setView(picker)
                .setNegativeButton(R.string.dialog_btn_cancel, null)
                .setPositiveButton(R.string.dialog_btn_save, (dialog, which) -> {
                    int altura = picker.getValue();
                    AppInputValidator.ValidationResult<Integer> validation =
                            AppInputValidator.validateHeight(fragment.requireContext(), altura);
                    if (!validation.isValid()) {
                        onError.accept(validationError(validation));
                        return;
                    }
                    viewModel.updatePerfil(new ProfilePatchPayload().altura(validation.getValue()).toJson());
                })
                .show();
    }

    // ── Picker de Peso (40–200 kg, pasos de 0.5) ─────────────────────────────

    /**
     * Muestra el selector de peso con incrementos de medio kilo y valida el resultado.
     */
    public void showPesoPickerDialog() {
        final double pesoMin  = 20.0;
        final double pesoMax  = 300.0;
        final double pesoStep = 0.5;
        final int totalItems  = (int) ((pesoMax - pesoMin) / pesoStep) + 1;

        String[] labels = new String[totalItems];
        for (int i = 0; i < totalItems; i++) {
            double val = pesoMin + i * pesoStep;
            labels[i] = String.format(Locale.getDefault(), "%.1f kg", val);
        }

        NumberPicker picker = new NumberPicker(fragment.requireContext());
        picker.setMinValue(0);
        picker.setMaxValue(totalItems - 1);
        picker.setDisplayedValues(labels);
        picker.setWrapSelectorWheel(false);

        int initialIndex = (int) ((70.0 - pesoMin) / pesoStep);
        PerfilUsuario perfilPeso = perfilSupplier.get();
        if (perfilPeso != null && perfilPeso.peso != null) {
            double current = perfilPeso.peso;
            if (current >= pesoMin && current <= pesoMax) {
                initialIndex = (int) Math.round((current - pesoMin) / pesoStep);
            }
        }
        picker.setValue(initialIndex);

        int pad = (int) (16 * fragment.getResources().getDisplayMetrics().density);
        picker.setPadding(pad, pad, pad, pad);

        new MaterialAlertDialogBuilder(fragment.requireContext())
                .setTitle(R.string.profile_label_peso)
                .setView(picker)
                .setNegativeButton(R.string.dialog_btn_cancel, null)
                .setPositiveButton(R.string.dialog_btn_save, (dialog, which) -> {
                    double peso = pesoMin + picker.getValue() * pesoStep;
                    peso = Math.round(peso * 10.0) / 10.0;
                    AppInputValidator.ValidationResult<Double> validation =
                            AppInputValidator.validateWeight(fragment.requireContext(), peso);
                    if (!validation.isValid()) {
                        onError.accept(validationError(validation));
                        return;
                    }
                    viewModel.updatePerfil(new ProfilePatchPayload().peso(validation.getValue()).toJson());
                })
                .show();
    }

    /**
     * Muestra un diálogo genérico de edición textual con validación básica de requerido.
     *
     * @param label etiqueta usada como título y hint.
     * @param currentValue valor actual a precargar.
     * @param inputType tipo de entrada Android del campo.
     * @param required indica si el campo no puede quedar vacío.
     * @param listener callback que decide si el diálogo debe cerrarse tras guardar.
     */
    public void showEditTextDialog(@NonNull String label,
                                   @Nullable String currentValue,
                                   int inputType,
                                   boolean required,
                                   @NonNull OnValueSavedListener listener) {
        DialogEditFieldBinding dialogBinding =
                DialogEditFieldBinding.inflate(LayoutInflater.from(fragment.requireContext()));

        dialogBinding.tilField.setHint(label);
        if (StringUtils.hasText(currentValue)) {
            dialogBinding.etField.setText(currentValue);
            dialogBinding.etField.setSelection(currentValue.length());
        }
        dialogBinding.etField.setInputType(inputType);

        AlertDialog dialog = new MaterialAlertDialogBuilder(fragment.requireContext())
                .setTitle(label)
                .setView(dialogBinding.getRoot())
                .setPositiveButton(R.string.dialog_btn_save, null)
                .setNegativeButton(R.string.dialog_btn_cancel, null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = dialogBinding.etField.getText() != null
                    ? dialogBinding.etField.getText().toString().trim() : "";
            if (required && value.isEmpty()) {
                dialogBinding.tilField.setError(fragment.getString(R.string.dialog_error_required));
                return;
            }
            dialogBinding.tilField.setError(null);
            if (listener.onSaved(value)) {
                dialog.dismiss();
            }
        }));

        dialog.show();
    }

    /**
     * Muestra el selector de fecha de nacimiento limitando las fechas a usuarios mayores de edad.
     */
    public void showBirthDatePicker() {
        LocalDate maxAllowedDate = LocalDate.now(ZoneOffset.UTC).minusYears(18);
        long maxAllowedMillis = maxAllowedDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        long minMillis = LocalDate.of(1900, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();

        CalendarConstraints constraints = new CalendarConstraints.Builder()
                .setStart(minMillis)
                .setEnd(maxAllowedMillis)
                .setValidator(DateValidatorPointBackward.before(maxAllowedMillis))
                .build();

        MaterialDatePicker.Builder<Long> builder = MaterialDatePicker.Builder.datePicker()
                .setTitleText(R.string.profile_label_birthdate)
                .setCalendarConstraints(constraints);

        if (perfilSupplier.get() != null && StringUtils.hasText(perfilSupplier.get().fechaNacimiento)) {
            try {
                LocalDate parsed = LocalDate.parse(perfilSupplier.get().fechaNacimiento);
                long utcMillis = parsed.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
                builder.setSelection(utcMillis);
            } catch (Exception ignored) { }
        }

        MaterialDatePicker<Long> picker = builder.build();

        picker.addOnPositiveButtonClickListener(selection -> {
            LocalDate selectedDate = Instant.ofEpochMilli(selection)
                    .atZone(ZoneOffset.UTC)
                    .toLocalDate();

            AppInputValidator.ValidationResult<String> validation =
                    AppInputValidator.validateBirthDate(fragment.requireContext(), selectedDate);
            if (!validation.isValid()) {
                onError.accept(validationError(validation));
                return;
            }

            String fecha = validation.getValue();
            String currentValue = perfilSupplier.get() != null ? perfilSupplier.get().fechaNacimiento : null;
            if (AppInputValidator.sameText(currentValue, fecha)) {
                return;
            }

            viewModel.updatePerfil(new ProfilePatchPayload()
                    .fechaNacimiento(fecha)
                    .toJson());
        });

        picker.show(fragment.getParentFragmentManager(), "birthdate_picker");
    }

    /**
     * Muestra el selector de provincia usando las etiquetas localizadas visibles en UI.
     */
    public void showEditProvinciaDialog() {
        DialogEditProvinciaBinding dialogBinding =
                DialogEditProvinciaBinding.inflate(LayoutInflater.from(fragment.requireContext()));

        String[] provincias = fragment.getResources().getStringArray(R.array.provincias_labels);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                fragment.requireContext(), android.R.layout.simple_dropdown_item_1line, provincias);
        dialogBinding.actvProvincia.setAdapter(adapter);
        dialogBinding.tilProvincia.setHint(fragment.getString(R.string.profile_label_provincia));

        if (perfilSupplier.get() != null && StringUtils.hasText(perfilSupplier.get().provincia)) {
            dialogBinding.actvProvincia.setText(
                    ProfileValueLocalizer.displayProvincia(fragment.requireContext(), perfilSupplier.get().provincia),
                    false
            );
        }

        dialogBinding.actvProvincia.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) dialogBinding.actvProvincia.showDropDown();
        });
        dialogBinding.actvProvincia.setOnClickListener(v ->
                dialogBinding.actvProvincia.showDropDown());

        AlertDialog dialog = new MaterialAlertDialogBuilder(fragment.requireContext())
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
                    dialogBinding.tilProvincia.setError(fragment.getString(R.string.dialog_error_required));
                    return;
                }
                dialogBinding.tilProvincia.setError(null);
                String provinciaValue = ProfileValueLocalizer.canonicalProvinciaFromLabel(fragment.requireContext(), value);
                viewModel.updatePerfil(new ProfilePatchPayload()
                        .provincia(provinciaValue)
                        .toJson());
                dialog.dismiss();
            });
        });

        dialog.show();
    }

    /**
     * Muestra el selector rápido de género y persiste la opción elegida.
     */
    public void showGeneroDialog() {
        String[] opciones = fragment.getResources().getStringArray(R.array.generos_labels);
        new MaterialAlertDialogBuilder(fragment.requireContext())
                .setTitle(R.string.profile_label_genero)
                .setItems(opciones, (d, which) ->
                        viewModel.updatePerfil(new ProfilePatchPayload()
                                .genero(ProfileValueLocalizer.canonicalGeneroFromLabel(fragment.requireContext(), opciones[which]))
                                .toJson()))
                .setNegativeButton(R.string.dialog_btn_cancel, null)
                .show();
    }


    /**
     * Muestra el selector de idioma de la app y fuerza recreación visual si cambia el modo.
     */
    public void showLanguageDialog() {
        String[] modes = fragment.getResources().getStringArray(R.array.app_language_modes);
        String[] labels = fragment.getResources().getStringArray(R.array.app_language_labels);

        int checkedItem = findLanguageModeIndex(viewModel.getAppLanguageMode());

        new MaterialAlertDialogBuilder(fragment.requireContext())
                .setTitle(R.string.profile_language_selector_title)
                .setSingleChoiceItems(labels, checkedItem, (dialog, which) -> {
                    String selectedMode = modes[which];
                    if (!selectedMode.equals(viewModel.getAppLanguageMode())) {
                        startUiRecreationWithSplash(() -> {
                            AppLanguageManager.saveOnly(fragment.requireContext(), selectedMode);
                            fragment.requireActivity().recreate();
                        });
                    }
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.dialog_btn_cancel, null)
                .show();
    }



    /**
     * Ejecuta una acción que recrea la UI dejando preparada la transición visual con splash.
     *
     * @param action acción que aplicará el cambio y recreará la activity.
     */
    public void startUiRecreationWithSplash(@NonNull Runnable action) {
        Context context = fragment.requireContext();
        AppSettingsManager.requestUiTransitionSplash(context);

        Activity activity = fragment.requireActivity();
        if (activity instanceof MainActivity) {
            ((MainActivity) activity).showUiTransitionSplashNow();
        }

        action.run();
    }

    /**
     * Busca la posición del modo de idioma dentro del array configurable de recursos.
     *
     * @param mode modo actualmente guardado.
     * @return índice seleccionado por defecto para el diálogo.
     */
    public int findLanguageModeIndex(@Nullable String mode) {
        String normalizedMode = AppLanguageManager.sanitizeSelectableMode(mode);
        String[] modes = fragment.getResources().getStringArray(R.array.app_language_modes);
        for (int i = 0; i < modes.length; i++) {
            if (modes[i].equals(normalizedMode)) return i;
        }
        return 0;
    }

    /**
     * Extrae un mensaje de error usable desde un {@link AppInputValidator.ValidationResult}.
     *
     * @param result resultado de validación.
     * @return texto de error o un fallback genérico si faltara.
     */
    @NonNull
    public String validationError(@NonNull AppInputValidator.ValidationResult<?> result) {
        String msg = result.getErrorMessage();
        return msg != null ? msg : fragment.getString(R.string.vm_error_generico);
    }
}

