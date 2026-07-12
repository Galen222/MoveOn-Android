package com.proyecto.moveon.ui.common;

import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.graphics.Insets;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
import androidx.core.view.DisplayCutoutCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

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

    /**
     * Muestra un snackbar superior de éxito con la duración estándar.
     *
     * @param root vista raíz usada como ancla para {@link Snackbar#make(View, CharSequence, int)}.
     * @param msg texto a mostrar.
     */
    public static void success(@NonNull View root, @NonNull CharSequence msg) {
        show(root, msg, Type.SUCCESS, Snackbar.LENGTH_LONG, null, null, 0);
    }

    /**
     * Muestra un snackbar de éxito desplazado hacia abajo desde el margen superior base.
     *
     * @param root vista raíz usada como ancla.
     * @param msg texto a mostrar.
     * @param extraTopOffsetPx desplazamiento extra en píxeles para ajustar la posición vertical.
     */
    public static void success(@NonNull View root,
                               @NonNull CharSequence msg,
                               int extraTopOffsetPx) {
        show(root, msg, Type.SUCCESS, Snackbar.LENGTH_LONG, null, null, extraTopOffsetPx);
    }

    /**
     * Muestra un snackbar superior de éxito usando un recurso de texto.
     *
     * @param root vista raíz usada como ancla.
     * @param msgRes recurso string a resolver desde el contexto de {@code root}.
     */
    public static void success(@NonNull View root, @StringRes int msgRes) {
        show(root, root.getContext().getString(msgRes), Type.SUCCESS, Snackbar.LENGTH_LONG, null, null, 0);
    }

    /**
     * Muestra un snackbar superior de éxito con duración extendida.
     *
     * @param root vista raíz usada como ancla.
     * @param msg texto a mostrar.
     */
    public static void successLong(@NonNull View root, @NonNull CharSequence msg) {
        show(root, msg, Type.SUCCESS, EXTENDED_DURATION_MS, null, null, 0);
    }

    /**
     * Muestra un snackbar superior de advertencia con duración estándar.
     *
     * @param root vista raíz usada como ancla.
     * @param msg texto a mostrar.
     */
    public static void warning(@NonNull View root, @NonNull CharSequence msg) {
        show(root, msg, Type.WARNING, Snackbar.LENGTH_LONG, null, null, 0);
    }

    /**
     * Muestra un snackbar superior de error sin acción adicional.
     *
     * @param root vista raíz usada como ancla.
     * @param msg texto a mostrar.
     */
    public static void error(@NonNull View root, @NonNull CharSequence msg) {
        show(root, msg, Type.ERROR, Snackbar.LENGTH_LONG, null, null, 0);
    }

    /**
     * Muestra un snackbar superior de error con offset adicional.
     *
     * @param root vista raíz usada como ancla.
     * @param msg texto a mostrar.
     * @param extraTopOffsetPx desplazamiento extra en píxeles.
     */
    public static void error(@NonNull View root,
                             @NonNull CharSequence msg,
                             int extraTopOffsetPx) {
        show(root, msg, Type.ERROR, Snackbar.LENGTH_LONG, null, null, extraTopOffsetPx);
    }

    /**
     * Muestra un snackbar superior de error con acción opcional.
     *
     * @param root vista raíz usada como ancla.
     * @param msg texto a mostrar.
     * @param actionLabel etiqueta del botón de acción o {@code null} para omitirlo.
     * @param action acción a ejecutar cuando el usuario pulsa el botón.
     */
    public static void error(@NonNull View root, @NonNull CharSequence msg,
                             @Nullable String actionLabel, @Nullable Runnable action) {
        show(root, msg, Type.ERROR, Snackbar.LENGTH_LONG, actionLabel, action, 0);
    }

    // ── Lógica interna ───────────────────────────────────────────────────────

    /**
     * Configura y muestra el {@link Snackbar} con el estilo superior propio de la app.
     *
     * @param root vista raíz usada como ancla.
     * @param msg texto a mostrar.
     * @param type tipo visual que determina colores e iconografía.
     * @param duration duración del snackbar en milisegundos o una constante de {@link Snackbar}.
     * @param actionLabel etiqueta del botón de acción o {@code null}.
     * @param action acción opcional asociada al botón.
     * @param extraTopOffsetPx desplazamiento extra sobre el margen superior base.
     */
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

        // Snackbar está pensado originalmente para mostrarse abajo y Material no
        // compensa el inset superior cuando cambiamos su gravity a TOP. En layouts
        // edge-to-edge (MainActivity) eso hacía que el aviso quedase detrás de la
        // barra de estado. Calculamos cuánto invade el parent real del Snackbar la
        // zona segura superior y usamos el mayor valor entre esa compensación y el
        // offset explícito de bottom sheets, evitando sumar dos veces el mismo inset.
        int automaticTopOffsetPx = calculateAutomaticTopOffsetPx(root);
        int resolvedTopMargin = resolveTopMarginPx(automaticTopOffsetPx, extraTopOffsetPx);

        if (rawParams instanceof CoordinatorLayout.LayoutParams clp) {
            clp.gravity = topGravity;
            clp.topMargin = resolvedTopMargin;
            clp.leftMargin = BASE_HORIZONTAL_MARGIN_PX;
            clp.rightMargin = BASE_HORIZONTAL_MARGIN_PX;
        } else if (rawParams instanceof FrameLayout.LayoutParams flp) {
            flp.gravity = topGravity;
            flp.topMargin = resolvedTopMargin;
            flp.leftMargin = BASE_HORIZONTAL_MARGIN_PX;
            flp.rightMargin = BASE_HORIZONTAL_MARGIN_PX;
        } else if (rawParams instanceof ViewGroup.MarginLayoutParams mlp) {
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
            snackbar.setAction(actionLabel, _ -> action.run());
        }

        snackbar.setAnimationMode(Snackbar.ANIMATION_MODE_FADE);
        snackbar.show();
    }

    /**
     * Calcula el margen superior final sin permitir que el snackbar invada la zona
     * segura del sistema ni duplique la compensación ya calculada por un diálogo.
     */
    static int resolveTopMarginPx(int automaticTopOffsetPx, int extraTopOffsetPx) {
        int safeAutomaticOffset = Math.max(0, automaticTopOffsetPx);
        int safeExplicitOffset = Math.max(0, extraTopOffsetPx);
        return BASE_TOP_MARGIN_PX + Math.max(safeAutomaticOffset, safeExplicitOffset);
    }

    /**
     * Devuelve la compensación necesaria para que el parent real elegido por
     * {@link Snackbar} comience por debajo de la barra de estado o del display cutout.
     */
    private static int calculateAutomaticTopOffsetPx(@NonNull View root) {
        ViewGroup snackbarParent = findSuitableSnackbarParent(root);
        View insetView = snackbarParent != null ? snackbarParent : root;

        WindowInsetsCompat windowInsets = ViewCompat.getRootWindowInsets(insetView);
        if (windowInsets == null) {
            return 0;
        }

        Insets statusBars = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars());
        int safeTop = statusBars.top;

        DisplayCutoutCompat cutout = windowInsets.getDisplayCutout();
        if (cutout != null) {
            safeTop = Math.max(safeTop, cutout.getSafeInsetTop());
        }

        int[] parentLocation = new int[2];
        insetView.getLocationOnScreen(parentLocation);
        return Math.max(0, safeTop - parentLocation[1]);
    }

    /**
     * Replica la selección de parent de {@link Snackbar}: prioriza un
     * {@link CoordinatorLayout}, después el contenido raíz y finalmente el último
     * {@link FrameLayout} encontrado durante el ascenso por la jerarquía.
     */
    @Nullable
    private static ViewGroup findSuitableSnackbarParent(@NonNull View start) {
        View current = start;
        ViewGroup fallback = null;

        while (current != null) {
            if (current instanceof CoordinatorLayout coordinatorLayout) {
                return coordinatorLayout;
            }

            if (current instanceof FrameLayout frameLayout) {
                if (current.getId() == android.R.id.content) {
                    return frameLayout;
                }
                fallback = frameLayout;
            }

            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }

        return fallback;
    }

}
