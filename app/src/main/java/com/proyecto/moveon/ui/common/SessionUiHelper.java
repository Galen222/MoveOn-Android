package com.proyecto.moveon.ui.common;

import android.app.Activity;
import android.content.Context;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.proyecto.moveon.R;
import com.proyecto.moveon.ui.auth.LoginActivity;
import com.proyecto.moveon.utils.NavigationUtils;
import com.proyecto.moveon.utils.OfflineSessionCleaner;
import com.proyecto.moveon.utils.StringUtils;

/**
 * Utilidad de apoyo para las tareas de session ui.
 *
 * <p>Centraliza la limpieza local de sesión mediante {@link OfflineSessionCleaner} y la
 * navegación final hacia {@link LoginActivity} para que Activities y Fragments usen el
 * mismo flujo cuando el backend invalida el refresh token.</p>
 */
public final class SessionUiHelper {

    /**
     * Constructor privado: clase de utilidades con sólo métodos estáticos,
     * no está pensada para instanciarse.
     */
    private SessionUiHelper() {}

    /**
     * Cierra sesión cuando el backend indica que el refresh token ya no es
     * válido: borra credenciales y datos locales, muestra un Toast y
     * redirige a {@link LoginActivity} limpiando el back-stack para que el
     * botón atrás no pueda volver a la pantalla protegida.
     *
     * @param activity actividad desde la que se dispara la transición.
     * @param message mensaje a mostrar en el Toast; si es vacío se usa el genérico de "sesión expirada".
     * @see OfflineSessionCleaner#clearSessionAndLocalDataAsync(Context)
     * @see NavigationUtils#goToActivityAndClearTask(Context, Class)
     * @see #handleSessionExpired(Fragment, String)
     */
    public static void handleSessionExpired(@NonNull Activity activity, String message) {
        OfflineSessionCleaner.clearSessionAndLocalDataAsync(activity);

        String toastMsg = StringUtils.hasText(message)
                ? message
                : activity.getString(R.string.auth_sesion_expirada);
        Toast.makeText(activity, toastMsg, Toast.LENGTH_LONG).show();

        NavigationUtils.goToActivityAndClearTask(activity, LoginActivity.class);
    }

    /**
     * Variante por conveniencia para fragments: delega en la versión basada en
     * {@link Activity} si el fragment sigue adjunto y no hace nada si ya se separó
     * para no lanzar NPE.
     *
     * @param fragment fragment desde el que se dispara la transición.
     * @param message mensaje a mostrar en el Toast o vacío para usar el genérico.
     * @see Fragment#getActivity()
     * @see #handleSessionExpired(Activity, String)
     */
    public static void handleSessionExpired(@NonNull Fragment fragment, String message) {
        if (fragment.getActivity() != null) {
            handleSessionExpired(fragment.getActivity(), message);
        }
    }
}
