package com.proyecto.moveon.ui.profile;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.proyecto.moveon.R;
import com.proyecto.moveon.data.session.SecureSessionManager;
import com.proyecto.moveon.databinding.BottomSheetDeleteAccountBinding;
import com.proyecto.moveon.ui.common.BaseExpandedBottomSheetDialogFragment;

/**
 * BottomSheet de confirmación para eliminar cuenta.
 *
 * <p>Ahora hereda del componente base expandido que también reutilizan
 * las alertas de tracking para mantener el mismo comportamiento visual.</p>
 */
public class DeleteAccountBottomSheet extends BaseExpandedBottomSheetDialogFragment {

    public static final String TAG = "delete_account_sheet";

    /** Gestor de sesión usado para decidir si la cuenta actual está vinculada con Google. */
    @Nullable private SecureSessionManager sessionManager;

    /**
     * Callback invocado cuando el usuario confirma que quiere eliminar su cuenta.
     */
    public interface OnDeleteConfirmedListener {
        /**
         * Notifica a la pantalla anfitriona que ya puede iniciar la operación remota de borrado.
         */
        void onDeleteAccountConfirmed();
    }

    private BottomSheetDeleteAccountBinding binding;
    @Nullable private OnDeleteConfirmedListener listener;

    /**
     * Crea una nueva instancia del sheet de confirmación.
     *
     * @return fragment listo para mostrarse desde {@link ProfileFragment}.
     */
    public static DeleteAccountBottomSheet newInstance() {
        return new DeleteAccountBottomSheet();
    }

    /**
     * Obtiene el {@link SecureSessionManager} para decidir si debe mostrarse el aviso de cuenta
     * vinculada con Google.
     *
     * @param context contexto anfitrión asociado al fragment.
     */
    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        sessionManager = SecureSessionManager.getInstance(context);
    }

    /**
     * Registra el listener que recibirá la confirmación final del usuario.
     *
     * @param listener receptor de la acción de borrado, o {@code null} para desregistrarlo.
     */
    public void setOnDeleteConfirmedListener(@Nullable OnDeleteConfirmedListener listener) {
        this.listener = listener;
    }

    /**
     * Infla el contenido del sheet y conserva el binding mientras la vista siga activa.
     *
     * @param inflater inflador de vistas del fragment.
     * @param container contenedor padre proporcionado por el sistema, puede ser {@code null}.
     * @param savedInstanceState estado restaurado por Android, puede ser {@code null}.
     * @return vista raíz del bottom sheet.
     */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = BottomSheetDeleteAccountBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    /**
     * Configura la palabra de confirmación y enlaza las acciones principales del bottom sheet.
     *
     * @param view vista raíz recién creada.
     * @param savedInstanceState estado previo restaurado por Android, puede ser {@code null}.
     */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        String confirmWord = getString(R.string.profile_delete_confirm_word);
        bindGoogleLinkItem();

        binding.etConfirm.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            /**
             * Habilita o deshabilita la confirmación según si el texto escrito coincide con la palabra exigida.
             *
             * @param s contenido editable actual del campo de confirmación.
             */
            @Override
            public void afterTextChanged(Editable s) {
                boolean matches = confirmWord.equals(s.toString().trim());
                binding.btnConfirmDelete.setEnabled(matches);
            }
        });

        binding.btnConfirmDelete.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDeleteAccountConfirmed();
            }
        });

        binding.btnCancel.setOnClickListener(v -> dismiss());
    }

    /**
     * Libera referencias al desmontarse el fragment de su contexto anfitrión.
     */
    @Override
    public void onDetach() {
        sessionManager = null;
        listener = null;
        super.onDetach();
    }

    /**
     * Limpia el binding al destruir la jerarquía visual del sheet.
     */
    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    /**
     * Muestra el cuarto elemento solo cuando la sesión actual pertenece a Google.
     */
    private void bindGoogleLinkItem() {
        BottomSheetDeleteAccountBinding b = binding;
        if (b == null) return;

        boolean showGoogleLink = sessionManager != null && sessionManager.isLoggedWithGoogle();

        // El separador previo también se oculta para no dejar un hueco visual.
        b.deleteAccountGoogleDivider.setVisibility(showGoogleLink ? View.VISIBLE : View.GONE);
        b.tvDeleteAccountGoogleLink.setVisibility(showGoogleLink ? View.VISIBLE : View.GONE);
    }

    /**
     * Cambia el estado del sheet durante la operación remota.
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
     * Muestra un error en el campo de confirmación.
     */
    public void setError(@Nullable String message) {
        if (binding == null) return;

        setLoading(false);
        binding.tilConfirm.setError(message != null
                ? message
                : getString(R.string.profile_error_delete_account));
    }
}
