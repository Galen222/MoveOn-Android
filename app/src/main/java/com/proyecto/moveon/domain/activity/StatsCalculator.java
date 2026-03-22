package com.proyecto.moveon.domain.activity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Clase de utilidad pura para calcular agregados estadísticos a partir
 * de una lista de {@link ActividadItem}.
 * Todos los métodos son estáticos y no tienen efectos secundarios.
 * No depende de ningún framework Android — es 100% testeable con JUnit.
 * Las semanas son siempre lunes–domingo (ISO 8601).
 * La racha cuenta días consecutivos hacia atrás desde hoy inclusive.
 * Si hoy no tiene actividad, la racha es 0.
 */
public final class StatsCalculator {

    public static final long DEFAULT_WEEKLY_GOAL_METERS  = 50_000L;
    public static final long DEFAULT_MONTHLY_GOAL_METERS = 150_000L;

    private StatsCalculator() {
        // Clase utilitaria — no instanciar
    }

    /**
     * Calcula el resumen completo de estadísticas a partir de la lista de actividades
     * y los objetivos del usuario.
     */
    @NonNull
    public static StatsResumen calcular(@NonNull List<ActividadItem> items,
                                        long weeklyGoalMeters,
                                        long monthlyGoalMeters) {
        if (items.isEmpty()) {
            return StatsResumen.empty(weeklyGoalMeters, monthlyGoalMeters);
        }

        final LocalDate hoy     = LocalDate.now(ZoneId.systemDefault());
        final LocalDate ayer    = hoy.minusDays(1);
        final LocalDate haceDos = hoy.minusDays(2);

        // Semana actual (ISO: lunes–domingo)
        final LocalDate lunesActual     = hoy.minusDays(hoy.getDayOfWeek().getValue() - 1L);
        final LocalDate domingoActual   = lunesActual.plusDays(6);

        // Semana anterior
        final LocalDate lunesAnterior   = lunesActual.minusDays(7);
        final LocalDate domingoAnterior = lunesAnterior.plusDays(6);

        // Mes actual y anterior
        final int mesActual          = hoy.getMonthValue();
        final int anioMesActual      = hoy.getYear();
        final LocalDate primerMesAnt = hoy.withDayOfMonth(1).minusMonths(1);
        final int mesAnterior        = primerMesAnt.getMonthValue();
        final int anioMesAnterior    = primerMesAnt.getYear();

        // Acumuladores
        long totalDistancia       = 0L;
        long totalDuracion        = 0L;
        long totalCalorias        = 0L;

        long distanciaHoy         = 0L;
        long duracionHoy          = 0L;
        long caloriasHoy          = 0L;

        long distanciaAyer        = 0L;
        long distanciaHaceDos     = 0L;

        long distanciaSemana      = 0L;
        long caloriasSemana       = 0L;
        long distanciaSemanaAnt   = 0L;
        long caloriasSemanaAnt    = 0L;

        long distanciaMesActual   = 0L;
        long caloriasMesActual    = 0L;
        long distanciaMesAnterior = 0L;
        long caloriasMesAnterior  = 0L;

        long[] porDia             = new long[7];

        Set<LocalDate> diasConActividad = new HashSet<>();

        // Mapa para historial: clave = "YYYY-MM" → [distancia, calorias, duracion]
        Map<String, long[]> porMes = new TreeMap<>(Collections.reverseOrder());

        for (ActividadItem item : items) {
            LocalDate fecha = parseFecha(item.fechaRutaIso);
            if (fecha == null) continue;

            long metros   = item.distanciaMetros;
            long segundos = item.duracionSegundos;
            long kcal     = item.caloriasQuemadas;

            totalDistancia += metros;
            totalDuracion  += segundos;
            totalCalorias  += kcal;
            diasConActividad.add(fecha);

            // Hoy
            if (fecha.equals(hoy)) {
                distanciaHoy += metros;
                duracionHoy  += segundos;
                caloriasHoy  += kcal;
            } else if (fecha.equals(ayer)) {
                distanciaAyer += metros;
            } else if (fecha.equals(haceDos)) {
                distanciaHaceDos += metros;
            }

            // Semana actual
            if (!fecha.isBefore(lunesActual) && !fecha.isAfter(domingoActual)) {
                distanciaSemana += metros;
                caloriasSemana  += kcal;

                int idx = (int) ChronoUnit.DAYS.between(lunesActual, fecha);
                if (idx >= 0 && idx < 7) {
                    porDia[idx] += metros;
                }
            }

            // Semana anterior
            if (!fecha.isBefore(lunesAnterior) && !fecha.isAfter(domingoAnterior)) {
                distanciaSemanaAnt += metros;
                caloriasSemanaAnt  += kcal;
            }

            // Mes actual
            if (fecha.getMonthValue() == mesActual && fecha.getYear() == anioMesActual) {
                distanciaMesActual += metros;
                caloriasMesActual  += kcal;
            }

            // Mes anterior
            if (fecha.getMonthValue() == mesAnterior && fecha.getYear() == anioMesAnterior) {
                distanciaMesAnterior += metros;
                caloriasMesAnterior  += kcal;
            }

            // Historial por mes (Card 9) — [distancia, calorias, duracion]
            String mesKey = fecha.getYear() + "-" + String.format(Locale.US, "%02d", fecha.getMonthValue());
            long[] totalesMes = porMes.computeIfAbsent(mesKey, k -> new long[3]);
            totalesMes[0] += metros;
            totalesMes[1] += kcal;
            totalesMes[2] += segundos;
        }

        int streak = calcularStreak(diasConActividad, hoy);
        List<StatsResumen.MonthBlock> monthBlocks = buildMonthBlocks(items, porMes);

        return new StatsResumen(
                distanciaHoy,
                duracionHoy,
                caloriasHoy,
                porDia,
                distanciaSemana,
                weeklyGoalMeters,
                distanciaMesActual,
                monthlyGoalMeters,
                distanciaAyer,
                distanciaHaceDos,
                distanciaMesAnterior,
                caloriasMesActual,
                caloriasMesAnterior,
                caloriasSemana,
                distanciaSemanaAnt,
                caloriasSemanaAnt,
                totalDistancia,
                totalDuracion,
                totalCalorias,
                streak,
                items.size(),
                monthBlocks
        );
    }

