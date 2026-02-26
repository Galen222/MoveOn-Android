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

import com.proyecto.moveon.R;
import com.proyecto.moveon.core.theme.ThemeManager;
import com.proyecto.moveon.data.session.AuthRepository;
import com.proyecto.moveon.data.session.SecureSessionManager;
import com.proyecto.moveon.databinding.FragmentProfileBinding;
import com.proyecto.moveon.ui.auth.LoginActivity;

public class ProfileFragment extends Fragment {

    private FragmentProfileBinding binding;

    public ProfileFragment() {
        // Constructor vacío requerido
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);

        bindUserData();
        setupListeners();
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
        if (binding == null) return;

        SecureSessionManager sessionManager = new SecureSessionManager(requireContext());

        String username = sessionManager.getUsername();
        if (username == null || username.trim().isEmpty()) {
            username = "Usuario";
        }

        // Placeholder hasta que guardes email real en sesión o lo traigas de API
        String email = "Sin correo disponible";

        binding.tvUserName.setText(username);
        binding.tvUserEmail.setText(email);

        binding.tvFullName.setText(username);
        binding.tvEmail.setText(email);

        if (binding.tvBirthdate.getText() == null || binding.tvBirthdate.getText().toString().trim().isEmpty()) {
            binding.tvBirthdate.setText("No indicada");
        }
        if (binding.tvCity.getText() == null || binding.tvCity.getText().toString().trim().isEmpty()) {
            binding.tvCity.setText("No indicada");
        }
    }

    private void setupListeners() {
        if (binding == null) return;

        // Tema (Claro / Oscuro / Sistema)
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

        // Edit profile (placeholder)
        binding.btnEditProfile.setOnClickListener(v -> {
            // TODO: Abrir pantalla edición
        });

        // Items (placeholders)
        binding.itemRanking.setOnClickListener(v -> {
            // TODO: abrir ranking
        });

        binding.itemShareRoutes.setOnClickListener(v -> {
            // TODO: abrir compartir rutas
        });

        binding.switchPublicProfile.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // TODO: guardar preferencia / enviar a backend
        });

        binding.switchNotifications.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // TODO: guardar preferencia / pedir permisos/notificaciones
        });

        // Logout
        binding.btnLogout.setOnClickListener(v -> logout());
    }

    private void syncThemeToggleWithSavedMode() {
        if (binding == null) return;

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

    private void logout() {
        SecureSessionManager sessionManager = new SecureSessionManager(requireContext());
        String refreshToken = sessionManager.getRefreshToken();

        // Si no hay refresh token, logout local directo
        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            finishLocalLogout(sessionManager);
            return;
        }

        if (binding != null) {
            binding.btnLogout.setEnabled(false);
            binding.btnLogout.setText("Saliendo...");
        }

        AuthRepository authRepository = new AuthRepository(requireContext());
        authRepository.logout(refreshToken, new AuthRepository.Callback<String>() {
            @Override
            public void onSuccess(String result) {
                finishLocalLogout(sessionManager);
            }

            @Override
            public void onError(String error) {
                if (isAdded()) {
                    Toast.makeText(requireContext(),
                            "No se pudo cerrar sesión en servidor, pero se cerrará en la app",
                            Toast.LENGTH_SHORT).show();
                }
                finishLocalLogout(sessionManager);
            }
        });
    }

    private void finishLocalLogout(SecureSessionManager sessionManager) {
        sessionManager.logout();

        if (!isAdded()) return;

        Intent intent = new Intent(requireActivity(), LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        requireActivity().finish();
    }
}