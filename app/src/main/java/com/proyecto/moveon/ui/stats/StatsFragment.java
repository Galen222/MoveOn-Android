package com.proyecto.moveon.ui.stats;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.slider.Slider;
import com.proyecto.moveon.R;
import com.proyecto.moveon.core.concurrency.MoveOnExecutors;
import com.proyecto.moveon.core.i18n.AppLanguageManager;
import com.proyecto.moveon.core.stats.GlobalStatsNotifier;
import com.proyecto.moveon.databinding.FragmentStatsBinding;
import com.proyecto.moveon.domain.activity.ActividadItem;
import com.proyecto.moveon.domain.activity.StatsCalculator;
import com.proyecto.moveon.domain.activity.StatsResumen;
import com.proyecto.moveon.ui.profile.ShareRouteFormatter;
import com.proyecto.moveon.ui.profile.ShareRouteImageGenerator;
import com.proyecto.moveon.ui.profile.ShareRoutePreviewBottomSheet;
import com.proyecto.moveon.ui.ranking.RankingFragment;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Fragmento de estadísticas y resumen histórico del usuario.
 *
 * <p>Incluye acciones de borrado y compartición de rutas. El flujo de share se ejecuta
 * fuera del hilo principal y, tras esta corrección, siempre restablece la marca interna
 * {@code isSharingInProgress} incluso si el fragment se desacopla durante la generación
 * de la imagen.</p>
 */
public class StatsFragment extends Fragment {

    private FragmentStatsBinding binding;
    private StatsViewModel viewModel;

    @Nullable private StatsResumen lastResumen = null;
    private boolean isSharingInProgress = false;

