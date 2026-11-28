package com.wayrapass.agent;

import com.wayrapass.agent.dto.ChatRequest;
import com.wayrapass.agent.dto.CurrentTripSummary;
import com.wayrapass.agent.dto.NextTripSummary;
import com.wayrapass.agent.dto.RouteDetailSummary;
import com.wayrapass.agent.tools.WayriaCoordinatorTools;
import com.wayrapass.agent.tools.WayriaReassignmentTools;
import com.wayrapass.agent.tools.WayriaTools;
import com.wayrapass.model.Role;
import com.wayrapass.model.TripStatus;
import com.wayrapass.model.User;
import com.wayrapass.repository.AiActionRequestRepository;
import com.wayrapass.security.CurrentUser;
import com.wayrapass.service.PunctualityAnalyticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentServiceStudentTripTest {
    @Mock private ChatClient studentFamilyChatClient;
    @Mock private ChatClient coordinatorChatClient;
    @Mock private CurrentUser currentUser;
    @Mock private WayriaTools tools;
    @Mock private WayriaCoordinatorTools coordinatorTools;
    @Mock private WayriaReassignmentTools reassignmentTools;
    @Mock private AiActionRequestRepository aiActions;
    @Mock private PunctualityAnalyticsService analytics;

    private AgentService service;

    @BeforeEach
    void setUp() {
        service = new AgentService(studentFamilyChatClient, coordinatorChatClient, currentUser, tools,
                coordinatorTools, reassignmentTools, aiActions, analytics);
        User student = new User();
        student.setId(9L);
        student.setRole(Role.STUDENT);
        when(currentUser.require()).thenReturn(student);
    }

    @Test
    void contextualNextTripReusesThePreviouslyMentionedRoute() {
        when(tools.getRouteDetails("UPC-MON-SMI-01")).thenReturn(List.of(routeDetails()));
        when(tools.getNextTripForMyAvailableRoute("UPC-MON-SMI-01"))
                .thenReturn(nextTrip("2026-11-27T20:20:00Z"));

        service.chat(new ChatRequest("¿Qué horarios tiene UPC-MON-SMI-01?"));
        var response = service.chat(new ChatRequest("¿Cuál es el próximo viaje de esa ruta?"));

        assertThat(response.message()).contains("UPC-MON-SMI-01", "15:20");
        verify(tools).getNextTripForMyAvailableRoute("UPC-MON-SMI-01");
    }

    @Test
    void explicitRouteNextTripUsesTheRealToolResult() {
        when(tools.getNextTripForMyAvailableRoute("UPC-MON-SMI-01"))
                .thenReturn(nextTrip("2026-11-27T20:20:00Z"));

        var response = service.chat(new ChatRequest("¿Cuál es el próximo viaje de UPC-MON-SMI-01?"));

        assertThat(response.message()).contains("27/11/2026", "15:20");
    }

    @Test
    void routeWithoutNextTripDoesNotInventOne() {
        when(tools.getNextTripForMyAvailableRoute("UPC-MON-SMI-01")).thenReturn(null);

        var response = service.chat(new ChatRequest("¿Cuál es el próximo viaje de UPC-MON-SMI-01?"));

        assertThat(response.message()).isEqualTo("No hay viajes próximos registrados para UPC-MON-SMI-01.");
    }

    @Test
    void activeTripIncludesConfirmedBoardingEtasAndIncidentDelay() {
        when(tools.getMyCurrentTrip()).thenReturn(activeTrip(true,
                List.of(new CurrentTripSummary.Incident("Tráfico por carril cerrado", 28))));

        var response = service.chat(new ChatRequest("¿Cuál es mi viaje actual?"));

        assertThat(response.message()).contains("UPC-MON-SMI-01", "En curso", "Hora programada: 15:14",
                "Inicio real: 15:14", "Abordaje confirmado: Sí, a las 15:17", "Plaza San Miguel",
                "Llegada estimada a tu paradero: 15:42", "Llegada estimada al campus: 15:48",
                "Tráfico por carril cerrado", "28 minutos");
    }

    @Test
    void activeTripMakesUnconfirmedBoardingAndNoIncidentsExplicit() {
        when(tools.getMyCurrentTrip()).thenReturn(activeTrip(false, List.of()));

        var response = service.chat(new ChatRequest("¿Tengo un viaje en curso?"));

        assertThat(response.message()).contains("Abordaje confirmado: No.",
                "Incidencias: no se han registrado incidencias.");
    }

    @Test
    void studentWithoutAnActiveTripGetsTheDefinedMessage() {
        when(tools.getMyCurrentTrip()).thenReturn(new CurrentTripSummary(false,
                "Actualmente no tienes un viaje en curso.", null, null, null, null, null,
                null, null, false, null, null, null, null, List.of()));

        var response = service.chat(new ChatRequest("¿En qué viaje me encuentro?"));

        assertThat(response.message()).isEqualTo("Actualmente no tienes un viaje en curso.");
    }

    @Test
    void currentTripNeverAcceptsAnotherStudentsIdentifier() {
        when(tools.getMyCurrentTrip()).thenReturn(new CurrentTripSummary(false,
                "Actualmente no tienes un viaje en curso.", null, null, null, null, null,
                null, null, false, null, null, null, null, List.of()));

        service.chat(new ChatRequest("¿Cuál es mi viaje actual del estudiante 999?"));

        verify(tools).getMyCurrentTrip();
        verifyNoMoreInteractions(tools);
    }

    private RouteDetailSummary routeDetails() {
        return new RouteDetailSummary("UPC-MON-SMI-01", "San Miguel", "San Miguel", "UPC Monterrico",
                "UPC", "Monterrico", List.of("Plaza San Miguel"), List.of("MONDAY 06:15"));
    }

    private NextTripSummary nextTrip(String instant) {
        return new NextTripSummary("UPC-MON-SMI-01", "San Miguel", "San Miguel", "UPC Monterrico",
                Instant.parse(instant));
    }

    private CurrentTripSummary activeTrip(boolean boarded, List<CurrentTripSummary.Incident> incidents) {
        return new CurrentTripSummary(true, "Viaje actual disponible.", "UPC-MON-SMI-01", "San Miguel",
                "San Miguel", "UPC Monterrico", TripStatus.IN_PROGRESS,
                Instant.parse("2026-11-27T20:14:00Z"), Instant.parse("2026-11-27T20:14:00Z"), boarded,
                boarded ? Instant.parse("2026-11-27T20:17:00Z") : null, "Plaza San Miguel",
                Instant.parse("2026-11-27T20:42:00Z"), Instant.parse("2026-11-27T20:48:00Z"), incidents);
    }
}
