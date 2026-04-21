package com.proyecto.moveon.ui.stats;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.proyecto.moveon.R;

/**
 * Bottom sheet expandido que muestra el historial completo de actividades.
 *
 * <p>Reutiliza el mismo {@link ActividadAdapter} y el mismo {@link StatsViewModel}
 * del fragment padre, por lo que borrar o compartir aquí se refleja inmediatamente
 * en la pantalla de estadísticas.</p>
 */
public class TodasActividadesBottomSheet extends BottomSheetDialogFragment {

    public static final String TAG = "todas_actividades_sheet";

    /**
     * Crea una nueva instancia del bottom sheet del historial completo.
     *
     * @return fragment listo para mostrarse desde {@link StatsFragment}.
     */
    @NonNull
    public static TodasActividadesBottomSheet newInstance() {
        return new TodasActividadesBottomSheet();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_todas_actividades, container, false);
    }

    /**
     * Conecta el RecyclerView al {@link StatsViewModel} compartido con el fragment padre.
     *
     * @param view vista raíz ya inflada del bottom sheet.
     * @param savedInstanceState estado previamente guardado, si existe.
     */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Compartimos el ViewModel con el StatsFragment padre para no duplicar datos ni lógica.
        StatsViewModel viewModel = new ViewModelProvider(requireParentFragment())
                .get(StatsViewModel.class);

        StatsFragment parent = (StatsFragment) requireParentFragment();

        ActividadAdapter adapter = new ActividadAdapter(
                parent::onDeleteClickPublic,
                parent::onShareClickPublic
        );

        RecyclerView rv = view.findViewById(R.id.rv_todas_actividades);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(adapter);

        viewModel.getAllActividades().observe(getViewLifecycleOwner(), adapter::submitList);
    }

    /**
     * Fuerza la apertura expandida del bottom sheet en cuanto entra en pantalla.
     */
    @Override
    public void onStart() {
        super.onStart();
        // Expandir el sheet al máximo al abrirse.
        if (getDialog() instanceof BottomSheetDialog) {
            BottomSheetDialog dialog = (BottomSheetDialog) getDialog();
            View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) {
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(sheet);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
            }
        }
    }

    /**
     * Libera el adapter del RecyclerView para evitar fugas de vistas al destruir el sheet.
     */
    @Override
    public void onDestroyView() {
        View view = getView();
        if (view != null) {
            RecyclerView rv = view.findViewById(R.id.rv_todas_actividades);
            if (rv != null) rv.setAdapter(null);
        }
        super.onDestroyView();
    }
}