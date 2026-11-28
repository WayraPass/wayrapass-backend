package com.wayrapass.agent.dto;

import com.wayrapass.model.TripStatus;

import java.time.Instant;
import java.util.List;

/** Minimal, authenticated-student projection of the same current-trip data used by the UI. */
public record CurrentTripSummary(boolean available, String message, String routeCode, String routeName,
                                 String origin, String destination, TripStatus status,
                                 Instant scheduledStartAt, Instant actualStartAt,
                                 boolean boardingConfirmed, Instant boardedAt, String assignedStop,
                                 Instant stopEstimatedArrivalAt, Instant campusEstimatedArrivalAt,
                                 List<Incident> incidents) {
    public record Incident(String description, int delayMinutes) {
    }
}
