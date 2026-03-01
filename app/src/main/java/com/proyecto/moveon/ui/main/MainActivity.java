package com.proyecto.moveon.ui.main;

import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;

import com.proyecto.moveon.R;
import com.proyecto.moveon.core.theme.ThemeManager;
import com.proyecto.moveon.databinding.ActivityMainBinding;
import com.proyecto.moveon.ui.auth.LoginActivity;
import com.proyecto.moveon.core.auth.GlobalAuthManager;
import com.proyecto.moveon.ui.common.SessionUiHelper;
import com.proyecto.moveon.ui.home.InicioFragment;
import com.proyecto.moveon.ui.profile.ProfileFragment;
import com.proyecto.moveon.ui.stats.StatsFragment;
import com.proyecto.moveon.utils.NavigationUtils;

public class MainActivity extends AppCompatActivity {

    private static final String KEY_SELECTED_ITEM = "selected_item";
    private static final String TAG_INICIO  = "tab_inicio";
    private static final String TAG_STATS   = "tab_stats";
    private static final String TAG_PROFILE = "tab_profile";

    private ActivityMainBinding binding;
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

        viewModel = new ViewModelProvider(this).get(MainViewModel.class);

        if (!viewModel.isLoggedIn()) {
            goToLoginAndFinish();
            return;
        }

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        fragmentManager = getSupportFragmentManager();

        // 1. Manejador de sesión de arranque (Silent Refresh)
        viewModel.getSessionExpiredEvent().observe(this, ev -> {
            if (ev == null) return;
            String msg = ev.getContentIfNotHandled();
            if (msg != null) {
                SessionUiHelper.handleSessionExpired(this, msg);
            }
        });

        // 2. NUEVO: Escuchador GLOBAL para cualquier petición fallida en cualquier momento
        GlobalAuthManager.getInstance().getSessionExpiredEvent().observe(this, expired -> {
            if (Boolean.TRUE.equals(expired)) {
                GlobalAuthManager.getInstance().resetEvent(); // Limpiar bandera
                SessionUiHelper.handleSessionExpired(this, getString(R.string.auth_sesion_expirada));
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

            tx.setMaxLifecycle(inicioFragment, Lifecycle.State.RESUMED);
            tx.setMaxLifecycle(statsFragment, Lifecycle.State.STARTED);
            tx.setMaxLifecycle(profileFragment, Lifecycle.State.STARTED);

            tx.commitNow();
            selectedItemId = R.id.nav_inicio;
        }

        binding.bottomNavigation.setSelectedItemId(selectedItemId);
        switchTo(selectedItemId);

        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == selectedItemId) return true;
            switchTo(id);
            return true;
        });

        viewModel.trySilentRefreshAtStartup();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (selectedItemId != R.id.nav_inicio) {
                    binding.bottomNavigation.setSelectedItemId(R.id.nav_inicio);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                    setEnabled(true);
                }
            }
        });
    }

    private void switchTo(int itemId) {
        Fragment target;

        if (itemId == R.id.nav_inicio) {
            if (inicioFragment == null) {
                Fragment f = fragmentManager.findFragmentByTag(TAG_INICIO);
                inicioFragment = (f instanceof InicioFragment) ? (InicioFragment) f : new InicioFragment();
            }
            target = inicioFragment;
        } else if (itemId == R.id.nav_stats) {
            if (statsFragment == null) {
                Fragment f = fragmentManager.findFragmentByTag(TAG_STATS);
                statsFragment = (f instanceof StatsFragment) ? (StatsFragment) f : new StatsFragment();
            }
            target = statsFragment;
        } else {
            if (profileFragment == null) {
                Fragment f = fragmentManager.findFragmentByTag(TAG_PROFILE);
                profileFragment = (f instanceof ProfileFragment) ? (ProfileFragment) f : new ProfileFragment();
            }
            target = profileFragment;
        }

        FragmentTransaction tx = fragmentManager.beginTransaction();
        tx.setReorderingAllowed(true);

        ensureAdded(tx, inicioFragment, TAG_INICIO);
        ensureAdded(tx, statsFragment, TAG_STATS);
        ensureAdded(tx, profileFragment, TAG_PROFILE);

        if (inicioFragment != null && inicioFragment != target) {
            tx.hide(inicioFragment);
            tx.setMaxLifecycle(inicioFragment, Lifecycle.State.STARTED);
        }
        if (statsFragment != null && statsFragment != target) {
            tx.hide(statsFragment);
            tx.setMaxLifecycle(statsFragment, Lifecycle.State.STARTED);
        }
        if (profileFragment != null && profileFragment != target) {
            tx.hide(profileFragment);
            tx.setMaxLifecycle(profileFragment, Lifecycle.State.STARTED);
        }

        if (target != null) {
            tx.show(target);
            tx.setMaxLifecycle(target, Lifecycle.State.RESUMED);
        }

        tx.commit();
        selectedItemId = itemId;
    }

    private void ensureAdded(@NonNull FragmentTransaction tx, Fragment f, String tag) {
        if (f == null) return;
        if (!f.isAdded()) {
            tx.add(R.id.frame_layout, f, tag).hide(f);
            tx.setMaxLifecycle(f, Lifecycle.State.STARTED);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Si el usuario vuelve a la app desde el fondo y la sesión ha muerto, lo expulsamos
        if (!viewModel.isLoggedIn()) {
            goToLoginAndFinish();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_SELECTED_ITEM, selectedItemId);
    }

    private void goToLoginAndFinish() {
        NavigationUtils.goToActivityAndClearTask(this, LoginActivity.class);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}