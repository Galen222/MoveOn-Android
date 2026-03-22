package com.proyecto.moveon.ui.profile;

import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;

import com.proyecto.moveon.R;
import com.proyecto.moveon.databinding.DialogRouteImageFullscreenBinding;

/**
 * DialogFragment a pantalla completa para ver la imagen generada de una ruta.
 */
public class RouteImageFullscreenDialogFragment extends DialogFragment {

    public static final String TAG = "route_image_fullscreen_dialog";

    private static final String ARG_URI = "arg_uri";

    private DialogRouteImageFullscreenBinding binding;

    @NonNull
    public static RouteImageFullscreenDialogFragment newInstance(@NonNull Uri uri) {
        RouteImageFullscreenDialogFragment fragment = new RouteImageFullscreenDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_URI, uri.toString());
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NORMAL, com.google.android.material.R.style.ThemeOverlay_Material3_Dialog);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = DialogRouteImageFullscreenBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = getArguments();
        if (args == null) {
            dismissAllowingStateLoss();
            return;
        }

        String uriString = args.getString(ARG_URI, "");
        if (uriString.isEmpty()) {
            dismissAllowingStateLoss();
            return;
        }

        Uri imageUri = Uri.parse(uriString);
        binding.ivRouteFullscreen.setImageURI(imageUri);

        binding.btnCloseFullscreen.setOnClickListener(v -> dismissAllowingStateLoss());
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() == null) return;

        Window window = getDialog().getWindow();
        if (window == null) return;

        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        window.setBackgroundDrawable(new ColorDrawable(
                ContextCompat.getColor(requireContext(), R.color.surfaceBackground)
        ));
        window.getDecorView().setPadding(0, 0, 0, 0);
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
