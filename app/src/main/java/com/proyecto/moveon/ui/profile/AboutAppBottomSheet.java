package com.proyecto.moveon.ui.profile;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.proyecto.moveon.BuildConfig;
import com.proyecto.moveon.R;
import com.proyecto.moveon.databinding.BottomSheetAboutAppBinding;
import com.proyecto.moveon.ui.common.BaseExpandedBottomSheetDialogFragment;

/**
 * Bottom sheet de información general de la aplicación.
 */
public class AboutAppBottomSheet extends BaseExpandedBottomSheetDialogFragment {

    public static final String TAG = "about_app_sheet";

    @Nullable private BottomSheetAboutAppBinding binding;

    @NonNull
    public static AboutAppBottomSheet newInstance() {
        return new AboutAppBottomSheet();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = BottomSheetAboutAppBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        BottomSheetAboutAppBinding b = binding;
        if (b == null) return;

        b.ivAboutAppIcon.setImageResource(R.mipmap.ic_launcher_round);
        b.tvAboutAppName.setText(R.string.app_name);
        b.tvAboutAppVersion.setText(getString(
                R.string.profile_about_version_value,
                BuildConfig.VERSION_NAME
        ));

        b.itemAboutTerms.setOnClickListener(v -> openLegalSheet(
                getString(R.string.registro_eula_titulo_terminos),
                getString(R.string.registro_eula_contenido_terminos)
        ));

        b.itemAboutPrivacy.setOnClickListener(v -> openLegalSheet(
                getString(R.string.registro_eula_titulo_politica),
                getString(R.string.registro_eula_contenido_politica)
        ));

        b.itemAboutLicenses.setOnClickListener(v -> openLegalSheet(
                getString(R.string.profile_about_titulo_licencia),
                getString(R.string.profile_about_contenido_licencia)
        ));

        b.itemAboutContact.setOnClickListener(v -> openEmailApp());

        b.btnCloseAbout.setOnClickListener(v -> dismissAllowingStateLoss());
    }

    private void openLegalSheet(@NonNull String title, @NonNull String content) {
        LegalTextBottomSheet.newInstance(title, content)
                .show(getParentFragmentManager(), LegalTextBottomSheet.TAG);
    }

    private void openEmailApp() {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:info.moveon.app@gmail.com"));
        intent.putExtra(Intent.EXTRA_EMAIL, new String[]{"info.moveon.app@gmail.com"});
        intent.putExtra(Intent.EXTRA_SUBJECT,
                getString(R.string.profile_about_contact_subject, getString(R.string.app_name)));

        try {
            startActivity(Intent.createChooser(
                    intent,
                    getString(R.string.profile_about_contact_chooser)
            ));
        } catch (ActivityNotFoundException e) {
            showSheetErrorSnackbar(getString(R.string.profile_about_contact_no_app));
        }
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}

