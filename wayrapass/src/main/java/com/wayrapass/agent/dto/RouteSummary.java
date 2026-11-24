package com.wayrapass.agent.dto;

public record RouteSummary(Long routeId, String code, String name, String origin, String destination,
                           String institution, String campus) {
}
