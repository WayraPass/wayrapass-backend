package com.wayrapass.controller;

import com.wayrapass.service.StudentAccessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalTime;
import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RouteSearchControllerSecurityTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean StudentAccessService studentAccessService;

    @Test
    void searchWithoutAuthenticationReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/routes/search"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"FAMILY", "DRIVER", "COORDINATOR"})
    void onlyStudentCanSearchAvailableRoutes(String role) throws Exception {
        mockMvc.perform(get("/api/routes/search").with(user("user@wayrapass.test").roles(role)))
                .andExpect(status().isForbidden());
    }

    @Test
    void studentCanSearchWithCombinedQueryParameters() throws Exception {
        when(studentAccessService.searchRoutes(1L, "San Borja", LocalTime.of(7, 0)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/routes/search")
                        .with(user("student@wayrapass.test").roles("STUDENT"))
                        .param("institutionId", "1")
                        .param("district", "San Borja")
                        .param("time", "07:00"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void invalidTimeParameterUsesApiErrorResponse() throws Exception {
        mockMvc.perform(get("/api/routes/search")
                        .with(user("student@wayrapass.test").roles("STUDENT"))
                        .param("time", "not-a-time"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("La solicitud contiene parámetros inválidos o incompletos."))
                .andExpect(jsonPath("$.path").value("/api/routes/search"));
    }

    @Test
    void studentCanSearchByRouteLocationAndTime() throws Exception {
        when(studentAccessService.searchRoutes(1L, 5L, "San Borja", LocalTime.of(7, 0),
                new BigDecimal("-12.100000"), new BigDecimal("-77.030000"))).thenReturn(List.of());

        mockMvc.perform(get("/api/routes/search")
                        .with(user("student@wayrapass.test").roles("STUDENT"))
                        .param("institutionId", "1")
                        .param("routeId", "5")
                        .param("district", "San Borja")
                        .param("time", "07:00")
                        .param("latitude", "-12.100000")
                        .param("longitude", "-77.030000"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(studentAccessService).searchRoutes(1L, 5L, "San Borja", LocalTime.of(7, 0),
                new BigDecimal("-12.100000"), new BigDecimal("-77.030000"));
    }

    @Test
    void latitudeOutsideRangeUsesApiErrorResponse() throws Exception {
        mockMvc.perform(get("/api/routes/search")
                        .with(user("student@wayrapass.test").roles("STUDENT"))
                        .param("latitude", "90.000001")
                        .param("longitude", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message")
                        .value("La solicitud contiene parámetros inválidos o incompletos."))
                .andExpect(jsonPath("$.path").value("/api/routes/search"));
    }
}
