package com.wayrapass.agent.dto;

import java.util.List;

public record ChatResponse(String message, List<RouteSummary> routes,
                           ServiceStatusSummary serviceStatus, PunctualitySummary punctualitySummary) {
    public ChatResponse(String message, List<RouteSummary> routes, ServiceStatusSummary serviceStatus) {
        this(message, routes, serviceStatus, null);
    }
}
