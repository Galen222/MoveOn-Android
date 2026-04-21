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

    /**
     * Construye el diálogo a pantalla completa con la URI de la imagen que debe mostrarse.
     *
     * @param uri ubicación de la imagen generada para la ruta.
     * @return fragment configurado con los argumentos necesarios para abrir la vista ampliada.
     */
    @NonNull
    public static RouteImageFullscreenDialogFragment newInstance(@NonNull Uri uri) {
        RouteImageFullscreenDialogFragment fragment = new RouteImageFullscreenDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_URI, uri.toString());
        fragment.setArguments(args);
        return fragment;
    }

    /**
     * Aplica el estilo Material del diálogo antes de crear la vista.
     *
     * @param savedInstanceState estado restaurado por Android, puede ser {@code null}.
     */
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NORMAL, com.google.android.material.R.style.ThemeOverlay_Material3_Dialog);
    }

    /**
     * Infla la vista que contiene la imagen ampliada y el botón de cierre.
     *
     * @param inflater inflador de vistas del fragment.
     * @param container contenedor padre proporcionado por el sistema, puede ser {@code null}.
     * @param savedInstanceState estado restaurado por Android, puede ser {@code null}.
     * @return raíz inflada del diálogo a pantalla completa.
     */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = DialogRouteImageFullscreenBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    /**
     * Recupera la URI enviada en los argumentos y la pinta en la vista ampliada.
     *
     * <p>Si el fragment se abre sin datos válidos, se cierra para evitar dejar un diálogo vacío.</p>
     *
     * @param view vista raíz ya creada.
     * @param savedInstanceState estado previo restaurado por Android, puede ser {@code null}.
     */
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

    /**
     * Ajusta el diálogo para ocupar toda la pantalla y reutiliza el color de fondo de la tarjeta
     * que envuelve la imagen compartida.
     */
    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() == null) return;

        Window window = getDialog().getWindow();
        if (window == null) return;

        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        // Igualamos el fondo del diálogo ampliado al mismo gris de la tarjeta que rodea la imagen.
        window.setBackgroundDrawable(new ColorDrawable(
                ContextCompat.getColor(requireContext(), R.color.cardBackground)
        ));
        window.getDecorView().setPadding(0, 0, 0, 0);
    }

    /**
     * Libera el binding cuando la vista del diálogo deja de existir.
     */
    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}