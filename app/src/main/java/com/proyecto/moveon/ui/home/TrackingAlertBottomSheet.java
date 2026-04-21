package com.proyecto.moveon.ui.home;

import android.os.Bundle;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.proyecto.moveon.R;
import com.proyecto.moveon.databinding.BottomSheetTrackingAlertBinding;
import com.proyecto.moveon.utils.StringUtils;
import com.proyecto.moveon.ui.common.BaseExpandedBottomSheetDialogFragment;
import com.proyecto.moveon.ui.home.tracking.TrackingAlert;

/**
 * Panel inferior contextual para alertas de tracking.
 *
 * <p>Se reutiliza tanto para auto-pausa por parada como para velocidad
 * sospechosa. La acción exacta la decide el fragment padre.</p>
 */
public final class TrackingAlertBottomSheet extends BaseExpandedBottomSheetDialogFragment {

    public static final String TAG = "tracking_alert_sheet";

    private static final String ARG_TYPE = "arg_type";
    private static final String ARG_TITLE = "arg_title";
    private static final String ARG_MESSAGE = "arg_message";
    private static final String ARG_PRIMARY = "arg_primary";
    private static final String ARG_SECONDARY = "arg_secondary";
    private static final String ARG_TERTIARY = "arg_tertiary";
    private static final String ARG_CANCELABLE = "arg_cancelable";

    public interface Listener {
        /**
         * Atiende la acción principal elegida para la alerta mostrada.
         *
         * @param type tipo de alerta que originó el sheet.
         */
        void onPrimaryAction(@NonNull TrackingAlert.Type type);

        /**
         * Atiende la acción secundaria elegida para la alerta mostrada.
         *
         * @param type tipo de alerta que originó el sheet.
         */
        void onSecondaryAction(@NonNull TrackingAlert.Type type);

        /**
         * Atiende la tercera acción opcional disponible en algunas alertas.
         *
         * @param type tipo de alerta que originó el sheet.
         */
        void onTertiaryAction(@NonNull TrackingAlert.Type type);
    }

    @Nullable private BottomSheetTrackingAlertBinding binding;
    @Nullable private Listener listener;

    /**
     * Crea una instancia configurada con los textos y botones de una alerta concreta.
     *
     * @param type tipo de alerta que condiciona el estilo y las acciones.
     * @param title título principal visible en el panel.
     * @param message mensaje descriptivo de la alerta.
     * @param primaryLabel texto del botón principal.
     * @param secondaryLabel texto del botón secundario.
     * @param tertiaryLabel texto opcional del tercer botón.
     * @param cancelable indica si el usuario puede cerrar el sheet sin elegir una acción.
     * @return bottom sheet listo para mostrarse.
     */
    @NonNull
    public static TrackingAlertBottomSheet newInstance(
            @NonNull TrackingAlert.Type type,
            @NonNull String title,
            @NonNull String message,
            @NonNull String primaryLabel,
            @NonNull String secondaryLabel,
            @Nullable String tertiaryLabel,
            boolean cancelable) {
        TrackingAlertBottomSheet sheet = new TrackingAlertBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_TYPE, type.name());
        args.putString(ARG_TITLE, title);
        args.putString(ARG_MESSAGE, message);
        args.putString(ARG_PRIMARY, primaryLabel);
        args.putString(ARG_SECONDARY, secondaryLabel);
        args.putString(ARG_TERTIARY, tertiaryLabel);
        args.putBoolean(ARG_CANCELABLE, cancelable);
        sheet.setArguments(args);
        return sheet;
    }

    /**
     * Registra el listener que recibirá las acciones pulsadas en el panel.
     *
     * @param listener receptor de eventos, o {@code null} para eliminarlo.
     */
    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = BottomSheetTrackingAlertBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    /**
     * Vuelca en la vista los argumentos del sheet y conecta cada botón con su acción correspondiente.
     *
     * @param view vista ya creada del bottom sheet.
     * @param savedInstanceState estado previamente guardado, si existe.
     */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Bundle args = requireArguments();
        setCancelable(args.getBoolean(ARG_CANCELABLE, true));

        TrackingAlert.Type type = TrackingAlert.Type.valueOf(args.getString(ARG_TYPE));
        binding.tvTitle.setText(args.getString(ARG_TITLE));
        binding.tvMessage.setText(args.getString(ARG_MESSAGE));
        binding.btnPrimary.setText(args.getString(ARG_PRIMARY));
        binding.btnSecondary.setText(args.getString(ARG_SECONDARY));

        String tertiaryLabel = args.getString(ARG_TERTIARY);
        if (StringUtils.hasText(tertiaryLabel)) {
            binding.btnTertiary.setVisibility(View.VISIBLE);
            binding.btnTertiary.setText(tertiaryLabel);
        } else {
            binding.btnTertiary.setVisibility(View.GONE);
        }

        if (type == TrackingAlert.Type.STATIONARY_AUTO_PAUSE) {
            styleOutlinedActionButton(binding.btnSecondary);
            if (binding.btnTertiary.getVisibility() == View.VISIBLE) {
                styleOutlinedActionButton(binding.btnTertiary);
            }
        }

        binding.btnPrimary.setOnClickListener(v -> {
            if (listener != null) {
                listener.onPrimaryAction(type);
            }
            dismissAllowingStateLoss();
        });

        binding.btnSecondary.setOnClickListener(v -> {
            if (listener != null) {
                listener.onSecondaryAction(type);
            }
            dismissAllowingStateLoss();
        });

        binding.btnTertiary.setOnClickListener(v -> {
            if (listener != null) {
                listener.onTertiaryAction(type);
            }
            dismissAllowingStateLoss();
        });
    }

    /**
     * Replica un estilo outlined para acciones secundarias y terciarias no destructivas.
     *
     * @param button botón material que debe restilarse.
     */
    private void styleOutlinedActionButton(@NonNull MaterialButton button) {
        int strokeColor = ContextCompat.getColor(requireContext(), R.color.dividerColor);
        int textColor = ContextCompat.getColor(requireContext(), R.color.textPrimary);

        button.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
        button.setStrokeColor(ColorStateList.valueOf(strokeColor));
        button.setStrokeWidth(dpToPx(1));
        button.setCornerRadius(dpToPx(12));
        button.setTextColor(textColor);
        button.setInsetTop(0);
        button.setInsetBottom(0);
    }

    /**
     * Convierte una medida en dp a píxeles enteros usando la densidad actual.
     *
     * @param dp valor expresado en densidad independiente.
     * @return equivalente redondeado en píxeles.
     */
    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    /**
     * Limpia el binding para no retener la jerarquía de vistas una vez destruido el sheet.
     */
    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
