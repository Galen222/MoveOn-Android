package com.proyecto.moveon.ui.profile;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.proyecto.moveon.databinding.BottomSheetLegalTextBinding;
import com.proyecto.moveon.ui.common.BaseExpandedBottomSheetDialogFragment;

/**
 * Bottom sheet reutilizable para mostrar texto legal largo.
 */
public class LegalTextBottomSheet extends BaseExpandedBottomSheetDialogFragment {

    public static final String TAG = "legal_text_sheet";

    private static final String ARG_TITLE = "arg_title";
    private static final String ARG_CONTENT = "arg_content";

    @Nullable private BottomSheetLegalTextBinding binding;

    /**
     * Factoría que crea el sheet con los argumentos ya empaquetados en un
     * {@link Bundle}. Usar esta vía en vez de pasar los datos al constructor
     * es obligado en fragments para que sobrevivan a una recreación del
     * proceso sin perder sus argumentos.
     *
     * @param title título mostrado en la cabecera del sheet.
     * @param content cuerpo legal a mostrar (términos, privacidad…).
     * @return instancia lista para mostrar con {@code show(FragmentManager, tag)}.
     */
    @NonNull
    public static LegalTextBottomSheet newInstance(@NonNull String title, @NonNull String content) {
        LegalTextBottomSheet fragment = new LegalTextBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_TITLE, title);
        args.putString(ARG_CONTENT, content);
        fragment.setArguments(args);
        return fragment;
    }

    /**
     * Infla el layout del sheet y guarda el ViewBinding; la referencia se
     * libera en {@link #onDestroyView()}.
     *
     * @param inflater inflator proporcionado por el sistema.
     * @param container contenedor padre al que se adjunta la vista.
     * @param savedInstanceState estado guardado o {@code null}.
     * @return la raíz de la vista inflada.
     */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = BottomSheetLegalTextBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    /**
     * Vuelca los argumentos del {@link Bundle} a los TextView del sheet.
     * Si no hay argumentos o el binding no está disponible (situación
     * anómala tras una recreación), cierra el diálogo con
     * {@code dismissAllowingStateLoss} para no dejar una UI vacía.
     *
     * @param view vista raíz creada por {@link #onCreateView}.
     * @param savedInstanceState estado guardado o {@code null}.
     */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        BottomSheetLegalTextBinding b = binding;
        Bundle args = getArguments();
        if (b == null || args == null) {
            dismissAllowingStateLoss();
            return;
        }

        b.tvLegalTitle.setText(args.getString(ARG_TITLE, ""));
        b.tvLegalContent.setText(args.getString(ARG_CONTENT, ""));
        b.btnCloseLegal.setOnClickListener(v -> dismissAllowingStateLoss());
    }

    /**
     * Libera el ViewBinding para evitar fugas de la jerarquía de vistas
     * mientras el fragment sigue vivo (por ejemplo durante una rotación).
     */
    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
