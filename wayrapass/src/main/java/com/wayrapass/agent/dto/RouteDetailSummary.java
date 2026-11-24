package com.wayrapass.agent.dto;

import java.util.List;

public record RouteDetailSummary(String code, String name, String origin, String destination,
                                 String institution, String campus, List<String> stops,
                                 List<String> schedules) {
}
