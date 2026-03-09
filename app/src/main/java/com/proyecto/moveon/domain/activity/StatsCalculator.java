package com.proyecto.moveon.domain.activity;

import androidx.annotation.NonNull;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.WeekFields;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Clase de utilidad pura para calcular agregados estadísticos a partir
 * de una lista de {@link ActividadItem}.
 *
 * <p>Todos los métodos son estáticos y no tienen efectos secundarios.
 * No depende de ningún framework Android — es 100% testeable con JUnit.
 *
 * <p>Los cálculos usan la zona horaria local del dispositivo para determinar
 * qué actividades pertenecen a "hoy", "esta semana", etc.
 */
public final class StatsCalculator {

    /** Objetivo semanal por defecto: 25 km en metros. */
    public static final long DEFAULT_WEEKLY_GOAL_METERS = 25_000L;

    private StatsCalculator() {
        // Clase utilitaria — no instanciar
    }

    /**
     * Calcula el resumen completo de estadísticas a partir de la lista de actividades.
     *
     * @param items lista de actividades visibles (nunca null, puede estar vacía)
     * @return {@link StatsResumen} con todos los agregados calculados
     */
    @NonNull
    public static StatsResumen calcular(@NonNull List<ActividadItem> items) {
        if (items.isEmpty()) {
            return StatsResumen.empty(DEFAULT_WEEKLY_GOAL_METERS);
        }

        final LocalDate hoy = LocalDate.now(ZoneId.systemDefault());
        final LocalDate ayer = hoy.minusDays(1);
        final LocalDate haceDos = hoy.minusDays(2);

        // Semana actual — lunes a domingo según locale ES
        final WeekFields semanaFields = WeekFields.of(Locale.forLanguageTag("es-ES"));
        final int semanaActual = hoy.get(semanaFields.weekOfWeekBasedYear());
        final int anioActual = hoy.getYear();

        // Mes actual y anterior
        final int mesActual = hoy.getMonthValue();
        final int anioMesActual = hoy.getYear();
        final LocalDate primerDiaMesAnterior = hoy.withDayOfMonth(1).minusMonths(1);
        final int mesAnterior = primerDiaMesAnterior.getMonthValue();
        final int anioMesAnterior = primerDiaMesAnterior.getYear();

        long totalDistancia = 0L;
        long totalDuracion = 0L;
        long distanciaHoy = 0L;
        long distanciaAyer = 0L;
        long distanciaHaceDos = 0L;
        long distanciaMesActual = 0L;
        long distanciaMesAnterior = 0L;
        long distanciaSemana = 0L;

        // Para el streak: conjunto de fechas con actividad
        Set<LocalDate> diasConActividad = new HashSet<>();

        for (ActividadItem item : items) {
            LocalDate fechaActividad = parseFecha(item.fechaRutaIso);
            if (fechaActividad == null) continue;

            long metros = item.distanciaMetros;
            long segundos = item.duracionSegundos;

            totalDistancia += metros;
            totalDuracion += segundos;
            diasConActividad.add(fechaActividad);

            if (fechaActividad.equals(hoy)) {
                distanciaHoy += metros;
            } else if (fechaActividad.equals(ayer)) {
                distanciaAyer += metros;
            } else if (fechaActividad.equals(haceDos)) {
                distanciaHaceDos += metros;
            }

            // Semana actual
            int semanaItem = fechaActividad.get(semanaFields.weekOfWeekBasedYear());
            int anioItem = fechaActividad.getYear();
            if (semanaItem == semanaActual && anioItem == anioActual) {
                distanciaSemana += metros;
            }

            // Mes actual
            if (fechaActividad.getMonthValue() == mesActual
                    && fechaActividad.getYear() == anioMesActual) {
                distanciaMesActual += metros;
            }

            // Mes anterior
            if (fechaActividad.getMonthValue() == mesAnterior
                    && fechaActividad.getYear() == anioMesAnterior) {
                distanciaMesAnterior += metros;
            }
        }

        int streak = calcularStreak(diasConActividad, hoy);

        return new StatsResumen(
                items.size(),
                totalDistancia,
                totalDuracion,
                streak,
                distanciaHoy,
                distanciaAyer,
                distanciaHaceDos,
                distanciaMesActual,
                distanciaMesAnterior,
                distanciaSemana,
                DEFAULT_WEEKLY_GOAL_METERS
        );
    }

    /**
     * Calcula la distancia en metros para cada día de la semana actual (Lun–Dom).
     * El array devuelto tiene 7 posiciones: índice 0 = lunes, índice 6 = domingo.
     *
     * @param items lista de actividades visibles
     * @return array de 7 longs con la distancia en metros por día
     */
    @NonNull
    public static long[] calcularDistanciaPorDiaSemana(@NonNull List<ActividadItem> items) {
        long[] porDia = new long[7];
        if (items.isEmpty()) return porDia;

        final LocalDate hoy = LocalDate.now(ZoneId.systemDefault());
        // DayOfWeek: MONDAY=1, SUNDAY=7 → convertimos a índice 0-6
        final LocalDate lunesActual = hoy.minusDays(hoy.getDayOfWeek().getValue() - 1L);

        for (ActividadItem item : items) {
            LocalDate fecha = parseFecha(item.fechaRutaIso);
            if (fecha == null) continue;

            long diasDesde = ChronoUnit.DAYS.between(lunesActual, fecha);
            if (diasDesde >= 0 && diasDesde < 7) {
                porDia[(int) diasDesde] += item.distanciaMetros;
            }
        }

        return porDia;
    }

    /**
     * Calcula la racha de días consecutivos con al menos una actividad,
     * contando hacia atrás desde hoy.
     *
     * <p>Si hoy no tiene actividad, pero ayer sí, la racha cuenta desde ayer
     * (el usuario aún puede completar hoy).
     *
     * @param diasConActividad conjunto de fechas con actividad registrada
     * @param hoy              fecha actual del dispositivo
     * @return número de días consecutivos de racha (mínimo 0)
     */
    public static int calcularStreak(@NonNull Set<LocalDate> diasConActividad,
                                     @NonNull LocalDate hoy) {
        if (diasConActividad.isEmpty()) return 0;

        // Si hoy tiene actividad, empezamos desde hoy; si no, desde ayer
        LocalDate inicio = diasConActividad.contains(hoy) ? hoy : hoy.minusDays(1);

        if (!diasConActividad.contains(inicio)) return 0;

        int streak = 0;
        LocalDate cursor = inicio;
        while (diasConActividad.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    /**
     * Parsea una fecha ISO-8601 con zona horaria a {@link LocalDate} en la zona local.
     * Devuelve null si el formato es inválido — nunca lanza excepción.
     */
    @NonNull
    static LocalDate[] calcularRangoSemanaActual() {
        LocalDate hoy = LocalDate.now(ZoneId.systemDefault());
        LocalDate lunes = hoy.minusDays(hoy.getDayOfWeek().getValue() - 1L);
        LocalDate domingo = lunes.plusDays(6);
        return new LocalDate[]{lunes, domingo};
    }

    // ── Privados ──────────────────────────────────────────────────────────────

    @androidx.annotation.Nullable
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