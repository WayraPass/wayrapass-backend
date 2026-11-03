package com.wayrapass.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

@Component
@ConditionalOnProperty(name = "app.trip-lifecycle.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class TripLifecycleScheduler {
    private final TripLifecycleService lifecycle;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${app.trip-lifecycle.interval-ms:30000}")
    public void reconcileDueTrips() {
        Instant now = clock.instant();
        for (Long tripId : lifecycle.dueTripIds(now)) {
            try {
                if (lifecycle.reconcileTrip(tripId, now)) {
                    log.info("Trip lifecycle reconciled for tripId={} at {}", tripId, now);
                }
            } catch (RuntimeException exception) {
                log.warn("Trip lifecycle could not be reconciled for tripId={}: {}",
                        tripId, exception.getMessage());
            }
        }
    }
}
