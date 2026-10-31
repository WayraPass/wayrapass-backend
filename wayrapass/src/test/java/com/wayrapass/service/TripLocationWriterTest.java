package com.wayrapass.service;

import com.wayrapass.exception.BusinessRuleException;
import com.wayrapass.exception.ResourceNotFoundException;
import com.wayrapass.model.Trip;
import com.wayrapass.model.TripLocation;
import com.wayrapass.model.TripStatus;
import com.wayrapass.repository.TripLocationRepository;
import com.wayrapass.repository.TripRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TripLocationWriterTest {
    @Mock TripRepository trips;
    @Mock TripLocationRepository locations;

    @Test
    void storesSimulationPointAsNormalTripLocationWithProvidedTimestamp() {
        Trip trip = new Trip();
        trip.setId(30L);
        trip.setStatus(TripStatus.IN_PROGRESS);
        when(trips.findById(30L)).thenReturn(Optional.of(trip));
        when(locations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        Instant timestamp = Instant.parse("2026-11-01T15:00:00Z");

        TripLocation saved = service().record(30L,
                new RouteTrajectoryBuilder.GeoPoint(new BigDecimal("-12.100000"), new BigDecimal("-77.100000")),
                timestamp);

        assertSame(trip, saved.getTrip());
        assertEquals(new BigDecimal("-12.100000"), saved.getLatitude());
        assertEquals(timestamp, saved.getRecordedAt());
        verify(locations).save(saved);
    }

    @Test
    void boardingTripCanReceiveSimulationLocations() {
        Trip trip = new Trip();
        trip.setStatus(TripStatus.BOARDING);
        when(trips.findById(30L)).thenReturn(Optional.of(trip));
        when(locations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertNotNull(service().record(30L, point(), Instant.now()));
    }

    @Test
    void missingTripIsRejected() {
        when(trips.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> service().record(99L, point(), Instant.now()));
        verifyNoInteractions(locations);
    }

    @Test
    void nonOperationalTripIsRejected() {
        Trip trip = new Trip();
        trip.setStatus(TripStatus.COMPLETED);
        when(trips.findById(30L)).thenReturn(Optional.of(trip));
        assertThrows(BusinessRuleException.class,
                () -> service().record(30L, point(), Instant.now()));
        verifyNoInteractions(locations);
    }

    private TripLocationWriter service() {
        return new TripLocationWriter(trips, locations);
    }

    private RouteTrajectoryBuilder.GeoPoint point() {
        return new RouteTrajectoryBuilder.GeoPoint(BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
