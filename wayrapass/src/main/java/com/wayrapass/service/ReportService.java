package com.wayrapass.service;

import com.wayrapass.dto.response.ReportResponses;
import com.wayrapass.exception.BusinessRuleException;
import com.wayrapass.model.BoardingStatus;
import com.wayrapass.model.ReportIndicator;
import com.wayrapass.model.TripStatus;
import com.wayrapass.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportService {
    static final ZoneId REPORT_ZONE = ZoneId.of("America/Lima");
    public static final String NO_DATA_MESSAGE = "No existen datos disponibles para el periodo seleccionado";

    private static final List<TripStatus> INCIDENT_OPERATION_STATUSES = List.of(
            TripStatus.BOARDING,
            TripStatus.IN_PROGRESS,
            TripStatus.COMPLETED,
            TripStatus.CANCELLED
    );
    private static final List<TripStatus> DEMAND_STATUSES = List.of(
            TripStatus.BOARDING,
            TripStatus.IN_PROGRESS,
            TripStatus.COMPLETED
    );

    private final ReportRepository reports;

    @Transactional(readOnly = true)
    public ReportResponses.OperationalReport generate(
            LocalDate from,
            LocalDate to,
            ReportIndicator requestedIndicator
    ) {
        validateRange(from, to);
        ReportIndicator indicator = requestedIndicator == null ? ReportIndicator.ALL : requestedIndicator;
        Instant fromInstant = from.atStartOfDay(REPORT_ZONE).toInstant();
        Instant toExclusive = to.plusDays(1).atStartOfDay(REPORT_ZONE).toInstant();
        Instant now = Instant.now();

        ReportResponses.Punctuality punctuality = includes(indicator, ReportIndicator.PUNCTUALITY)
                ? punctuality(fromInstant, toExclusive, now)
                : null;
        ReportResponses.Occupancy occupancy = includes(indicator, ReportIndicator.OCCUPANCY)
                ? occupancy(fromInstant, toExclusive, now)
                : null;
        ReportResponses.Incidents incidents = includes(indicator, ReportIndicator.INCIDENTS)
                ? incidents(fromInstant, toExclusive, now)
                : null;
        List<ReportResponses.StopDemand> stopDemand = includes(indicator, ReportIndicator.STOP_DEMAND)
                ? stopDemand(fromInstant, toExclusive, now)
                : null;

        boolean hasData = punctuality != null && punctuality.evaluatedTrips() > 0
                || occupancy != null && occupancy.evaluatedTrips() > 0
                || incidents != null && incidents.totalIncidents() > 0
                || stopDemand != null && !stopDemand.isEmpty();

        return new ReportResponses.OperationalReport(
                new ReportResponses.Period(from, to, REPORT_ZONE.getId()),
                indicator,
                now,
                hasData,
                hasData ? null : NO_DATA_MESSAGE,
                punctuality,
                occupancy,
                incidents,
                stopDemand
        );
    }

    private ReportResponses.Punctuality punctuality(Instant from, Instant toExclusive, Instant now) {
        ReportRepository.PunctualityProjection result = reports.punctuality(
                from, toExclusive, now, TripStatus.COMPLETED);
        long evaluated = result == null ? 0 : result.getEvaluatedTrips();
        long onTime = result == null ? 0 : result.getOnTimeTrips();
        return new ReportResponses.Punctuality(
                evaluated,
                onTime,
                evaluated - onTime,
                percentage(onTime, evaluated)
        );
    }

    private ReportResponses.Occupancy occupancy(Instant from, Instant toExclusive, Instant now) {
        List<ReportRepository.OccupancyProjection> rows = reports.occupancy(
                from, toExclusive, now, TripStatus.COMPLETED, BoardingStatus.CONFIRMED);
        long confirmedBoardings = rows.stream().mapToLong(ReportRepository.OccupancyProjection::getConfirmedBoardings).sum();
        long offeredCapacity = rows.stream().mapToLong(ReportRepository.OccupancyProjection::getVehicleCapacity).sum();
        return new ReportResponses.Occupancy(
                rows.size(),
                confirmedBoardings,
                offeredCapacity,
                percentage(confirmedBoardings, offeredCapacity)
        );
    }

    private ReportResponses.Incidents incidents(Instant from, Instant toExclusive, Instant now) {
        return new ReportResponses.Incidents(
                reports.incidentCount(from, toExclusive, now, INCIDENT_OPERATION_STATUSES)
        );
    }

    private List<ReportResponses.StopDemand> stopDemand(Instant from, Instant toExclusive, Instant now) {
        return reports.stopDemand(from, toExclusive, now, DEMAND_STATUSES, BoardingStatus.CONFIRMED).stream()
                .map(row -> new ReportResponses.StopDemand(
                        row.getStopId(),
                        row.getStopName(),
                        row.getConfirmedBoardings()
                ))
                .toList();
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new BusinessRuleException("Debe indicar las fechas inicial y final del reporte.");
        }
        if (from.isAfter(to)) {
            throw new BusinessRuleException("La fecha inicial no puede ser posterior a la fecha final.");
        }
    }

    private boolean includes(ReportIndicator selected, ReportIndicator section) {
        return selected == ReportIndicator.ALL || selected == section;
    }

    private BigDecimal percentage(long numerator, long denominator) {
        if (denominator == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }
}
