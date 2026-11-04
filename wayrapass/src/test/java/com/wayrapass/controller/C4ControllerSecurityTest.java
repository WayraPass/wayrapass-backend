package com.wayrapass.controller;

import com.wayrapass.dto.response.OperationalResponses;
import com.wayrapass.exception.BusinessRuleException;
import com.wayrapass.model.TripStatus;
import com.wayrapass.service.CatalogService;
import com.wayrapass.service.DriverService;
import com.wayrapass.service.OperationalService;
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
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class C4ControllerSecurityTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean OperationalService operationalService;
    @MockitoBean DriverService driverService;
    @MockitoBean CatalogService catalogService;

    @Test
    void assignmentWithoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(put("/api/trips/30/assignment")
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"driverId\":10,\"vehicleId\":12}"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"STUDENT", "FAMILY", "DRIVER"})
    void onlyCoordinatorCanAssignResources(String role) throws Exception {
        mockMvc.perform(put("/api/trips/30/assignment")
                        .with(user("user@wayrapass.test").roles(role))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"driverId\":10,\"vehicleId\":12}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void coordinatorCanAssignResources() throws Exception {
        when(operationalService.assignResources(any(), any())).thenReturn(new OperationalResponses.Trip(
                30L, 5L, 10L, 12L, Instant.parse("2026-11-10T11:45:00Z"),
                Instant.parse("2026-11-10T13:00:00Z"), null, TripStatus.SCHEDULED, false));

        mockMvc.perform(put("/api/trips/30/assignment")
                        .with(user("coordinator@wayrapass.test").roles("COORDINATOR"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"driverId\":10,\"vehicleId\":12}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.driverId").value(10))
                .andExpect(jsonPath("$.vehicleId").value(12));
    }

    @Test
    void assignmentRequiresBothResourceIds() throws Exception {
        mockMvc.perform(put("/api/trips/30/assignment")
                        .with(user("coordinator@wayrapass.test").roles("COORDINATOR"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"driverId\":10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.vehicleId").exists());
    }

    @Test
    void coordinatorCanReadReassignmentOptions() throws Exception {
        when(operationalService.reassignmentOptions(30L)).thenReturn(
                new OperationalResponses.ReassignmentOptions(30L, List.of(), List.of(),
                        "No existen recursos disponibles para realizar la reasignación"));

        mockMvc.perform(get("/api/trips/30/reassignment-options")
                        .with(user("coordinator@wayrapass.test").roles("COORDINATOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tripId").value(30))
                .andExpect(jsonPath("$.drivers").isEmpty())
                .andExpect(jsonPath("$.message").value("No existen recursos disponibles para realizar la reasignación"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"STUDENT", "FAMILY", "DRIVER"})
    void onlyCoordinatorCanReadWeeklyCalendar(String role) throws Exception {
        mockMvc.perform(get("/api/trips/calendar?from=2026-11-09&to=2026-11-15")
                        .with(user("user@wayrapass.test").roles(role)))
                .andExpect(status().isForbidden());
    }

    @Test
    void coordinatorReadsWeeklyCalendar() throws Exception {
        var operation = new OperationalResponses.WeeklyOperation(
                30L, LocalDate.of(2026, 11, 10), Instant.parse("2026-11-10T11:45:00Z"),
                Instant.parse("2026-11-10T13:00:00Z"), TripStatus.SCHEDULED,
                5L, "R-12", "Ate - San Isidro", "Ate", "UPC San Isidro",
                null, null, true, "La operación fue modificada o reasignada.");
        when(operationalService.weeklyCalendar(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 15)))
                .thenReturn(List.of(operation));

        mockMvc.perform(get("/api/trips/calendar?from=2026-11-09&to=2026-11-15")
                        .with(user("coordinator@wayrapass.test").roles("COORDINATOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].routeCode").value("R-12"))
                .andExpect(jsonPath("$[0].modified").value(true));
    }

    @Test
    void invalidCalendarRangeReturnsBadRequest() throws Exception {
        when(operationalService.weeklyCalendar(any(), any()))
                .thenThrow(new BusinessRuleException("El calendario debe consultarse de lunes a domingo dentro de una misma semana."));

        mockMvc.perform(get("/api/trips/calendar?from=2026-11-10&to=2026-11-15")
                        .with(user("coordinator@wayrapass.test").roles("COORDINATOR")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @ParameterizedTest
    @ValueSource(strings = {"STUDENT", "FAMILY", "DRIVER"})
    void onlyCoordinatorCanCreateDrivers(String role) throws Exception {
        mockMvc.perform(post("/api/drivers")
                        .with(user("user@wayrapass.test").roles(role))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"fullName":"Carlos","email":"driver@test.com","password":"Password123!",
                                 "licenseNumber":"LIC-1","licenseCategory":"AIII-C","licenseExpiration":"2030-01-01",
                                 "authorizationStatus":"AUTHORIZED","operationalStatus":"AVAILABLE"}
                                """))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"STUDENT", "FAMILY", "DRIVER"})
    void onlyCoordinatorCanUpdateVehicles(String role) throws Exception {
        mockMvc.perform(put("/api/vehicles/12")
                        .with(user("user@wayrapass.test").roles(role))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"plate":"ABC-123","brand":"Mercedes","model":"Sprinter","capacity":24,
                                 "qrToken":"QR-12","authorizationStatus":"AUTHORIZED","operationalStatus":"AVAILABLE"}
                                """))
                .andExpect(status().isForbidden());
    }
}
