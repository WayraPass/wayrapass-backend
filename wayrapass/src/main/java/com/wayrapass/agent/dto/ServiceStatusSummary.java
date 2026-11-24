package com.wayrapass.agent.dto;

import java.time.Instant;

public record ServiceStatusSummary(String studentName, String routeCode, String routeName,
                                   String status, Instant scheduledStartAt, Instant scheduledEndAt,
                                   Instant estimatedArrivalAt, Long delayMinutes, String assignedStop,
                                   String message) {
}
