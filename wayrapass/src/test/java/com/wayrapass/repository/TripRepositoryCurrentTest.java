package com.wayrapass.repository;

import com.wayrapass.model.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@ActiveProfiles("test")
class TripRepositoryCurrentTest {
    @Autowired TripRepository trips;
    @Autowired EntityManager entityManager;

    private Route route;
    private Instant now;

    @BeforeEach
    void setUp() {
        Institution institution = new Institution();
        institution.setName("UPC");
        institution.setCampusName("San Isidro");
        entityManager.persist(institution);

        route = new Route();
        route.setInstitution(institution);
        route.setCode("R-CURRENT");
        route.setName("Ruta actual");
        route.setOrigin("San Borja");
        route.setDestination("UPC");
        entityManager.persist(route);

        now = Instant.parse("2026-10-31T12:00:00Z");
    }

    @Test
    void currentCandidatesContainOperationalAndFutureScheduledTripsOnly() {
        trip(TripStatus.IN_PROGRESS, now.minusSeconds(600));
        trip(TripStatus.BOARDING, now.minusSeconds(300));
        trip(TripStatus.SCHEDULED, now.plusSeconds(3600));
        trip(TripStatus.SCHEDULED, now.minusSeconds(3600));
        trip(TripStatus.COMPLETED, now.minusSeconds(7200));
        trip(TripStatus.CANCELLED, now.plusSeconds(7200));
        entityManager.flush();

        var result = trips.findCurrentCandidates(
                route.getId(),
                List.of(TripStatus.BOARDING, TripStatus.IN_PROGRESS),
                TripStatus.SCHEDULED,
                now
        );

        assertEquals(3, result.size());
        assertTrue(result.stream().anyMatch(t -> t.getStatus() == TripStatus.IN_PROGRESS));
        assertTrue(result.stream().anyMatch(t -> t.getStatus() == TripStatus.BOARDING));
        assertTrue(result.stream().anyMatch(t -> t.getStatus() == TripStatus.SCHEDULED
                && t.getScheduledStartAt().isAfter(now)));
    }

    @Test
    void calendarOperationsUseHalfOpenRangeAndChronologicalOrder() {
        trip(TripStatus.SCHEDULED, Instant.parse("2026-11-10T15:00:00Z"));
        trip(TripStatus.BOARDING, Instant.parse("2026-11-09T11:00:00Z"));
        trip(TripStatus.COMPLETED, Instant.parse("2026-11-16T05:00:00Z"));
        entityManager.flush();
        entityManager.clear();

        var result = trips.findCalendarOperations(
                Instant.parse("2026-11-09T05:00:00Z"),
                Instant.parse("2026-11-16T05:00:00Z"));

        assertEquals(2, result.size());
        assertEquals(TripStatus.BOARDING, result.get(0).getStatus());
        assertEquals(TripStatus.SCHEDULED, result.get(1).getStatus());
        assertEquals("R-CURRENT", result.get(0).getRoute().getCode());
    }

    private void trip(TripStatus status, Instant start) {
        Trip trip = new Trip();
        trip.setRoute(route);
        trip.setStatus(status);
        trip.setScheduledStartAt(start);
        trip.setScheduledEndAt(start.plusSeconds(3600));
        entityManager.persist(trip);
    }
}
