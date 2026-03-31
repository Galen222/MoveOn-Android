package com.proyecto.moveon.ui.ranking;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.proyecto.moveon.R;
import com.proyecto.moveon.data.ranking.dto.RankingItemDto;
import com.proyecto.moveon.databinding.FragmentRankingBinding;

import java.util.Collections;
import java.util.List;

/**
 * Bottom sheet principal del ranking.
 *
 * <p>Esta versión elimina el parpadeo del ranking anterior al cambiar de filtro.
 * La causa real era que el adapter previo estaba basado en ListAdapter/AsyncListDiffer,
 * así que vaciar la lista con submitList(emptyList()) no era instantáneo.</p>
 *
 * <p>Ahora el vaciado es síncrono mediante {@link RankingAdapter#clearNow()}.</p>
 */
public final class RankingFragment extends BottomSheetDialogFragment {

    public static final String TAG = "RankingFragment";
    private static final String ARG_PROVINCIA = "provincia_usuario";

    private static final String[] PROVINCIAS = {
            "A Coruña", "Álava", "Albacete", "Alicante", "Almería", "Asturias",
            "Ávila", "Badajoz", "Barcelona", "Burgos", "Cáceres", "Cádiz",
            "Cantabria", "Castellón", "Ceuta", "Ciudad Real", "Córdoba", "Cuenca",
            "Girona", "Granada", "Guadalajara", "Guipúzcoa", "Huelva", "Huesca",
            "Islas Baleares", "Jaén", "La Rioja", "Las Palmas", "León", "Lleida",
            "Lugo", "Madrid", "Málaga", "Melilla", "Murcia", "Navarra", "Ourense",
            "Palencia", "Pontevedra", "Salamanca", "Santa Cruz de Tenerife",
            "Segovia", "Sevilla", "Soria", "Tarragona", "Teruel", "Toledo",
            "Valencia", "Valladolid", "Vizcaya", "Zamora", "Zaragoza"
    };

    @Nullable private FragmentRankingBinding binding;
    private RankingViewModel viewModel;
    private RankingAdapter adapter;
    @Nullable private String provinciaSeleccionada = null;

