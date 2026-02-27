package com.proyecto.moveon.ui.profile;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.proyecto.moveon.R;
import com.proyecto.moveon.core.theme.ThemeManager;
import com.proyecto.moveon.databinding.FragmentProfileBinding;
import com.proyecto.moveon.ui.auth.LoginActivity;

public class ProfileFragment extends Fragment {

    private FragmentProfileBinding binding;
    private ProfileViewModel viewModel;

    public ProfileFragment() {
        // Constructor vacío requerido
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);

        // Inicializamos el ViewModel
        viewModel = new ViewModelProvider(this).get(ProfileViewModel.class);

        bindUserData();
        setupListeners();
        observeViewModel();
        syncThemeToggleWithSavedMode();

        return binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        syncThemeToggleWithSavedMode();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void bindUserData() {
        // Pedimos los datos al ViewModel y usamos strings.xml
        String username = viewModel.getUsername();
        if (username == null) {
            username = getString(R.string.profile_default_username);
        }

        String email = getString(R.string.profile_default_email);
        String notIndicated = getString(R.string.profile_not_indicated);

        binding.tvUserName.setText(username);
        binding.tvUserEmail.setText(email);
        binding.tvFullName.setText(username);
        binding.tvEmail.setText(email);

        if (binding.tvBirthdate.getText() == null || binding.tvBirthdate.getText().toString().trim().isEmpty()) {
            binding.tvBirthdate.setText(notIndicated);
        }
        if (binding.tvCity.getText() == null || binding.tvCity.getText().toString().trim().isEmpty()) {
            binding.tvCity.setText(notIndicated);
        }
    }

    private void setupListeners() {
        binding.toggleThemeMode.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;

            String newMode;
            if (checkedId == R.id.btn_theme_light) {
                newMode = ThemeManager.MODE_LIGHT;
            } else if (checkedId == R.id.btn_theme_dark) {
                newMode = ThemeManager.MODE_DARK;
            } else if (checkedId == R.id.btn_theme_system) {
                newMode = ThemeManager.MODE_SYSTEM;
            } else {
                return;
            }

            String currentMode = ThemeManager.getSavedMode(requireContext());
            if (newMode.equals(currentMode)) return;

            ThemeManager.saveAndApply(requireContext(), newMode);
            requireActivity().recreate();
        });

        // Placeholders de los botones
        binding.btnEditProfile.setOnClickListener(v -> {});
        binding.itemRanking.setOnClickListener(v -> {});
        binding.itemShareRoutes.setOnClickListener(v -> {});
        binding.switchPublicProfile.setOnCheckedChangeListener((buttonView, isChecked) -> {});
        binding.switchNotifications.setOnCheckedChangeListener((buttonView, isChecked) -> {});

        // Delegamos el evento al ViewModel
        binding.btnLogout.setOnClickListener(v -> viewModel.logout());
    }

    private void observeViewModel() {
        // Observamos el UiState exactamente igual que en el Login
        viewModel.getLogoutState().observe(getViewLifecycleOwner(), state -> {
            if (state == null) return;

            // Bloqueamos el botón y cambiamos el texto usando strings.xml
            binding.btnLogout.setEnabled(!state.loading);
            binding.btnLogout.setText(state.loading
                    ? getString(R.string.profile_btn_logging_out)
                    : getString(R.string.profile_btn_logout));

            // Si falla la red, el ViewModel ya borró la sesión local.
            // Solo avisamos al usuario y le mandamos al Login.
            if (state.error != null) {
                Toast.makeText(requireContext(), getString(R.string.profile_error_logout_server), Toast.LENGTH_SHORT).show();
                goToLogin();
            }

            // Si el servidor responde OK, vamos al Login.
            if (state.data != null) {
                goToLogin();
            }
        });
    }

    private void syncThemeToggleWithSavedMode() {
        String mode = ThemeManager.getSavedMode(requireContext());
        if (ThemeManager.MODE_LIGHT.equals(mode)) {
            if (binding.toggleThemeMode.getCheckedButtonId() != R.id.btn_theme_light) {
                binding.toggleThemeMode.check(R.id.btn_theme_light);
            }
        } else if (ThemeManager.MODE_DARK.equals(mode)) {
            if (binding.toggleThemeMode.getCheckedButtonId() != R.id.btn_theme_dark) {
                binding.toggleThemeMode.check(R.id.btn_theme_dark);
            }
        } else {
            if (binding.toggleThemeMode.getCheckedButtonId() != R.id.btn_theme_system) {
                binding.toggleThemeMode.check(R.id.btn_theme_system);
            }
        }
    }

    private void goToLogin() {
        if (!isAdded()) return;
        Intent intent = new Intent(requireActivity(), LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        requireActivity().finish();
    }
}