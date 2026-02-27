package com.proyecto.moveon.ui.main;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;

import com.proyecto.moveon.R;
import com.proyecto.moveon.core.theme.ThemeManager;
import com.proyecto.moveon.databinding.ActivityMainBinding;
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

        // Inicializamos el ViewModel lo primero
        viewModel = new ViewModelProvider(this).get(MainViewModel.class);

        // Guard de sesión consultando al ViewModel
        if (!viewModel.isLoggedIn()) {
            goToLoginAndFinish();
            return;
        }

        // 2. Inflamos la vista con ViewBinding
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

            tx.commitNow(); // <- CLAVE (evita tocar fragments antes de que estén añadidos)
            selectedItemId = R.id.nav_inicio;
        }

        // 3. Reemplazamos bottomNavigationView por binding.bottomNavigation
        binding.bottomNavigation.setSelectedItemId(selectedItemId);
        // Muestra el fragment correcto UNA sola vez
        switchTo(selectedItemId);
        // Ahora sí, listener (evita doble switchTo en el arranque)
        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == selectedItemId) return true;
            switchTo(id);
            return true;
        });

        // Refresh silencioso lifecycle-safe
        viewModel.trySilentRefreshAtStartup();

        // Back: si no estás en Inicio, vuelve a Inicio
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (binding.bottomNavigation.getSelectedItemId() != R.id.nav_inicio) {
                    binding.bottomNavigation.setSelectedItemId(R.id.nav_inicio);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
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

        if (inicioFragment != null) tx.hide(inicioFragment);
        if (statsFragment != null) tx.hide(statsFragment);
        if (profileFragment != null) tx.hide(profileFragment);

        tx.show(target);
        tx.commit();
        selectedItemId = itemId;
    }

    private void ensureAdded(@NonNull FragmentTransaction tx, Fragment f, String tag) {
        if (f == null) return;
        if (!f.isAdded()) {
            tx.add(R.id.frame_layout, f, tag).hide(f);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_SELECTED_ITEM, selectedItemId);
    }

    private void goToLoginAndFinish() {
        Intent i = new Intent(this, LoginActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    // 4. Liberamos memoria destruyendo el binding, buena práctica siempre
    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}