package com.proyecto.moveon.ui.common;

import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;

import com.google.android.material.snackbar.Snackbar;
import com.proyecto.moveon.R;

/**
 * Helper visual para mostrar snackbars superiores con estilo consistente.
 *
 * <p>La clase permite, además, aplicar un desplazamiento superior extra para casos
 * como diálogos o bottom sheets, cuya ventana puede empezar más arriba que el
 * contenido estándar de la Activity.</p>
 */
public final class TopSnackbar {

    public enum Type { SUCCESS, WARNING, ERROR }

    /**
     * Márgenes base heredados del comportamiento ya usado en la app.
     *
     * <p>Se mantienen en píxeles para no alterar la apariencia de las pantallas
     * que ya se veían correctamente. Los bottom sheets añaden un offset extra
     * calculado dinámicamente cuando hace falta.</p>
     */
    private static final int BASE_TOP_MARGIN_PX = 24;
    private static final int BASE_HORIZONTAL_MARGIN_PX = 32;
    private static final int EXTENDED_DURATION_MS = 4500;

    private TopSnackbar() {}

    // ── Métodos principales ──────────────────────────────────────────────────

    public static void success(@NonNull View root, @NonNull CharSequence msg) {
        show(root, msg, Type.SUCCESS, Snackbar.LENGTH_LONG, null, null, 0);
    }

    public static void success(@NonNull View root,
                               @NonNull CharSequence msg,
                               int extraTopOffsetPx) {
        show(root, msg, Type.SUCCESS, Snackbar.LENGTH_LONG, null, null, extraTopOffsetPx);
    }

    public static void success(@NonNull View root, @StringRes int msgRes) {
        show(root, root.getContext().getString(msgRes), Type.SUCCESS, Snackbar.LENGTH_LONG, null, null, 0);
    }

    public static void success(@NonNull View root, @StringRes int msgRes, int extraTopOffsetPx) {
        show(root, root.getContext().getString(msgRes), Type.SUCCESS, Snackbar.LENGTH_LONG, null, null, extraTopOffsetPx);
    }

    public static void successLong(@NonNull View root, @NonNull CharSequence msg) {
        show(root, msg, Type.SUCCESS, EXTENDED_DURATION_MS, null, null, 0);
    }

    public static void successLong(@NonNull View root, @StringRes int msgRes) {
        show(root, root.getContext().getString(msgRes), Type.SUCCESS, EXTENDED_DURATION_MS, null, null, 0);
    }

    public static void warning(@NonNull View root, @NonNull CharSequence msg) {
        show(root, msg, Type.WARNING, Snackbar.LENGTH_LONG, null, null, 0);
    }

    public static void warning(@NonNull View root,
                               @NonNull CharSequence msg,
                               int extraTopOffsetPx) {
        show(root, msg, Type.WARNING, Snackbar.LENGTH_LONG, null, null, extraTopOffsetPx);
    }

    public static void warning(@NonNull View root, @StringRes int msgRes) {
        show(root, root.getContext().getString(msgRes), Type.WARNING, Snackbar.LENGTH_LONG, null, null, 0);
    }

    public static void warning(@NonNull View root, @StringRes int msgRes, int extraTopOffsetPx) {
        show(root, root.getContext().getString(msgRes), Type.WARNING, Snackbar.LENGTH_LONG, null, null, extraTopOffsetPx);
    }

    public static void error(@NonNull View root, @NonNull CharSequence msg) {
        show(root, msg, Type.ERROR, Snackbar.LENGTH_LONG, null, null, 0);
    }

    public static void error(@NonNull View root,
                             @NonNull CharSequence msg,
                             int extraTopOffsetPx) {
        show(root, msg, Type.ERROR, Snackbar.LENGTH_LONG, null, null, extraTopOffsetPx);
    }

    public static void error(@NonNull View root, @NonNull CharSequence msg,
                             @Nullable String actionLabel, @Nullable Runnable action) {
        show(root, msg, Type.ERROR, Snackbar.LENGTH_LONG, actionLabel, action, 0);
    }

