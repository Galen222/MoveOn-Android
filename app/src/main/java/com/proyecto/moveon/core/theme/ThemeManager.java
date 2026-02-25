package com.proyecto.moveon.core.theme;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

public final class ThemeManager {

    private ThemeManager() {}

    public static final String PREFS = "AppSettings";
    public static final String KEY_THEME_MODE = "theme_mode";

    public static final String MODE_SYSTEM = "system";
    public static final String MODE_LIGHT  = "light";
    public static final String MODE_DARK   = "dark";

    public static void applySavedTheme(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String mode = prefs.getString(KEY_THEME_MODE, MODE_SYSTEM);
        applyMode(mode);
    }

    public static void saveAndApply(Context context, String mode) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_THEME_MODE, mode).apply();
        applyMode(mode);
    }

    public static String getSavedMode(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return prefs.getString(KEY_THEME_MODE, MODE_SYSTEM);
    }

    private static void applyMode(String mode) {
        switch (mode) {
            case MODE_DARK:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            case MODE_LIGHT:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }
}