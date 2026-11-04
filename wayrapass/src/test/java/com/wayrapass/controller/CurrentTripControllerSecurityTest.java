package com.wayrapass.controller;

import com.wayrapass.dto.response.TripQueryResponses;
import com.wayrapass.model.*;
import com.wayrapass.service.TripQueryService;
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

import java.time.LocalDate;
import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CurrentTripControllerSecurityTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean TripQueryService service;

    @Test
    void currentTripWithoutAuthenticationReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/trips/me/current"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"FAMILY", "DRIVER", "COORDINATOR"})
    void onlyStudentCanReadOwnCurrentTrip(String role) throws Exception {
        mockMvc.perform(get("/api/trips/me/current")
                        .with(user("user@wayrapass.test").roles(role)))
                .andExpect(status().isForbidden());
    }

    @Test
    void studentWithoutCurrentTripReceivesOkWithExplicitAvailability() throws Exception {
        when(service.myCurrentTrip()).thenReturn(new TripQueryResponses.CurrentTripResponse(
                false, "No hay un viaje activo o programado para la inscripción actual.", null));

        mockMvc.perform(get("/api/trips/me/current")
                        .with(user("student@wayrapass.test").roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.trip").doesNotExist());
    }

    @Test
    void upcomingTripsWithoutAuthenticationReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/trips/me/upcoming"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"FAMILY", "DRIVER", "COORDINATOR"})
    void onlyStudentCanReadOwnUpcomingTrips(String role) throws Exception {
        mockMvc.perform(get("/api/trips/me/upcoming")
                        .with(user("user@wayrapass.test").roles(role)))
                .andExpect(status().isForbidden());
    }

    @Test
    void studentCanReadOwnUpcomingTrips() throws Exception {
        when(service.myUpcomingTrips()).thenReturn(List.of());

        mockMvc.perform(get("/api/trips/me/upcoming")
                        .with(user("student@wayrapass.test").roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void studentUpcomingTripsResponseUsesTheExistingDto() throws Exception {
        when(service.myUpcomingTrips()).thenReturn(List.of(new TripQueryResponses.UpcomingTrip(
                41L, 5L, "R-12", "Ruta Centro", "Próceres", "Campus Centro",
                "Universidad Wayra", "Campus Centro", 8L, "Paradero Próceres",
                Instant.parse("2026-11-04T12:00:00Z"), Instant.parse("2026-11-04T13:30:00Z"),
                TripStatus.SCHEDULED, false, false, null
        )));

        mockMvc.perform(get("/api/trips/me/upcoming")
                        .with(user("student@wayrapass.test").roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tripId").value(41))
                .andExpect(jsonPath("$[0].status").value("SCHEDULED"))
                .andExpect(jsonPath("$[0].stopName").value("Paradero Próceres"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"STUDENT", "DRIVER", "COORDINATOR"})
    void onlyFamilyCanReadLinkedStudentDriver(String role) throws Exception {
        mockMvc.perform(get("/api/family-links/me/students/2/current-trip/driver")
                        .with(user("user@wayrapass.test").roles(role)))
                .andExpect(status().isForbidden());
    }

    @Test
    void unlinkedFamilyReceivesForbidden() throws Exception {
        when(service.familyCurrentTripDriver(2L)).thenThrow(new AccessDeniedException("Sin acceso"));

        mockMvc.perform(get("/api/family-links/me/students/2/current-trip/driver")
                        .with(user("family@wayrapass.test").roles("FAMILY")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void familyDriverResponseContainsPublicCredentialWithoutSensitiveAccountFields() throws Exception {
        when(service.familyCurrentTripDriver(2L)).thenReturn(new TripQueryResponses.DriverCredential(
                true,
                "Credencial del conductor disponible.",
                80L,
                "Carlos Chofer",
                "https://cdn.test/credential.png",
                true,
                "LIC-123",
                "AIII-C",
                LocalDate.of(2030, 1, 1),
                false,
                AuthorizationStatus.AUTHORIZED,
                DriverOperationalStatus.AVAILABLE,
                true
        ));

        mockMvc.perform(get("/api/family-links/me/students/2/current-trip/driver")
                        .with(user("family@wayrapass.test").roles("FAMILY")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.driverId").value(80))
                .andExpect(jsonPath("$.fullName").value("Carlos Chofer"))
                .andExpect(jsonPath("$.licenseNumber").value("LIC-123"))
                .andExpect(jsonPath("$.authorizationStatus").value("AUTHORIZED"))
                .andExpect(content().string(not(containsString("password"))))
                .andExpect(content().string(not(containsString("email"))))
                .andExpect(content().string(not(containsString("phone"))));
    }
}
