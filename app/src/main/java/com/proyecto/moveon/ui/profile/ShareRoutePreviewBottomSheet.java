package com.proyecto.moveon.ui.profile;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;

import com.proyecto.moveon.R;
import com.proyecto.moveon.databinding.BottomSheetShareRoutePreviewBinding;
import com.proyecto.moveon.ui.common.BaseExpandedBottomSheetDialogFragment;

/**
 * Bottom sheet que muestra una vista previa de la imagen antes de abrir el chooser de Android.
 *
 * <p>Se abre después de generar el PNG temporal. Desde aquí el usuario puede revisar la tarjeta,
 * cancelar o lanzar la acción de compartir.</p>
 *
 * <p>En esta revisión la previsualización ya no muestra el texto adjunto del share. Ese copy se
 * sigue enviando en el intent de compartir, pero se oculta en la UI para dejar una tarjeta más
 * limpia y centrada en la imagen.</p>
 */
public class ShareRoutePreviewBottomSheet extends BaseExpandedBottomSheetDialogFragment {

    /** Tag estable del fragment para el {@link androidx.fragment.app.FragmentManager}. */
    public static final String TAG = "share_route_preview_sheet";

    /** Argumento con la {@link Uri} serializada del PNG temporal. */
    private static final String ARG_URI = "arg_uri";

    /** Argumento con el texto que debe enviarse junto a la imagen al compartir. */
    private static final String ARG_SHARE_TEXT = "arg_share_text";

    /** Binding de la vista del bottom sheet. Se limpia en {@link #onDestroyView()}. */
    @Nullable
    private BottomSheetShareRoutePreviewBinding binding;

    /** Uri segura de la imagen generada con {@code FileProvider}. */
    @Nullable
    private Uri previewUri;

    /** Texto que se adjunta al intent de compartir, aunque no se muestre en la preview. */
    @Nullable
    private String shareText;

    /**
     * Crea una nueva instancia del sheet de preview.
     *
     * @param uri Uri segura del PNG temporal generado con FileProvider.
     * @param shareText texto resumen que se enviará junto a la imagen.
     * @return instancia del bottom sheet lista para mostrarse.
     */
    @NonNull
    public static ShareRoutePreviewBottomSheet newInstance(@NonNull Uri uri,
                                                           @NonNull String shareText) {
        ShareRoutePreviewBottomSheet sheet = new ShareRoutePreviewBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_URI, uri.toString());
        args.putString(ARG_SHARE_TEXT, shareText);
        sheet.setArguments(args);
        return sheet;
    }

    /**
     * Infla el layout del bottom sheet de previsualización y guarda el
     * ViewBinding para liberar referencias en {@link #onDestroyView()}.
     *
     * @param inflater inflator proporcionado por el sistema.
     * @param container contenedor padre al que se adjuntará la vista.
     * @param savedInstanceState estado guardado, puede ser {@code null}.
     * @return la raíz de la vista inflada.
     */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = BottomSheetShareRoutePreviewBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    /**
     * Lee los argumentos del sheet (imagen a previsualizar, textos) y
     * cablea botones de compartir/cancelar cuando la vista ya existe.
     *
     * @param view vista raíz creada por {@link #onCreateView}.
     * @param savedInstanceState estado guardado, puede ser {@code null}.
     */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        BottomSheetShareRoutePreviewBinding currentBinding = binding;
        Bundle args = getArguments();
        if (currentBinding == null || args == null) {
            dismissAllowingStateLoss();
            return;
        }

        String uriString = args.getString(ARG_URI, "");
        shareText = args.getString(ARG_SHARE_TEXT, "");

        if (uriString.isEmpty()) {
            dismissAllowingStateLoss();
            return;
        }

        previewUri = Uri.parse(uriString);

        // La vista previa solo muestra la imagen. El texto del share se sigue enviando
        // en el chooser, pero ya no se pinta en pantalla para mantener la tarjeta limpia.
        currentBinding.ivShareRoutePreview.setImageURI(previewUri);
        currentBinding.ivShareRoutePreview.setOnClickListener(v -> openFullscreenPreview());
        currentBinding.btnShareRouteNow.setOnClickListener(v -> shareRoute());
        currentBinding.btnClosePreview.setOnClickListener(v -> dismissAllowingStateLoss());
    }

    /**
     * Abre la imagen de preview a pantalla completa sobre el mismo fondo base de la app.
     *
     * <p>Se cancela silenciosamente si el fragment ya no está añadido o si el estado del
     * {@link FragmentManager} ya quedó guardado.</p>
     */
    private void openFullscreenPreview() {
        if (previewUri == null || !isAdded()) {
            return;
        }

        FragmentManager fragmentManager = getParentFragmentManager();
        if (fragmentManager.isStateSaved()) {
            return;
        }

        RouteImageFullscreenDialogFragment
                .newInstance(previewUri)
                .show(fragmentManager, RouteImageFullscreenDialogFragment.TAG);
    }

    /**
     * Abre el chooser de Android con la imagen y el texto resumen adjuntos.
     *
     * <p>Antes de lanzar el chooser concede permisos explícitos de lectura a las apps destino
     * para evitar fallos con URIs {@code content://} compartidas mediante {@code FileProvider}.</p>
     */
    private void shareRoute() {
        if (binding == null || previewUri == null) {
            return;
        }

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("image/png");
        intent.putExtra(Intent.EXTRA_STREAM, previewUri);
        intent.putExtra(Intent.EXTRA_TEXT, shareText);
        intent.setClipData(ClipData.newRawUri("shared_route_preview", previewUri));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        grantReadPermissionToResolvedApps(intent, previewUri);

        try {
            startActivity(Intent.createChooser(intent, getString(R.string.share_routes_chooser_title)));
            dismissAllowingStateLoss();
        } catch (ActivityNotFoundException e) {
            // Se muestra dentro de la ventana del bottom sheet para que no quede
            // oculto por detrás del propio diálogo.
            showSheetErrorSnackbar(getString(R.string.share_routes_error_no_apps));
        }
    }

    /**
     * Concede permisos explícitos de lectura para destinos que los exigen con URIs {@code content://}.
     *
     * @param intent intent de compartir ya configurado.
     * @param uri uri de la imagen temporal compartida.
     */
    private void grantReadPermissionToResolvedApps(@NonNull Intent intent, @NonNull Uri uri) {
        PackageManager pm = requireContext().getPackageManager();
        for (ResolveInfo info : pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)) {
            if (info.activityInfo != null && info.activityInfo.packageName != null) {
                requireContext().grantUriPermission(
                        info.activityInfo.packageName,
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                );
            }
        }
    }

    /**
     * Libera el ViewBinding para evitar fugas: el fragment puede sobrevivir
     * a la vista durante rotaciones, y mantener la referencia impediría
     * que el sistema recicle la jerarquía antigua.
     */
    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
