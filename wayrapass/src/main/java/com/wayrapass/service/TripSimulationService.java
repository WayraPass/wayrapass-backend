package com.wayrapass.service;

import com.wayrapass.config.SimulatorProperties;
import com.wayrapass.dto.response.SimulationResponses;
import com.wayrapass.exception.BusinessRuleException;
import com.wayrapass.exception.ConflictException;
import com.wayrapass.exception.ResourceNotFoundException;
import com.wayrapass.model.*;
import com.wayrapass.repository.RouteStopRepository;
import com.wayrapass.repository.TripRepository;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Service
@ConditionalOnProperty(prefix = "app.simulator", name = "enabled", havingValue = "true")
@Slf4j
public class TripSimulationService {
    private static final EnumSet<TripStatus> SIMULATABLE =
            EnumSet.of(TripStatus.BOARDING, TripStatus.IN_PROGRESS);

    private final TripRepository trips;
    private final RouteStopRepository routeStops;
    private final RouteTrajectoryBuilder trajectories;
    private final TripLocationWriter locationWriter;
    private final SimulatorProperties properties;
    private final ScheduledExecutorService executor;
    private final Clock clock;
    private final ConcurrentHashMap<Long, SimulationSession> sessions = new ConcurrentHashMap<>();

    public TripSimulationService(TripRepository trips,
                                 RouteStopRepository routeStops,
                                 RouteTrajectoryBuilder trajectories,
                                 TripLocationWriter locationWriter,
                                 SimulatorProperties properties,
                                 ScheduledExecutorService executor,
                                 Clock clock) {
        this.trips = trips;
        this.routeStops = routeStops;
        this.trajectories = trajectories;
        this.locationWriter = locationWriter;
        this.properties = properties;
        this.executor = executor;
        this.clock = clock;
    }

    public SimulationResponses.Status start(Long tripId) {
        Trip trip = requireTrip(tripId);
        requireSimulatable(trip);
        List<RouteTrajectoryBuilder.GeoPoint> points = trajectories.build(
                routeStops.findByRouteIdOrderByStopOrder(trip.getRoute().getId()),
                properties.getInterpolationStepsPerSegment()
        );
        Instant startedAt = clock.instant();
        SimulationSession candidate = new SimulationSession(tripId, points, startedAt);
        sessions.compute(tripId, (id, current) -> {
            if (current != null && current.isRunning()) {
                throw new ConflictException("Ya existe una simulación activa para este viaje.");
            }
            return candidate;
        });

        try {
            recordNext(candidate);
            if (candidate.isRunning()) {
                ScheduledFuture<?> future = executor.scheduleAtFixedRate(
                        () -> advanceSafely(candidate),
                        properties.getIntervalSeconds(),
                        properties.getIntervalSeconds(),
                        TimeUnit.SECONDS
                );
                candidate.attach(future);
            }
            log.info("Simulation started for tripId={} with {} points", tripId, points.size());
            return candidate.snapshot();
        } catch (RuntimeException exception) {
            sessions.remove(tripId, candidate);
            candidate.stop();
            throw exception;
        }
    }

    public SimulationResponses.Status stop(Long tripId) {
        requireTrip(tripId);
        SimulationSession session = sessions.get(tripId);
        if (session == null) {
            return idle(tripId);
        }
        boolean changed = session.stop();
        if (changed) {
            log.info("Simulation stopped for tripId={}", tripId);
        }
        return session.snapshot();
    }

    public SimulationResponses.Status status(Long tripId) {
        requireTrip(tripId);
        SimulationSession session = sessions.get(tripId);
        return session == null ? idle(tripId) : session.snapshot();
    }

    void advance(Long tripId) {
        SimulationSession session = sessions.get(tripId);
        if (session != null) {
            recordNext(session);
        }
    }

    private void advanceSafely(SimulationSession session) {
        try {
            recordNext(session);
        } catch (RuntimeException exception) {
            session.stop();
            log.warn("Simulation stopped after an update failure for tripId={}: {}",
                    session.tripId(), exception.getMessage());
        }
    }

    private void recordNext(SimulationSession session) {
        synchronized (session) {
            if (!session.isRunning() || !session.hasNext()) {
                return;
            }
            Instant recordedAt = clock.instant();
            locationWriter.record(session.tripId(), session.nextPoint(), recordedAt);
            boolean finished = session.recorded(recordedAt);
            if (finished) {
                log.info("Simulation finished for tripId={}", session.tripId());
            }
        }
    }

    private Trip requireTrip(Long tripId) {
        return trips.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Viaje no encontrado."));
    }

    private void requireSimulatable(Trip trip) {
        if (!SIMULATABLE.contains(trip.getStatus())) {
            throw new BusinessRuleException("Solo se pueden simular viajes en abordaje o en curso.");
        }
    }

    private SimulationResponses.Status idle(Long tripId) {
        return new SimulationResponses.Status(tripId, SimulationStatus.IDLE, 0, 0, null, null);
    }

    @PreDestroy
    void stopSessions() {
        sessions.values().forEach(SimulationSession::stop);
        sessions.clear();
    }

    private static final class SimulationSession {
        private final Long tripId;
        private final List<RouteTrajectoryBuilder.GeoPoint> points;
        private final Instant startedAt;
        private SimulationStatus status = SimulationStatus.RUNNING;
        private int currentPoint;
        private Instant lastUpdatedAt;
        private ScheduledFuture<?> future;

        private SimulationSession(Long tripId, List<RouteTrajectoryBuilder.GeoPoint> points, Instant startedAt) {
            this.tripId = tripId;
            this.points = points;
            this.startedAt = startedAt;
        }

        private synchronized boolean isRunning() {
            return status == SimulationStatus.RUNNING;
        }

        private synchronized boolean hasNext() {
            return currentPoint < points.size();
        }

        private synchronized RouteTrajectoryBuilder.GeoPoint nextPoint() {
            return points.get(currentPoint);
        }

        private synchronized boolean recorded(Instant at) {
            currentPoint++;
            lastUpdatedAt = at;
            if (currentPoint == points.size()) {
                status = SimulationStatus.FINISHED;
                cancelFuture();
                return true;
            }
            return false;
        }

        private synchronized void attach(ScheduledFuture<?> future) {
            if (status == SimulationStatus.RUNNING) {
                this.future = future;
            } else {
                future.cancel(false);
            }
        }

        private synchronized boolean stop() {
            if (status != SimulationStatus.RUNNING) {
                return false;
            }
            status = SimulationStatus.STOPPED;
            cancelFuture();
            return true;
        }

        private void cancelFuture() {
            if (future != null) {
                future.cancel(false);
            }
        }

        private Long tripId() {
            return tripId;
        }

        private synchronized SimulationResponses.Status snapshot() {
            return new SimulationResponses.Status(
                    tripId, status, currentPoint, points.size(), startedAt, lastUpdatedAt);
        }
    }
}