    /**
     * Calcula la racha de días consecutivos con al menos una actividad,
     * contando hacia atrás desde hoy inclusive.
     */
    public static int calcularStreak(@NonNull Set<LocalDate> diasConActividad,
                                     @NonNull LocalDate hoy) {
        if (diasConActividad.isEmpty() || !diasConActividad.contains(hoy)) return 0;

        int streak       = 0;
        LocalDate cursor = hoy;
        while (diasConActividad.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    // ── Privados ──────────────────────────────────────────────────────────────

    @NonNull
    private static List<StatsResumen.MonthBlock> buildMonthBlocks(
            @NonNull List<ActividadItem> items,
            @NonNull Map<String, long[]> porMes) {

        if (porMes.isEmpty()) return Collections.emptyList();

        // Mapa de actividades por fecha: [distancia, calorias, duracion]
        Map<LocalDate, long[]> porFecha = new HashMap<>();
        for (ActividadItem item : items) {
            LocalDate fecha = parseFecha(item.fechaRutaIso);
            if (fecha == null) continue;
            long[] totales = porFecha.computeIfAbsent(fecha, k -> new long[3]);
            totales[0] += item.distanciaMetros;
            totales[1] += item.caloriasQuemadas;
            totales[2] += item.duracionSegundos;
        }

        List<StatsResumen.MonthBlock> result = new ArrayList<>();

        for (Map.Entry<String, long[]> entry : porMes.entrySet()) {
            String[] parts    = entry.getKey().split("-");
            int year          = Integer.parseInt(parts[0]);
            int month         = Integer.parseInt(parts[1]);
            long[] totalesMes = entry.getValue();

            List<StatsResumen.WeekBlock> weekBlocks =
                    buildWeekBlocks(year, month, porFecha);

            result.add(new StatsResumen.MonthBlock(
                    year, month,
                    totalesMes[0], totalesMes[1], totalesMes[2],
                    weekBlocks));
        }

        return result;
    }

    @NonNull
    private static List<StatsResumen.WeekBlock> buildWeekBlocks(
            int year, int month,
            @NonNull Map<LocalDate, long[]> porFecha) {

        List<StatsResumen.WeekBlock> blocks = new ArrayList<>();

        LocalDate primerDia = LocalDate.of(year, month, 1);
        LocalDate ultimoDia = YearMonth.of(year, month).atEndOfMonth();

        LocalDate cursor = primerDia.minusDays(primerDia.getDayOfWeek().getValue() - 1L);

        while (!cursor.isAfter(ultimoDia)) {
            LocalDate lunes = cursor;
            LocalDate domingo = cursor.plusDays(6);

            LocalDate inicioVisible = lunes.isBefore(primerDia) ? primerDia : lunes;
            LocalDate finVisible = domingo.isAfter(ultimoDia) ? ultimoDia : domingo;

            long distSemana = 0L;
            long kcalSemana = 0L;
            long durSemana = 0L;

            for (LocalDate d = inicioVisible; !d.isAfter(finVisible); d = d.plusDays(1)) {
                long[] totales = porFecha.get(d);
                if (totales != null) {
                    distSemana += totales[0];
                    kcalSemana += totales[1];
                    durSemana += totales[2];
                }
            }

            int startDay = inicioVisible.getDayOfMonth();
            int endDay = finVisible.getDayOfMonth();

            blocks.add(new StatsResumen.WeekBlock(
                    startDay, endDay, distSemana, kcalSemana, durSemana));

            cursor = cursor.plusDays(7);
        }

        return blocks;
    }

    @Nullable
    private static LocalDate parseFecha(@NonNull String fechaIso) {
        try {
            return OffsetDateTime.parse(fechaIso)
                    .atZoneSameInstant(ZoneId.systemDefault())
                    .toLocalDate();
        } catch (Exception e) {
            return null;
        }
    }
}
