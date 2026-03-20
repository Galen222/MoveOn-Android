package com.proyecto.moveon.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.proyecto.moveon.databinding.BottomSheetTrackingAlertBinding;
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
    private static final String ARG_CANCELABLE = "arg_cancelable";

    public interface Listener {
        void onPrimaryAction(@NonNull TrackingAlert.Type type);
        void onSecondaryAction(@NonNull TrackingAlert.Type type);
    }

    @Nullable private BottomSheetTrackingAlertBinding binding;
    @Nullable private Listener listener;

    @NonNull
    public static TrackingAlertBottomSheet newInstance(
            @NonNull TrackingAlert.Type type,
            @NonNull String title,
            @NonNull String message,
            @NonNull String primaryLabel,
            @NonNull String secondaryLabel,
            boolean cancelable) {
        TrackingAlertBottomSheet sheet = new TrackingAlertBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_TYPE, type.name());
        args.putString(ARG_TITLE, title);
        args.putString(ARG_MESSAGE, message);
        args.putString(ARG_PRIMARY, primaryLabel);
        args.putString(ARG_SECONDARY, secondaryLabel);
        args.putBoolean(ARG_CANCELABLE, cancelable);
        sheet.setArguments(args);
        return sheet;
    }

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
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
