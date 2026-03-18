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

import java.util.List;

public final class RankingFragment extends BottomSheetDialogFragment {

    public static final String TAG = "RankingFragment";
    private static final String ARG_PROVINCIA = "provincia_usuario";

    // Todas las provincias del enum ProvinciaEspaña del backend, en orden alfabético
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

    // Provincia seleccionada actualmente en el chip. Null = España
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
        setStyle(STYLE_NORMAL,
                com.google.android.material.R.style.Theme_Material3_Light_BottomSheetDialog);
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
        setupCloseButton();
        observeViewModel();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void setupBottomSheetExpanded() {
        if (!(getDialog() instanceof BottomSheetDialog)) return;
        BottomSheetDialog dialog = (BottomSheetDialog) getDialog();
        dialog.setOnShowListener(d -> {
            View bottomSheet = ((BottomSheetDialog) d)
                    .findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet == null) return;
            BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
            bottomSheet.getLayoutParams().height = ViewGroup.LayoutParams.MATCH_PARENT;
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            behavior.setSkipCollapsed(true);
        });
    }

    private void setupRecyclerView() {
        FragmentRankingBinding b = binding;
        if (b == null) return;
        adapter = new RankingAdapter();
        b.rvRanking.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.rvRanking.setAdapter(adapter);
        b.rvRanking.setHasFixedSize(false);
    }

    private void setupFiltros() {
        FragmentRankingBinding b = binding;
        if (b == null) return;

        // Chip España → ranking nacional
        b.chipEspana.setOnClickListener(v -> {
            if (viewModel.getRankingState().getValue() != null
                    && viewModel.getRankingState().getValue().loading) return;
            provinciaSeleccionada = null;
            b.chipProvincia.setText(R.string.ranking_filter_provincia);
            viewModel.cargarRanking(null);
        });

        // Chip Provincia → abre diálogo con lista de todas las provincias
        b.chipProvincia.setOnClickListener(v -> {
            if (viewModel.getRankingState().getValue() != null
                    && viewModel.getRankingState().getValue().loading) return;
            mostrarSelectorProvincias(b);
        });
    }

    private void mostrarSelectorProvincias(@NonNull FragmentRankingBinding b) {
        // Índice de la provincia actualmente seleccionada (para marcarla en el diálogo)
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
                    b.chipProvincia.setText(provinciaSeleccionada);
                    b.chipProvincia.setChecked(true);
                    b.chipEspana.setChecked(false);
                    dialog.dismiss();
                    viewModel.cargarRanking(provinciaSeleccionada);
                })
                .setNegativeButton(R.string.dialog_btn_cancel, null)
                .show();
    }

    private void setupCloseButton() {
        FragmentRankingBinding b = binding;
        if (b == null) return;
        b.btnRankingClose.setOnClickListener(v -> dismiss());
    }

    private void observeViewModel() {
        viewModel.getRankingState().observe(getViewLifecycleOwner(), state -> {
            FragmentRankingBinding b = binding;
            if (b == null || state == null) return;
            if (!state.loading && state.data == null && state.error == null) return;

            b.rankingProgress.setVisibility(state.loading ? View.VISIBLE : View.GONE);
            if (state.loading) {
                b.rvRanking.setVisibility(View.GONE);
                b.rankingEmptyState.setVisibility(View.GONE);
                return;
            }

            if (state.error != null) {
                renderError(b);
                return;
            }

            renderLista(b, state.data);
        });
    }

    private void renderLista(@NonNull FragmentRankingBinding b,
                             @Nullable List<RankingItemDto> lista) {
        if (lista == null || lista.isEmpty()) {
            b.rvRanking.setVisibility(View.GONE);
            b.rankingEmptyState.setVisibility(View.VISIBLE);
            b.tvRankingEmpty.setText(R.string.ranking_empty);
            b.btnRankingRetry.setOnClickListener(v -> viewModel.recargar());
        } else {
            b.rvRanking.setVisibility(View.VISIBLE);
            b.rankingEmptyState.setVisibility(View.GONE);
            adapter.submitList(lista);
        }
    }

    private void renderError(@NonNull FragmentRankingBinding b) {
        b.rvRanking.setVisibility(View.GONE);
        b.rankingEmptyState.setVisibility(View.VISIBLE);
        b.tvRankingEmpty.setText(R.string.ranking_error);
        b.btnRankingRetry.setOnClickListener(v -> viewModel.recargar());
    }
}