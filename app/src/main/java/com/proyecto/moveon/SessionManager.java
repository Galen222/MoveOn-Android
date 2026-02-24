package com.proyecto.moveon;

import android.content.Context;
import android.content.SharedPreferences;

public class SessionManager {

    private static final String PREF_NAME = "user_prefs";
    private static final String KEY_TOKEN = "access_token";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_LOGGED_IN = "is_logged_in";

    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void saveLogin(String username, String token) {
        prefs.edit()
                .putString(KEY_USERNAME, username)
                .putString(KEY_TOKEN, token)
                .putBoolean(KEY_LOGGED_IN, true)
                .apply();
    }

    public boolean isLoggedIn() {
        return prefs.getBoolean(KEY_LOGGED_IN, false)
                && prefs.getString(KEY_TOKEN, null) != null;
    }

    public String getAccessToken() {
        return prefs.getString(KEY_TOKEN, null);
    }

    public String getUsername() {
        return prefs.getString(KEY_USERNAME, null);
    }

    public void logout() {
        prefs.edit().clear().apply();
    }
}