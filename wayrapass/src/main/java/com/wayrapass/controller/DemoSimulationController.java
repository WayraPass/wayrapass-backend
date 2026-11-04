package com.wayrapass.controller;

import com.wayrapass.dto.response.SimulationResponses;
import com.wayrapass.service.TripSimulationService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/demo/trips/{tripId}/simulation")
@PreAuthorize("hasRole('COORDINATOR')")
@ConditionalOnProperty(prefix = "app.simulator", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class DemoSimulationController {
    private final TripSimulationService simulations;

    @PostMapping("/start")
    public SimulationResponses.Status start(@PathVariable Long tripId) {
        return simulations.start(tripId);
    }

    @PostMapping("/stop")
    public SimulationResponses.Status stop(@PathVariable Long tripId) {
        return simulations.stop(tripId);
    }

    @GetMapping
    public SimulationResponses.Status status(@PathVariable Long tripId) {
        return simulations.status(tripId);
    }
}
