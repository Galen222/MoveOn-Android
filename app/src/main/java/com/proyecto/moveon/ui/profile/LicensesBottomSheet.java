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

    public static final String TAG = "licenses_sheet";

    @Nullable private BottomSheetLicensesBinding binding;

    @NonNull
    public static LicensesBottomSheet newInstance() {
        return new LicensesBottomSheet();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = BottomSheetLicensesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (binding == null) return;

        binding.tvLicensesContent.setText(R.string.profile_about_licenses_content);
        binding.btnCloseLicenses.setOnClickListener(v -> dismissAllowingStateLoss());
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
