package com.proyecto.moveon.ui.profile;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.proyecto.moveon.R;
import com.proyecto.moveon.databinding.BottomSheetLicensesBinding;
import com.proyecto.moveon.ui.common.BaseExpandedBottomSheetDialogFragment;

/**
 * Bottom sheet informativo con el resumen de licencias de terceros.
 */
public class LicensesBottomSheet extends BaseExpandedBottomSheetDialogFragment {

    @Nullable private BottomSheetLicensesBinding binding;

    /**
     * Infla el layout del sheet de licencias de terceros y guarda el
     * ViewBinding para liberarlo en {@link #onDestroyView()}.
     *
     * @param inflater inflator proporcionado por el sistema.
     * @param container contenedor padre al que se adjuntará la vista.
     * @param savedInstanceState estado guardado o {@code null}.
     * @return la raíz de la vista inflada.
     */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = BottomSheetLicensesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    /**
     * Fija el texto de las licencias (hardcodeado como recurso
     * {@code R.string.profile_about_contenido_licencia}) y enlaza el botón
     * de cerrar. No carga nada dinámicamente: el contenido es estático y
     * vive en recursos para localizarse.
     *
     * @param view vista raíz creada por {@link #onCreateView}.
     * @param savedInstanceState estado guardado o {@code null}.
     */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (binding == null) return;

        binding.tvLicensesContent.setText(R.string.profile_about_contenido_licencia);
        binding.btnCloseLicenses.setOnClickListener(v -> dismissAllowingStateLoss());
    }

    /**
     * Libera el ViewBinding para evitar fugas cuando el fragment sobreviva
     * a la vista (rotación, cambio de configuración).
     */
    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
