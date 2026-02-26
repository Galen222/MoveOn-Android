package com.proyecto.moveon.ui.profile;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.proyecto.moveon.data.session.AuthRepository;
import com.proyecto.moveon.R;
import com.proyecto.moveon.data.session.SecureSessionManager;
import com.proyecto.moveon.core.theme.ThemeManager;
import com.proyecto.moveon.ui.auth.LoginActivity;

public class ProfileFragment extends Fragment {

    // Header
    private ImageView ivProfilePicture;
    private TextView tvUserName;
    private TextView tvUserEmail;

    // Card info personal
    private MaterialCardView cardPersonalInfo;
    private MaterialButton btnEditProfile;
    private TextView tvFullName;
    private TextView tvEmail;
    private TextView tvBirthdate;
    private TextView tvCity;

    // Card settings
    private MaterialCardView cardSettings;
    private LinearLayout itemRanking;
    private LinearLayout itemShareRoutes;
    private SwitchMaterial switchPublicProfile;
    private SwitchMaterial switchNotifications;

    // NUEVO: selector de tema (3 estados)
    private MaterialButtonToggleGroup toggleThemeMode;

    // Logout
    private MaterialButton btnLogout;

    public ProfileFragment() {
        // Constructor vacío requerido
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        initializeViews(view);
        bindUserData();
        setupListeners();
        syncThemeToggleWithSavedMode();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Por si cambiaste el tema desde otra pantalla
        syncThemeToggleWithSavedMode();
    }

    private void initializeViews(@NonNull View view) {
        // Header
        ivProfilePicture = view.findViewById(R.id.iv_profile_picture);
        tvUserName = view.findViewById(R.id.tv_user_name);
        tvUserEmail = view.findViewById(R.id.tv_user_email);

        // Card 1
        cardPersonalInfo = view.findViewById(R.id.card_personal_info);
        btnEditProfile = view.findViewById(R.id.btn_edit_profile);
        tvFullName = view.findViewById(R.id.tv_full_name);
        tvEmail = view.findViewById(R.id.tv_email);
        tvBirthdate = view.findViewById(R.id.tv_birthdate);
        tvCity = view.findViewById(R.id.tv_city);

        // Card 2
        cardSettings = view.findViewById(R.id.card_settings);
        itemRanking = view.findViewById(R.id.item_ranking);
        itemShareRoutes = view.findViewById(R.id.item_share_routes);
        switchPublicProfile = view.findViewById(R.id.switch_public_profile);

        // OJO: este switch de tema ya NO se usa si migras a 3 estados
        // switchDarkTheme = view.findViewById(R.id.switch_dark_theme);

        switchNotifications = view.findViewById(R.id.switch_notifications);

        // NUEVO: toggle de tema
        toggleThemeMode = view.findViewById(R.id.toggle_theme_mode);

        // Logout
        btnLogout = view.findViewById(R.id.btn_logout);
    }

    private void bindUserData() {
        SecureSessionManager sessionManager = new SecureSessionManager(requireContext());

        String username = sessionManager.getUsername();
        if (username == null || username.trim().isEmpty()) {
            username = "Usuario";
        }

        // Como SessionManager actual no guarda email, usamos placeholder
        // (puedes cambiar esto cuando guardes el correo en sesión)
        String email = "Sin correo disponible";

        tvUserName.setText(username);
        tvUserEmail.setText(email);

        // Si quieres, también puedes reflejarlo en la card personal:
        tvFullName.setText(username);
        tvEmail.setText(email);

        // Placeholders mientras no venga de API/BD
        if (tvBirthdate.getText() == null || tvBirthdate.getText().toString().trim().isEmpty()) {
            tvBirthdate.setText("No indicada");
        }
        if (tvCity.getText() == null || tvCity.getText().toString().trim().isEmpty()) {
            tvCity.setText("No indicada");
        }
    }

    private void setupListeners() {
        // Listener del selector de tema (Claro / Oscuro / Sistema)
        if (toggleThemeMode != null) {
            toggleThemeMode.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
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

                // Evita trabajo/recreate innecesario si ya está en ese modo
                String currentMode = ThemeManager.getSavedMode(requireContext());
                if (newMode.equals(currentMode)) return;

                ThemeManager.saveAndApply(requireContext(), newMode);
                requireActivity().recreate();
            });
        }

        // Botón editar perfil (placeholder)
        if (btnEditProfile != null) {
            btnEditProfile.setOnClickListener(v -> {
                // TODO: Abrir pantalla de edición
                // Ejemplo:
                // startActivity(new Intent(requireContext(), EditProfileActivity.class));
            });
        }

        // Items de configuración (placeholders)
        if (itemRanking != null) {
            itemRanking.setOnClickListener(v -> {
                // TODO: abrir ranking
            });
        }

        if (itemShareRoutes != null) {
            itemShareRoutes.setOnClickListener(v -> {
                // TODO: abrir compartir rutas
            });
        }

        // Si quieres persistir estos switches después, te puedo pasar el código con SharedPreferences
        if (switchPublicProfile != null) {
            switchPublicProfile.setOnCheckedChangeListener((buttonView, isChecked) -> {
                // TODO: guardar preferencia / enviar a backend
            });
        }

        if (switchNotifications != null) {
            switchNotifications.setOnCheckedChangeListener((buttonView, isChecked) -> {
                // TODO: guardar preferencia / pedir permisos/notificaciones
            });
        }

        // Logout
        if (btnLogout != null) {
            btnLogout.setOnClickListener(v -> logout());
        }
    }

    private void syncThemeToggleWithSavedMode() {
        if (toggleThemeMode == null) return;

        String mode = ThemeManager.getSavedMode(requireContext());

        if (ThemeManager.MODE_LIGHT.equals(mode)) {
            if (toggleThemeMode.getCheckedButtonId() != R.id.btn_theme_light) {
                toggleThemeMode.check(R.id.btn_theme_light);
            }
        } else if (ThemeManager.MODE_DARK.equals(mode)) {
            if (toggleThemeMode.getCheckedButtonId() != R.id.btn_theme_dark) {
                toggleThemeMode.check(R.id.btn_theme_dark);
            }
        } else {
            if (toggleThemeMode.getCheckedButtonId() != R.id.btn_theme_system) {
                toggleThemeMode.check(R.id.btn_theme_system);
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

        if (btnLogout != null) {
            btnLogout.setEnabled(false);
            btnLogout.setText("Saliendo...");
        }

        AuthRepository authRepository = new AuthRepository(requireContext());
        authRepository.logout(refreshToken, new AuthRepository.Callback<String>() {
            @Override
            public void onSuccess(String result) {
                finishLocalLogout(sessionManager);
            }

            @Override
            public void onError(String error) {
                // Best effort: aunque falle backend (sin red, timeout...), cerramos local
                Toast.makeText(requireContext(),
                        "No se pudo cerrar sesión en servidor, pero se cerrará en la app",
                        Toast.LENGTH_SHORT).show();
                finishLocalLogout(sessionManager);
            }
        });
    }

    private void finishLocalLogout(SecureSessionManager sessionManager) {
        sessionManager.logout();

        Intent intent = new Intent(requireActivity(), LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        requireActivity().finish();
    }
}