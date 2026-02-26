package com.proyecto.moveon.ui.home;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.proyecto.moveon.R;
import com.proyecto.moveon.databinding.FragmentInicioBinding;

public class InicioFragment extends Fragment {

    private FragmentInicioBinding binding;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentInicioBinding.inflate(inflater, container, false);

        // TODO: Configurar listeners
        // binding.btnAdd.setOnClickListener(v -> { ... });
        // binding.btnPlay.setOnClickListener(v -> { ... });
        // binding.btnStop.setOnClickListener(v -> { ... });
        // binding.btnReset.setOnClickListener(v -> { ... });

        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    // Métodos listos para cuando metas acelerómetro / lógica de estados
    private void showWalkingStatus() {
        if (binding == null) return;

        binding.statusWalking.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.greenPrimary));
        binding.tvWalking.setTextColor(ContextCompat.getColor(requireContext(), R.color.textOnGreen));
        binding.ivWalking.setColorFilter(ContextCompat.getColor(requireContext(), R.color.textOnGreen));

        binding.statusRunning.setBackgroundColor(Color.TRANSPARENT);
        binding.tvRunning.setTextColor(ContextCompat.getColor(requireContext(), R.color.textSecondary));
        binding.ivRunning.setColorFilter(ContextCompat.getColor(requireContext(), R.color.textSecondary));
    }

    private void showRunningStatus() {
        if (binding == null) return;

        binding.statusRunning.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.greenPrimary));
        binding.tvRunning.setTextColor(ContextCompat.getColor(requireContext(), R.color.textOnGreen));
        binding.ivRunning.setColorFilter(ContextCompat.getColor(requireContext(), R.color.textOnGreen));

        binding.statusWalking.setBackgroundColor(Color.TRANSPARENT);
        binding.tvWalking.setTextColor(ContextCompat.getColor(requireContext(), R.color.textSecondary));
        binding.ivWalking.setColorFilter(ContextCompat.getColor(requireContext(), R.color.textSecondary));
    }
}