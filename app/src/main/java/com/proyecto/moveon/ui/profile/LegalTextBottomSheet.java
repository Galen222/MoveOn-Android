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

    @NonNull
    public static LegalTextBottomSheet newInstance(@NonNull String title, @NonNull String content) {
        LegalTextBottomSheet fragment = new LegalTextBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_TITLE, title);
        args.putString(ARG_CONTENT, content);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = BottomSheetLegalTextBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

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

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
