package com.wayrapass.service;

import com.wayrapass.config.SimulatorProperties;
import com.wayrapass.exception.BusinessRuleException;
import com.wayrapass.exception.ConflictException;
import com.wayrapass.exception.ResourceNotFoundException;
import com.wayrapass.model.*;
import com.wayrapass.repository.RouteStopRepository;
import com.wayrapass.repository.TripRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TripSimulationServiceTest {
    @Mock TripRepository trips;
    @Mock RouteStopRepository routeStops;
    @Mock RouteTrajectoryBuilder trajectories;
    @Mock TripLocationWriter writer;
    @Mock ScheduledExecutorService executor;
    @Mock ScheduledFuture<Object> future;

    private TripSimulationService service;
    private SimulatorProperties properties;
    private Trip trip;
    private List<RouteTrajectoryBuilder.GeoPoint> points;
    private Instant now;

    @BeforeEach
    void setUp() {
        properties = new SimulatorProperties();
        properties.setEnabled(true);
        properties.setIntervalSeconds(7);
        properties.setInterpolationStepsPerSegment(3);
        now = Instant.parse("2026-11-01T15:00:00Z");
        service = new TripSimulationService(trips, routeStops, trajectories, writer, properties, executor,
                Clock.fixed(now, ZoneOffset.UTC));
        Route route = new Route();
        route.setId(5L);
        trip = new Trip();
        trip.setId(30L);
        trip.setRoute(route);
        trip.setStatus(TripStatus.IN_PROGRESS);
        points = List.of(point("-12.000000"), point("-12.001000"), point("-12.002000"));
    }

    @Test
    void validStartStoresFirstPointAndSchedulesFollowingUpdates() {
        prepareStart(30L, trip, points);

        var result = service.start(30L);

        assertEquals(SimulationStatus.RUNNING, result.status());
        assertEquals(1, result.currentPoint());
        assertEquals(3, result.totalPoints());
        assertEquals(now, result.startedAt());
        assertEquals(now, result.lastUpdatedAt());
        verify(writer).record(30L, points.getFirst(), now);
        verify(executor).scheduleAtFixedRate(any(Runnable.class), eq(7L), eq(7L), eq(TimeUnit.SECONDS));
    }

    @Test
    void missingTripCannotStart() {
        when(trips.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.start(99L));
        verifyNoInteractions(trajectories, writer, executor);
    }

    @Test
    void scheduledCompletedAndCancelledTripsCannotStart() {
        for (TripStatus status : List.of(TripStatus.SCHEDULED, TripStatus.COMPLETED, TripStatus.CANCELLED)) {
            trip.setStatus(status);
            when(trips.findById(30L)).thenReturn(Optional.of(trip));
            assertThrows(BusinessRuleException.class, () -> service.start(30L));
        }
        verifyNoInteractions(trajectories, writer, executor);
    }

    @Test
    void insufficientRouteCoordinatesPropagateControlledConflict() {
        when(trips.findById(30L)).thenReturn(Optional.of(trip));
        when(routeStops.findByRouteIdOrderByStopOrder(5L)).thenReturn(List.of());
        when(trajectories.build(anyList(), eq(3)))
                .thenThrow(new ConflictException(RouteTrajectoryBuilder.INSUFFICIENT_POINTS_MESSAGE));

        assertThrows(ConflictException.class, () -> service.start(30L));
        verifyNoInteractions(writer, executor);
    }

    @Test
    void secondStartForRunningTripIsRejected() {
        prepareStart(30L, trip, points);
        service.start(30L);

        assertThrows(ConflictException.class, () -> service.start(30L));
        verify(writer, times(1)).record(anyLong(), any(), any());
    }

    @Test
    void stopCancelsFutureAndIsIdempotentWithoutChangingTripStatus() {
        prepareStart(30L, trip, points);
        service.start(30L);

        var stopped = service.stop(30L);
        var stoppedAgain = service.stop(30L);

        assertEquals(SimulationStatus.STOPPED, stopped.status());
        assertEquals(SimulationStatus.STOPPED, stoppedAgain.status());
        assertEquals(TripStatus.IN_PROGRESS, trip.getStatus());
        verify(future, times(1)).cancel(false);
    }

    @Test
    void naturalEndStoresEveryPointAndMarksSessionFinished() {
        prepareStart(30L, trip, points);
        service.start(30L);

        service.advance(30L);
        service.advance(30L);

        var result = service.status(30L);
        assertEquals(SimulationStatus.FINISHED, result.status());
        assertEquals(3, result.currentPoint());
        verify(writer).record(30L, points.get(0), now);
        verify(writer).record(30L, points.get(1), now);
        verify(writer).record(30L, points.get(2), now);
        verify(future).cancel(false);
        assertEquals(TripStatus.IN_PROGRESS, trip.getStatus());
    }

    @Test
    void twoDifferentTripsRunIndependently() {
        Trip secondTrip = new Trip();
        secondTrip.setId(31L);
        secondTrip.setRoute(trip.getRoute());
        secondTrip.setStatus(TripStatus.BOARDING);
        prepareStart(30L, trip, points);
        prepareStart(31L, secondTrip, points);

        assertEquals(SimulationStatus.RUNNING, service.start(30L).status());
        assertEquals(SimulationStatus.RUNNING, service.start(31L).status());
        assertEquals(SimulationStatus.RUNNING, service.status(30L).status());
        assertEquals(SimulationStatus.RUNNING, service.status(31L).status());
        verify(writer).record(eq(30L), any(), eq(now));
        verify(writer).record(eq(31L), any(), eq(now));
    }

    @Test
    void tripWithoutSessionReportsIdleAndStopIsSafe() {
        when(trips.findById(30L)).thenReturn(Optional.of(trip));
        assertEquals(SimulationStatus.IDLE, service.status(30L).status());
        assertEquals(SimulationStatus.IDLE, service.stop(30L).status());
    }

    @Test
    void applicationShutdownCancelsActiveSessionsAndClearsMemory() {
        prepareStart(30L, trip, points);
        service.start(30L);

        service.stopSessions();

        assertEquals(SimulationStatus.IDLE, service.status(30L).status());
        verify(future).cancel(false);
    }

    private void prepareStart(Long tripId, Trip selectedTrip, List<RouteTrajectoryBuilder.GeoPoint> selectedPoints) {
        when(trips.findById(tripId)).thenReturn(Optional.of(selectedTrip));
        lenient().when(routeStops.findByRouteIdOrderByStopOrder(5L)).thenReturn(List.of());
        lenient().when(trajectories.build(anyList(), eq(3))).thenReturn(selectedPoints);
        lenient().doReturn(future).when(executor)
                .scheduleAtFixedRate(any(Runnable.class), eq(7L), eq(7L), eq(TimeUnit.SECONDS));
    }

    private RouteTrajectoryBuilder.GeoPoint point(String latitude) {
        return new RouteTrajectoryBuilder.GeoPoint(new BigDecimal(latitude), new BigDecimal("-77.000000"));
    }
}
