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

    // 1. Declaramos el binding y eliminamos la variable de BottomNavigationView
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

        // ViewModel primero + guard de sesión
        viewModel = new ViewModelProvider(this).get(MainViewModel.class);
        if (!viewModel.isLoggedIn()) {
            goToLoginAndFinish();
            return;
        }

        // 2. ViewBinding
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        fragmentManager = getSupportFragmentManager();

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
            // Pre-creas y dejas listos los 3 tabs, PERO con commitNow para que estén realmente añadidos
            inicioFragment = new InicioFragment();
            statsFragment = new StatsFragment();
            profileFragment = new ProfileFragment();

            FragmentTransaction tx = fragmentManager.beginTransaction();
            tx.setReorderingAllowed(true);

            tx.add(R.id.frame_layout, inicioFragment, TAG_INICIO);
            tx.add(R.id.frame_layout, statsFragment, TAG_STATS).hide(statsFragment);
            tx.add(R.id.frame_layout, profileFragment, TAG_PROFILE).hide(profileFragment);

            // Importante para Maps/rutas: los ocultos a STARTED, el visible a RESUMED
            tx.setMaxLifecycle(inicioFragment, Lifecycle.State.RESUMED);
            tx.setMaxLifecycle(statsFragment, Lifecycle.State.STARTED);
            tx.setMaxLifecycle(profileFragment, Lifecycle.State.STARTED);

            tx.commitNow();
            selectedItemId = R.id.nav_inicio;
        }

        // 3. Reemplazamos bottomNavigationView por binding.bottomNavigation
        binding.bottomNavigation.setSelectedItemId(selectedItemId);
        // Muestra el fragment correcto UNA sola vez
        switchTo(selectedItemId);

        // Listener (evita doble switchTo en el arranque)
        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == selectedItemId) return true;
            switchTo(id);
            return true;
        });

        // Refresh silencioso
        viewModel.trySilentRefreshAtStartup();

        // Back: si no estás en Inicio, vuelve a Inicio
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

        } else { // R.id.nav_profile
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

        // Ocultamos y bajamos lifecycle (clave para Maps/rutas)
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

        // Mostramos y subimos lifecycle del target
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