package com.wayrapass.service;

import com.wayrapass.exception.BusinessRuleException;
import com.wayrapass.exception.ResourceNotFoundException;
import com.wayrapass.model.Trip;
import com.wayrapass.model.TripLocation;
import com.wayrapass.model.TripStatus;
import com.wayrapass.repository.TripLocationRepository;
import com.wayrapass.repository.TripRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;

@Service
@ConditionalOnProperty(prefix = "app.simulator", name = "enabled", havingValue = "true")
public class TripLocationWriter {
    private static final EnumSet<TripStatus> SIMULATABLE =
            EnumSet.of(TripStatus.BOARDING, TripStatus.IN_PROGRESS);

    private final TripRepository trips;
    private final TripLocationRepository locations;

    public TripLocationWriter(TripRepository trips, TripLocationRepository locations) {
        this.trips = trips;
        this.locations = locations;
    }

    @Transactional
    public TripLocation record(Long tripId, RouteTrajectoryBuilder.GeoPoint point, Instant recordedAt) {
        Trip trip = trips.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Viaje no encontrado."));
        if (!SIMULATABLE.contains(trip.getStatus())) {
            throw new BusinessRuleException("Solo se pueden simular viajes en abordaje o en curso.");
        }
        TripLocation location = new TripLocation();
        location.setTrip(trip);
        location.setLatitude(point.latitude());
        location.setLongitude(point.longitude());
        location.setRecordedAt(recordedAt);
        return locations.save(location);
    }
}
