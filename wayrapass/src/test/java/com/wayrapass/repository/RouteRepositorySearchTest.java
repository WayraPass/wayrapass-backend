package com.wayrapass.repository;

import com.wayrapass.model.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
@ActiveProfiles("test")
class RouteRepositorySearchTest {
    @Autowired RouteRepository routes;
    @Autowired EntityManager entityManager;

    private Institution institution;

    @BeforeEach
    void setUp() {
        institution = new Institution();
        institution.setName("UPC");
        institution.setCampusName("Campus San Isidro");
        institution.setActive(true);
        entityManager.persist(institution);
    }

    @Test
    void searchFiltersInDatabaseAndExcludesInactiveSelectionData() {
        Route available = route("R-12", RouteStatus.ACTIVE);
        Stop sanBorja = stop("Próceres", "San Borja", true);
        routeStop(available, sanBorja, true, 1, 15);
        schedule(available, true, LocalTime.of(6, 45));

        Route inactiveRoute = route("R-99", RouteStatus.INACTIVE);
        Stop inactiveRouteStop = stop("Oculto", "San Borja", true);
        routeStop(inactiveRoute, inactiveRouteStop, true, 1, 5);
        schedule(inactiveRoute, true, LocalTime.of(7, 0));

        Stop inactiveStop = stop("Fuera de servicio", "San Borja", false);
        routeStop(available, inactiveStop, true, 2, 20);
        Stop inactiveRelationStop = stop("Relación inactiva", "San Borja", true);
        routeStop(available, inactiveRelationStop, false, 3, 25);
        entityManager.flush();

        var matches = routes.searchAvailableOptions(institution.getId(), "san borja");
        var withoutCoverage = routes.searchAvailableOptions(institution.getId(), "Ancón");

        assertEquals(1, matches.size());
        assertEquals("R-12", matches.getFirst().getRouteCode());
        assertEquals("Próceres", matches.getFirst().getStopName());
        assertEquals(15, matches.getFirst().getEstimatedMinutesFromStart());
        assertEquals(new BigDecimal("-12.100000"), matches.getFirst().getStopLatitude());
        assertEquals(0, withoutCoverage.size());
    }

    @Test
    void searchCanRestrictCandidatesToOneRoute() {
        Route selected = route("R-12", RouteStatus.ACTIVE);
        Stop selectedStop = stop("Próceres", "San Borja", true);
        routeStop(selected, selectedStop, true, 1, 15);
        schedule(selected, true, LocalTime.of(6, 45));
        Route other = route("R-08", RouteStatus.ACTIVE);
        Stop otherStop = stop("Canadá", "San Borja", true);
        routeStop(other, otherStop, true, 1, 10);
        schedule(other, true, LocalTime.of(6, 50));
        entityManager.flush();

        var matches = routes.searchAvailableOptions(institution.getId(), null, selected.getId());

        assertEquals(1, matches.size());
        assertEquals("R-12", matches.getFirst().getRouteCode());
    }

    private Route route(String code, RouteStatus status) {
        Route route = new Route();
        route.setInstitution(institution);
        route.setCode(code);
        route.setName("Ruta " + code);
        route.setOrigin("Origen");
        route.setDestination("Destino");
        route.setStatus(status);
        entityManager.persist(route);
        return route;
    }

    private Stop stop(String name, String district, boolean active) {
        Stop stop = new Stop();
        stop.setName(name);
        stop.setDistrict(district);
        stop.setLatitude(new BigDecimal("-12.100000"));
        stop.setLongitude(new BigDecimal("-77.030000"));
        stop.setActive(active);
        entityManager.persist(stop);
        return stop;
    }

    private void routeStop(Route route, Stop stop, boolean active, int order, int offset) {
        RouteStop relation = new RouteStop();
        relation.setRoute(route);
        relation.setStop(stop);
        relation.setActive(active);
        relation.setStopOrder(order);
        relation.setEstimatedMinutesFromStart(offset);
        entityManager.persist(relation);
    }

    private void schedule(Route route, boolean active, LocalTime departure) {
        RouteSchedule schedule = new RouteSchedule();
        schedule.setRoute(route);
        schedule.setDayOfWeek(DayOfWeek.MONDAY);
        schedule.setDepartureTime(departure);
        schedule.setEstimatedArrivalTime(departure.plusHours(1));
        schedule.setActive(active);
        entityManager.persist(schedule);
    }
}
