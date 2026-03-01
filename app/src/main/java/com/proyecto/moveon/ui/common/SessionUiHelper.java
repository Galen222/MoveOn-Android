package com.proyecto.moveon.ui.common;

import android.app.Activity;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.proyecto.moveon.R;
import com.proyecto.moveon.data.session.SecureSessionManager;
import com.proyecto.moveon.ui.auth.LoginActivity;
import com.proyecto.moveon.utils.NavigationUtils;
import com.proyecto.moveon.utils.StringUtils;

public final class SessionUiHelper {

    private SessionUiHelper() {}

    public static void handleSessionExpired(@NonNull Activity activity, String message) {
        try {
            new SecureSessionManager(activity).logout();
        } catch (Exception ignored) {}

        String toastMsg = StringUtils.hasText(message) ? message : activity.getString(R.string.auth_sesion_expirada);
        Toast.makeText(activity, toastMsg, Toast.LENGTH_LONG).show();

        NavigationUtils.goToActivityAndClearTask(activity, LoginActivity.class);
    }

    public static void handleSessionExpired(@NonNull Fragment fragment, String message) {
        if (fragment.getActivity() != null) {
            handleSessionExpired(fragment.getActivity(), message);
        }
    }
}