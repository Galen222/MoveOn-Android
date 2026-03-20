package com.proyecto.moveon.ui.common;

import android.app.Dialog;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

/**
 * Bottom sheet base que fuerza el estado expandido.
 *
 * <p>Se reutiliza para unificar el comportamiento visual de paneles
 * inferiores importantes, como eliminar cuenta o alertas de tracking.
 * Mantener la expansión aquí evita repetir el mismo bloque en cada sheet.</p>
 */
public abstract class BaseExpandedBottomSheetDialogFragment extends BottomSheetDialogFragment {

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(d -> {
            BottomSheetDialog bottomSheetDialog = (BottomSheetDialog) d;
            View bottomSheet = bottomSheetDialog.findViewById(
                    com.google.android.material.R.id.design_bottom_sheet
            );
            if (bottomSheet == null) {
                return;
            }

            BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            behavior.setSkipCollapsed(true);
            behavior.setFitToContents(true);
        });
        return dialog;
    }
}
