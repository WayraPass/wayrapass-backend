package com.wayrapass.service;

import java.math.BigDecimal;

final class GeographicDistanceCalculator {
    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    private GeographicDistanceCalculator() {
    }

    static long distanceMeters(BigDecimal fromLatitude, BigDecimal fromLongitude,
                               BigDecimal toLatitude, BigDecimal toLongitude) {
        double latitude1 = Math.toRadians(fromLatitude.doubleValue());
        double latitude2 = Math.toRadians(toLatitude.doubleValue());
        double latitudeDelta = latitude2 - latitude1;
        double longitudeDelta = Math.toRadians(toLongitude.doubleValue() - fromLongitude.doubleValue());

        double haversine = Math.pow(Math.sin(latitudeDelta / 2), 2)
                + Math.cos(latitude1) * Math.cos(latitude2)
                * Math.pow(Math.sin(longitudeDelta / 2), 2);
        double centralAngle = 2 * Math.atan2(Math.sqrt(haversine), Math.sqrt(1 - haversine));
        return Math.round(EARTH_RADIUS_METERS * centralAngle);
    }
}
