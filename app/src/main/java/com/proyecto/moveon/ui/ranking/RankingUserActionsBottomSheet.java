package com.proyecto.moveon.ui.ranking;

import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.signature.ObjectKey;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.proyecto.moveon.R;
import com.proyecto.moveon.app.ServiceLocator;
import com.proyecto.moveon.data.ranking.RankingRepository;
import com.proyecto.moveon.databinding.BottomSheetRankingUserActionsBinding;
import com.proyecto.moveon.databinding.DialogReportRankingUserBinding;
import com.proyecto.moveon.ui.common.BaseExpandedBottomSheetDialogFragment;
import com.proyecto.moveon.ui.common.TopSnackbar;
import com.proyecto.moveon.utils.StringUtils;

/**
 * Bottom sheet secundario que se abre al pulsar un usuario del ranking.
 *
 * <p>Este componente muestra la foto, el nombre y los puntos del usuario pulsado.
 * Desde aquí el usuario puede cerrar el sheet o abrir un modal de reporte.</p>
 *
 * <p>El modal de reporte obliga a marcar al menos una causa ({@code nombre} o
 * {@code foto}) antes de permitir el envío. Además, mantiene la validación final
 * en el momento de enviar para no depender solo del estado visual del botón.</p>
 */
public final class RankingUserActionsBottomSheet extends BaseExpandedBottomSheetDialogFragment {

    /** Tag público para mostrar el bottom sheet desde el fragment padre. */
    public static final String TAG = "ranking_user_actions_sheet";

    /** Argumento con el nombre del usuario pulsado en ranking. */
    private static final String ARG_USERNAME = "arg_username";

    /** Argumento con la URL de foto del usuario. */
    private static final String ARG_PHOTO_URL = "arg_photo_url";

    /** Argumento con la versión de caché de la foto. */
    private static final String ARG_PHOTO_VERSION = "arg_photo_version";

    /** Argumento con los puntos totales del usuario. */
    private static final String ARG_POINTS = "arg_points";

    /** Binding del sheet principal con acciones del usuario. */
    @Nullable private BottomSheetRankingUserActionsBinding binding;

    /** Binding del diálogo de reporte. Se crea solo mientras el diálogo está visible. */
    @Nullable private DialogReportRankingUserBinding reportDialogBinding;

    /** Referencia al diálogo para controlar botones y estado de carga. */
    @Nullable private AlertDialog reportDialog;

    /** Repositorio de ranking reutilizado para enviar el reporte al backend. */
    @Nullable private RankingRepository repository;

