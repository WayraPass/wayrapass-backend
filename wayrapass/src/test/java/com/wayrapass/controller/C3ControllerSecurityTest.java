package com.wayrapass.controller;

import com.wayrapass.dto.response.OperationalResponses;
import com.wayrapass.model.TripStatus;
import com.wayrapass.service.OperationalService;
import com.wayrapass.service.StudentAccessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
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
class C3ControllerSecurityTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean StudentAccessService studentAccessService;
    @MockitoBean OperationalService operationalService;

    @Test
    void notificationPreferencesWithoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/family-links/me/students/2/notification-preferences"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"STUDENT", "DRIVER", "COORDINATOR"})
    void onlyFamilyCanManageNotificationPreferences(String role) throws Exception {
        mockMvc.perform(get("/api/family-links/me/students/2/notification-preferences")
                        .with(user("user@wayrapass.test").roles(role)))
                .andExpect(status().isForbidden());
    }

    @Test
    void linkedFamilyReadsPreferences() throws Exception {
        when(studentAccessService.notificationPreferences(2L)).thenReturn(
                new OperationalResponses.NotificationPreferences(7L, 2L, true, false, true, false, true));

        mockMvc.perform(get("/api/family-links/me/students/2/notification-preferences")
                        .with(user("family@wayrapass.test").roles("FAMILY")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentId").value(2))
                .andExpect(jsonPath("$.notifyArrival").value(false))
                .andExpect(jsonPath("$.notifyIncident").value(true));
    }

    @Test
    void unlinkedFamilyCannotUpdatePreferences() throws Exception {
        when(studentAccessService.updateNotificationPreferences(any(), any()))
                .thenThrow(new AccessDeniedException("Sin acceso al estudiante."));

        mockMvc.perform(put("/api/family-links/me/students/2/notification-preferences")
                        .with(user("family@wayrapass.test").roles("FAMILY"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"notifyBoarding":true,"notifyArrival":true,"notifyDelay":true,
                                 "notifyVehicleChange":true,"notifyIncident":true}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void incompletePreferencesReturnFieldErrors() throws Exception {
        mockMvc.perform(put("/api/family-links/me/students/2/notification-preferences")
                        .with(user("family@wayrapass.test").roles("FAMILY"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"notifyBoarding":true,"notifyArrival":true,"notifyDelay":true,
                                 "notifyVehicleChange":true}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.notifyIncident").exists());
    }

    @Test
    void driverReceivesEnrichedAssignments() throws Exception {
        var assignment = new OperationalResponses.DriverTrip(
                30L, 30L, 5L, 10L, 12L,
                Instant.parse("2026-11-10T11:45:00Z"), Instant.parse("2026-11-10T13:00:00Z"),
                null, TripStatus.SCHEDULED, true,
                "R-12", "Ate - San Isidro", "Ate", "UPC San Isidro", null,
                null, null, true,
                "La asignación de este recorrido fue actualizada.",
                new OperationalResponses.DriverVehicle(12L, "ABC-123", "Mercedes", "Sprinter", 24),
                List.of(new OperationalResponses.DriverRouteStop(8L, 1, "Próceres", "Av. Próceres", "Ate", 15, true)));
        when(operationalService.myDriverTrips(any(), any())).thenReturn(List.of(assignment));

        mockMvc.perform(get("/api/trips/me?from=2026-11-10&to=2026-11-10")
                        .with(user("driver@wayrapass.test").roles("DRIVER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].routeCode").value("R-12"))
                .andExpect(jsonPath("$[0].vehicle.plate").value("ABC-123"))
                .andExpect(jsonPath("$[0].stops[0].name").value("Próceres"))
                .andExpect(jsonPath("$[0].assignmentChanged").value(true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"STUDENT", "FAMILY", "COORDINATOR"})
    void onlyDriverCanReadOwnAssignments(String role) throws Exception {
        mockMvc.perform(get("/api/trips/me")
                        .with(user("user@wayrapass.test").roles(role)))
                .andExpect(status().isForbidden());
    }

    @Test
    void incidentWithoutRequiredTypeReturnsOfficialValidationMessage() throws Exception {
        mockMvc.perform(post("/api/incidents")
                        .with(user("driver@wayrapass.test").roles("DRIVER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"tripId":30,"description":"Congestión","delayMinutes":10,"priority":"NORMAL"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.type").value("Selecciona el tipo de incidencia"));
    }

    @Test
    void incidentWithNegativeDelayReturnsValidationError() throws Exception {
        mockMvc.perform(post("/api/incidents")
                        .with(user("driver@wayrapass.test").roles("DRIVER"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"tripId":30,"type":"DETOUR","description":"Desvío",
                                 "delayMinutes":-1,"priority":"HIGH"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.delayMinutes").exists());
    }

    @Test
    void nonDriverCannotReportIncident() throws Exception {
        mockMvc.perform(post("/api/incidents")
                        .with(user("student@wayrapass.test").roles("STUDENT"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"tripId":30,"type":"TRAFFIC_DELAY","description":"Congestión",
                                 "delayMinutes":10,"priority":"NORMAL"}
                                """))
                .andExpect(status().isForbidden());
    }
}
