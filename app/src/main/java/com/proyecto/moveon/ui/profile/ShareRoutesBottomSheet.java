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
import com.proyecto.moveon.core.i18n.AppLanguageManager;
import com.proyecto.moveon.databinding.BottomSheetShareRoutesBinding;
import com.proyecto.moveon.domain.activity.ActividadItem;
import com.proyecto.moveon.ui.common.BaseExpandedBottomSheetDialogFragment;
import com.proyecto.moveon.ui.common.UiState;
import com.proyecto.moveon.utils.StringUtils;

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

    private BottomSheetShareRoutesBinding binding;
    private ShareRoutesAdapter adapter;
    private ShareRoutesViewModel viewModel;
    private boolean isSharingInProgress;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = BottomSheetShareRoutesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    /**
     * Monta el RecyclerView con su adapter, crea el ViewModel específico
     * del sheet y observa sus LiveData. Dispara la carga inicial en el
     * mismo punto para que al abrirse el sheet ya tenga datos en vuelo.
     *
     * @param view vista raíz creada por {@link #onCreateView}.
     * @param savedInstanceState estado guardado o {@code null}.
     */
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
     * Pinta la lista, el estado vacío o el error de carga según el snapshot recibido.
     *
     * @param state estado actual publicado por {@link ShareRoutesViewModel}.
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
            showSheetErrorSnackbar(getString(R.string.share_routes_error_loading));
        }
    }

    /**
     * Gestiona el clic sobre una actividad, valida la polilínea y abre el preview antes de compartir.
     *
     * @param item ruta seleccionada por el usuario en el listado.
     */
    private void onRouteSelected(@NonNull ActividadItem item) {
        if (binding == null || isSharingInProgress) {
            return;
        }

        // Regla pedida: si no hay polilínea, se avisa y no se genera nada.
        if (!StringUtils.hasText(item.rutaPolilinea)) {
            showSheetErrorSnackbar(getString(R.string.share_routes_error_no_polyline));
            return;
        }

        setSharingInProgress(true);

        final Context localizedContext = AppLanguageManager.localizedContext(requireContext());
        MoveOnExecutors.executeIo(() -> {
            try {
                // Generamos la imagen fuera del hilo principal para no bloquear la UI.
                // Generamos tanto el bitmap como el texto usando un contexto
                // reenvuelto al idioma activo. Así la tarjeta compartida y su
                // copy adjunto salen en el idioma elegido por el usuario.
                Uri uri = ShareRouteImageGenerator.generateShareImage(localizedContext, item);
                String shareText = ShareRouteFormatter.buildShareText(localizedContext);

                FragmentActivity activity = getActivity();
                if (activity == null) {
                    // El sheet puede quedar retenido por FragmentManager aunque la activity
                    // ya no esté disponible. Limpiamos la flag para no bloquear futuros shares.
                    isSharingInProgress = false;
                    return;
                }

                activity.runOnUiThread(() -> {
                    // Primero se limpia el estado interno y después se toca la UI.
                    // Así el flujo no queda atascado si la vista ya fue destruida.
                    setSharingInProgress(false);
                    if (binding == null) return;
                    openPreview(uri, shareText);
                });
            } catch (IllegalArgumentException e) {
                FragmentActivity activity = getActivity();
                if (activity == null) {
                    isSharingInProgress = false;
                    return;
                }
                activity.runOnUiThread(() -> {
                    setSharingInProgress(false);
                    if (binding == null) return;
                    showSheetErrorSnackbar(getString(R.string.share_routes_error_no_polyline));
                });
            } catch (Exception e) {
                FragmentActivity activity = getActivity();
                if (activity == null) {
                    isSharingInProgress = false;
                    return;
                }
                activity.runOnUiThread(() -> {
                    setSharingInProgress(false);
                    if (binding == null) return;
                    showSheetErrorSnackbar(getString(R.string.share_routes_error_generating_image));
                });
            }
        });
    }

    /**
     * Abre el segundo bottom sheet con la vista previa de la tarjeta.
     *
     * @param uri uri temporal de la imagen recién generada.
     * @param shareText texto que se adjuntará al intent final de compartir.
     */
    private void openPreview(@NonNull Uri uri, @NonNull String shareText) {
        if (!isAdded()) {
            return;
        }

        if (getParentFragmentManager().isStateSaved()) {
            showSheetErrorSnackbar(getString(R.string.share_routes_error_opening_preview));
            return;
        }

        ShareRoutePreviewBottomSheet.newInstance(uri, shareText)
                .show(getParentFragmentManager(), ShareRoutePreviewBottomSheet.TAG);

        // Cerramos el listado para que la navegación entre sheets quede limpia.
        dismissAllowingStateLoss();
    }

    /**
     * Bloquea temporalmente la UI del sheet durante la generación de la imagen.
     *
     * @param sharing {@code true} para mostrar el estado de trabajo en curso y desactivar la lista.
     */
    private void setSharingInProgress(boolean sharing) {
        isSharingInProgress = sharing;

        if (binding == null) return;

        binding.progressShareRoutes.setVisibility(sharing ? View.VISIBLE : View.GONE);
        binding.rvShareRoutes.setEnabled(!sharing);
        binding.rvShareRoutes.setAlpha(sharing ? 0.5f : 1f);
    }

    /**
     * Limpia el flag "compartiendo en curso" antes de soltar el binding
     * para que, si el sheet se recrea, no herede un bloqueo visual de la
     * vista anterior.
     */
    @Override
    public void onDestroyView() {
        // Al destruir la vista se limpia el estado para que una recreación del sheet
        // no herede un bloqueo de compartición pendiente de una vista anterior.
        setSharingInProgress(false);
        binding = null;
        super.onDestroyView();
    }
}
