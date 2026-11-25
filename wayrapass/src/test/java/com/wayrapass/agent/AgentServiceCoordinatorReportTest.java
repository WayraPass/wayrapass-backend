package com.wayrapass.agent;

import com.wayrapass.agent.dto.ChatRequest;
import com.wayrapass.agent.tools.WayriaCoordinatorTools;
import com.wayrapass.agent.tools.WayriaTools;
import com.wayrapass.dto.response.ReportResponses;
import com.wayrapass.model.ReportIndicator;
import com.wayrapass.model.Role;
import com.wayrapass.model.User;
import com.wayrapass.security.CurrentUser;
import com.wayrapass.service.PunctualityAnalyticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.wayrapass.agent.dto.PunctualitySummary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentServiceCoordinatorReportTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 24);

    @Mock private ChatClient studentFamilyChatClient;
    @Mock private ChatClient coordinatorChatClient;
    @Mock private CurrentUser currentUser;
    @Mock private WayriaTools tools;
    @Mock private WayriaCoordinatorTools coordinatorTools;
    @Mock private PunctualityAnalyticsService analytics;

    private AgentService service;

    @BeforeEach
    void setUp() {
        service = new AgentService(studentFamilyChatClient, coordinatorChatClient, currentUser, tools, coordinatorTools, analytics);
        User coordinator = new User();
        coordinator.setId(77L);
        coordinator.setRole(Role.COORDINATOR);
        when(currentUser.require()).thenReturn(coordinator);
        when(analytics.today()).thenReturn(TODAY);
    }

    @Test
    void routeReportWithPartiallyFutureRangeKeepsRouteContextAndTruncatesAtToday() {
        when(coordinatorTools.getRouteOperationalReport(eq("UPC-MON-SMI-01"), eq(LocalDate.of(2026, 11, 1)), eq(TODAY)))
                .thenReturn(report(true));

        var periodQuestion = service.chat(new ChatRequest("Ahora genérame un reporte operativo pero de la ruta UPC-MON-SMI-01"));
        var response = service.chat(new ChatRequest("Del 1 al 29 de noviembre de 2026"));

        assertTrue(periodQuestion.message().contains("¿De qué periodo"));
        assertTrue(response.message().contains("UPC-MON-SMI-01"));
        verify(coordinatorTools).getRouteOperationalReport("UPC-MON-SMI-01", LocalDate.of(2026, 11, 1), TODAY);
    }

    @Test
    void globalReportWithPartiallyFutureRangeKeepsItsExistingBehavior() {
        when(coordinatorTools.getOperationalReportSummary(eq(LocalDate.of(2026, 11, 1)), eq(TODAY))).thenReturn(report(true));

        service.chat(new ChatRequest("Genérame un reporte operativo del 1 al 29 de noviembre de 2026"));

        verify(coordinatorTools).getOperationalReportSummary(LocalDate.of(2026, 11, 1), TODAY);
    }

    @Test
    void fullyFutureRouteReportReturnsNoOperationalDataWithoutCallingTheTool() {
        var response = service.chat(new ChatRequest("Genérame un reporte operativo de la ruta UPC-MON-SMI-01 del 26 al 29 de noviembre de 2026"));

        assertEquals("No existen datos disponibles para el periodo seleccionado", response.message());
        verify(coordinatorTools, never()).getRouteOperationalReport(eq("UPC-MON-SMI-01"), eq(LocalDate.of(2026, 11, 26)), eq(LocalDate.of(2026, 11, 29)));
    }

    @Test
    void pastRouteReportPreservesTheRequestedRange() {
        when(coordinatorTools.getRouteOperationalReport(eq("UPC-MON-SMI-01"), eq(LocalDate.of(2026, 11, 1)), eq(LocalDate.of(2026, 11, 20))))
                .thenReturn(report(true));

        service.chat(new ChatRequest("Genérame un reporte operativo de la ruta UPC-MON-SMI-01 del 1 al 20 de noviembre de 2026"));

        verify(coordinatorTools).getRouteOperationalReport("UPC-MON-SMI-01", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 20));
    }

    @Test
    void routePunctualityKeepsItsIntentAndUsesTheTruncatedPeriod() {
        when(coordinatorTools.getPunctualitySummary(eq(LocalDate.of(2026, 11, 1)), eq(TODAY))).thenReturn(punctuality());

        var question = service.chat(new ChatRequest("Dame un reporte de puntualidad de la ruta UPC-MON-SMI-01"));
        var response = service.chat(new ChatRequest("Del 1 al 29 de noviembre de 2026"));

        assertTrue(question.message().contains("consultar la puntualidad"));
        assertTrue(response.message().contains("8 viajes evaluados, 8 puntuales, 0 con retraso"));
        verify(coordinatorTools).getPunctualitySummary(LocalDate.of(2026, 11, 1), TODAY);
        verify(coordinatorTools, never()).getRouteOperationalReport(eq("UPC-MON-SMI-01"), eq(LocalDate.of(2026, 11, 1)), eq(TODAY));
    }

    private PunctualitySummary punctuality() {
        return new PunctualitySummary(LocalDate.of(2026, 11, 1), TODAY, 13, 13, 0, new BigDecimal("100.00"),
                List.of(new PunctualitySummary.DelayedRoute(1L, "UPC-MON-SMI-01", "Ruta", 8, 0,
                        BigDecimal.ZERO.setScale(2), 0)), List.of());
    }

    private ReportResponses.OperationalReport report(boolean hasData) {
        return new ReportResponses.OperationalReport(
                new ReportResponses.Period(LocalDate.of(2026, 11, 1), TODAY, "America/Lima"),
                ReportIndicator.ALL,
                Instant.parse("2026-11-24T12:00:00Z"),
                hasData,
                hasData ? null : "No existen datos disponibles para el periodo seleccionado",
                new ReportResponses.Punctuality(8, 8, 0, new BigDecimal("100.00")),
                new ReportResponses.Occupancy(8, 3, 256, new BigDecimal("1.17")),
                new ReportResponses.Incidents(3),
                List.of(new ReportResponses.StopDemand(1L, "Plaza San Miguel", 3))
        );
    }
}