    public static void error(@NonNull View root,
                             @NonNull CharSequence msg,
                             @Nullable String actionLabel,
                             @Nullable Runnable action,
                             int extraTopOffsetPx) {
        show(root, msg, Type.ERROR, Snackbar.LENGTH_LONG, actionLabel, action, extraTopOffsetPx);
    }

    // ── Lógica interna ───────────────────────────────────────────────────────

    private static void show(@NonNull View root,
                             @NonNull CharSequence msg,
                             @NonNull Type type,
                             int duration,
                             @Nullable String actionLabel,
                             @Nullable Runnable action,
                             int extraTopOffsetPx) {

        Snackbar snackbar = Snackbar.make(root, msg, duration);

        // ── Colores según tipo ──
        int bgColor, textColor, iconRes;
        switch (type) {
            case SUCCESS:
                bgColor   = R.color.snackbarSuccessBg;
                textColor = R.color.snackbarSuccessText;
                iconRes   = R.drawable.ic_snackbar_success;
                break;
            case WARNING:
                bgColor   = R.color.snackbarWarningBg;
                textColor = R.color.snackbarWarningText;
                iconRes   = R.drawable.ic_snackbar_warning;
                break;
            default: // ERROR
                bgColor   = R.color.snackbarErrorBg;
                textColor = R.color.snackbarErrorText;
                iconRes   = R.drawable.ic_snackbar_error;
                break;
        }

        View snackView = snackbar.getView();

        // ── Posición arriba ──
        // El parent del Snackbar puede ser FrameLayout o CoordinatorLayout
        // dependiendo del root del layout que invoca. Ambos soportan gravity.
        ViewGroup.LayoutParams rawParams = snackView.getLayoutParams();
        int topGravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        int resolvedTopMargin = BASE_TOP_MARGIN_PX + Math.max(0, extraTopOffsetPx);

        if (rawParams instanceof CoordinatorLayout.LayoutParams) {
            CoordinatorLayout.LayoutParams clp = (CoordinatorLayout.LayoutParams) rawParams;
            clp.gravity = topGravity;
            clp.topMargin = resolvedTopMargin;
            clp.leftMargin = BASE_HORIZONTAL_MARGIN_PX;
            clp.rightMargin = BASE_HORIZONTAL_MARGIN_PX;
        } else if (rawParams instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) rawParams;
            flp.gravity = topGravity;
            flp.topMargin = resolvedTopMargin;
            flp.leftMargin = BASE_HORIZONTAL_MARGIN_PX;
            flp.rightMargin = BASE_HORIZONTAL_MARGIN_PX;
        } else if (rawParams instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) rawParams;
            mlp.topMargin = resolvedTopMargin;
            mlp.leftMargin = BASE_HORIZONTAL_MARGIN_PX;
            mlp.rightMargin = BASE_HORIZONTAL_MARGIN_PX;
        }
        snackView.setLayoutParams(rawParams);

        // ── Fondo redondeado ──
        int resolvedBg = ContextCompat.getColor(root.getContext(), bgColor);
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setCornerRadius(32f);
        shape.setColor(resolvedBg);
        snackView.setBackgroundTintList(null);
        snackView.setBackground(shape);

        // ── Texto ──
        int resolvedText = ContextCompat.getColor(root.getContext(), textColor);
        TextView tv = snackView.findViewById(com.google.android.material.R.id.snackbar_text);
        tv.setTextColor(resolvedText);
        tv.setMaxLines(3);
        tv.setCompoundDrawablePadding(24);

        // ── Icono ──
        tv.setCompoundDrawablesRelativeWithIntrinsicBounds(iconRes, 0, 0, 0);
        // Tint del icono al color del texto
        if (tv.getCompoundDrawablesRelative()[0] != null) {
            tv.getCompoundDrawablesRelative()[0].setTint(resolvedText);
        }

        // ── Botón de acción (retry, etc.) ──
        if (actionLabel != null && action != null) {
            snackbar.setActionTextColor(resolvedText);
            snackbar.setAction(actionLabel, v -> action.run());
        }

        snackbar.setAnimationMode(Snackbar.ANIMATION_MODE_FADE);
        snackbar.show();
    }
}
