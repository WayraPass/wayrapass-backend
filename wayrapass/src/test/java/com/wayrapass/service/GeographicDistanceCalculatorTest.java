package com.wayrapass.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeographicDistanceCalculatorTest {
    @Test
    void sameCoordinatesProduceZeroMeters() {
        assertEquals(0, GeographicDistanceCalculator.distanceMeters(
                new BigDecimal("-12.097000"), new BigDecimal("-77.036000"),
                new BigDecimal("-12.097000"), new BigDecimal("-77.036000")));
    }

    @Test
    void oneDegreeAtEquatorUsesKnownHaversineDistance() {
        long meters = GeographicDistanceCalculator.distanceMeters(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE);

        assertTrue(Math.abs(meters - 111_195) <= 1);
    }

    @Test
    void validCoordinateLimitsAreDeterministic() {
        long meters = GeographicDistanceCalculator.distanceMeters(
                new BigDecimal("90"), new BigDecimal("180"),
                new BigDecimal("90"), new BigDecimal("-180"));

        assertEquals(0, meters);
    }
}
