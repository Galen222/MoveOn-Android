
package com.proyecto.moveon.ui.main;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.core.graphics.Insets;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.view.DisplayCutoutCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;

import com.proyecto.moveon.R;
import com.proyecto.moveon.core.auth.GlobalAuthManager;
import com.proyecto.moveon.core.i18n.AppLanguageManager;
import com.proyecto.moveon.core.profile.GlobalProfileNotifier;
import com.proyecto.moveon.core.stats.GlobalStatsNotifier;
import com.proyecto.moveon.core.network.ConnectivityObserver;
import com.proyecto.moveon.core.sync.GlobalSyncNotifier;
import com.proyecto.moveon.core.settings.AppSettingsManager;
import com.proyecto.moveon.core.theme.ThemeManager;
import com.proyecto.moveon.databinding.ActivityMainBinding;
import com.proyecto.moveon.ui.auth.LoginActivity;
import com.proyecto.moveon.ui.common.GlobalSnackbarMessage;
import com.proyecto.moveon.ui.common.SessionUiHelper;
import com.proyecto.moveon.ui.common.TopSnackbar;
import com.proyecto.moveon.ui.home.InicioFragment;
import com.proyecto.moveon.ui.home.tracking.TrackingService;
import com.proyecto.moveon.ui.profile.ProfileFragment;
import com.proyecto.moveon.ui.stats.StatsFragment;
import com.proyecto.moveon.utils.NavigationUtils;
/**
 * Shell principal de la aplicación una vez autenticado el usuario.
 *
 * <p>Coordina la navegación entre {@link InicioFragment}, {@link StatsFragment} y
 * {@link ProfileFragment}, resuelve mensajes globales nacidos en notificadores de proceso
 * y atiende intents externos como la acción de parada lanzada desde
 * {@link TrackingService}.</p>
 */
public class MainActivity extends AppCompatActivity {

    private static final String KEY_SELECTED_ITEM = "selected_item";
    private static final String TAG_INICIO  = "tab_inicio";
    private static final String TAG_STATS   = "tab_stats";
    private static final String TAG_PROFILE = "tab_profile";
    /**
     * Extra pública usada por la notificación de tracking para pedir a la actividad
     * principal que abra Inicio y muestre el mismo diálogo de detener que existe en pantalla.
     */
    public static final String EXTRA_SHOW_TRACKING_STOP_DIALOG =
            "com.proyecto.moveon.extra.SHOW_TRACKING_STOP_DIALOG";

    private ActivityMainBinding binding;
    private FragmentManager fragmentManager;

    private InicioFragment inicioFragment;
    private StatsFragment statsFragment;
    private ProfileFragment profileFragment;

    private int selectedItemId = R.id.nav_inicio;
    private MainViewModel viewModel;
    private boolean keepSystemSplashVisible = false;

    /**
     * Construye un intent listo para abrir la app en primer plano y pedir el flujo
     * de confirmación de parada (Guardar / Cancelar / Descartar).
     *
     * @param context contexto desde el que construir el relanzamiento.
     * @return intent preparado para reabrir {@link MainActivity} y delegar en {@link InicioFragment}.
     */
    @NonNull
    public static Intent createLaunchIntentToShowTrackingStopDialog(@NonNull Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(
                Intent.FLAG_ACTIVITY_SINGLE_TOP
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_NEW_TASK
        );
        intent.putExtra(EXTRA_SHOW_TRACKING_STOP_DIALOG, true);
        return intent;
    }

