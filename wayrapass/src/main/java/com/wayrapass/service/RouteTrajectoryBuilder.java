package com.wayrapass.service;

import com.wayrapass.exception.ConflictException;
import com.wayrapass.model.RouteStop;
import com.wayrapass.model.Stop;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
@ConditionalOnProperty(prefix = "app.simulator", name = "enabled", havingValue = "true")
public class RouteTrajectoryBuilder {
    static final String INSUFFICIENT_POINTS_MESSAGE =
            "No existen suficientes puntos georreferenciados para simular el recorrido.";
    private static final MathContext MATH = MathContext.DECIMAL64;
    private static final int COORDINATE_SCALE = 6;

    public List<GeoPoint> build(List<RouteStop> routeStops, int stepsPerSegment) {
        List<GeoPoint> stops = routeStops.stream()
                .filter(RouteStop::isActive)
                .filter(routeStop -> routeStop.getStop() != null && routeStop.getStop().isActive())
                .sorted(Comparator.comparingInt(RouteStop::getStopOrder))
                .map(RouteStop::getStop)
                .filter(this::hasValidCoordinates)
                .map(stop -> point(stop.getLatitude(), stop.getLongitude()))
                .toList();
        if (stops.size() < 2) {
            throw new ConflictException(INSUFFICIENT_POINTS_MESSAGE);
        }

        List<GeoPoint> trajectory = new ArrayList<>();
        trajectory.add(stops.getFirst());
        for (int segment = 0; segment < stops.size() - 1; segment++) {
            GeoPoint start = stops.get(segment);
            GeoPoint end = stops.get(segment + 1);
            for (int step = 1; step <= stepsPerSegment; step++) {
                trajectory.add(interpolate(start, end, step, stepsPerSegment));
            }
        }
        return List.copyOf(trajectory);
    }

    private GeoPoint interpolate(GeoPoint start, GeoPoint end, int step, int totalSteps) {
        BigDecimal fraction = BigDecimal.valueOf(step).divide(BigDecimal.valueOf(totalSteps), MATH);
        BigDecimal latitude = start.latitude().add(
                end.latitude().subtract(start.latitude(), MATH).multiply(fraction, MATH), MATH);
        BigDecimal longitude = start.longitude().add(
                end.longitude().subtract(start.longitude(), MATH).multiply(fraction, MATH), MATH);
        return point(latitude, longitude);
    }

    private boolean hasValidCoordinates(Stop stop) {
        return stop.getLatitude() != null
                && stop.getLongitude() != null
                && stop.getLatitude().compareTo(BigDecimal.valueOf(-90)) >= 0
                && stop.getLatitude().compareTo(BigDecimal.valueOf(90)) <= 0
                && stop.getLongitude().compareTo(BigDecimal.valueOf(-180)) >= 0
                && stop.getLongitude().compareTo(BigDecimal.valueOf(180)) <= 0;
    }

    private GeoPoint point(BigDecimal latitude, BigDecimal longitude) {
        return new GeoPoint(
                latitude.setScale(COORDINATE_SCALE, RoundingMode.HALF_UP),
                longitude.setScale(COORDINATE_SCALE, RoundingMode.HALF_UP)
        );
    }

    public record GeoPoint(BigDecimal latitude, BigDecimal longitude) {
    }
}
