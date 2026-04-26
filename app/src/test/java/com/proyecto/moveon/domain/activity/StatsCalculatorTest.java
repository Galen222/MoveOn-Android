package com.proyecto.moveon.domain.activity;

import static org.junit.Assert.*;

import com.proyecto.moveon.data.activities.ActivitySyncState;

import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Tests unitarios masivos para {@link StatsCalculator}.
 *
 * <p>Se centran en escenarios de agregación pura que no dependen de Android:
 * totales históricos, objetivos, rachas, semanas ISO y bloques mensuales.</p>
 */
public class StatsCalculatorTest {

    /**
     * Verifica que una lista vacía devuelve un resumen seguro, con todos los acumulados a cero
     * y respetando los objetivos configurados por el usuario.
     */
    @Test
    public void calcular_emptyList_returnsEmptyResumenWithGoals() {
        StatsResumen resumen = StatsCalculator.calcular(Collections.emptyList(), 12_000L, 44_000L);

        assertEquals(0L, resumen.todayDistanceMeters);
        assertEquals(0L, resumen.weeklyDistanceMeters);
        assertEquals(0L, resumen.currentMonthDistanceMeters);
        assertEquals(12_000L, resumen.weeklyGoalMeters);
        assertEquals(44_000L, resumen.monthlyGoalMeters);
        assertEquals(0, resumen.totalActivities);
        assertEquals(7, resumen.weekDaysDistanceMeters.length);
        assertTrue(resumen.monthBlocks.isEmpty());
    }

    /**
     * Verifica que una actividad de hoy alimenta las tarjetas de hoy, semana, mes,
     * totales históricos y racha.
     */
    @Test
    public void calcular_todayActivity_populatesTodayWeekMonthAndTotals() {
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        ActividadItem item = activity("today", today, 5_000, 1_800, 350);

        StatsResumen resumen = StatsCalculator.calcular(Collections.singletonList(item), 10_000L, 30_000L);

        assertEquals(5_000L, resumen.todayDistanceMeters);
        assertEquals(1_800L, resumen.todayDurationSeconds);
        assertEquals(350L, resumen.todayCalories);
        assertEquals(5_000L, resumen.weeklyDistanceMeters);
        assertEquals(5_000L, resumen.currentMonthDistanceMeters);
        assertEquals(5_000L, resumen.totalDistanceMeters);
        assertEquals(1_800L, resumen.totalDurationSeconds);
        assertEquals(350L, resumen.totalCalories);
        assertEquals(1, resumen.streakDays);
        assertEquals(1, resumen.totalActivities);
    }

    /**
     * Verifica que el array semanal usa índices ISO lunes-domingo y acumula varias actividades
     * del mismo día en la misma posición.
     */
    @Test
    public void calcular_weekDaysDistance_usesIsoMondayToSundayIndexes() {
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        LocalDate monday = today.minusDays(today.getDayOfWeek().getValue() - 1L);
        LocalDate sunday = monday.plusDays(6);

        List<ActividadItem> items = Arrays.asList(
                activity("mon-a", monday, 1_000, 100, 10),
                activity("mon-b", monday, 2_000, 200, 20),
                activity("sun", sunday, 7_000, 700, 70)
        );

        StatsResumen resumen = StatsCalculator.calcular(items, 1L, 1L);

        assertEquals(3_000L, resumen.weekDaysDistanceMeters[0]);
        assertEquals(7_000L, resumen.weekDaysDistanceMeters[6]);
        assertEquals(10_000L, resumen.weeklyDistanceMeters);
    }

    /**
     * Verifica que las distancias de ayer y anteayer se calculan solo para esas fechas relativas.
     */
    @Test
    public void calcular_recentActivity_populatesYesterdayAndTwoDaysAgo() {
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        List<ActividadItem> items = Arrays.asList(
                activity("yesterday", today.minusDays(1), 2_000, 300, 40),
                activity("two-days-ago", today.minusDays(2), 3_000, 400, 50),
                activity("old", today.minusDays(5), 9_000, 900, 90)
        );

        StatsResumen resumen = StatsCalculator.calcular(items, 1L, 1L);

        assertEquals(2_000L, resumen.yesterdayDistanceMeters);
        assertEquals(3_000L, resumen.twoDaysAgoDistanceMeters);
    }

    /**
     * Verifica que la semana anterior se agrega de forma independiente a la semana actual.
     */
    @Test
    public void calcular_previousWeek_populatesPreviousWeekComparison() {
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        LocalDate monday = today.minusDays(today.getDayOfWeek().getValue() - 1L);
        LocalDate previousWeekDay = monday.minusDays(3);

        List<ActividadItem> items = Arrays.asList(
                activity("current", monday, 1_500, 150, 15),
                activity("previous", previousWeekDay, 4_500, 450, 45)
        );

        StatsResumen resumen = StatsCalculator.calcular(items, 1L, 1L);

        assertEquals(1_500L, resumen.weeklyDistanceMeters);
        assertEquals(15L, resumen.weeklyCalories);
        assertEquals(4_500L, resumen.previousWeekDistanceMeters);
        assertEquals(45L, resumen.previousWeekCalories);
    }

