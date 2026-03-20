package com.proyecto.moveon.ui.profile;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.proyecto.moveon.R;
import com.proyecto.moveon.core.concurrency.MoveOnExecutors;
import com.proyecto.moveon.databinding.BottomSheetShareRoutesBinding;
import com.proyecto.moveon.domain.activity.ActividadItem;
import com.proyecto.moveon.ui.common.BaseExpandedBottomSheetDialogFragment;
import com.proyecto.moveon.ui.common.TopSnackbar;
import com.proyecto.moveon.ui.common.UiState;
import com.proyecto.moveon.utils.StringUtils;

import java.io.IOException;
import java.util.List;

/**
 * Bottom sheet expandido que muestra todas las rutas del usuario para compartir.
 *
 * <p>En esta segunda versión el flujo queda así:</p>
 * <ol>
 *     <li>Se cargan las actividades del usuario.</li>
 *     <li>El usuario toca una actividad.</li>
 *     <li>Se valida que exista polilínea.</li>
 *     <li>Se genera una imagen en segundo plano.</li>
 *     <li>Se abre un segundo bottom sheet con preview.</li>
 *     <li>Desde el preview se lanza el chooser de Android.</li>
 * </ol>
 */
public class ShareRoutesBottomSheet extends BaseExpandedBottomSheetDialogFragment {

    public static final String TAG = "share_routes_sheet";

    private BottomSheetShareRoutesBinding binding;
    private ShareRoutesAdapter adapter;
    private ShareRoutesViewModel viewModel;
    private boolean isSharingInProgress;

    @NonNull
    public static ShareRoutesBottomSheet newInstance() {
        return new ShareRoutesBottomSheet();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = BottomSheetShareRoutesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adapter = new ShareRoutesAdapter(this::onRouteSelected);
        binding.rvShareRoutes.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvShareRoutes.setAdapter(adapter);

        viewModel = new ViewModelProvider(this).get(ShareRoutesViewModel.class);
        observeViewModel();
        viewModel.load();
    }

    /**
     * Observa el estado del ViewModel y actualiza la UI.
     */
    private void observeViewModel() {
        viewModel.getState().observe(getViewLifecycleOwner(), state -> {
            if (state == null || binding == null) return;
            renderState(state);
        });
    }

    /**
     * Pinta lista, estado vacío o error de carga.
     */
    private void renderState(@NonNull UiState<List<ActividadItem>> state) {
        boolean showLoading = state.loading && (state.data == null || state.data.isEmpty()) && !isSharingInProgress;
        binding.progressShareRoutes.setVisibility(showLoading ? View.VISIBLE : View.GONE);

        if (state.data != null) {
            adapter.submitList(state.data);

            boolean empty = state.data.isEmpty();
            binding.tvEmptyShareRoutes.setVisibility(empty ? View.VISIBLE : View.GONE);
            binding.rvShareRoutes.setVisibility(empty ? View.GONE : View.VISIBLE);
        }

        if (state.error != null && (state.data == null || state.data.isEmpty())) {
            binding.rvShareRoutes.setVisibility(View.GONE);
            binding.tvEmptyShareRoutes.setVisibility(View.VISIBLE);
            binding.tvEmptyShareRoutes.setText(R.string.share_routes_empty);
            TopSnackbar.error(binding.getRoot(), getString(R.string.share_routes_error_loading));
        }
    }

    /**
     * Gestiona el clic sobre una actividad y abre el preview antes de compartir.
     */
    private void onRouteSelected(@NonNull ActividadItem item) {
        if (binding == null || isSharingInProgress) {
            return;
        }

        // Regla pedida: si no hay polilínea, se avisa y no se genera nada.
        if (!StringUtils.hasText(item.rutaPolilinea)) {
            TopSnackbar.error(binding.getRoot(), getString(R.string.share_routes_error_no_polyline));
            return;
        }

        setSharingInProgress(true);

        final Context appContext = requireContext().getApplicationContext();
        MoveOnExecutors.io().execute(() -> {
            try {
                // Generamos la imagen fuera del hilo principal para no bloquear la UI.
                Uri uri = ShareRouteImageGenerator.generateShareImage(appContext, item);
                String shareText = ShareRouteFormatter.buildShareText(appContext, item);

                FragmentActivity activity = getActivity();
                if (activity == null) return;

                activity.runOnUiThread(() -> {
                    if (binding == null) return;
                    setSharingInProgress(false);
                    openPreview(uri, shareText);
                });
            } catch (IllegalArgumentException e) {
                FragmentActivity activity = getActivity();
                if (activity == null) return;
                activity.runOnUiThread(() -> {
                    if (binding == null) return;
                    setSharingInProgress(false);
                    TopSnackbar.error(binding.getRoot(), getString(R.string.share_routes_error_no_polyline));
                });
            } catch (IOException e) {
                FragmentActivity activity = getActivity();
                if (activity == null) return;
                activity.runOnUiThread(() -> {
                    if (binding == null) return;
                    setSharingInProgress(false);
                    TopSnackbar.error(binding.getRoot(), getString(R.string.share_routes_error_generating_image));
                });
            } catch (Exception e) {
                FragmentActivity activity = getActivity();
                if (activity == null) return;
                activity.runOnUiThread(() -> {
                    if (binding == null) return;
                    setSharingInProgress(false);
                    TopSnackbar.error(binding.getRoot(), getString(R.string.share_routes_error_generating_image));
                });
            }
        });
    }

    /**
     * Abre el segundo bottom sheet con la vista previa de la tarjeta.
     */
    private void openPreview(@NonNull Uri uri, @NonNull String shareText) {
        if (!isAdded()) {
            return;
        }

        if (getParentFragmentManager().isStateSaved()) {
            TopSnackbar.error(binding.getRoot(), getString(R.string.share_routes_error_opening_preview));
            return;
        }

        ShareRoutePreviewBottomSheet.newInstance(uri, shareText)
                .show(getParentFragmentManager(), ShareRoutePreviewBottomSheet.TAG);

        // Cerramos el listado para que la navegación entre sheets quede limpia.
        dismissAllowingStateLoss();
    }

    /**
     * Bloquea temporalmente la UI del sheet durante la generación de la imagen.
     */
    private void setSharingInProgress(boolean sharing) {
        isSharingInProgress = sharing;

        if (binding == null) return;

        binding.progressShareRoutes.setVisibility(sharing ? View.VISIBLE : View.GONE);
        binding.rvShareRoutes.setEnabled(!sharing);
        binding.rvShareRoutes.setAlpha(sharing ? 0.5f : 1f);
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
