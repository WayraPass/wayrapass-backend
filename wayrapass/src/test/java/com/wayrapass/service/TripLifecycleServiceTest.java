package com.wayrapass.service;

import com.wayrapass.exception.BusinessRuleException;
import com.wayrapass.model.*;
import com.wayrapass.repository.NotificationRepository;
import com.wayrapass.repository.StudentFamilyLinkRepository;
import com.wayrapass.repository.StudentRouteEnrollmentRepository;
import com.wayrapass.repository.TripRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TripLifecycleServiceTest {
    private static final Instant START = Instant.parse("2026-11-03T13:00:00Z");
    private static final Instant END = Instant.parse("2026-11-03T14:00:00Z");

    @Mock TripRepository trips;
    @Mock StudentRouteEnrollmentRepository enrollments;
    @Mock StudentFamilyLinkRepository links;
    @Mock NotificationRepository notifications;

    private TripLifecycleService service;

    @BeforeEach
    void setUp() {
        service = new TripLifecycleService(trips, enrollments, links, notifications,
                Clock.fixed(START, ZoneOffset.UTC));
    }

    @Test
    void scheduledTripDoesNotStartBeforeScheduledTime() {
        Trip trip = trip(TripStatus.SCHEDULED);

        assertFalse(service.reconcile(trip, START.minusMillis(1)));
        assertEquals(TripStatus.SCHEDULED, trip.getStatus());
        assertNull(trip.getActualStartAt());
    }

    @Test
    void scheduledTripStartsAtScheduledTimeAndActivatesVehicle() {
        Trip trip = trip(TripStatus.SCHEDULED);

        assertTrue(service.reconcile(trip, START));

        assertEquals(TripStatus.IN_PROGRESS, trip.getStatus());
        assertEquals(START, trip.getActualStartAt());
        assertEquals(VehicleOperationalStatus.IN_SERVICE, trip.getVehicle().getOperationalStatus());
    }

    @Test
    void reconciliationIsIdempotentWhileTripRemainsInProgress() {
        Trip trip = trip(TripStatus.SCHEDULED);
        service.reconcile(trip, START);

        assertFalse(service.reconcile(trip, START.plusSeconds(900)));
        assertEquals(TripStatus.IN_PROGRESS, trip.getStatus());
        assertEquals(START, trip.getActualStartAt());
    }

    @Test
    void inProgressTripCompletesAtScheduledEndAndReleasesVehicle() {
        Trip trip = trip(TripStatus.IN_PROGRESS);
        trip.setActualStartAt(START);
        trip.getVehicle().setOperationalStatus(VehicleOperationalStatus.IN_SERVICE);

        assertTrue(service.reconcile(trip, END));

        assertEquals(TripStatus.COMPLETED, trip.getStatus());
        assertEquals(END, trip.getActualEndAt());
        assertEquals(VehicleOperationalStatus.AVAILABLE, trip.getVehicle().getOperationalStatus());
    }

    @Test
    void delayedRunCatchesUpWithoutUsingSchedulerDelayAsActualTimes() {
        Trip trip = trip(TripStatus.SCHEDULED);

        assertTrue(service.reconcile(trip, END.plusSeconds(600)));

        assertEquals(TripStatus.COMPLETED, trip.getStatus());
        assertEquals(START, trip.getActualStartAt());
        assertEquals(END, trip.getActualEndAt());
        assertEquals(VehicleOperationalStatus.AVAILABLE, trip.getVehicle().getOperationalStatus());
    }

    @Test
    void terminalTripsRemainUnchanged() {
        Trip completed = trip(TripStatus.COMPLETED);
        Trip cancelled = trip(TripStatus.CANCELLED);

        assertFalse(service.reconcile(completed, END.plusSeconds(1)));
        assertFalse(service.reconcile(cancelled, END.plusSeconds(1)));
        assertEquals(TripStatus.COMPLETED, completed.getStatus());
        assertEquals(TripStatus.CANCELLED, cancelled.getStatus());
    }

    @Test
    void invalidManualTransitionIsRejected() {
        Trip trip = trip(TripStatus.SCHEDULED);

        assertThrows(BusinessRuleException.class,
                () -> service.transition(trip, TripStatus.COMPLETED));
        assertEquals(TripStatus.SCHEDULED, trip.getStatus());
    }

    @Test
    void driverLicenseIsEvaluatedInLimaAtEffectiveTransitionTime() {
        Instant utcAfterMidnightButPreviousDayInLima = Instant.parse("2026-11-03T04:30:00Z");
        Trip trip = trip(TripStatus.SCHEDULED);
        trip.setScheduledStartAt(utcAfterMidnightButPreviousDayInLima);
        trip.setScheduledEndAt(utcAfterMidnightButPreviousDayInLima.plusSeconds(3600));
        trip.getDriver().setLicenseExpiration(LocalDate.of(2026, 11, 2));

        assertTrue(service.reconcile(trip, utcAfterMidnightButPreviousDayInLima));
        assertEquals(TripStatus.IN_PROGRESS, trip.getStatus());
    }

    @Test
    void reconcileTripUsesLockedDatabaseLookup() {
        Trip trip = trip(TripStatus.SCHEDULED);
        when(trips.findByIdForUpdate(40L)).thenReturn(Optional.of(trip));

        assertTrue(service.reconcileTrip(40L, START));

        verify(trips).findByIdForUpdate(40L);
    }

    private Trip trip(TripStatus status) {
        User driverUser = new User();
        driverUser.setId(20L);
        driverUser.setEnabled(true);
        Driver driver = new Driver();
        driver.setId(10L);
        driver.setUser(driverUser);
        driver.setAuthorizationStatus(AuthorizationStatus.AUTHORIZED);
        driver.setOperationalStatus(DriverOperationalStatus.AVAILABLE);
        driver.setLicenseExpiration(LocalDate.of(2027, 1, 1));

        Vehicle vehicle = new Vehicle();
        vehicle.setId(12L);
        vehicle.setAuthorizationStatus(AuthorizationStatus.AUTHORIZED);
        vehicle.setOperationalStatus(status == TripStatus.SCHEDULED
                ? VehicleOperationalStatus.AVAILABLE : VehicleOperationalStatus.IN_SERVICE);

        Route route = new Route();
        route.setId(5L);

        Trip trip = new Trip();
        trip.setId(40L);
        trip.setRoute(route);
        trip.setDriver(driver);
        trip.setVehicle(vehicle);
        trip.setScheduledStartAt(START);
        trip.setScheduledEndAt(END);
        trip.setStatus(status);
        return trip;
    }
}
