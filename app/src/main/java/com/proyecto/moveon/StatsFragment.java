package com.proyecto.moveon;

import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import java.text.SimpleDateFormat; // estos 3 imports será para calcular los dias de la semana y trabajar en funcion a ello
import java.util.Calendar; // estos 3 imports será para calcular los dias de la semana y trabajar en funcion a ello
import java.util.Locale; // estos 3 imports será para calcular los dias de la semana y trabajar en funcion a ello

public class StatsFragment extends Fragment {
    // DECLARACIÓN DE VARIABLES UI
    private TextView tvTitle;

    // Card 1: Distancia, Tiempo, Racha
    private MaterialCardView cardStatsSummary;
    private TextView tvDistance;
    private TextView tvTime;
    private TextView tvStreak;

    // Card 2: Gráfico Semanal
    private MaterialCardView cardWeeklyChart;
    private ImageView ivChartPlaceholder; //se cambiará la imagenview por los gráficos

    // Card 3: Objetivo Semanal
    private MaterialCardView cardWeeklyGoal;
    private TextView tvGoalRemaining;
    private LinearProgressIndicator progressWeeklyGoal;
    private TextView tvCurrentProgress;
    private TextView tvGoalTarget;

    // Card 4: Actividad Reciente
    private MaterialCardView cardRecentActivity;
    private TextView tvTodayLabel;
    private TextView tvTodayDistance;
    private TextView tvYesterdayLabel;
    private TextView tvYesterdayDistance;
    private TextView tvDay2Label;
    private TextView tvDay2Distance;

    // Card 5: Comparación
    private MaterialCardView cardComparison;
    private TextView tvCurrentMonth;
    private TextView tvPreviousMonth;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Infla el layout del fragmento (Convertir el archivo XML del fragmento en una pantalla real que Android puede mostrar y usar)
        View view = inflater.inflate(R.layout.fragment_stats, container, false);
        // INICIALIZACIÓN DE VISTAS ( toddo lo "tocable por si queremos añadir funciones a ellos)
        initializeViews(view);
        return view;
    }
    // INICIALIZACIÓN DE VISTAS
    private void initializeViews(View view) {
        tvTitle = view.findViewById(R.id.tv_title);
        // Card 1: Resumen
        cardStatsSummary = view.findViewById(R.id.card_stats_summary);
        tvDistance = view.findViewById(R.id.tv_distance);
        tvTime = view.findViewById(R.id.tv_time);
        tvStreak = view.findViewById(R.id.tv_streak);
        // Card 2: Gráfico Semanal
        cardWeeklyChart = view.findViewById(R.id.card_weekly_chart);
        ivChartPlaceholder = view.findViewById(R.id.iv_chart_placeholder);
        // Card 3: Objetivo Semanal
        cardWeeklyGoal = view.findViewById(R.id.card_weekly_goal);
        tvGoalRemaining = view.findViewById(R.id.tv_goal_remaining);
        progressWeeklyGoal = view.findViewById(R.id.progress_weekly_goal);
        tvCurrentProgress = view.findViewById(R.id.tv_current_progress);
        tvGoalTarget = view.findViewById(R.id.tv_goal_target);
        // Card 4: Actividad Reciente
        cardRecentActivity = view.findViewById(R.id.card_recent_activity);
        tvTodayLabel = view.findViewById(R.id.tv_today_label);
        tvTodayDistance = view.findViewById(R.id.tv_today_distance);
        tvYesterdayLabel = view.findViewById(R.id.tv_yesterday_label);
        tvYesterdayDistance = view.findViewById(R.id.tv_yesterday_distance);
        tvDay2Label = view.findViewById(R.id.tv_day2_label);
        tvDay2Distance = view.findViewById(R.id.tv_day2_distance);
    }
    // TODO: Aqui pondremos todos los métodos para reocger información de la BBDD
}