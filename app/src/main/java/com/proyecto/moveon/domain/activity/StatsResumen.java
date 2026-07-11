package com.proyecto.moveon.domain.activity;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.List;

/**
 * Modelo de dominio con todos los datos calculados para la pantalla de estadísticas.
 * Todos los campos son inmutables. Se construye en StatsViewModel a partir de
 * las actividades cacheadas en Room y las preferencias del usuario.
 * Semanas: siempre lunes–domingo (ISO 8601).
 * Racha: días consecutivos hacia atrás desde hoy inclusive. 0 si hoy no hay actividad.
 */
public final class StatsResumen {

    // -------------------------------------------------------------------------
    // Card 1 — Hoy
    // -------------------------------------------------------------------------
    public final long todayDistanceMeters;
    public final long todayDurationSeconds;
    public final long todayCalories;

    // -------------------------------------------------------------------------
    // Card 2 — Gráfico semanal (distancia por día lunes–domingo, 7 valores)
    // -------------------------------------------------------------------------
    @NonNull
    public final long[] weekDaysDistanceMeters;

    // -------------------------------------------------------------------------
    // Card 3 — Objetivo semanal
    // -------------------------------------------------------------------------
    public final long weeklyDistanceMeters;
    public final long weeklyGoalMeters;

    // -------------------------------------------------------------------------
    // Card 4 — Objetivo mensual
    // -------------------------------------------------------------------------
    public final long currentMonthDistanceMeters;
    public final long monthlyGoalMeters;

    // -------------------------------------------------------------------------
    // Card 5 — Actividad reciente (hoy, ayer, hace 2 días)
    // -------------------------------------------------------------------------
    public final long yesterdayDistanceMeters;
    public final long twoDaysAgoDistanceMeters;

    // -------------------------------------------------------------------------
    // Card 6 — Comparativa mes actual vs anterior
    // -------------------------------------------------------------------------
    public final long previousMonthDistanceMeters;
    public final long currentMonthCalories;
    public final long previousMonthCalories;

    // -------------------------------------------------------------------------
    // Card 7 — Comparativa semana actual vs anterior
    // -------------------------------------------------------------------------
    public final long weeklyCalories;
    public final long previousWeekDistanceMeters;
    public final long previousWeekCalories;

    // -------------------------------------------------------------------------
    // Card 8 — Totales históricos + racha
    // -------------------------------------------------------------------------
    public final long totalDistanceMeters;
    public final long totalDurationSeconds;
    public final long totalCalories;
    public final int  streakDays;
    public final int  totalActivities;

    // -------------------------------------------------------------------------
    // Card 9 — Historial por semanas (calendario)
    // -------------------------------------------------------------------------
    @NonNull
    public final List<MonthBlock> monthBlocks;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Construye el resumen consolidado que consumirá la UI de estadísticas.
     *
     * <p>Los campos vienen pre-calculados por {@code StatsCalculator}: la clase
     * no los recalcula ni valida, solo actúa como contenedor inmutable.</p>
     *
     * @param todayDistanceMeters metros recorridos hoy.
     * @param todayDurationSeconds segundos en actividad hoy.
     * @param todayCalories calorías quemadas hoy.
     * @param weekDaysDistanceMeters array fijo de 7 posiciones con los metros de cada día de la semana (lunes-domingo).
     * @param weeklyDistanceMeters metros acumulados esta semana.
     * @param weeklyGoalMeters objetivo semanal configurado por el usuario.
     * @param currentMonthDistanceMeters metros acumulados en el mes actual.
     * @param monthlyGoalMeters objetivo mensual configurado por el usuario.
     * @param yesterdayDistanceMeters metros de ayer, usados para comparativas cortas.
     * @param twoDaysAgoDistanceMeters metros de hace dos días.
     * @param previousMonthDistanceMeters metros del mes anterior cerrado (para mostrar variación).
     * @param currentMonthCalories calorías del mes actual.
     * @param previousMonthCalories calorías del mes anterior.
     * @param weeklyCalories calorías acumuladas esta semana.
     * @param previousWeekDistanceMeters metros de la semana anterior cerrada.
     * @param previousWeekCalories calorías de la semana anterior.
     * @param totalDistanceMeters metros totales históricos.
     * @param totalDurationSeconds segundos totales históricos en actividad.
     * @param totalCalories calorías totales históricas.
     * @param streakDays racha de días consecutivos con actividad.
     * @param totalActivities número de actividades cerradas hasta la fecha.
     * @param monthBlocks bloques agregados por mes para pintar el histórico en la UI.
     */
    public StatsResumen(
            long todayDistanceMeters,
            long todayDurationSeconds,
            long todayCalories,
            @NonNull long[] weekDaysDistanceMeters,
            long weeklyDistanceMeters,
            long weeklyGoalMeters,
            long currentMonthDistanceMeters,
            long monthlyGoalMeters,
            long yesterdayDistanceMeters,
            long twoDaysAgoDistanceMeters,
            long previousMonthDistanceMeters,
            long currentMonthCalories,
            long previousMonthCalories,
            long weeklyCalories,
            long previousWeekDistanceMeters,
            long previousWeekCalories,
            long totalDistanceMeters,
            long totalDurationSeconds,
            long totalCalories,
            int streakDays,
            int totalActivities,
            @NonNull List<MonthBlock> monthBlocks) {

        this.todayDistanceMeters        = todayDistanceMeters;
        this.todayDurationSeconds       = todayDurationSeconds;
        this.todayCalories              = todayCalories;
        this.weekDaysDistanceMeters     = weekDaysDistanceMeters;
        this.weeklyDistanceMeters       = weeklyDistanceMeters;
        this.weeklyGoalMeters           = weeklyGoalMeters;
        this.currentMonthDistanceMeters = currentMonthDistanceMeters;
        this.monthlyGoalMeters          = monthlyGoalMeters;
        this.yesterdayDistanceMeters    = yesterdayDistanceMeters;
        this.twoDaysAgoDistanceMeters   = twoDaysAgoDistanceMeters;
        this.previousMonthDistanceMeters = previousMonthDistanceMeters;
        this.currentMonthCalories       = currentMonthCalories;
        this.previousMonthCalories      = previousMonthCalories;
        this.weeklyCalories             = weeklyCalories;
        this.previousWeekDistanceMeters = previousWeekDistanceMeters;
        this.previousWeekCalories       = previousWeekCalories;
        this.totalDistanceMeters        = totalDistanceMeters;
        this.totalDurationSeconds       = totalDurationSeconds;
        this.totalCalories              = totalCalories;
        this.streakDays                 = streakDays;
        this.totalActivities            = totalActivities;
        this.monthBlocks                = monthBlocks;
    }

