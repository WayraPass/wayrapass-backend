package com.wayrapass.service;

import com.wayrapass.model.Institution;
import com.wayrapass.model.Route;
import com.wayrapass.model.RouteStatus;
import com.wayrapass.model.RouteStop;
import com.wayrapass.model.Stop;
import com.wayrapass.model.Trip;
import com.wayrapass.model.TripStatus;
import com.wayrapass.repository.InstitutionRepository;
import com.wayrapass.repository.RouteRepository;
import com.wayrapass.repository.RouteStopRepository;
import com.wayrapass.repository.StopRepository;
import com.wayrapass.repository.TripLocationRepository;
import com.wayrapass.repository.TripRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "app.simulator.enabled=true",
        "app.simulator.interval-seconds=60"
})
@ActiveProfiles("test")
class TripSimulationPersistenceIntegrationTest {

    @Autowired TripSimulationService simulation;
    @Autowired InstitutionRepository institutions;
    @Autowired RouteRepository routes;
    @Autowired StopRepository stops;
    @Autowired RouteStopRepository routeStops;
    @Autowired TripRepository trips;
    @Autowired TripLocationRepository locations;

    @Test
    void startLoadsLazyRouteAndStopsWithOpenInViewDisabled() {
        Institution institution = new Institution();
        institution.setName("Institución simulación persistida");
        institution.setCampusName("Campus D4");
        institution = institutions.save(institution);

        Route route = new Route();
        route.setInstitution(institution);
        route.setCode("R-D4-PERSISTENCE");
        route.setName("Ruta D4 persistida");
        route.setOrigin("Origen");
        route.setDestination("Destino");
        route.setCapacity(20);
        route.setStatus(RouteStatus.ACTIVE);
        route = routes.save(route);

        Stop first = stop("Paradero D4 A", "-12.077500", "-77.090000");
        Stop second = stop("Paradero D4 B", "-12.056000", "-77.030000");
        first = stops.save(first);
        second = stops.save(second);
        routeStops.save(routeStop(route, first, 1));
        routeStops.save(routeStop(route, second, 2));

        Trip trip = new Trip();
        trip.setRoute(route);
        trip.setStatus(TripStatus.BOARDING);
        trip.setScheduledStartAt(Instant.now());
        trip.setScheduledEndAt(Instant.now().plusSeconds(3600));
        trip = trips.save(trip);

        var status = simulation.start(trip.getId());
        try {
            assertEquals(1, status.currentPoint());
            assertEquals(6, status.totalPoints());
            assertTrue(locations.findFirstByTripIdOrderByRecordedAtDesc(trip.getId()).isPresent());
        } finally {
            simulation.stop(trip.getId());
        }
    }

    private Stop stop(String name, String latitude, String longitude) {
        Stop stop = new Stop();
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
        routeStop.setEstimatedMinutesFromStart((order - 1) * 10);
        routeStop.setActive(true);
        return routeStop;
    }
}