    /**
     * Infla el layout de estadísticas y conserva el binding mientras la vista exista.
     *
     * @param inflater inflador usado para crear la jerarquía XML.
     * @param container contenedor padre del fragment, puede ser {@code null}.
     * @param savedInstanceState estado previamente guardado, puede ser {@code null}.
     * @return la raíz de {@link FragmentStatsBinding} para que Android la monte en pantalla.
     */
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentStatsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    /**
     * Inicializa el {@link StatsViewModel}, registra listeners y observadores, y dispara la carga inicial.
     *
     * @param view vista raíz ya creada en {@link #onCreateView(LayoutInflater, ViewGroup, Bundle)}.
     * @param savedInstanceState estado previamente guardado, puede ser {@code null}.
     */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(StatsViewModel.class);
        setupListeners();
        observeViewModel();
        viewModel.load();
    }

    /**
     * Libera el binding y resetea la marca interna de compartición para que una recreación de vista
     * no herede un flujo de share a medias de la instancia anterior.
     */
    @Override
    public void onDestroyView() {
        // Si la vista se destruye en mitad de un share, la siguiente no debe heredar
        // un estado de "compartiendo" pendiente de la instancia anterior.
        isSharingInProgress = false;
        super.onDestroyView();
        binding = null;
    }

    /**
     * Conecta las acciones principales de la UI con sus flujos: recarga, edición de metas,
     * apertura del histórico y acceso al ranking.
     */
    private void setupListeners() {
        binding.btnRetry.setOnClickListener(v -> viewModel.load());
        binding.tvWeeklyGoalHeader.setOnClickListener(v -> showGoalDialog(true));
        binding.tvMonthlyGoalHeader.setOnClickListener(v -> showGoalDialog(false));
        binding.cardHistory.setOnClickListener(v -> openUnifiedHistory());
        binding.cardRanking.setOnClickListener(v ->
                RankingFragment.newInstance(null)
                        .show(getChildFragmentManager(), RankingFragment.TAG));
    }

    /**
     * Observa el estado de estadísticas y los eventos de borrado para refrescar la pantalla
     * y emitir mensajes globales cuando una operación termina.
     */
    private void observeViewModel() {
        viewModel.getStatsState().observe(getViewLifecycleOwner(), state -> {
            if (state == null || binding == null) return;
            binding.layoutLoading.setVisibility(state.loading ? View.VISIBLE : View.GONE);
            if (state.data != null) {
                lastResumen = state.data;
                bindAll(state.data);
                showContent();
            } else if (state.error != null && !state.loading) {
                showEmpty();
            }
        });

        viewModel.getDeleteEvent().observe(getViewLifecycleOwner(), event -> {
            if (binding == null || event == null) return;
            var state = event.getContentIfNotHandled();
            if (state == null) return;
            if (state.data != null) {
                GlobalStatsNotifier.getInstance().notifySuccess(
                        getString(R.string.stats_delete_ok));
            } else if (state.error != null) {
                GlobalStatsNotifier.getInstance().notifyError(
                        state.error.getMessage());
            }
        });
    }

    /**
     * Reparte un {@link StatsResumen} por todas las tarjetas y secciones del fragment.
     *
     * @param r resumen agregado calculado por el {@link StatsViewModel}.
     */
    private void bindAll(@NonNull StatsResumen r) {
        if (binding == null) return;
        bindCard5RecentActivity(r);
        bindCard1Today(r);
        bindWeeklySection(r);
        bindCard7WeekComparison(r);
        bindMonthlySection(r);
        bindCard6MonthComparison(r);
        bindCard8Totals(r);
        bindHistoryHub(r);
    }

    /**
     * Rellena la tarjeta de actividad del día con distancia, duración y calorías.
     *
     * @param r resumen del que se extraen las métricas de hoy.
     */
    private void bindCard1Today(@NonNull StatsResumen r) {
        binding.tvTodayDist.setText(formatDistance(r.todayDistanceMeters));
        binding.tvTodayTime.setText(formatDuration(r.todayDurationSeconds));
        binding.tvTodayKcal.setText(formatKcal(r.todayCalories));
    }

    /**
     * Actualiza el gráfico semanal y el progreso contra la meta de la semana.
     *
     * @param r resumen con distancias y objetivo semanal ya calculados.
     */
    private void bindWeeklySection(@NonNull StatsResumen r) {
        renderWeeklyChart(r.weekDaysDistanceMeters);

        int progress = r.weeklyGoalMeters > 0
                ? (int) Math.min(100L, (r.weeklyDistanceMeters * 100L) / r.weeklyGoalMeters)
                : 0;
        binding.progressWeeklyGoal.setProgress(progress, true);
        binding.tvWeeklyProgress.setText(formatDistance(r.weeklyDistanceMeters));
        binding.tvWeeklyGoalTarget.setText(formatDistance(r.weeklyGoalMeters));

        long remaining = Math.max(0L, r.weeklyGoalMeters - r.weeklyDistanceMeters);
        binding.tvWeeklyGoalRemaining.setText(remaining > 0
                ? getString(R.string.stats_goal_remaining_format, formatDistance(remaining))
                : getString(R.string.stats_weekly_goal_done));
    }

    /**
     * Actualiza el gráfico mensual y el progreso contra la meta del mes.
     *
     * @param r resumen con los acumulados y objetivo del mes actual.
     */
    private void bindMonthlySection(@NonNull StatsResumen r) {
        renderMonthlyChart(r);

        int progress = r.monthlyGoalMeters > 0
                ? (int) Math.min(100L, (r.currentMonthDistanceMeters * 100L) / r.monthlyGoalMeters)
                : 0;
        binding.progressMonthlyGoal.setProgress(progress, true);
        binding.tvMonthlyProgress.setText(formatDistance(r.currentMonthDistanceMeters));
        binding.tvMonthlyGoalTarget.setText(formatDistance(r.monthlyGoalMeters));

        long remaining = Math.max(0L, r.monthlyGoalMeters - r.currentMonthDistanceMeters);
        binding.tvMonthlyGoalRemaining.setText(remaining > 0
                ? getString(R.string.stats_monthly_goal_remaining_format, formatDistance(remaining))
                : getString(R.string.stats_monthly_goal_done));
    }

    /**
     * Muestra la actividad de hoy, ayer y anteayer junto con sus etiquetas temporales.
     *
     * @param r resumen que contiene esos tres acumulados recientes.
     */
    private void bindCard5RecentActivity(@NonNull StatsResumen r) {
        bindRecentActivityLabels();
        binding.tvTodayDistance.setText(formatDistance(r.todayDistanceMeters));
        binding.tvYesterdayDistance.setText(formatDistance(r.yesterdayDistanceMeters));
        binding.tvDay2Distance.setText(formatDistance(r.twoDaysAgoDistanceMeters));
    }

    /**
     * Calcula y pinta las etiquetas de los tres días recientes usando el locale activo de la app.
     */
    private void bindRecentActivityLabels() {
        if (binding == null) return;

        final Locale locale = getAppLocale();
        final LocalDate today = LocalDate.now();

        binding.tvRecentDay0Label.setText(getString(R.string.stats_period_today));
        binding.tvRecentDay1Label.setText(formatWeekdayLabel(today.minusDays(1), locale));
        binding.tvRecentDay2Label.setText(formatWeekdayLabel(today.minusDays(2), locale));
    }

    /**
     * Devuelve el locale actualmente activo en la app, no necesariamente el configurado por el sistema.
     *
     * @return locale resuelto por {@link AppLanguageManager}.
     */
    @NonNull
    private Locale getAppLocale() {
        return AppLanguageManager.getActiveLocale(requireContext());
    }

    /**
     * Formatea un nombre de día con inicial en mayúscula para usarlo como etiqueta visible.
     *
     * @param date fecha a convertir en nombre de día.
     * @param locale locale con el que se obtiene el nombre localizado.
     * @return día de la semana con la primera letra capitalizada.
     */
    @NonNull
    private String formatWeekdayLabel(@NonNull LocalDate date, @NonNull Locale locale) {
        String label = date.getDayOfWeek().getDisplayName(TextStyle.FULL, locale);
        if (label.isEmpty()) {
            return label;
        }
        return label.substring(0, 1).toUpperCase(locale) + label.substring(1);
    }

    /**
     * Rellena la comparativa entre el mes actual y el mes anterior.
     *
     * @param r resumen con los agregados mensuales listos para mostrar.
     */
    private void bindCard6MonthComparison(@NonNull StatsResumen r) {
        binding.tvCurrentMonthDist.setText(formatDistance(r.currentMonthDistanceMeters));
        binding.tvCurrentMonthKcal.setText(formatKcal(r.currentMonthCalories));
        binding.tvPreviousMonthDist.setText(formatDistance(r.previousMonthDistanceMeters));
        binding.tvPreviousMonthKcal.setText(formatKcal(r.previousMonthCalories));
    }

    /**
     * Rellena la comparativa entre la semana actual y la previa.
     *
     * @param r resumen con los agregados semanales listos para mostrar.
     */
    private void bindCard7WeekComparison(@NonNull StatsResumen r) {
        binding.tvCurrentWeekDist.setText(formatDistance(r.weeklyDistanceMeters));
        binding.tvCurrentWeekKcal.setText(formatKcal(r.weeklyCalories));
        binding.tvPreviousWeekDist.setText(formatDistance(r.previousWeekDistanceMeters));
        binding.tvPreviousWeekKcal.setText(formatKcal(r.previousWeekCalories));
    }

    /**
     * Muestra los totales históricos acumulados y la racha actual del usuario.
     *
     * @param r resumen global de actividad.
     */
    private void bindCard8Totals(@NonNull StatsResumen r) {
        binding.tvTotalDistance.setText(formatDistance(r.totalDistanceMeters));
        binding.tvTotalTime.setText(formatDuration(r.totalDurationSeconds));
        binding.tvTotalKcal.setText(formatKcal(r.totalCalories));
        binding.tvStreak.setText(formatStreak(r.streakDays));
    }

    /**
     * Habilita o deshabilita el acceso al histórico según exista contenido navegable.
     *
     * @param r resumen desde el que se comprueba si hay bloques mensuales o actividades totales.
     */
    private void bindHistoryHub(@NonNull StatsResumen r) {
        boolean hasHistory = !r.monthBlocks.isEmpty() || r.totalActivities > 0;
        binding.cardHistory.setEnabled(hasHistory);
        binding.cardHistory.setAlpha(hasHistory ? 1.0f : 0.5f);
    }

    /**
     * Construye el gráfico de barras semanal y resalta el día actual.
     *
     * @param distancias distancias por día de la semana, en metros.
     */
    private void renderWeeklyChart(@NonNull long[] distancias) {
        if (binding == null) return;
        String[] dias = getResources().getStringArray(R.array.stats_week_days_short);
        int todayIndex = LocalDate.now().getDayOfWeek().getValue() - 1;
        renderBarChart(binding.llChartBars, distancias, dias, todayIndex);
    }

    /**
     * Construye el gráfico del mes actual usando semanas reales o bloques vacíos si todavía no hay datos.
     *
     * @param resumen resumen del que se extraen los bloques semanales del mes.
     */
    private void renderMonthlyChart(@NonNull StatsResumen resumen) {
        if (binding == null) return;
        binding.llMonthChartBars.removeAllViews();

        StatsResumen.MonthBlock current = findCurrentMonthBlock(resumen.monthBlocks);
        List<StatsResumen.WeekBlock> weeks = current != null && !current.weeks.isEmpty()
                ? current.weeks
                : buildEmptyCurrentMonthWeekBlocks();

        long[] distances = new long[weeks.size()];
        String[] labels = new String[weeks.size()];

        for (int i = 0; i < weeks.size(); i++) {
            StatsResumen.WeekBlock week = weeks.get(i);
            distances[i] = week.distanceMeters;
            labels[i] = week.startDay == week.endDay
                    ? String.valueOf(week.startDay)
                    : week.startDay + "-" + week.endDay;
        }

        int currentWeekIndex = findCurrentMonthWeekIndex(weeks);
        renderBarChart(binding.llMonthChartBars, distances, labels, currentWeekIndex);
    }

    /**
     * Busca el bloque agregado que corresponde al mes actual.
     *
     * @param blocks bloques mensuales disponibles en el resumen.
     * @return bloque del mes en curso o {@code null} si todavía no existe.
     */
    @Nullable
    private StatsResumen.MonthBlock findCurrentMonthBlock(@NonNull List<StatsResumen.MonthBlock> blocks) {
        LocalDate now = LocalDate.now();
        for (StatsResumen.MonthBlock block : blocks) {
            if (block.year == now.getYear() && block.month == now.getMonthValue()) {
                return block;
            }
        }
        return null;
    }

    /**
     * Localiza qué bloque semanal contiene el día actual para resaltarlo en el gráfico mensual.
     *
     * @param weeks semanas visibles del mes actual.
     * @return índice de la semana actual o {@code -1} si hoy queda fuera de los bloques recibidos.
     */
    private int findCurrentMonthWeekIndex(@NonNull List<StatsResumen.WeekBlock> weeks) {
        LocalDate today = LocalDate.now();
        int todayDayOfMonth = today.getDayOfMonth();

        for (int i = 0; i < weeks.size(); i++) {
            StatsResumen.WeekBlock week = weeks.get(i);
            if (todayDayOfMonth >= week.startDay && todayDayOfMonth <= week.endDay) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Genera bloques semanales vacíos del mes actual cuando aún no hay actividad sincronizada.
     *
     * @return lista de {@link StatsResumen.WeekBlock} con distancia, tiempo y calorías a cero.
     */
    @NonNull
    private List<StatsResumen.WeekBlock> buildEmptyCurrentMonthWeekBlocks() {
        LocalDate firstDay = LocalDate.now().withDayOfMonth(1);
        LocalDate lastDay = YearMonth.from(firstDay).atEndOfMonth();
        LocalDate cursor = firstDay.minusDays(firstDay.getDayOfWeek().getValue() - 1L);

        List<StatsResumen.WeekBlock> emptyWeeks = new ArrayList<>();
        while (!cursor.isAfter(lastDay)) {
            LocalDate monday = cursor;
            LocalDate sunday = cursor.plusDays(6);

            LocalDate startVisible = monday.isBefore(firstDay) ? firstDay : monday;
            LocalDate endVisible = sunday.isAfter(lastDay) ? lastDay : sunday;

            emptyWeeks.add(new StatsResumen.WeekBlock(
                    startVisible.getDayOfMonth(),
                    endVisible.getDayOfMonth(),
                    0L,
                    0L,
                    0L
            ));

            cursor = cursor.plusDays(7);
        }
        return emptyWeeks;
    }

    /**
     * Dibuja un gráfico de barras simple dentro del contenedor recibido, ajustando cada altura
     * de forma proporcional al valor máximo y pudiendo resaltar una columna concreta.
     *
     * @param container layout que recibirá las columnas del gráfico.
     * @param values valores numéricos a representar.
     * @param labels etiquetas visibles bajo cada barra.
     * @param highlightedIndex índice de la barra destacada, o {@code null} si no hay ninguna.
     */
    private void renderBarChart(@NonNull LinearLayout container,
                                @NonNull long[] values,
                                @NonNull String[] labels,
                                @Nullable Integer highlightedIndex) {
        container.removeAllViews();
        if (values.length == 0 || labels.length != values.length) return;

        long maxVal = 1L;
        for (long value : values) {
            if (value > maxVal) maxVal = value;
        }

        int chartHeightPx = (int) getResources().getDimension(R.dimen.stats_chart_height);
        int barWidthPx = (int) getResources().getDimension(R.dimen.stats_chart_bar_width);
        int barRadiusPx = (int) getResources().getDimension(R.dimen.stats_chart_bar_radius);
        int labelSizePx = (int) getResources().getDimension(R.dimen.stats_chart_label_size);

        int colorActive = ContextCompat.getColor(requireContext(), R.color.greenPrimary);
        int colorHasData = ContextCompat.getColor(requireContext(), R.color.statsBarHasDataColor);
        int colorInactive = ContextCompat.getColor(requireContext(), R.color.dividerColor);
        int colorLabel = ContextCompat.getColor(requireContext(), R.color.textTertiary);

        for (int i = 0; i < values.length; i++) {
            LinearLayout column = new LinearLayout(requireContext());
            column.setOrientation(LinearLayout.VERTICAL);
            column.setGravity(android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL);
            column.setLayoutParams(new LinearLayout.LayoutParams(0, chartHeightPx, 1f));

            View bar = new View(requireContext());
            int drawableMaxHeight = Math.max(1, chartHeightPx - labelSizePx * 2);
            int computedHeight = values[i] == 0L
                    ? barRadiusPx * 2
                    : (int) ((values[i] * (long) drawableMaxHeight) / maxVal);
            int barHeightPx = Math.max(barRadiusPx * 2, computedHeight);
            bar.setLayoutParams(new LinearLayout.LayoutParams(barWidthPx, barHeightPx));

            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.RECTANGLE);
            shape.setCornerRadii(new float[]{
                    barRadiusPx, barRadiusPx,
                    barRadiusPx, barRadiusPx,
                    0, 0,
                    0, 0
            });
            boolean isHighlighted = highlightedIndex != null && highlightedIndex >= 0 && i == highlightedIndex;
            boolean hasData = values[i] > 0L;
            shape.setColor(isHighlighted ? colorActive : (hasData ? colorHasData : colorInactive));
            bar.setBackground(shape);

            TextView label = new TextView(requireContext());
            label.setText(labels[i]);
            label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, labelSizePx);
            label.setTextColor(colorLabel);
            label.setGravity(android.view.Gravity.CENTER);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            labelParams.topMargin = barRadiusPx;
            label.setLayoutParams(labelParams);

            column.addView(bar);
            column.addView(label);
            container.addView(column);
        }
    }

    /**
     * Muestra el diálogo deslizante para editar la meta semanal o mensual partiendo del valor actual.
     *
     * @param isWeekly {@code true} para editar la meta semanal; {@code false} para la mensual.
     */
    private void showGoalDialog(boolean isWeekly) {
        final long currentMeters;
        if (lastResumen != null) {
            currentMeters = isWeekly ? lastResumen.weeklyGoalMeters : lastResumen.monthlyGoalMeters;
        } else {
            currentMeters = isWeekly
                    ? StatsCalculator.DEFAULT_WEEKLY_GOAL_METERS
                    : StatsCalculator.DEFAULT_MONTHLY_GOAL_METERS;
        }

        final float valueFrom = isWeekly ? 5f : 10f;
        final float valueTo = isWeekly ? 200f : 500f;
        final float stepSize = isWeekly ? 5f : 10f;

        float currentKm = currentMeters / 1000f;
        float clampedKm = Math.max(valueFrom, Math.min(valueTo, currentKm));
        float initialValue = Math.round(clampedKm / stepSize) * stepSize;
        initialValue = Math.max(valueFrom, Math.min(valueTo, initialValue));

        Context context = requireContext();
        View content = LayoutInflater.from(context).inflate(R.layout.dialog_goal_slider, null, false);

        TextView tvValue = content.findViewById(R.id.tvGoalSliderValue);
        Slider slider = content.findViewById(R.id.sliderGoal);

        tvValue.setText(getString(R.string.stats_format_km, initialValue));

        slider.setValueFrom(valueFrom);
        slider.setValueTo(valueTo);
        slider.setStepSize(stepSize);
        slider.setValue(initialValue);

        slider.addOnChangeListener((s, value, fromUser) ->
                tvValue.setText(getString(R.string.stats_format_km, value)));

        int titleRes = isWeekly
                ? R.string.stats_dialog_weekly_goal_title
                : R.string.stats_dialog_monthly_goal_title;

        TextView titleView = new TextView(context);
        titleView.setText(titleRes);
        titleView.setTextColor(ContextCompat.getColor(context, R.color.textPrimary));
        titleView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f);
        titleView.setTypeface(null, android.graphics.Typeface.NORMAL);
        titleView.setPadding(dp(24), dp(20), dp(24), dp(4));

        new MaterialAlertDialogBuilder(context)
                .setCustomTitle(titleView)
                .setView(content)
                .setNegativeButton(R.string.dialog_btn_cancel, null)
                .setPositiveButton(R.string.dialog_btn_save, (dialog, which) -> {
                    long selectedMeters = (long) slider.getValue() * 1_000L;
                    if (isWeekly) {
                        viewModel.setWeeklyGoal(selectedMeters);
                    } else {
                        viewModel.setMonthlyGoal(selectedMeters);
                    }
                })
                .show();
    }

    /**
     * Abre el histórico detallado usando los bloques mensuales ya cargados y la lista completa de actividades.
     */
    private void openUnifiedHistory() {
        if (lastResumen == null || lastResumen.monthBlocks.isEmpty()) return;
        List<ActividadItem> actividades = viewModel.getAllActividades().getValue();
        if (actividades == null) actividades = Collections.emptyList();
        HistorialBottomSheet.newInstance(lastResumen.monthBlocks, actividades)
                .show(getChildFragmentManager(), "historial");
    }

    /**
     * Convierte una medida en dp a píxeles usando la densidad actual de pantalla.
     *
     * @param value valor en density-independent pixels.
     * @return equivalente aproximado en píxeles.
     */
    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /**
     * Punto de entrada visible para colaboradores del paquete que delega en el borrado interno.
     *
     * @param item actividad que el usuario quiere eliminar.
     */
    void onDeleteClickPublic(@NonNull ActividadItem item) {
        onDeleteClick(item);
    }

    /**
     * Punto de entrada visible para colaboradores del paquete que delega en el flujo de compartir.
     *
     * @param item actividad cuya ruta se quiere compartir.
     */
    void onShareClickPublic(@NonNull ActividadItem item) {
        onShareClick(item);
    }

    /**
     * Lanza la confirmación de borrado si la actividad ya está sincronizada; las pendientes quedan protegidas.
     *
     * @param item actividad seleccionada en el historial.
     */
    private void onDeleteClick(@NonNull ActividadItem item) {
        if (item.isPendingSync()) {
            GlobalStatsNotifier.getInstance().notifyWarning(
                    getString(R.string.stats_delete_no_sync));
            return;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.stats_delete_title)
                .setMessage(R.string.stats_delete_message)
                .setNegativeButton(R.string.dialog_btn_cancel, null)
                .setPositiveButton(R.string.stats_delete_confirm,
                        (dialog, which) -> viewModel.borrarActividad(item.localId))
                .show();
    }

    /**
     * Genera en background la tarjeta visual de la ruta y, si todo va bien, abre la preview para compartir.
     *
     * @param item actividad desde la que se obtienen polilínea, métricas y texto del share.
     */
    @SuppressWarnings("resource")
    private void onShareClick(@NonNull ActividadItem item) {
        if (binding == null || isSharingInProgress) return;

        isSharingInProgress = true;

        final Context localizedContext = AppLanguageManager.localizedContext(requireContext());
        MoveOnExecutors.executeIo(() -> {
            try {
                Uri uri = ShareRouteImageGenerator.generateShareImage(localizedContext, item);
                String shareText = ShareRouteFormatter.buildShareText(localizedContext, item);

                FragmentActivity activity = getActivity();
                if (activity == null) {
                    // El fragment puede desacoplarse mientras el trabajo en background
                    // sigue ejecutándose. Restablecemos la flag para no bloquear
                    // nuevos intentos de share en esta instancia.
                    isSharingInProgress = false;
                    return;
                }

                activity.runOnUiThread(() -> {
                    isSharingInProgress = false;
                    if (binding == null || !isAdded()) return;
                    if (getChildFragmentManager().isStateSaved()) {
                        GlobalStatsNotifier.getInstance().notifyError(
                                getString(R.string.share_routes_error_opening_preview));
                        return;
                    }
                    ShareRoutePreviewBottomSheet.newInstance(uri, shareText)
                            .show(getChildFragmentManager(), ShareRoutePreviewBottomSheet.TAG);
                });
            } catch (IllegalArgumentException e) {
                handleShareError(R.string.share_routes_error_no_polyline);
            } catch (Exception e) {
                handleShareError(R.string.share_routes_error_generating_image);
            }
        });
    }

    /**
     * Restablece el estado interno del flujo de share y muestra el error si la vista sigue activa.
     *
     * <p>Si la activity ya no existe, igualmente se limpia la flag para que la instancia del
     * fragment no quede inutilizable al volver a la pestaña.</p>
     */
    private void handleShareError(int messageRes) {
        FragmentActivity activity = getActivity();
        if (activity == null) {
            isSharingInProgress = false;
            return;
        }
        activity.runOnUiThread(() -> {
            isSharingInProgress = false;
            if (binding != null) {
                GlobalStatsNotifier.getInstance().notifyError(getString(messageRes));
            }
        });
    }

    /**
     * Hace visible el contenido principal y oculta el estado vacío.
     */
    private void showContent() {
        if (binding == null) return;
        binding.scrollContent.setVisibility(View.VISIBLE);
        binding.layoutEmpty.setVisibility(View.GONE);
    }

    /**
     * Muestra el estado vacío cuando no hay datos renderizables o la carga falla.
     */
    private void showEmpty() {
        if (binding == null) return;
        binding.scrollContent.setVisibility(View.GONE);
        binding.layoutEmpty.setVisibility(View.VISIBLE);
    }

    /**
     * Convierte una distancia en metros al formato localizado en kilómetros usado por la pantalla.
     *
     * @param meters distancia en metros.
     * @return texto listo para pintar en la UI.
     */
    @NonNull
    private String formatDistance(long meters) {
        if (meters == 0L) {
            return "0 km";
        }
        return getString(R.string.stats_format_km, meters / 1000.0f);
    }

    /**
     * Formatea una duración total en minutos o en horas y minutos según su magnitud.
     *
     * @param seconds duración en segundos.
     * @return texto localizado con el formato temporal más legible.
     */
    @NonNull
    private String formatDuration(long seconds) {
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        return hours > 0L
                ? getString(R.string.stats_format_time_hm, hours, minutes)
                : getString(R.string.stats_format_time_m, minutes);
    }

    /**
     * Formatea calorías usando sufijos abreviados para miles o millones cuando procede.
     *
     * @param kcal calorías acumuladas.
     * @return representación corta apta para tarjetas compactas.
     */
    @NonNull
    private String formatKcal(long kcal) {
        if (kcal >= 1_000_000L) {
            return getString(R.string.stats_format_kcal_m, kcal / 1_000_000.0f);
        }
        if (kcal >= 1_000L) {
            return getString(R.string.stats_format_kcal_k, kcal / 1_000.0f);
        }
        return getString(R.string.stats_format_kcal, (int) kcal);
    }

    /**
     * Genera el texto de racha para el contador global de días consecutivos.
     *
     * @param streakDays número de días seguidos activos.
     * @return cadena localizada con la racha embebida.
     */
    @NonNull
    private String formatStreak(int streakDays) {
        return getResources().getQuantityString(
                R.plurals.stats_format_streak, streakDays, streakDays);
    }
}