    // -------------------------------------------------------------------------
    // Factory: estado vacío con defaults
    // -------------------------------------------------------------------------

    /**
     * Devuelve un resumen vacío pero con los objetivos del usuario ya
     * aplicados. Se usa como estado inicial antes de tener datos locales o
     * como fallback si el cálculo falla, para que la UI no vea {@code null}
     * ni tenga que inventarse objetivos.
     *
     * @param weeklyGoalMeters objetivo semanal del usuario en metros.
     * @param monthlyGoalMeters objetivo mensual del usuario en metros.
     * @return resumen con todos los totales a cero y los objetivos indicados.
     */
    @NonNull
    public static StatsResumen empty(long weeklyGoalMeters, long monthlyGoalMeters) {
        return new StatsResumen(
                0L, 0L, 0L,
                new long[7],
                0L, weeklyGoalMeters,
                0L, monthlyGoalMeters,
                0L, 0L,
                0L, 0L, 0L,
                0L, 0L, 0L,
                0L, 0L, 0L,
                0, 0,
                Collections.emptyList()
        );
    }

    // -------------------------------------------------------------------------
    // Modelo de historial (Card 9)
    // -------------------------------------------------------------------------

    /** Un mes con su lista de semanas ISO. */
    public static final class MonthBlock {
        /** Año del mes, por ej. 2025. */
        public final int year;
        /** Mes (1–12). */
        public final int month;
        /** Distancia total del mes en metros. */
        public final long distanceMeters;
        /** Calorías totales del mes. */
        public final long calories;
        /** Duración total del mes en segundos. */
        public final long durationSeconds;
        /** Semanas ISO que componen este mes. */
        @NonNull
        public final List<WeekBlock> weeks;

        public MonthBlock(int year, int month,
                          long distanceMeters, long calories, long durationSeconds,
                          @NonNull List<WeekBlock> weeks) {
            this.year            = year;
            this.month           = month;
            this.distanceMeters  = distanceMeters;
            this.calories        = calories;
            this.durationSeconds = durationSeconds;
            this.weeks           = weeks;
        }
    }

    /** Una semana ISO (lunes–domingo) dentro de un mes. */
    public static final class WeekBlock {
        /** Día del mes en que empieza la semana (lunes). */
        public final int startDay;
        /** Día del mes en que termina la semana (domingo). */
        public final int endDay;
        /** Distancia total de la semana en metros. */
        public final long distanceMeters;
        /** Calorías totales de la semana. */
        public final long calories;
        /** Duración total de la semana en segundos. */
        public final long durationSeconds;

        public WeekBlock(int startDay, int endDay,
                         long distanceMeters, long calories, long durationSeconds) {
            this.startDay        = startDay;
            this.endDay          = endDay;
            this.distanceMeters  = distanceMeters;
            this.calories        = calories;
            this.durationSeconds = durationSeconds;
        }
    }
}