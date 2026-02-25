package com.proyecto.moveon.ui.main;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;

import android.content.Intent;
import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.proyecto.moveon.data.session.AuthRepository;
import com.proyecto.moveon.ui.home.InicioFragment;
import com.proyecto.moveon.ui.profile.ProfileFragment;
import com.proyecto.moveon.R;
import com.proyecto.moveon.data.session.SecureSessionManager;
import com.proyecto.moveon.ui.common.SessionUiHelper;
import com.proyecto.moveon.ui.stats.StatsFragment;
import com.proyecto.moveon.core.theme.ThemeManager;
import com.proyecto.moveon.ui.auth.LoginActivity;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNavigationView;
    private FragmentManager fragmentManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // SplashScreen API (debe ir antes de super.onCreate)
        SplashScreen.installSplashScreen(this);

        // Aplicar tema guardado (claro/oscuro/sistema)
        ThemeManager.applySavedTheme(this);

        super.onCreate(savedInstanceState);

        // Guard de sesión antes de inflar layout (evita flashes)
        SecureSessionManager sessionManager = new SecureSessionManager(this);
        if (!sessionManager.isLoggedIn()) {
            goToLoginAndFinish();
            return;
        }

        setContentView(R.layout.activity_main);

        // Inicializar el BottomNavigationView
        bottomNavigationView = findViewById(R.id.bottom_navigation);
        fragmentManager = getSupportFragmentManager();

        // Cargar el InicioFragment por defecto al iniciar
        if (savedInstanceState == null) {
            loadFragment(new InicioFragment());
            bottomNavigationView.setSelectedItemId(R.id.nav_inicio);
        }

        // Configurar el listener para el menú de navegación
        bottomNavigationView.setOnItemSelectedListener(item -> {
            Fragment fragment = null;
            int itemId = item.getItemId();

            if (itemId == R.id.nav_inicio) {
                fragment = new InicioFragment();
            } else if (itemId == R.id.nav_stats) {
                fragment = new StatsFragment();
            } else if (itemId == R.id.nav_profile) {
                fragment = new ProfileFragment();
            }

            if (fragment != null) {
                loadFragment(fragment);
                return true;
            }
            return false;
        });

        // 🔐 Intento de autorefresco en segundo plano (sin bloquear la app)
        // Offline-first: si falla por red, NO cerramos sesión.
        trySilentRefreshAtStartup();
    }

    private void trySilentRefreshAtStartup() {
        SecureSessionManager sessionManager = new SecureSessionManager(this);
        String refreshToken = sessionManager.getRefreshToken();

        if (!hasText(refreshToken)) {
            return; // no hay refresh, no intentamos nada
        }

        AuthRepository authRepository = new AuthRepository();
        authRepository.refreshSession(refreshToken, new AuthRepository.Callback<AuthRepository.LoginResult>() {
            @Override
            public void onSuccess(AuthRepository.LoginResult result) {
                // Guardamos access+refresh nuevos (rotación)
                String username = result.nombreUsuario;
                if (!hasText(username)) {
                    username = sessionManager.getUsername();
                }
                if (username == null) username = "";

                sessionManager.saveLogin(username, result.tokenAcceso, result.refreshToken);
            }

            @Override
            public void onError(String error) {
                // Offline-first:
                // - si falla por red/timeout, dejamos la app funcionar local
                // - si parece refresh inválido/expirado/reutilizado, cerramos sesión
                if (looksLikeInvalidRefresh(error)) {
                    SessionUiHelper.handleSessionExpired(MainActivity.this, error);
                }
                // Si no, ignoramos (p. ej., sin red) y seguimos en local.
            }
        });
    }

    private boolean looksLikeInvalidRefresh(String error) {
        if (error == null) return false;
        String e = error.toLowerCase(Locale.ROOT);

        return e.contains("refresh token inválido")
                || e.contains("refresh token invalido")
                || e.contains("refresh token expirado")
                || e.contains("refresh token reutilizado")
                || e.contains("token inválido")
                || e.contains("token invalido")
                || e.contains("token expirado")
                || e.contains("401");
    }

    private boolean hasText(String v) {
        return v != null && !v.trim().isEmpty();
    }

    private void goToLoginAndFinish() {
        Intent i = new Intent(this, LoginActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    /**
     * Método para cargar fragmentos en el FrameLayout
     */
    private void loadFragment(Fragment fragment) {
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.replace(R.id.frame_layout, fragment);
        transaction.commit();
    }
}