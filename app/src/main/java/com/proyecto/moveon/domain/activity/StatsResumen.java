package com.proyecto.moveon.domain.activity;

public final class StatsResumen {

    public final int totalActivities;
    public final long totalDistanceMeters;
    public final long totalDurationSeconds;
    public final int streakDays;

    public final long todayDistanceMeters;
    public final long yesterdayDistanceMeters;
    public final long twoDaysAgoDistanceMeters;

    public final long currentMonthDistanceMeters;
    public final long previousMonthDistanceMeters;

    public final long weeklyDistanceMeters;
    public final long weeklyGoalMeters;

    public StatsResumen(int totalActivities,
                        long totalDistanceMeters,
                        long totalDurationSeconds,
                        int streakDays,
                        long todayDistanceMeters,
                        long yesterdayDistanceMeters,
                        long twoDaysAgoDistanceMeters,
                        long currentMonthDistanceMeters,
                        long previousMonthDistanceMeters,
                        long weeklyDistanceMeters,
                        long weeklyGoalMeters) {
        this.totalActivities = totalActivities;
        this.totalDistanceMeters = totalDistanceMeters;
        this.totalDurationSeconds = totalDurationSeconds;
        this.streakDays = streakDays;
        this.todayDistanceMeters = todayDistanceMeters;
        this.yesterdayDistanceMeters = yesterdayDistanceMeters;
        this.twoDaysAgoDistanceMeters = twoDaysAgoDistanceMeters;
        this.currentMonthDistanceMeters = currentMonthDistanceMeters;
        this.previousMonthDistanceMeters = previousMonthDistanceMeters;
        this.weeklyDistanceMeters = weeklyDistanceMeters;
        this.weeklyGoalMeters = weeklyGoalMeters;
    }

    public static StatsResumen empty(long weeklyGoalMeters) {
        return new StatsResumen(
                0,
                0L,
                0L,
                0,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                weeklyGoalMeters
        );
    }
}
