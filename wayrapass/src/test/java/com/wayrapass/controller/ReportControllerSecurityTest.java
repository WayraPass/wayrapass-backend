package com.wayrapass.controller;

import com.wayrapass.service.ReportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportControllerSecurityTest {
    @Autowired MockMvc mockMvc;

    @Test
    void reportWithoutAuthenticationReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/reports/operations")
                        .param("from", "2026-09-01")
                        .param("to", "2026-09-30"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"STUDENT", "FAMILY", "DRIVER"})
    void nonCoordinatorRolesCannotAccessReports(String role) throws Exception {
        mockMvc.perform(get("/api/reports/operations")
                        .with(user("user@wayrapass.test").roles(role))
                        .param("from", "2026-09-01")
                        .param("to", "2026-09-30"))
                .andExpect(status().isForbidden());
    }

    @Test
    void coordinatorCanConsultEmptyPeriodAndReceivesExpectedMessage() throws Exception {
        mockMvc.perform(get("/api/reports/operations")
                        .with(user("coordinator@wayrapass.test").roles("COORDINATOR"))
                        .param("from", "2025-01-01")
                        .param("to", "2025-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasData").value(false))
                .andExpect(jsonPath("$.message").value(ReportService.NO_DATA_MESSAGE));
    }

    @Test
    void coordinatorReceivesBadRequestForInvertedPeriod() throws Exception {
        mockMvc.perform(get("/api/reports/operations")
                        .with(user("coordinator@wayrapass.test").roles("COORDINATOR"))
                        .param("from", "2026-10-01")
                        .param("to", "2026-09-30"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La fecha inicial no puede ser posterior a la fecha final."));
    }

    @Test
    void coordinatorCanExportXlsxWithBinaryHeaders() throws Exception {
        mockMvc.perform(get("/api/reports/operations/export")
                        .with(user("coordinator@wayrapass.test").roles("COORDINATOR"))
                        .param("from", "2025-01-01")
                        .param("to", "2025-01-31")
                        .param("indicator", "PUNCTUALITY")
                        .param("format", "XLSX"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE,
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString(".xlsx")))
                .andExpect(result -> assertTrue(result.getResponse().getContentAsByteArray().length > 0));
    }

    @Test
    void coordinatorCanExportPdfWithBinaryHeaders() throws Exception {
        mockMvc.perform(get("/api/reports/operations/export")
                        .with(user("coordinator@wayrapass.test").roles("COORDINATOR"))
                        .param("from", "2025-01-01")
                        .param("to", "2025-01-31")
                        .param("format", "PDF"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/pdf"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString(".pdf")))
                .andExpect(result -> assertTrue(result.getResponse().getContentAsByteArray().length > 0));
    }

    @Test
    void studentCannotExportAdministrativeReports() throws Exception {
        mockMvc.perform(get("/api/reports/operations/export")
                        .with(user("student@wayrapass.test").roles("STUDENT"))
                        .param("from", "2025-01-01")
                        .param("to", "2025-01-31")
                        .param("format", "PDF"))
                .andExpect(status().isForbidden());
    }
}
