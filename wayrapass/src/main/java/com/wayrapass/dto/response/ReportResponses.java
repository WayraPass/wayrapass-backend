package com.wayrapass.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.wayrapass.model.ReportIndicator;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ReportResponses {
    private ReportResponses() {
    }

    public record Period(LocalDate from, LocalDate to, String zoneId) {
    }

    public record Punctuality(
            long evaluatedTrips,
            long onTimeTrips,
            long lateTrips,
            BigDecimal onTimePercentage
    ) {
    }

    public record Occupancy(
            long evaluatedTrips,
            long confirmedBoardings,
            long offeredCapacity,
            BigDecimal occupancyPercentage
    ) {
    }

    public record Incidents(long totalIncidents) {
    }

    public record StopDemand(
            Long stopId,
            String stopName,
            long confirmedBoardings
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record OperationalReport(
            Period period,
            ReportIndicator indicator,
            Instant generatedAt,
            boolean hasData,
            String message,
            Punctuality punctuality,
            Occupancy occupancy,
            Incidents incidents,
            List<StopDemand> stopDemand
    ) {
    }
}
