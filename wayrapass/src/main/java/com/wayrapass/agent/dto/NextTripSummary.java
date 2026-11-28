package com.wayrapass.agent.dto;

import java.time.Instant;

/** Read-only next scheduled trip for a route the authenticated student can use. */
public record NextTripSummary(String routeCode, String routeName, String origin, String destination,
                              Instant scheduledStartAt) {
}