    /**
     * Crea una nueva instancia del sheet de acciones para un usuario concreto.
     *
     * @param username nombre del usuario pulsado.
     * @param photoUrl URL de foto opcional.
     * @param photoVersion versión usada para invalidar caché de la imagen.
     * @param totalPoints puntos visibles del usuario en ranking.
     * @return instancia lista para mostrarse con {@code show(...)}.
     */
    @NonNull
    public static RankingUserActionsBottomSheet newInstance(@NonNull String username,
                                                            @Nullable String photoUrl,
                                                            int photoVersion,
                                                            int totalPoints) {
        RankingUserActionsBottomSheet sheet = new RankingUserActionsBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_USERNAME, username);
        args.putString(ARG_PHOTO_URL, photoUrl);
        args.putInt(ARG_PHOTO_VERSION, photoVersion);
        args.putInt(ARG_POINTS, totalPoints);
        sheet.setArguments(args);
        return sheet;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        repository = ServiceLocator.getInstance(context).newRankingRepository();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = BottomSheetRankingUserActionsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bindUser();
        setupListeners();
    }

    /**
     * Rellena la cabecera del sheet con la información del usuario seleccionado.
     */
    private void bindUser() {
        BottomSheetRankingUserActionsBinding b = binding;
        Bundle args = getArguments();
        if (b == null || args == null) {
            dismissAllowingStateLoss();
            return;
        }

        String username = args.getString(ARG_USERNAME, "");
        String photoUrl = args.getString(ARG_PHOTO_URL);
        int photoVersion = args.getInt(ARG_PHOTO_VERSION, 0);
        int totalPoints = args.getInt(ARG_POINTS, 0);

        b.tvUsername.setText(username);
        b.tvPoints.setText(getString(R.string.ranking_puntos_format, totalPoints));

        // Añadimos versión a la URL para que Glide invalide la caché cuando la foto cambie.
        String imageUrl = buildVersionedPhotoUrl(photoUrl, photoVersion);
        if (imageUrl != null) {
            Glide.with(this)
                    .load(imageUrl)
                    .placeholder(R.drawable.default_profile)
                    .error(R.drawable.default_profile)
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .signature(new ObjectKey(imageUrl))
                    .circleCrop()
                    .into(b.ivUserPhoto);
        } else {
            Glide.with(this)
                    .load(R.drawable.default_profile)
                    .circleCrop()
                    .into(b.ivUserPhoto);
        }
    }

    /**
     * Configura las acciones principales del sheet.
     */
    private void setupListeners() {
        BottomSheetRankingUserActionsBinding b = binding;
        if (b == null) return;

        b.btnClose.setOnClickListener(v -> dismiss());
        b.btnReportUser.setOnClickListener(v -> showReportDialog());
    }

    /**
     * Abre el diálogo modal de reporte.
     *
     * <p>El botón positivo queda deshabilitado mientras no haya al menos una causa marcada.
     * Así evitamos que el usuario intente enviar un reporte incompleto desde la propia UI.</p>
     */
    private void showReportDialog() {
        if (!isAdded()) return;
        if (reportDialog != null && reportDialog.isShowing()) return;

        reportDialogBinding = DialogReportRankingUserBinding.inflate(
                LayoutInflater.from(requireContext())
        );
        reportDialogBinding.tvReasonError.setVisibility(View.GONE);

        String username = requireArguments().getString(ARG_USERNAME, "");
        reportDialogBinding.tvDialogTitle.setText(
                getString(R.string.ranking_report_dialog_title, username)
        );

        setupReportOptionCards(reportDialogBinding);
        setupReasonSelection(reportDialogBinding);
        refreshReportOptionCards(reportDialogBinding);

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(reportDialogBinding.getRoot())
                .setPositiveButton(R.string.common_accept, null)
                .setNegativeButton(R.string.dialog_btn_cancel, null)
                .create();

        dialog.setOnShowListener(d -> {
            Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);

            if (positive != null) {
                stylePositiveDialogButton(positive);
                positive.setOnClickListener(v -> submitReport());
            }

            if (negative != null) {
                negative.setTextColor(ContextCompat.getColor(requireContext(), R.color.textSecondary));
            }

            // El estado inicial del botón depende de si ya hay alguna causa marcada.
            updateReportSubmitState(false);
        });

        dialog.setOnDismissListener(d -> {
            reportDialog = null;
            reportDialogBinding = null;
        });

        reportDialog = dialog;
        dialog.show();
    }

    /**
     * Hace que pulsar una tarjeta entera active o desactive su checkbox asociado.
     *
     * @param b binding del diálogo de reporte.
     */
    private void setupReportOptionCards(@NonNull DialogReportRankingUserBinding b) {
        b.cardReportName.setOnClickListener(v ->
                b.cbReportName.setChecked(!b.cbReportName.isChecked())
        );

        b.cardReportPhoto.setOnClickListener(v ->
                b.cbReportPhoto.setChecked(!b.cbReportPhoto.isChecked())
        );
    }

    /**
     * Escucha cambios en las causas del reporte para actualizar borde, error y botón de envío.
     *
     * @param b binding del diálogo de reporte.
     */
    private void setupReasonSelection(@NonNull DialogReportRankingUserBinding b) {
        CompoundButton.OnCheckedChangeListener reasonListener = (buttonView, isChecked) -> {
            // Si el usuario ya ha marcado alguna causa, ocultamos el error de selección.
            if (b.cbReportName.isChecked() || b.cbReportPhoto.isChecked()) {
                b.tvReasonError.setVisibility(View.GONE);
            }

            refreshReportOptionCards(b);
            updateReportSubmitState(false);
        };

        b.cbReportName.setOnCheckedChangeListener(reasonListener);
        b.cbReportPhoto.setOnCheckedChangeListener(reasonListener);
    }

    /**
     * Refuerza visualmente las opciones activas del formulario.
     *
     * @param b binding del diálogo de reporte.
     */
    private void refreshReportOptionCards(@NonNull DialogReportRankingUserBinding b) {
        updateReportCardState(b.cardReportName, b.cbReportName.isChecked());
        updateReportCardState(b.cardReportPhoto, b.cbReportPhoto.isChecked());
    }

    /**
     * Ajusta el borde de cada tarjeta según esté o no seleccionada.
     *
     * @param card tarjeta visual de la opción.
     * @param selected indica si la opción está marcada.
     */
    private void updateReportCardState(@NonNull MaterialCardView card, boolean selected) {
        int strokeColor = ContextCompat.getColor(
                requireContext(),
                selected ? R.color.greenPrimary : R.color.dividerColor
        );
        card.setStrokeColor(strokeColor);
        card.setStrokeWidth(selected ? dpToPx(2) : dpToPx(1));
    }

    /**
     * Aplica el aspecto del botón principal del diálogo de reporte.
     *
     * <p>Se fuerza fondo {@code greenPrimary} y texto {@code textOnGreen} para que el botón
     * positivo tenga el mismo lenguaje visual que el resto de acciones primarias de la app.</p>
     *
     * @param button botón positivo del diálogo.
     */
    private void stylePositiveDialogButton(@NonNull Button button) {
        int backgroundColor = ContextCompat.getColor(requireContext(), R.color.greenPrimary);
        int textColor = ContextCompat.getColor(requireContext(), R.color.textOnGreen);

        button.setBackgroundTintList(ColorStateList.valueOf(backgroundColor));
        button.setTextColor(textColor);
    }

    /**
     * Controla si el botón positivo del diálogo puede pulsarse.
     *
     * @param loading true cuando hay una petición de red en curso; false en estado normal.
     */
    private void updateReportSubmitState(boolean loading) {
        DialogReportRankingUserBinding b = reportDialogBinding;
        AlertDialog dialog = reportDialog;
        if (b == null || dialog == null) return;

        Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (positive == null) return;

        boolean hasAnyReason = b.cbReportName.isChecked() || b.cbReportPhoto.isChecked();
        boolean enabled = !loading && hasAnyReason;

        positive.setEnabled(enabled);
        positive.setAlpha(enabled ? 1f : 0.5f);
        positive.setText(loading
                ? getString(R.string.ranking_report_sending)
                : getString(R.string.common_accept));
    }

    /**
     * Convierte dp a px enteros para usarlos en el borde de las tarjetas.
     *
     * @param dp valor en densidad independiente.
     * @return valor equivalente en píxeles.
     */
    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    /**
     * Valida el formulario y llama al endpoint de reporte.
     *
     * <p>La UI ya bloquea el botón cuando no hay ninguna causa marcada, pero mantenemos esta
     * validación final para cubrir cambios de estado inesperados o llamadas forzadas.</p>
     */
    private void submitReport() {
        DialogReportRankingUserBinding d = reportDialogBinding;
        RankingRepository repo = repository;
        if (d == null || repo == null) return;

        boolean reportarNombre = d.cbReportName.isChecked();
        boolean reportarFoto = d.cbReportPhoto.isChecked();
        String observaciones = StringUtils.textOf(d.etObservaciones.getText());

        d.tilObservaciones.setError(null);
        d.tvReasonError.setVisibility(View.GONE);

        if (!reportarNombre && !reportarFoto) {
            d.tvReasonError.setText(R.string.ranking_report_error_reason_required);
            d.tvReasonError.setVisibility(View.VISIBLE);
            updateReportSubmitState(false);
            return;
        }

        if (observaciones.length() > 500) {
            d.tilObservaciones.setError(
                    getString(R.string.ranking_report_error_observaciones_too_long)
            );
            return;
        }

        setReportLoading(true);

        String username = requireArguments().getString(ARG_USERNAME, "");

        repo.reportarUsuario(
                username,
                reportarNombre,
                reportarFoto,
                observaciones,
                result -> {
                    if (!isAdded()) return;

                    setReportLoading(false);

                    if (result.isSuccess()) {
                        if (reportDialog != null) {
                            reportDialog.dismiss();
                        }
                        if (binding != null) {
                            TopSnackbar.success(
                                    binding.getRoot(),
                                    StringUtils.hasText(result.data)
                                            ? result.data
                                            : getString(R.string.ranking_report_success)
                            );
                        }
                        return;
                    }

                    if (binding != null) {
                        TopSnackbar.error(
                                binding.getRoot(),
                                result.error != null
                                        ? result.error.getMessage()
                                        : getString(R.string.ranking_report_error_generic)
                        );
                    }
                }
        );
    }

    /**
     * Habilita o deshabilita controles mientras el reporte está en curso.
     *
     * @param loading true cuando se está enviando el reporte.
     */
    private void setReportLoading(boolean loading) {
        BottomSheetRankingUserActionsBinding b = binding;
        DialogReportRankingUserBinding d = reportDialogBinding;

        if (b != null) {
            b.btnReportUser.setEnabled(!loading);
            b.btnClose.setEnabled(!loading);
        }

        if (d != null) {
            d.cardReportName.setEnabled(!loading);
            d.cardReportPhoto.setEnabled(!loading);
            d.cbReportName.setEnabled(!loading);
            d.cbReportPhoto.setEnabled(!loading);
            d.etObservaciones.setEnabled(!loading);
        }

        if (reportDialog != null) {
            Button negative = reportDialog.getButton(AlertDialog.BUTTON_NEGATIVE);

            updateReportSubmitState(loading);

            if (negative != null) {
                negative.setEnabled(!loading);
            }

            reportDialog.setCancelable(!loading);
            reportDialog.setCanceledOnTouchOutside(!loading);
        }

        setCancelable(!loading);
    }

    /**
     * Genera la URL final de la foto con una query param de versión.
     *
     * @param photoUrl URL base de la foto.
     * @param photoVersion versión que invalida la caché.
     * @return URL final con parámetro {@code v}, o null si no hay foto.
     */
    @Nullable
    private String buildVersionedPhotoUrl(@Nullable String photoUrl, int photoVersion) {
        if (!StringUtils.hasText(photoUrl)) {
            return null;
        }
        return photoUrl + (photoUrl.contains("?") ? "&" : "?") + "v=" + photoVersion;
    }

    @Override
    public void onDestroy() {
        RankingRepository repo = repository;
        if (repo != null) {
            repo.cancelAll();
        }
        repository = null;
        super.onDestroy();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
