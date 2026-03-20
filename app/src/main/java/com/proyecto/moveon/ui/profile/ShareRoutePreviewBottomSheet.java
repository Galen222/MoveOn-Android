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

import com.proyecto.moveon.R;
import com.proyecto.moveon.databinding.BottomSheetShareRoutePreviewBinding;
import com.proyecto.moveon.ui.common.BaseExpandedBottomSheetDialogFragment;
import com.proyecto.moveon.ui.common.TopSnackbar;

/**
 * Bottom sheet que muestra una vista previa de la imagen antes de abrir el chooser de Android.
 *
 * <p>Se abre después de generar el PNG temporal. Desde aquí el usuario puede revisar la tarjeta,
 * cancelar o lanzar la acción de compartir.</p>
 */
public class ShareRoutePreviewBottomSheet extends BaseExpandedBottomSheetDialogFragment {

    public static final String TAG = "share_route_preview_sheet";

    private static final String ARG_URI = "arg_uri";
    private static final String ARG_SHARE_TEXT = "arg_share_text";

    private BottomSheetShareRoutePreviewBinding binding;
    private Uri previewUri;
    private String shareText;

    /**
     * Crea una nueva instancia del sheet de preview.
     *
     * @param uri       Uri segura del PNG temporal generado con FileProvider.
     * @param shareText Texto resumen que se enviará junto a la imagen.
     * @return instancia del bottom sheet.
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

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = BottomSheetShareRoutePreviewBinding.inflate(inflater, container, false);
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
        shareText = args.getString(ARG_SHARE_TEXT, "");

        if (uriString.isEmpty()) {
            dismissAllowingStateLoss();
            return;
        }

        previewUri = Uri.parse(uriString);

        // Cargamos la imagen generada en el ImageView para que el usuario vea exactamente lo que se enviará.
        binding.ivShareRoutePreview.setImageURI(previewUri);
        binding.tvShareRoutePreviewSummary.setText(shareText);

        binding.btnShareRouteNow.setOnClickListener(v -> shareRoute());
        binding.btnClosePreview.setOnClickListener(v -> dismissAllowingStateLoss());
    }

    /**
     * Abre el chooser de Android con la imagen y el texto resumen adjuntos.
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
            TopSnackbar.error(binding.getRoot(), getString(R.string.share_routes_error_no_apps));
        }
    }

    /**
     * Concede permisos explícitos de lectura para destinos que los exigen con content:// URIs.
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

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
