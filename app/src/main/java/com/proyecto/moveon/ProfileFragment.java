package com.proyecto.moveon;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.switchmaterial.SwitchMaterial;

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
    private SwitchMaterial switchDarkTheme;
    private SwitchMaterial switchNotifications;

    // Logout
    private MaterialButton btnLogout;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        initializeViews(view);
        setupListeners();

        return view;
    }

    private void initializeViews(View view) {
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
        switchDarkTheme = view.findViewById(R.id.switch_dark_theme);
        switchNotifications = view.findViewById(R.id.switch_notifications);

        // Logout
        btnLogout = view.findViewById(R.id.btn_logout);
    }

    private void setupListeners() {
        SharedPreferences prefs = requireContext().getSharedPreferences("AppSettings", Context.MODE_PRIVATE);
        boolean isDarkMode = prefs.getBoolean("dark_mode", true); // true = oscuro

        // Estado inicial del switch (sin disparar listener)
        switchDarkTheme.setOnCheckedChangeListener(null);
        switchDarkTheme.setChecked(isDarkMode);

        // ✅ Arreglado: dark_mode=true -> MODE_NIGHT_YES
        switchDarkTheme.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("dark_mode", isChecked).apply();

            if (isChecked) {
                // ON = oscuro
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            } else {
                // OFF = claro
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            }

            requireActivity().recreate();
        });

        // Conectar botón logout
        btnLogout.setOnClickListener(v -> logout());
    }

    private void logout() {
        new SessionManager(requireContext()).logout();

        Intent intent = new Intent(requireActivity(), LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        requireActivity().finish();
    }
}