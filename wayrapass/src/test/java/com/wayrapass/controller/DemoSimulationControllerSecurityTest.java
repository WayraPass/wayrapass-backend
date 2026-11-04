package com.wayrapass.controller;

import com.wayrapass.dto.response.SimulationResponses;
import com.wayrapass.exception.BusinessRuleException;
import com.wayrapass.exception.ConflictException;
import com.wayrapass.exception.ResourceNotFoundException;
import com.wayrapass.model.SimulationStatus;
import com.wayrapass.service.TripSimulationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "app.simulator.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DemoSimulationControllerSecurityTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean TripSimulationService simulations;

    @Test
    void unauthenticatedRequestIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/demo/trips/30/simulation/start"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"STUDENT", "FAMILY", "DRIVER"})
    void onlyCoordinatorCanControlSimulation(String role) throws Exception {
        mockMvc.perform(post("/api/demo/trips/30/simulation/start")
                        .with(user("user@wayrapass.test").roles(role)))
                .andExpect(status().isForbidden());
    }

    @Test
    void coordinatorCanStartSimulation() throws Exception {
        when(simulations.start(30L)).thenReturn(running());

        mockMvc.perform(post("/api/demo/trips/30/simulation/start")
                        .with(user("coordinator@wayrapass.test").roles("COORDINATOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tripId").value(30))
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.currentPoint").value(1))
                .andExpect(jsonPath("$.totalPoints").value(6));
    }

    @Test
    void coordinatorCanStopSimulation() throws Exception {
        when(simulations.stop(30L)).thenReturn(new SimulationResponses.Status(
                30L, SimulationStatus.STOPPED, 3, 6,
                Instant.parse("2026-11-01T15:00:00Z"), Instant.parse("2026-11-01T15:00:10Z")));

        mockMvc.perform(post("/api/demo/trips/30/simulation/stop")
                        .with(user("coordinator@wayrapass.test").roles("COORDINATOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("STOPPED"));
    }

    @Test
    void coordinatorCanReadSimulationStatus() throws Exception {
        when(simulations.status(30L)).thenReturn(running());

        mockMvc.perform(get("/api/demo/trips/30/simulation")
                        .with(user("coordinator@wayrapass.test").roles("COORDINATOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"));
    }

    @Test
    void missingTripUsesApiErrorResponseWithNotFound() throws Exception {
        when(simulations.start(999L)).thenThrow(new ResourceNotFoundException("Viaje no encontrado."));

        mockMvc.perform(post("/api/demo/trips/999/simulation/start")
                        .with(user("coordinator@wayrapass.test").roles("COORDINATOR")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Viaje no encontrado."));
    }

    @Test
    void incompatibleTripStatusUsesBadRequest() throws Exception {
        when(simulations.start(30L))
                .thenThrow(new BusinessRuleException("Solo se pueden simular viajes en abordaje o en curso."));

        mockMvc.perform(post("/api/demo/trips/30/simulation/start")
                        .with(user("coordinator@wayrapass.test").roles("COORDINATOR")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void duplicateSimulationUsesConflict() throws Exception {
        when(simulations.start(30L))
                .thenThrow(new ConflictException("Ya existe una simulación activa para este viaje."));

        mockMvc.perform(post("/api/demo/trips/30/simulation/start")
                        .with(user("coordinator@wayrapass.test").roles("COORDINATOR")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    private SimulationResponses.Status running() {
        Instant now = Instant.parse("2026-11-01T15:00:00Z");
        return new SimulationResponses.Status(30L, SimulationStatus.RUNNING, 1, 6, now, now);
    }
}
