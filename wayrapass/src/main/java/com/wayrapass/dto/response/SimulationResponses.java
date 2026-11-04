package com.wayrapass.dto.response;

import com.wayrapass.model.SimulationStatus;

import java.time.Instant;

public final class SimulationResponses {
    private SimulationResponses() {
    }

    public record Status(
            Long tripId,
            SimulationStatus status,
            int currentPoint,
            int totalPoints,
            Instant startedAt,
            Instant lastUpdatedAt
    ) {
    }
}
