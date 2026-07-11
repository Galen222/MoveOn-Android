package com.proyecto.moveon.ui.common;

import android.app.Dialog;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.proyecto.moveon.R;

/**
 * Bottom sheet base que fuerza el estado expandido y centraliza la lógica de snackbars.
 *
 * <p>Los {@link BottomSheetDialogFragment} viven en una ventana distinta a la Activity.
 * Si un {@link com.google.android.material.snackbar.Snackbar} se ancla al contenedor de
 * la Activity puede quedar oculto detrás del diálogo. Por eso esta clase ofrece helpers
 * para mostrar notificaciones en la propia ventana del bottom sheet, pero con un
 * desplazamiento vertical calculado para que queden a una altura visual coherente con
 * las notificaciones de las pantallas normales.</p>
 */
public abstract class BaseExpandedBottomSheetDialogFragment extends BottomSheetDialogFragment {

    /**
     * Construye el {@link BottomSheetDialog} y fuerza al comportamiento
     * {@code STATE_EXPANDED} en cuanto el sheet es visible, para que las
     * subclases no tengan que duplicar este arranque.
     *
     * @param savedInstanceState estado guardado por el sistema, puede ser {@code null}.
     * @return el diálogo ya configurado para abrirse completamente expandido.
     */
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

    /**
     * Devuelve la vista que debe usarse como ancla del snackbar dentro de la ventana del sheet.
     *
     * <p>Se prioriza el {@code android.R.id.content} del propio diálogo para garantizar que
     * la notificación se dibuje por encima del contenido del bottom sheet y no por detrás de
     * la Activity anfitriona.</p>
     *
     * @return vista válida sobre la que crear el snackbar.
     * @throws IllegalStateException si el fragment no tiene ninguna vista disponible.
     */
    @NonNull
    protected View requireBottomSheetSnackbarAnchor() {
        View anchor = findBottomSheetSnackbarAnchor();
        if (anchor != null) {
            return anchor;
        }

        throw new IllegalStateException(
                "No hay una vista disponible para mostrar el snackbar del bottom sheet."
        );
    }

    /**
     * Muestra un snackbar de éxito usando la ventana del propio bottom sheet.
     *
     * @param message texto a mostrar.
     */
    protected void showSheetSuccessSnackbar(@NonNull CharSequence message) {
        TopSnackbar.success(
                requireBottomSheetSnackbarAnchor(),
                message,
                getBottomSheetSnackbarExtraTopOffsetPx()
        );
    }


    /**
     * Muestra un snackbar de error usando la ventana del propio bottom sheet.
     *
     * @param message texto a mostrar.
     */
    protected void showSheetErrorSnackbar(@NonNull CharSequence message) {
        TopSnackbar.error(
                requireBottomSheetSnackbarAnchor(),
                message,
                getBottomSheetSnackbarExtraTopOffsetPx()
        );
    }

    /**
     * Calcula un desplazamiento extra para alinear visualmente el snackbar del sheet con
     * los snackbars lanzados desde fragmentos normales.
     *
     * <p>La referencia preferida es el contenedor principal de fragmentos de la Activity
     * ({@code frame_layout}). Así, si el bottom sheet vive en una ventana cuyo origen
     * está más arriba que el contenido real de la pantalla, compensamos esa diferencia
     * para evitar que la notificación aparezca demasiado alta.</p>
     *
     * @return offset adicional en píxeles, nunca negativo.
     */
    protected int getBottomSheetSnackbarExtraTopOffsetPx() {
        View anchor = findBottomSheetSnackbarAnchor();
        View hostReference = findHostSnackbarReferenceView();
        if (anchor == null || hostReference == null) {
            return 0;
        }

        int[] anchorLocation = new int[2];
        int[] referenceLocation = new int[2];

        anchor.getLocationOnScreen(anchorLocation);
        hostReference.getLocationOnScreen(referenceLocation);

        return Math.max(0, referenceLocation[1] - anchorLocation[1]);
    }

    /**
     * Busca la mejor vista candidata para pintar snackbars en la ventana del diálogo.
     *
     * @return la vista más estable disponible dentro de la ventana del sheet, o {@code null}
     * si todavía no existe ninguna jerarquía visible.
     */
    @Nullable
    private View findBottomSheetSnackbarAnchor() {
        Dialog dialog = getDialog();
        if (dialog != null) {
            View dialogContent = dialog.findViewById(android.R.id.content);
            if (dialogContent != null) {
                return dialogContent;
            }

            if (dialog.getWindow() != null) {
                return dialog.getWindow().getDecorView();
            }
        }

        return getView();
    }

    /**
     * Busca una vista de referencia en la Activity para reutilizar su alineación vertical.
     *
     * @return contenedor anfitrión cuya posición vertical sirve como referencia, o {@code null}
     * si el fragment no está asociado a ninguna {@link FragmentActivity}.
     */
    @Nullable
    private View findHostSnackbarReferenceView() {
        FragmentActivity activity = getActivity();
        if (activity == null) {
            return null;
        }

        // Referencia principal: contenedor real de fragments del flujo autenticado.
        View fragmentContainer = activity.findViewById(R.id.frame_layout);
        if (fragmentContainer != null) {
            return fragmentContainer;
        }

        // Fallback útil para layouts que no usen frame_layout pero sí un contenedor principal.
        View mainContentContainer = activity.findViewById(R.id.main_content_container);
        if (mainContentContainer != null) {
            return mainContentContainer;
        }

        return activity.findViewById(android.R.id.content);
    }
}
