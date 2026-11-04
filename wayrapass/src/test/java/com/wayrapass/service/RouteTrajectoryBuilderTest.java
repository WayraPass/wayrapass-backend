package com.wayrapass.service;

import com.wayrapass.exception.ConflictException;
import com.wayrapass.model.RouteStop;
import com.wayrapass.model.Stop;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RouteTrajectoryBuilderTest {
    private final RouteTrajectoryBuilder builder = new RouteTrajectoryBuilder();

    @Test
    void buildsLinearTrajectoryBetweenTwoStopsWithoutDuplicatingEndpoints() {
        var result = builder.build(List.of(
                routeStop(1, "-12.000000", "-77.000000", true),
                routeStop(2, "-12.003000", "-77.006000", true)
        ), 3);

        assertEquals(4, result.size());
        assertPoint(result.get(0), "-12.000000", "-77.000000");
        assertPoint(result.get(1), "-12.001000", "-77.002000");
        assertPoint(result.get(2), "-12.002000", "-77.004000");
        assertPoint(result.get(3), "-12.003000", "-77.006000");
    }

    @Test
    void buildsMultipleSegmentsInOperationalOrder() {
        var result = builder.build(List.of(
                routeStop(30, "-12.006000", "-77.006000", true),
                routeStop(10, "-12.000000", "-77.000000", true),
                routeStop(20, "-12.002000", "-77.002000", true)
        ), 2);

        assertEquals(5, result.size());
        assertPoint(result.getFirst(), "-12.000000", "-77.000000");
        assertPoint(result.get(2), "-12.002000", "-77.002000");
        assertPoint(result.getLast(), "-12.006000", "-77.006000");
    }

    @Test
    void ignoresInactiveRouteStops() {
        var result = builder.build(List.of(
                routeStop(1, "-12.000000", "-77.000000", true),
                routeStop(2, "-12.500000", "-77.500000", false),
                routeStop(3, "-12.010000", "-77.010000", true)
        ), 1);

        assertEquals(2, result.size());
        assertPoint(result.getLast(), "-12.010000", "-77.010000");
    }

    @Test
    void ignoresStopsWithoutCompleteCoordinates() {
        RouteStop incomplete = routeStop(2, null, "-77.500000", true);
        var result = builder.build(List.of(
                routeStop(1, "-12.000000", "-77.000000", true),
                incomplete,
                routeStop(3, "-12.010000", "-77.010000", true)
        ), 1);

        assertEquals(2, result.size());
    }

    @Test
    void ignoresStopsWithCoordinatesOutsideGeographicRange() {
        var result = builder.build(List.of(
                routeStop(1, "-12.000000", "-77.000000", true),
                routeStop(2, "-92.000000", "-77.500000", true),
                routeStop(3, "-12.010000", "-77.010000", true)
        ), 1);

        assertEquals(2, result.size());
    }

    @Test
    void rejectsTrajectoryWithFewerThanTwoGeoreferencedStops() {
        ConflictException error = assertThrows(ConflictException.class, () -> builder.build(List.of(
                routeStop(1, "-12.000000", "-77.000000", true),
                routeStop(2, null, null, true)
        ), 5));

        assertEquals(RouteTrajectoryBuilder.INSUFFICIENT_POINTS_MESSAGE, error.getMessage());
    }

    private RouteStop routeStop(int order, String latitude, String longitude, boolean active) {
        Stop stop = new Stop();
        stop.setActive(true);
        stop.setLatitude(latitude == null ? null : new BigDecimal(latitude));
        stop.setLongitude(longitude == null ? null : new BigDecimal(longitude));
        RouteStop routeStop = new RouteStop();
        routeStop.setStop(stop);
        routeStop.setStopOrder(order);
        routeStop.setActive(active);
        return routeStop;
    }

    private void assertPoint(RouteTrajectoryBuilder.GeoPoint point, String latitude, String longitude) {
        assertEquals(new BigDecimal(latitude), point.latitude());
        assertEquals(new BigDecimal(longitude), point.longitude());
    }
}
