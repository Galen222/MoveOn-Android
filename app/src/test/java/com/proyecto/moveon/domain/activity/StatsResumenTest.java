package com.proyecto.moveon.domain.activity;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

/**
 * Tests de contenedor para {@link StatsResumen} y sus bloques de historial.
 */
public class StatsResumenTest {

    /**
     * Verifica que la factoría vacía inicializa todos los acumulados a cero y crea un array semanal de 7 días.
     */
    @Test
    public void empty_initializesZeroValuesAndGoals() {
        StatsResumen resumen = StatsResumen.empty(50_000L, 150_000L);

        assertEquals(0L, resumen.todayDistanceMeters);
        assertEquals(0L, resumen.weeklyDistanceMeters);
        assertEquals(50_000L, resumen.weeklyGoalMeters);
        assertEquals(150_000L, resumen.monthlyGoalMeters);
        assertEquals(7, resumen.weekDaysDistanceMeters.length);
        assertTrue(resumen.monthBlocks.isEmpty());
    }

    /**
     * Verifica que el constructor conserva todos los valores recibidos sin recalcularlos.
     */
    @Test
    public void constructor_preservesAllValues() {
        StatsResumen.WeekBlock week = new StatsResumen.WeekBlock(1, 7, 1_000L, 100L, 600L);
        StatsResumen.MonthBlock month = new StatsResumen.MonthBlock(2026, 4, 1_000L, 100L, 600L,
                Collections.singletonList(week));
        long[] days = new long[]{1, 2, 3, 4, 5, 6, 7};

        StatsResumen resumen = new StatsResumen(
                10L, 20L, 30L,
                days,
                40L, 50L,
                60L, 70L,
                80L, 90L,
                100L, 110L, 120L,
                130L, 140L, 150L,
                160L, 170L, 180L,
                3, 4,
                Collections.singletonList(month)
        );

        assertEquals(10L, resumen.todayDistanceMeters);
        assertArrayEquals(days, resumen.weekDaysDistanceMeters);
        assertEquals(130L, resumen.weeklyCalories);
        assertEquals(3, resumen.streakDays);
        assertEquals(4, resumen.totalActivities);
        assertSame(month, resumen.monthBlocks.get(0));
    }

    /**
     * Verifica que {@link StatsResumen.MonthBlock} expone los totales y su lista de semanas.
     */
    @Test
    public void monthBlock_preservesCalendarAndTotals() {
        StatsResumen.WeekBlock first = new StatsResumen.WeekBlock(1, 7, 700L, 70L, 7000L);
        StatsResumen.WeekBlock second = new StatsResumen.WeekBlock(8, 14, 800L, 80L, 8000L);

        StatsResumen.MonthBlock month = new StatsResumen.MonthBlock(
                2026,
                4,
                1_500L,
                150L,
                15_000L,
                Arrays.asList(first, second)
        );

        assertEquals(2026, month.year);
        assertEquals(4, month.month);
        assertEquals(1_500L, month.distanceMeters);
        assertEquals(2, month.weeks.size());
    }

    /**
     * Verifica que {@link StatsResumen.WeekBlock} conserva el rango visible y los acumulados.
     */
    @Test
    public void weekBlock_preservesVisibleRangeAndTotals() {
        StatsResumen.WeekBlock block = new StatsResumen.WeekBlock(29, 30, 2_000L, 200L, 1_200L);

        assertEquals(29, block.startDay);
        assertEquals(30, block.endDay);
        assertEquals(2_000L, block.distanceMeters);
        assertEquals(200L, block.calories);
        assertEquals(1_200L, block.durationSeconds);
    }
}