    /**
     * Verifica que el mes actual y el mes anterior se separan correctamente aunque haya cambio de año.
     */
    @Test
    public void calcular_previousMonth_handlesYearBoundary() {
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        LocalDate previousMonth = today.withDayOfMonth(1).minusDays(1);

        List<ActividadItem> items = Arrays.asList(
                activity("current-month", today, 8_000, 800, 80),
                activity("previous-month", previousMonth, 6_000, 600, 60)
        );

        StatsResumen resumen = StatsCalculator.calcular(items, 1L, 1L);

        assertEquals(8_000L, resumen.currentMonthDistanceMeters);
        assertEquals(80L, resumen.currentMonthCalories);
        assertEquals(6_000L, resumen.previousMonthDistanceMeters);
        assertEquals(60L, resumen.previousMonthCalories);
    }

    /**
     * Verifica que las fechas ISO con offset se parsean y agregan sin lanzar excepciones.
     */
    @Test
    public void calcular_isoDateWithOffset_isParsed() {
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        ActividadItem item = activityWithIso("offset", today + "T12:30:00+00:00", 1_234, 321, 22);

        StatsResumen resumen = StatsCalculator.calcular(Collections.singletonList(item), 1L, 1L);

        assertEquals(1_234L, resumen.totalDistanceMeters);
        assertEquals(1, resumen.totalActivities);
    }

    /**
     * Verifica que los registros con fecha no parseable no aportan métricas,
     * pero la cuenta total de actividades conserva el tamaño recibido.
     */
    @Test
    public void calcular_invalidDate_isIgnoredForMetricsButActivityCountRemainsInputSize() {
        ActividadItem item = activityWithIso("bad", "fecha-no-valida", 9_999, 999, 99);

        StatsResumen resumen = StatsCalculator.calcular(Collections.singletonList(item), 1L, 1L);

        assertEquals(0L, resumen.totalDistanceMeters);
        assertEquals(0L, resumen.totalDurationSeconds);
        assertEquals(0L, resumen.totalCalories);
        assertEquals(1, resumen.totalActivities);
        assertTrue(resumen.monthBlocks.isEmpty());
    }

    /**
     * Verifica que el histórico mensual se ordena del mes más reciente al más antiguo
     * y conserva los totales mensuales.
     */
    @Test
    public void calcular_monthBlocks_areReverseChronologicalWithTotals() {
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        LocalDate previousMonth = today.withDayOfMonth(1).minusDays(1);

        List<ActividadItem> items = Arrays.asList(
                activity("current", today, 2_000, 200, 20),
                activity("previous", previousMonth, 3_000, 300, 30)
        );

        StatsResumen resumen = StatsCalculator.calcular(items, 1L, 1L);

        assertEquals(2, resumen.monthBlocks.size());
        assertEquals(today.getYear(), resumen.monthBlocks.get(0).year);
        assertEquals(today.getMonthValue(), resumen.monthBlocks.get(0).month);
        assertEquals(2_000L, resumen.monthBlocks.get(0).distanceMeters);
        assertEquals(previousMonth.getYear(), resumen.monthBlocks.get(1).year);
        assertEquals(previousMonth.getMonthValue(), resumen.monthBlocks.get(1).month);
        assertEquals(3_000L, resumen.monthBlocks.get(1).distanceMeters);
    }

    /**
     * Verifica que los bloques semanales de un mes contienen el rango visible dentro del mes
     * y los acumulados de la semana correspondiente.
     */
    @Test
    public void calcular_monthBlocks_includeWeekBlocksWithVisibleRanges() {
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        LocalDate firstDay = today.withDayOfMonth(1);

        StatsResumen resumen = StatsCalculator.calcular(
                Collections.singletonList(activity("first-day", firstDay, 4_000, 400, 40)),
                1L,
                1L
        );

        StatsResumen.MonthBlock month = resumen.monthBlocks.get(0);

        assertFalse(month.weeks.isEmpty());
        assertEquals(1, month.weeks.get(0).startDay);
        assertTrue(month.weeks.get(0).endDay >= 1);
        assertTrue(month.weeks.stream().anyMatch(w -> w.distanceMeters == 4_000L));
    }

    /**
     * Verifica que una racha continua hacia atrás desde hoy cuenta todos los días consecutivos.
     */
    @Test
    public void calcularStreak_countsConsecutiveDaysBackwardsFromToday() {
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        Set<LocalDate> days = new HashSet<>(Arrays.asList(
                today,
                today.minusDays(1),
                today.minusDays(2),
                today.minusDays(4)
        ));

        assertEquals(3, StatsCalculator.calcularStreak(days, today));
    }

    /**
     * Verifica que una racha siempre es cero cuando hoy no tiene actividad,
     * aunque los días anteriores sí la tengan.
     */
    @Test
    public void calcularStreak_returnsZeroWhenTodayMissing() {
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        Set<LocalDate> days = new HashSet<>(Arrays.asList(today.minusDays(1), today.minusDays(2)));

        assertEquals(0, StatsCalculator.calcularStreak(days, today));
    }

    /**
     * Crea una actividad mínima con fecha local en ISO compatible con {@link StatsCalculator}.
     */
    private static ActividadItem activity(String localId, LocalDate date, int distance, int duration, int calories) {
        return activityWithIso(localId, date + "T10:00:00+00:00", distance, duration, calories);
    }

    /**
     * Crea una actividad mínima permitiendo controlar la cadena ISO exacta usada por el cálculo.
     */
    private static ActividadItem activityWithIso(String localId, String iso, int distance, int duration, int calories) {
        return new ActividadItem(
                localId,
                null,
                "carrera",
                distance,
                duration,
                Math.max(0, duration - 60),
                60,
                0,
                calories,
                300,
                330,
                250,
                1_000,
                1_400,
                1,
                0,
                0,
                "polyline",
                null,
                iso,
                ActivitySyncState.SYNCED,
                null
        );
    }
}
