package com.wayrapass.service;

import com.wayrapass.agent.dto.PunctualitySummary;
import com.wayrapass.exception.BusinessRuleException;
import com.wayrapass.model.Trip;
import com.wayrapass.model.TripStatus;
import com.wayrapass.repository.ReportRepository;
import com.wayrapass.dto.response.ReportResponses;
import com.wayrapass.model.ReportIndicator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Uses the exact US23 punctuality population and rule, adding only deterministic rankings. */
@Service
@RequiredArgsConstructor
public class PunctualityAnalyticsService {
    public static final ZoneId SERVICE_ZONE = ZoneId.of("America/Lima");
    private final ReportRepository reports;
    private final ReportService reportService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PunctualitySummary summarize(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)) {
            throw new BusinessRuleException("El periodo de puntualidad no es válido.");
        }
        Instant fromInstant = from.atStartOfDay(SERVICE_ZONE).toInstant();
        Instant toExclusive = to.plusDays(1).atStartOfDay(SERVICE_ZONE).toInstant();
        List<Trip> trips = reports.punctualityTrips(fromInstant, toExclusive, clock.instant(), TripStatus.COMPLETED);
        ReportResponses.Punctuality punctuality = reportService.generate(from, to, ReportIndicator.PUNCTUALITY).punctuality();
        return new PunctualitySummary(from, to, punctuality.evaluatedTrips(), punctuality.onTimeTrips(),
                punctuality.lateTrips(), punctuality.onTimePercentage(),
                delayedRoutes(trips), affectedSchedules(trips));
    }

    public LocalDate currentWeekStart() {
        LocalDate today = today();
        return today.minusDays(today.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue());
    }

    public LocalDate today() {
        return LocalDate.now(clock.withZone(SERVICE_ZONE));
    }

    private boolean onTime(Trip trip) {
        return !trip.getActualEndAt().isAfter(trip.getScheduledEndAt());
    }

    private List<PunctualitySummary.DelayedRoute> delayedRoutes(List<Trip> trips) {
        Map<Long, List<Trip>> byRoute = trips.stream().collect(java.util.stream.Collectors.groupingBy(
                trip -> trip.getRoute().getId(), LinkedHashMap::new, java.util.stream.Collectors.toList()));
        return byRoute.values().stream().map(items -> {
            Trip first = items.getFirst();
            List<Trip> delayed = items.stream().filter(trip -> !onTime(trip)).toList();
            long averageDelay = delayed.isEmpty() ? 0 : Math.round(delayed.stream()
                    .mapToLong(this::delayMinutes).average().orElse(0));
            return new PunctualitySummary.DelayedRoute(first.getRoute().getId(), first.getRoute().getCode(),
                    first.getRoute().getName(), items.size(), delayed.size(), percentage(delayed.size(), items.size()), averageDelay);
        }).sorted(Comparator.comparingLong(PunctualitySummary.DelayedRoute::lateTrips).reversed()
                        .thenComparing(PunctualitySummary.DelayedRoute::latePercentage, Comparator.reverseOrder())
                        .thenComparing(PunctualitySummary.DelayedRoute::routeCode))
                .toList();
    }

    private List<PunctualitySummary.AffectedSchedule> affectedSchedules(List<Trip> trips) {
        Map<String, Long> counts = trips.stream().filter(trip -> !onTime(trip))
                .collect(java.util.stream.Collectors.groupingBy(this::scheduleKey, LinkedHashMap::new,
                        java.util.stream.Collectors.counting()));
        return counts.entrySet().stream().map(entry -> {
            String[] parts = entry.getKey().split("\\|", -1);
            return new PunctualitySummary.AffectedSchedule(parts[0], parts[1], LocalTime.parse(parts[2]), entry.getValue());
        }).sorted(Comparator.comparingLong(PunctualitySummary.AffectedSchedule::lateTrips).reversed()
                .thenComparing(PunctualitySummary.AffectedSchedule::routeCode)
                .thenComparing(PunctualitySummary.AffectedSchedule::dayOfWeek)
                .thenComparing(PunctualitySummary.AffectedSchedule::departureTime)).toList();
    }

    private String scheduleKey(Trip trip) {
        if (trip.getRouteSchedule() != null) {
            return trip.getRoute().getCode() + "|" + trip.getRouteSchedule().getDayOfWeek() + "|" + trip.getRouteSchedule().getDepartureTime();
        }
        var local = trip.getScheduledStartAt().atZone(SERVICE_ZONE);
        return trip.getRoute().getCode() + "|" + local.getDayOfWeek() + "|" + local.toLocalTime().withSecond(0).withNano(0);
    }

    private long delayMinutes(Trip trip) {
        return Math.max(0, Duration.between(trip.getScheduledEndAt(), trip.getActualEndAt()).toMinutes());
    }

    private BigDecimal percentage(long numerator, long denominator) {
        return denominator == 0 ? BigDecimal.ZERO.setScale(2) : BigDecimal.valueOf(numerator * 100)
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }
}
