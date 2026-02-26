package com.proyecto.moveon.ui.main;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;

import android.content.Intent;
import android.os.Bundle;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.proyecto.moveon.R;
import com.proyecto.moveon.core.theme.ThemeManager;
import com.proyecto.moveon.data.session.SecureSessionManager;
import com.proyecto.moveon.ui.auth.LoginActivity;
import com.proyecto.moveon.ui.common.SessionUiHelper;
import com.proyecto.moveon.ui.home.InicioFragment;
import com.proyecto.moveon.ui.profile.ProfileFragment;
import com.proyecto.moveon.ui.stats.StatsFragment;

public class MainActivity extends AppCompatActivity {

    private static final String KEY_SELECTED_ITEM = "selected_item";

    private static final String TAG_INICIO  = "tab_inicio";
    private static final String TAG_STATS   = "tab_stats";
    private static final String TAG_PROFILE = "tab_profile";

    private BottomNavigationView bottomNavigationView;
    private FragmentManager fragmentManager;

    private InicioFragment inicioFragment;
    private StatsFragment statsFragment;
    private ProfileFragment profileFragment;

    private int selectedItemId = R.id.nav_inicio;

    private MainViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        ThemeManager.applySavedTheme(this);
        super.onCreate(savedInstanceState);

        // Guard de sesión antes de inflar layout
        SecureSessionManager sessionManager = new SecureSessionManager(this);
        if (!sessionManager.isLoggedIn()) {
            goToLoginAndFinish();
            return;
        }

        setContentView(R.layout.activity_main);

        bottomNavigationView = findViewById(R.id.bottom_navigation);
        fragmentManager = getSupportFragmentManager();

        viewModel = new ViewModelProvider(this).get(MainViewModel.class);

        viewModel.getSessionExpiredEvent().observe(this, ev -> {
            if (ev == null) return;
            String msg = ev.getContentIfNotHandled();
            if (msg != null) {
                SessionUiHelper.handleSessionExpired(this, msg);
            }
        });

        if (savedInstanceState != null) {
            selectedItemId = savedInstanceState.getInt(KEY_SELECTED_ITEM, R.id.nav_inicio);

            Fragment fInicio = fragmentManager.findFragmentByTag(TAG_INICIO);
            Fragment fStats = fragmentManager.findFragmentByTag(TAG_STATS);
            Fragment fProfile = fragmentManager.findFragmentByTag(TAG_PROFILE);

            if (fInicio instanceof InicioFragment) inicioFragment = (InicioFragment) fInicio;
            if (fStats instanceof StatsFragment) statsFragment = (StatsFragment) fStats;
            if (fProfile instanceof ProfileFragment) profileFragment = (ProfileFragment) fProfile;

        } else {
            inicioFragment = new InicioFragment();
            statsFragment = new StatsFragment();
            profileFragment = new ProfileFragment();

            FragmentTransaction tx = fragmentManager.beginTransaction();
            tx.setReorderingAllowed(true);

            tx.add(R.id.frame_layout, inicioFragment, TAG_INICIO);
            tx.add(R.id.frame_layout, statsFragment, TAG_STATS).hide(statsFragment);
            tx.add(R.id.frame_layout, profileFragment, TAG_PROFILE).hide(profileFragment);

            tx.commit();
            selectedItemId = R.id.nav_inicio;
        }

        bottomNavigationView.setOnItemSelectedListener(item -> {
            switchTo(item.getItemId());
            return true;
        });

        // fuerza la pestaña correcta y muestra el fragment correcto (sin recrearlo)
        bottomNavigationView.setSelectedItemId(selectedItemId);
        switchTo(selectedItemId);

        // Refresh silencioso lifecycle-safe
        viewModel.trySilentRefreshAtStartup();

        // Back: si no estás en Inicio, vuelve a Inicio
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (bottomNavigationView.getSelectedItemId() != R.id.nav_inicio) {
                    bottomNavigationView.setSelectedItemId(R.id.nav_inicio);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    private void switchTo(int itemId) {
        if (itemId == R.id.nav_inicio) {
            if (inicioFragment == null) {
                Fragment f = fragmentManager.findFragmentByTag(TAG_INICIO);
                inicioFragment = (f instanceof InicioFragment) ? (InicioFragment) f : new InicioFragment();
            }
        } else if (itemId == R.id.nav_stats) {
            if (statsFragment == null) {
                Fragment f = fragmentManager.findFragmentByTag(TAG_STATS);
                statsFragment = (f instanceof StatsFragment) ? (StatsFragment) f : new StatsFragment();
            }
        } else if (itemId == R.id.nav_profile) {
            if (profileFragment == null) {
                Fragment f = fragmentManager.findFragmentByTag(TAG_PROFILE);
                profileFragment = (f instanceof ProfileFragment) ? (ProfileFragment) f : new ProfileFragment();
            }
        }

        Fragment target;
        if (itemId == R.id.nav_inicio) target = inicioFragment;
        else if (itemId == R.id.nav_stats) target = statsFragment;
        else target = profileFragment;

        FragmentTransaction tx = fragmentManager.beginTransaction();
        tx.setReorderingAllowed(true);

        ensureAdded(tx, inicioFragment, TAG_INICIO);
        ensureAdded(tx, statsFragment, TAG_STATS);
        ensureAdded(tx, profileFragment, TAG_PROFILE);

        if (inicioFragment != null) tx.hide(inicioFragment);
        if (statsFragment != null) tx.hide(statsFragment);
        if (profileFragment != null) tx.hide(profileFragment);

        tx.show(target);
        tx.commit();

        selectedItemId = itemId;
    }

    private void ensureAdded(@NonNull FragmentTransaction tx, Fragment f, String tag) {
        if (f == null) return;
        if (f.isAdded()) return;
        tx.add(R.id.frame_layout, f, tag).hide(f);
    }

    private void goToLoginAndFinish() {
        Intent i = new Intent(this, LoginActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_SELECTED_ITEM, bottomNavigationView.getSelectedItemId());
    }
}