    @NonNull
    public static RankingFragment newInstance(@Nullable String provinciaUsuario) {
        RankingFragment fragment = new RankingFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PROVINCIA, provinciaUsuario);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(
                STYLE_NORMAL,
                com.google.android.material.R.style.Theme_Material3_Light_BottomSheetDialog
        );
    }

    @Override
    @NonNull
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentRankingBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(RankingViewModel.class);
        setupBottomSheetExpanded();
        setupRecyclerView();
        setupFiltros();
        setupRetryButton();
        setupCloseButton();
        observeViewModel();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    private void setupBottomSheetExpanded() {
        if (!(getDialog() instanceof BottomSheetDialog)) {
            return;
        }

        BottomSheetDialog dialog = (BottomSheetDialog) getDialog();
        dialog.setOnShowListener(d -> {
            View bottomSheet = ((BottomSheetDialog) d)
                    .findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet == null) {
                return;
            }

            BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
            bottomSheet.getLayoutParams().height = ViewGroup.LayoutParams.MATCH_PARENT;
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            behavior.setSkipCollapsed(true);
        });
    }

    private void setupRecyclerView() {
        FragmentRankingBinding b = binding;
        if (b == null) {
            return;
        }

        adapter = new RankingAdapter(this::mostrarAccionesUsuario);
        b.rvRanking.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.rvRanking.setAdapter(adapter);
        b.rvRanking.setHasFixedSize(false);
        b.rvRanking.setItemAnimator(null);
    }

    private void setupFiltros() {
        FragmentRankingBinding b = binding;
        if (b == null) {
            return;
        }

        b.chipEspana.setOnClickListener(v -> {
            if (isLoading()) {
                return;
            }

            provinciaSeleccionada = null;
            b.chipEspana.setChecked(true);
            b.chipProvincia.setChecked(false);
            b.chipProvincia.setText(R.string.ranking_filter_provincia);

            showLoadingImmediately();
            viewModel.cargarRanking(null);
        });

        b.chipProvincia.setOnClickListener(v -> {
            if (isLoading()) {
                return;
            }
            mostrarSelectorProvincias(b);
        });
    }

    private void setupRetryButton() {
        FragmentRankingBinding b = binding;
        if (b == null) {
            return;
        }

        b.btnRankingRetry.setOnClickListener(v -> {
            if (isLoading()) {
                return;
            }

            showLoadingImmediately();
            viewModel.recargar();
        });
    }

    private void setupCloseButton() {
        FragmentRankingBinding b = binding;
        if (b == null) {
            return;
        }

        b.btnRankingClose.setOnClickListener(v -> dismiss());
    }

    private void mostrarSelectorProvincias(@NonNull FragmentRankingBinding b) {
        int indiceActual = -1;
        if (provinciaSeleccionada != null) {
            for (int i = 0; i < PROVINCIAS.length; i++) {
                if (PROVINCIAS[i].equals(provinciaSeleccionada)) {
                    indiceActual = i;
                    break;
                }
            }
        }

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.ranking_selector_title)
                .setSingleChoiceItems(PROVINCIAS, indiceActual, (dialog, which) -> {
                    provinciaSeleccionada = PROVINCIAS[which];

                    // Se vacía la lista ANTES de cerrar el diálogo para que durante el dismiss
                    // no quede visible el ranking anterior debajo.
                    showLoadingImmediately();

                    b.chipProvincia.setText(provinciaSeleccionada);
                    b.chipProvincia.setChecked(true);
                    b.chipEspana.setChecked(false);

                    viewModel.cargarRanking(provinciaSeleccionada);
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.dialog_btn_cancel, null)
                .show();
    }

    private void observeViewModel() {
        viewModel.getRankingState().observe(getViewLifecycleOwner(), state -> {
            FragmentRankingBinding b = binding;
            if (b == null || state == null) {
                return;
            }

            if (!state.loading && state.data == null && state.error == null) {
                return;
            }

            if (state.loading) {
                renderLoading(b);
                return;
            }

            if (state.error != null) {
                renderError(b);
                return;
            }

            renderLista(b, state.data);
        });
    }

    private void renderLoading(@NonNull FragmentRankingBinding b) {
        b.rankingProgress.setVisibility(View.VISIBLE);
        b.rvRanking.setVisibility(View.GONE);
        b.rankingEmptyState.setVisibility(View.GONE);
    }

    private void renderLista(@NonNull FragmentRankingBinding b,
                             @Nullable List<RankingItemDto> lista) {
        b.rankingProgress.setVisibility(View.GONE);

        List<RankingItemDto> safeList = lista != null ? lista : Collections.emptyList();
        adapter.setItems(safeList);

        boolean empty = safeList.isEmpty();
        b.rvRanking.setVisibility(empty ? View.GONE : View.VISIBLE);
        b.rankingEmptyState.setVisibility(empty ? View.VISIBLE : View.GONE);

        if (empty) {
            b.tvRankingEmpty.setText(R.string.ranking_empty);
            b.btnRankingRetry.setVisibility(View.GONE);
        } else {
            b.btnRankingRetry.setVisibility(View.GONE);
        }
    }

    private void renderError(@NonNull FragmentRankingBinding b) {
        b.rankingProgress.setVisibility(View.GONE);
        b.rvRanking.setVisibility(View.GONE);
        b.rankingEmptyState.setVisibility(View.VISIBLE);
        b.tvRankingEmpty.setText(R.string.ranking_error);
        b.btnRankingRetry.setVisibility(View.VISIBLE);
    }

    /**
     * Limpia el ranking viejo de forma síncrona y muestra el loader.
     *
     * <p>A diferencia de submitList(emptyList()), esto no depende de un diff asíncrono.</p>
     */
    private void showLoadingImmediately() {
        FragmentRankingBinding b = binding;
        if (b == null) {
            return;
        }

        adapter.clearNow();
        b.rankingProgress.setVisibility(View.VISIBLE);
        b.rvRanking.setVisibility(View.GONE);
        b.rankingEmptyState.setVisibility(View.GONE);
    }

    private boolean isLoading() {
        return viewModel.getRankingState().getValue() != null
                && viewModel.getRankingState().getValue().loading;
    }

    private void mostrarAccionesUsuario(@NonNull RankingItemDto item) {
        if (!isAdded() || getParentFragmentManager().isStateSaved()) {
            return;
        }

        RankingUserActionsBottomSheet
                .newInstance(
                        item.nombreUsuario,
                        item.fotoPerfil,
                        item.fotoVersion,
                        item.totalPuntos
                )
                .show(getParentFragmentManager(), RankingUserActionsBottomSheet.TAG);
    }
}
