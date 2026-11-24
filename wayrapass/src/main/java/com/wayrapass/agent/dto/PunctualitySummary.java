package com.wayrapass.agent.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Deterministic, read-only analytics projection for coordinator WayrIA answers. */
public record PunctualitySummary(
        LocalDate from,
        LocalDate to,
        long evaluatedTrips,
        long onTimeTrips,
        long lateTrips,
        BigDecimal onTimePercentage,
        List<DelayedRoute> mostDelayedRoutes,
        List<AffectedSchedule> affectedSchedules
) {
    public record DelayedRoute(Long routeId, String routeCode, String routeName, long evaluatedTrips,
                               long lateTrips, BigDecimal latePercentage, long averageDelayMinutes) {}
    public record AffectedSchedule(String routeCode, String dayOfWeek, LocalTime departureTime, long lateTrips) {}
}
