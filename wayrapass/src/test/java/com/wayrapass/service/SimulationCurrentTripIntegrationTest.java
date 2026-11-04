package com.wayrapass.service;

import com.wayrapass.config.LocationTrackingProperties;
import com.wayrapass.config.SimulatorProperties;
import com.wayrapass.dto.response.TripQueryResponses;
import com.wayrapass.model.*;
import com.wayrapass.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SimulationCurrentTripIntegrationTest {
    @Mock StudentAccessService studentAccess;
    @Mock StudentRouteEnrollmentRepository enrollments;
    @Mock BoardingRepository boardings;
    @Mock TripRepository trips;
    @Mock IncidentRepository incidents;
    @Mock TripLocationRepository locations;
    @Mock RouteStopRepository routeStops;
    @Mock EnrollmentLifecycleService enrollmentLifecycle;
    @Mock ScheduledExecutorService executor;
    @Mock ScheduledFuture<Object> future;

    private MutableClock clock;
    private TripSimulationService simulation;
    private TripQueryService queries;
    private AtomicReference<TripLocation> latest;
    private Trip trip;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-11-01T15:00:00Z"));
        latest = new AtomicReference<>();
        Institution institution = new Institution();
        institution.setId(1L);
        institution.setName("Universidad Wayra");
        Route route = new Route();
        route.setId(5L);
        route.setInstitution(institution);
        route.setCode("R-001");
        route.setName("Ruta Centro");
        route.setOrigin("Origen");
        route.setDestination("Campus");
        Stop assignedStop = stop(8L, "Paradero A", "-12.000000", "-77.000000");
        Student student = new Student();
        student.setId(2L);
        StudentRouteEnrollment enrollment = new StudentRouteEnrollment();
        enrollment.setStudent(student);
        enrollment.setRoute(route);
        enrollment.setStop(assignedStop);
        enrollment.setStatus(EnrollmentStatus.ACTIVE);
        trip = new Trip();
        trip.setId(30L);
        trip.setRoute(route);
        trip.setStatus(TripStatus.IN_PROGRESS);
        trip.setScheduledStartAt(clock.instant().minusSeconds(300));
        trip.setScheduledEndAt(clock.instant().plusSeconds(3600));

        List<RouteStop> geometry = List.of(
                routeStop(route, assignedStop, 1),
                routeStop(route, stop(9L, "Paradero B", "-12.010000", "-77.010000"), 2)
        );
        when(trips.findById(30L)).thenReturn(Optional.of(trip));
        when(routeStops.findByRouteIdOrderByStopOrder(5L)).thenReturn(geometry);
        doReturn(future).when(executor).scheduleAtFixedRate(any(), anyLong(), anyLong(), any());
        when(locations.save(any(TripLocation.class))).thenAnswer(invocation -> {
            TripLocation location = invocation.getArgument(0);
            location.setId(100L);
            latest.set(location);
            return location;
        });
        when(locations.findFirstByTripIdOrderByRecordedAtDesc(30L))
                .thenAnswer(invocation -> Optional.ofNullable(latest.get()));
        when(studentAccess.requireCurrentStudent()).thenReturn(student);
        when(enrollmentLifecycle.currentForUse(2L)).thenReturn(Optional.of(enrollment));
        when(trips.findCurrentCandidates(eq(5L), anyCollection(), eq(TripStatus.SCHEDULED), any()))
                .thenReturn(List.of(trip));
        when(routeStops.findByRouteIdAndStopId(5L, 8L)).thenReturn(Optional.empty());

        SimulatorProperties simulatorProperties = new SimulatorProperties();
        simulatorProperties.setEnabled(true);
        simulatorProperties.setIntervalSeconds(5);
        simulatorProperties.setInterpolationStepsPerSegment(2);
        simulation = new TripSimulationService(
                trips, routeStops, new RouteTrajectoryBuilder(),
                new TripLocationWriter(trips, locations), simulatorProperties, executor, clock);

        LocationTrackingProperties tracking = new LocationTrackingProperties();
        tracking.setStaleAfterSeconds(120);
        queries = new TripQueryService(studentAccess, enrollments, boardings, trips, incidents,
                locations, routeStops, tracking, enrollmentLifecycle, clock);
    }

    @Test
    void simulatedLocationFlowsIntoCurrentTripAsRecent() {
        simulation.start(30L);

        TripQueryResponses.LocationStatus location = queries.myCurrentTrip().trip().location();

        assertTrue(location.available());
        assertFalse(location.stale());
        assertEquals(TripQueryResponses.LocationFreshness.RECENT, location.freshness());
        assertEquals(new BigDecimal("-12.000000"), location.latitude());
        assertEquals(clock.instant(), location.recordedAt());
    }

    @Test
    void stoppedSimulationNaturallyBecomesStaleAfterConfiguredThreshold() {
        simulation.start(30L);
        simulation.stop(30L);
        clock.advance(Duration.ofSeconds(121));

        TripQueryResponses.LocationStatus location = queries.myCurrentTrip().trip().location();

        assertTrue(location.available());
        assertTrue(location.stale());
        assertEquals(TripQueryResponses.LocationFreshness.STALE, location.freshness());
    }

    private Stop stop(Long id, String name, String latitude, String longitude) {
        Stop stop = new Stop();
        stop.setId(id);
        stop.setName(name);
        stop.setActive(true);
        stop.setLatitude(new BigDecimal(latitude));
        stop.setLongitude(new BigDecimal(longitude));
        return stop;
    }

    private RouteStop routeStop(Route route, Stop stop, int order) {
        RouteStop routeStop = new RouteStop();
        routeStop.setRoute(route);
        routeStop.setStop(stop);
        routeStop.setStopOrder(order);
        routeStop.setActive(true);
        return routeStop;
    }

    private static final class MutableClock extends Clock {
        private Instant value;

        private MutableClock(Instant value) {
            this.value = value;
        }

        private void advance(Duration duration) {
            value = value.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return value;
        }
    }
}
