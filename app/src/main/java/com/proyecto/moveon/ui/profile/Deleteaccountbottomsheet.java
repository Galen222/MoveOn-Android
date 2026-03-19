package com.proyecto.moveon.ui.profile;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.proyecto.moveon.R;
import com.proyecto.moveon.databinding.BottomSheetDeleteAccountBinding;

/**
 * BottomSheet de confirmación para eliminar cuenta.
 * <p>
 * Sustituye a los dos {@code MaterialAlertDialog} encadenados
 * que se usaban anteriormente, ofreciendo una experiencia más
 * nativa y visual.
 * <p>
 * El flujo es:
 * <ol>
 *   <li>El usuario escribe "DELETE" en el campo de texto.</li>
 *   <li>Se habilita el botón rojo de confirmación.</li>
 *   <li>Al pulsarlo, se notifica al {@link OnDeleteConfirmedListener}.</li>
 *   <li>El Fragment padre observa el estado y llama a
 *       {@link #setLoading(boolean)} / {@link #setError(String)} según proceda.</li>
 * </ol>
 */
public class DeleteAccountBottomSheet extends BottomSheetDialogFragment {

    public static final String TAG = "delete_account_sheet";

    // ── Listener ────────────────────────────────────────────────────────────────

    public interface OnDeleteConfirmedListener {
        void onDeleteAccountConfirmed();
    }

    // ── Estado ──────────────────────────────────────────────────────────────────

    private BottomSheetDeleteAccountBinding binding;
    @Nullable private OnDeleteConfirmedListener listener;

    // ── Factory ─────────────────────────────────────────────────────────────────

    public static DeleteAccountBottomSheet newInstance() {
        return new DeleteAccountBottomSheet();
    }

    // ── Ciclo de vida ───────────────────────────────────────────────────────────

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        // El listener se inyecta manualmente desde ProfileFragment
        // (no se resuelve por onAttach porque el Fragment padre
        // es quien implementa la lógica, no la Activity).
    }

    public void setOnDeleteConfirmedListener(@Nullable OnDeleteConfirmedListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);

        dialog.setOnShowListener(d -> {
            BottomSheetDialog bsd = (BottomSheetDialog) d;
            View bottomSheet = bsd.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
            }
        });

        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = BottomSheetDeleteAccountBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        String confirmWord = getString(R.string.profile_delete_confirm_word);

        // ── TextWatcher: habilita el botón solo si coincide la palabra ──────────
        binding.etConfirm.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                boolean matches = confirmWord.equalsIgnoreCase(s.toString().trim());
                binding.btnConfirmDelete.setEnabled(matches);
            }
        });

        // ── Botón de confirmar ──────────────────────────────────────────────────
        binding.btnConfirmDelete.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDeleteAccountConfirmed();
            }
        });

        // ── Botón de cancelar ───────────────────────────────────────────────────
        binding.btnCancel.setOnClickListener(v -> dismiss());
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    // ── API pública para que ProfileFragment controle el estado ──────────────────

    /**
     * Muestra/oculta el estado de carga en el botón de confirmación.
     *
     * @param loading {@code true} para bloquear el botón y mostrar texto de progreso.
     */
    public void setLoading(boolean loading) {
        if (binding == null) return;

        binding.btnConfirmDelete.setEnabled(!loading);
        binding.etConfirm.setEnabled(!loading);
        binding.btnCancel.setEnabled(!loading);
        setCancelable(!loading);

        binding.btnConfirmDelete.setText(loading
                ? R.string.delete_account_sheet_btn_deleting
                : R.string.delete_account_sheet_btn_delete);
    }

    /**
     * Restaura el bottom sheet al estado interactivo y muestra un error
     * en el campo de texto.
     *
     * @param message Mensaje de error a mostrar. Si es {@code null} se usa un genérico.
     */
    public void setError(@Nullable String message) {
        if (binding == null) return;

        setLoading(false);
        binding.tilConfirm.setError(message != null
                ? message
                : getString(R.string.profile_error_delete_account));
    }
}