    /**
     * Envuelve el contexto base para aplicar el idioma elegido antes de inflar recursos.
     *
     * @param newBase contexto original.
     */
    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(AppLanguageManager.wrapContext(newBase));
    }

    /**
     * Inicializa la activity principal, sus fragmentos y los observadores globales de estado.
     *
     * @param savedInstanceState estado previo de la activity.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        final boolean pendingUiTransitionSplash =
                AppSettingsManager.isUiTransitionSplashRequested(this);

        SplashScreen splashScreen = SplashScreen.installSplashScreen(this);
        if (pendingUiTransitionSplash) {
            keepSystemSplashVisible = true;
            splashScreen.setKeepOnScreenCondition(() -> keepSystemSplashVisible);
        }

        ThemeManager.applySavedTheme(this);
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        viewModel = new ViewModelProvider(this).get(MainViewModel.class);

        if (viewModel.isNotLoggedIn()) {
            keepSystemSplashVisible = false;
            goToLoginAndFinish();
            return;
        }

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applyMainContentSystemBarInsets();

        if (pendingUiTransitionSplash) {
            AppSettingsManager.clearUiTransitionSplashRequest(this);
            showUiTransitionSplashNow();
        }

        fragmentManager = getSupportFragmentManager();

        GlobalAuthManager.getInstance().getSessionExpiredEvent().observe(this, ev -> {
            if (ev == null) return;
            String token = ev.getContentIfNotHandled();
            if (token != null) {
                GlobalAuthManager.getInstance().acknowledgeSessionExpired();
                SessionUiHelper.handleSessionExpired(this, getString(R.string.auth_sesion_expirada));
            }
        });

        // Observador global para avisar cuando una cola offline termina de sincronizarse.
        // Igual que perfil y stats, el worker emite un GlobalSnackbarMessage y la Activity lo pinta.
        GlobalSyncNotifier.getInstance().getMessageEvent().observe(this, ev -> {
            if (ev == null || binding == null) return;

            GlobalSnackbarMessage message = ev.getContentIfNotHandled();
            if (message != null) {
                showGlobalSnackbarMessage(message);
            }
        });

        // Observador global para los mensajes nacidos en ProfileFragment.
        // Se resuelven aquí para que sobrevivan aunque el usuario cambie de pestaña.
        GlobalProfileNotifier.getInstance().getMessageEvent().observe(this, ev -> {
            if (ev == null || binding == null) return;

            GlobalSnackbarMessage message = ev.getContentIfNotHandled();
            if (message != null) {
                showGlobalSnackbarMessage(message);
            }
        });

        // Observador global para los mensajes nacidos en StatsFragment.
        // Mismo criterio: el render final del snackbar depende de la Activity visible.
        GlobalStatsNotifier.getInstance().getMessageEvent().observe(this, ev -> {
            if (ev == null || binding == null) return;

            GlobalSnackbarMessage message = ev.getContentIfNotHandled();
            if (message != null) {
                showGlobalSnackbarMessage(message);
            }
        });

        // Observa el estado de red para mostrar u ocultar el banner offline.
        ConnectivityObserver.getInstance().isConnected().observe(this, online -> {
            if (binding != null) {
                binding.offlineBanner.setVisibility(
                        Boolean.TRUE.equals(online) ? View.GONE : View.VISIBLE);
            }
        });

        if (savedInstanceState != null) {
            selectedItemId = savedInstanceState.getInt(KEY_SELECTED_ITEM, R.id.nav_inicio);
            Fragment fInicio  = fragmentManager.findFragmentByTag(TAG_INICIO);
            Fragment fStats   = fragmentManager.findFragmentByTag(TAG_STATS);
            Fragment fProfile = fragmentManager.findFragmentByTag(TAG_PROFILE);

            if (fInicio  instanceof InicioFragment)  inicioFragment  = (InicioFragment)  fInicio;
            if (fStats   instanceof StatsFragment)   statsFragment   = (StatsFragment)   fStats;
            if (fProfile instanceof ProfileFragment) profileFragment = (ProfileFragment) fProfile;
        } else {
            inicioFragment  = new InicioFragment();
            statsFragment   = new StatsFragment();
            profileFragment = new ProfileFragment();

            FragmentTransaction tx = fragmentManager.beginTransaction();
            tx.setReorderingAllowed(true);

            tx.add(R.id.frame_layout, inicioFragment,  TAG_INICIO);
            tx.add(R.id.frame_layout, statsFragment,   TAG_STATS).hide(statsFragment);
            tx.add(R.id.frame_layout, profileFragment, TAG_PROFILE).hide(profileFragment);

            tx.setMaxLifecycle(inicioFragment,  Lifecycle.State.RESUMED);
            tx.setMaxLifecycle(statsFragment,   Lifecycle.State.STARTED);
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

        viewModel.ensureSessionFresh();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            /**
             * Devuelve al usuario a Inicio antes de delegar el comportamiento normal de retroceso.
             */
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

        if (pendingUiTransitionSplash) {
            binding.getRoot().post(this::hideUiTransitionSplashWhenReady);
        } else {
            keepSystemSplashVisible = false;
        }

        handleLaunchIntent(getIntent());
    }


    /**
     * Ajusta solo la zona superior del contenedor principal para que el banner
     * offline no quede debajo de la barra de estado ni del recorte de pantalla.
     *
     * <p>No se aplica el inset inferior al contenedor porque eso empuja la
     * BottomNavigationView hacia arriba y deja un hueco visible sobre los botones
     * de navegación del sistema en HyperOS.</p>
     */
    private void applyMainContentSystemBarInsets() {
        final View root = binding.getRoot();
        final View content = binding.mainContentContainer;

        final int initialLeft = content.getPaddingLeft();
        final int initialTop = content.getPaddingTop();
        final int initialRight = content.getPaddingRight();
        final int initialBottom = content.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars());
            DisplayCutoutCompat cutout = windowInsets.getDisplayCutout();

            int safeLeft = systemBars.left;
            int safeTop = systemBars.top;
            int safeRight = systemBars.right;

            if (cutout != null) {
                safeLeft = Math.max(safeLeft, cutout.getSafeInsetLeft());
                safeTop = Math.max(safeTop, cutout.getSafeInsetTop());
                safeRight = Math.max(safeRight, cutout.getSafeInsetRight());
            }

            content.setPadding(
                    initialLeft + safeLeft,
                    initialTop + safeTop,
                    initialRight + safeRight,
                    initialBottom
            );

            return windowInsets;
        });

        ViewCompat.requestApplyInsets(root);
    }

    /**
     * Pinta un snackbar global sobre la raíz de la actividad activa.
     *
     * <p>La Activity es el punto de render común de la app, así que el mensaje no depende
     * de que el fragment que lo originó siga visible en pantalla.</p>
     *
     * @param message payload visual con texto y severidad a representar.
     */
    private void showGlobalSnackbarMessage(@NonNull GlobalSnackbarMessage message) {
        if (binding == null) return;

        switch (message.type) {
            case SUCCESS:
                TopSnackbar.success(binding.getRoot(), message.message);
                return;
            case WARNING:
                TopSnackbar.warning(binding.getRoot(), message.message);
                return;
            case ERROR:
                if (message.actionLabel != null && message.action != null) {
                    TopSnackbar.error(binding.getRoot(), message.message, message.actionLabel, message.action);
                } else {
                    TopSnackbar.error(binding.getRoot(), message.message);
                }
                return;
        }
    }

    /**
     * Recibe intents nuevos cuando la activity ya estaba viva en la tarea actual.
     *
     * @param intent intent entrante.
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleLaunchIntent(intent);
    }

    /**
     * Muestra inmediatamente el overlay de transición visual usado en recreaciones de UI.
     */
    public void showUiTransitionSplashNow() {
        if (binding == null) return;
        binding.transitionSplashOverlay.animate().cancel();
        binding.transitionSplashOverlay.setAlpha(1f);
        binding.transitionSplashOverlay.setVisibility(View.VISIBLE);
    }

    /**
     * Consume intents externos que piden abrir Inicio y mostrar el diálogo de parada.
     *
     * <p>Se usa desde la acción "Detener" de la notificación para reutilizar exactamente
     * la misma lógica que ya existe en {@link InicioFragment}, evitando finalizar la sesión
     * por un camino distinto al de la UI principal.</p>
     *
     * @param intent intent recibido por la actividad, normalmente desde
     *               {@link #createLaunchIntentToShowTrackingStopDialog(Context)}.
     */
    private void handleLaunchIntent(@Nullable Intent intent) {
        if (intent == null || !intent.getBooleanExtra(EXTRA_SHOW_TRACKING_STOP_DIALOG, false)) {
            return;
        }

        intent.removeExtra(EXTRA_SHOW_TRACKING_STOP_DIALOG);
        openInicioAndRequestStopDialog();
    }

    /**
     * Lleva al usuario a la pestaña Inicio y delega allí la apertura del modal de stop.
     *
     * <p>La petición real se entrega a {@link InicioFragment#requestStopDialogFromExternalAction()}
     * una vez que la transacción pendiente de fragmentos ha quedado aplicada.</p>
     */
    private void openInicioAndRequestStopDialog() {
        if (binding == null || fragmentManager == null) {
            return;
        }

        if (selectedItemId != R.id.nav_inicio) {
            binding.bottomNavigation.setSelectedItemId(R.id.nav_inicio);
        } else {
            switchTo(R.id.nav_inicio);
        }

        binding.getRoot().post(() -> {
            if (fragmentManager == null || isFinishing()) {
                return;
            }
            fragmentManager.executePendingTransactions();
            InicioFragment fragment = resolveInicioFragment();
            if (fragment != null) {
                fragment.requestStopDialogFromExternalAction();
            }
        });
    }

    /**
     * Resuelve la instancia actual del fragment de inicio, reutilizando la referencia o buscándola por tag.
     *
     * @return fragment de inicio activo o {@code null} si todavía no está disponible.
     */
    @Nullable
    private InicioFragment resolveInicioFragment() {
        if (inicioFragment != null) {
            return inicioFragment;
        }
        Fragment fragment = fragmentManager.findFragmentByTag(TAG_INICIO);
        if (fragment instanceof InicioFragment) {
            inicioFragment = (InicioFragment) fragment;
        }
        return inicioFragment;
    }

    /**
     * Oculta con fade el overlay de transición una vez que la UI principal está lista.
     */
    private void hideUiTransitionSplashWhenReady() {
        keepSystemSplashVisible = false;
        if (binding == null) return;

        binding.transitionSplashOverlay.animate()
                .alpha(0f)
                .setDuration(150L)
                .withEndAction(() -> {
                    if (binding == null) return;
                    binding.transitionSplashOverlay.setVisibility(View.GONE);
                    binding.transitionSplashOverlay.setAlpha(1f);
                })
                .start();
    }

    /**
     * Cambia de pestaña reutilizando los fragmentos existentes y ajustando su lifecycle máximo.
     *
     * @param itemId id del ítem seleccionado en la bottom navigation.
     */
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

        ensureAdded(tx, inicioFragment,  TAG_INICIO);
        ensureAdded(tx, statsFragment,   TAG_STATS);
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

        tx.show(target);
        tx.setMaxLifecycle(target, Lifecycle.State.RESUMED);

        tx.commit();
        selectedItemId = itemId;
    }

    /**
     * Añade un fragmento a la transacción si todavía no estaba añadido al contenedor.
     *
     * @param tx transacción en curso.
     * @param f fragmento a asegurar.
     * @param tag tag estable asociado al fragmento.
     */
    private void ensureAdded(@NonNull FragmentTransaction tx, Fragment f, String tag) {
        if (f == null) return;
        if (!f.isAdded()) {
            tx.add(R.id.frame_layout, f, tag).hide(f);
            tx.setMaxLifecycle(f, Lifecycle.State.STARTED);
        }
    }

    /**
     * Revalida el estado de sesión cada vez que la activity vuelve al primer plano.
     */
    @Override
    protected void onResume() {
        super.onResume();
        if (viewModel.isNotLoggedIn()) {
            goToLoginAndFinish();
            return;
        }

        viewModel.ensureSessionFresh();
    }

    /**
     * Guarda la pestaña seleccionada para restaurarla tras recreaciones.
     *
     * @param outState bundle donde persistir el estado.
     */
    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_SELECTED_ITEM, selectedItemId);
    }

    /**
     * Navega a {@link LoginActivity} limpiando la pila actual de la tarea.
     */
    private void goToLoginAndFinish() {
        NavigationUtils.goToActivityAndClearTask(this, LoginActivity.class);
    }

    /**
     * Libera recursos de la activity y detiene el servicio de tracking si la app se está cerrando.
     */
    @Override
    protected void onDestroy() {
        if (isFinishing() && !isChangingConfigurations()) {
            TrackingService.stopService(this);
        }
        super.onDestroy();
        binding = null;
    }
}
