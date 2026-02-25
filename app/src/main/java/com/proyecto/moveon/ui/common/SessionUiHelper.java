package com.proyecto.moveon.ui.common;

import android.app.Activity;
import android.content.Intent;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.proyecto.moveon.data.session.SecureSessionManager;
import com.proyecto.moveon.ui.auth.LoginActivity;

public final class SessionUiHelper {

    private SessionUiHelper() {}

    public static void handleSessionExpired(@NonNull Activity activity, String message) {
        try {
            new SecureSessionManager(activity).logout();
        } catch (Exception ignored) {}

        if (message != null && !message.trim().isEmpty()) {
            Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(activity, "Tu sesión ha expirado. Inicia sesión de nuevo.", Toast.LENGTH_LONG).show();
        }

        Intent i = new Intent(activity, LoginActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(i);
        activity.finish();
    }

    public static void handleSessionExpired(@NonNull Fragment fragment, String message) {
        if (fragment.getActivity() != null) {
            handleSessionExpired(fragment.getActivity(), message);
        }
    }
}