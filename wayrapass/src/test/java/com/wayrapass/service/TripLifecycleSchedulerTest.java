package com.wayrapass.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.Mockito.*;

class TripLifecycleSchedulerTest {
    private static final Instant NOW = Instant.parse("2026-11-03T13:00:00Z");

    @Test
    void schedulerReconcilesEveryDueTrip() {
        TripLifecycleService lifecycle = mock(TripLifecycleService.class);
        when(lifecycle.dueTripIds(NOW)).thenReturn(List.of(3L, 4L));
        TripLifecycleScheduler scheduler = new TripLifecycleScheduler(
                lifecycle, Clock.fixed(NOW, ZoneOffset.UTC));

        scheduler.reconcileDueTrips();

        verify(lifecycle).reconcileTrip(3L, NOW);
        verify(lifecycle).reconcileTrip(4L, NOW);
    }

    @Test
    void failureInOneTripDoesNotBlockRemainingTrips() {
        TripLifecycleService lifecycle = mock(TripLifecycleService.class);
        when(lifecycle.dueTripIds(NOW)).thenReturn(List.of(3L, 4L));
        when(lifecycle.reconcileTrip(3L, NOW)).thenThrow(new IllegalStateException("dato inválido"));
        TripLifecycleScheduler scheduler = new TripLifecycleScheduler(
                lifecycle, Clock.fixed(NOW, ZoneOffset.UTC));

        scheduler.reconcileDueTrips();

        verify(lifecycle).reconcileTrip(4L, NOW);
    }
}
