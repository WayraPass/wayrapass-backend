package com.wayrapass.agent.dto;

import java.util.List;

public record ChatResponse(String message, List<RouteSummary> routes,
                           ServiceStatusSummary serviceStatus) {
}
