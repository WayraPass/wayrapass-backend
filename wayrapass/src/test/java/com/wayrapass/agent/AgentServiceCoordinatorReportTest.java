package com.wayrapass.agent;

import com.wayrapass.agent.dto.ChatRequest;
import com.wayrapass.agent.tools.WayriaCoordinatorTools;
import com.wayrapass.agent.tools.WayriaReassignmentTools;
import com.wayrapass.repository.AiActionRequestRepository;
import com.wayrapass.agent.tools.WayriaTools;
import com.wayrapass.dto.response.ReportResponses;
import com.wayrapass.model.ReportIndicator;
import com.wayrapass.model.Role;
import com.wayrapass.model.User;
import com.wayrapass.model.TripStatus;
import com.wayrapass.model.AuthorizationStatus;
import com.wayrapass.model.VehicleOperationalStatus;
import com.wayrapass.model.DriverOperationalStatus;
import com.wayrapass.security.CurrentUser;
import com.wayrapass.service.PunctualityAnalyticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
    @Mock private WayriaReassignmentTools reassignmentTools;
    @Mock private AiActionRequestRepository aiActions;
    @Mock private PunctualityAnalyticsService analytics;

    private AgentService service;

    @BeforeEach
    void setUp() {
        service = new AgentService(studentFamilyChatClient, coordinatorChatClient, currentUser, tools, coordinatorTools, reassignmentTools, aiActions, analytics);
        User coordinator = new User();
        coordinator.setId(77L);
        coordinator.setRole(Role.COORDINATOR);
        when(currentUser.require()).thenReturn(coordinator);
        org.mockito.Mockito.lenient().when(aiActions.findFirstByRequestedByIdAndStatusOrderByCreatedAtDesc(77L, com.wayrapass.model.AiActionStatus.PENDING)).thenReturn(java.util.Optional.empty());
        org.mockito.Mockito.lenient().when(analytics.today()).thenReturn(TODAY);
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

    @Test
    void vehicleReassignmentTakesPrecedenceOverRouteReportRouting() {
        var response = service.chat(new ChatRequest("Necesito reasignar una unidad disponible a la ruta UPC-MON-SMI-01"));

        assertEquals("Indica qué viaje u horario de UPC-MON-SMI-01 deseas reasignar.", response.message());
        assertTrue(!response.message().contains("periodo"));
        verify(coordinatorTools, never()).getRouteOperationalReport(eq("UPC-MON-SMI-01"), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(coordinatorTools, never()).getPunctualitySummary(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void driverReassignmentTakesPrecedenceOverRouteReportRouting() {
        var response = service.chat(new ChatRequest("Necesito reasignar un conductor disponible a la ruta UPC-MON-SMI-01"));
        when(reassignmentTools.findRelevantTrips("UPC-MON-SMI-01")).thenReturn(List.of(trip(17L, 15, 20)));
        when(reassignmentTools.findReassignmentOptions(17L, "UPC-MON-SMI-01")).thenReturn(driverOptions(17L, "Ana Conductora"));

        var options = service.chat(new ChatRequest("El de las 15:20"));

        assertEquals("Indica qué viaje u horario de UPC-MON-SMI-01 deseas reasignar.", response.message());
        assertTrue(options.message().contains("Ana Conductora"));
        verify(coordinatorTools, never()).getRouteOperationalReport(eq("UPC-MON-SMI-01"), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void operationalRouteReportStillUsesUs25() {
        var response = service.chat(new ChatRequest("Genera un reporte operativo de la ruta UPC-MON-SMI-01"));

        assertEquals("¿De qué periodo deseas generar el reporte operativo de UPC-MON-SMI-01?", response.message());
    }

    @Test
    void routePunctualityStillUsesUs25() {
        var response = service.chat(new ChatRequest("Dame la puntualidad de UPC-MON-SMI-01"));

        assertEquals("¿De qué periodo deseas consultar la puntualidad de UPC-MON-SMI-01?", response.message());
    }

    @Test
    void reassignmentScheduleQuestionStaysInUs26InsteadOfFallingBackToUs25() {
        startVehicleReassignment();
        when(reassignmentTools.findRelevantTrips("UPC-MON-SMI-01")).thenReturn(List.of(trip(17L, 15, 20)));

        var response = service.chat(new ChatRequest("¿Qué horarios tiene?"));

        assertTrue(response.message().contains("Viajes disponibles para UPC-MON-SMI-01: 15:20"));
        verify(coordinatorTools, never()).getRouteOperationalReport(eq("UPC-MON-SMI-01"), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(coordinatorTools, never()).getPunctualitySummary(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void selectingATimeResolvesTheRealTripAndAdvancesToVehicleOptions() {
        startVehicleReassignment();
        when(reassignmentTools.findRelevantTrips("UPC-MON-SMI-01")).thenReturn(List.of(trip(17L, 15, 20)));
        when(reassignmentTools.findReassignmentOptions(17L, "UPC-MON-SMI-01")).thenReturn(vehicleOptions(17L, "PRB-002", "PRB-004"));

        var response = service.chat(new ChatRequest("El de las 15:20"));
        var afterSelection = service.chat(new ChatRequest("¿Qué opciones quedan?"));

        assertTrue(response.message().contains("PRB-002"));
        assertTrue(!response.message().contains("Indica cuál deseas modificar"));
        assertTrue(afterSelection.message().contains("PRB-002"));
        verify(reassignmentTools, org.mockito.Mockito.times(2)).findReassignmentOptions(17L, "UPC-MON-SMI-01");
    }

    @Test
    void timeAndVehicleRequestAreHandledInTheSameTurn() {
        startVehicleReassignment();
        when(reassignmentTools.findRelevantTrips("UPC-MON-SMI-01")).thenReturn(List.of(trip(17L, 15, 20)));
        when(reassignmentTools.findReassignmentOptions(17L, "UPC-MON-SMI-01")).thenReturn(vehicleOptions(17L, "PRB-002", "PRB-004"));

        var response = service.chat(new ChatRequest("Quiero modificar el viaje de las 3:20 pm. Muéstrame las unidades disponibles para reasignar."));

        assertTrue(response.message().contains("PRB-002"));
        assertTrue(response.message().contains("PRB-004"));
        verify(reassignmentTools).findReassignmentOptions(17L, "UPC-MON-SMI-01");
    }

    @Test
    void unknownTimeKeepsTheTripSelectionPendingAndShowsRealAvailableTimes() {
        startVehicleReassignment();
        when(reassignmentTools.findRelevantTrips("UPC-MON-SMI-01")).thenReturn(List.of(trip(17L, 15, 20)));

        var response = service.chat(new ChatRequest("El de las 16:30"));

        assertTrue(response.message().contains("No encontré un viaje"));
        assertTrue(response.message().contains("15:20"));
        verify(reassignmentTools, never()).findReassignmentOptions(eq(17L), eq("UPC-MON-SMI-01"));
    }

    @Test
    void duplicateTimesRequireAnExplicitTripIdInsteadOfChoosingArbitrarily() {
        startVehicleReassignment();
        when(reassignmentTools.findRelevantTrips("UPC-MON-SMI-01")).thenReturn(List.of(trip(17L, 15, 20), trip(18L, 15, 20)));

        var response = service.chat(new ChatRequest("El de las 15:20"));

        assertTrue(response.message().contains("varios viajes"));
        assertTrue(response.message().contains("id 17"));
        assertTrue(response.message().contains("id 18"));
        verify(reassignmentTools, never()).findReassignmentOptions(org.mockito.ArgumentMatchers.anyLong(), eq("UPC-MON-SMI-01"));
    }

    @Test
    void driverReassignmentUsesTheSameTripResolutionAndShowsDrivers() {
        service.chat(new ChatRequest("Necesito reasignar un conductor disponible a la ruta UPC-MON-SMI-01"));
        when(reassignmentTools.findRelevantTrips("UPC-MON-SMI-01")).thenReturn(List.of(trip(17L, 15, 20)));
        when(reassignmentTools.findReassignmentOptions(17L, "UPC-MON-SMI-01")).thenReturn(driverOptions(17L, "Ana Conductora"));

        var response = service.chat(new ChatRequest("Quiero modificar el viaje de las 15:20 y ver conductores disponibles."));

        assertTrue(response.message().contains("Ana Conductora"));
        verify(reassignmentTools).findReassignmentOptions(17L, "UPC-MON-SMI-01");
    }

    @Test
    void askingForRouteSchedulesBeforeSelectingATripStillListsRealTrips() {
        startVehicleReassignment();
        when(reassignmentTools.findRelevantTrips("UPC-MON-SMI-01")).thenReturn(List.of(trip(17L, 15, 20)));

        var response = service.chat(new ChatRequest("Muéstrame los horarios de esa ruta"));

        assertTrue(response.message().contains("Viajes disponibles para UPC-MON-SMI-01: 15:20"));
    }

    @Test
    void exactVehiclePlateCreatesPendingProposalButDoesNotExecuteIt() {
        prepareVehicleSelection();
        when(reassignmentTools.proposeReassignment(17L, "VEHICLE", 5L)).thenReturn(action(71L, com.wayrapass.model.AiActionStatus.PENDING));

        var response = service.chat(new ChatRequest("WP-111"));

        assertTrue(response.message().contains("WP-111"));
        assertTrue(response.message().contains("¿Confirmas"));
        verify(reassignmentTools).proposeReassignment(17L, "VEHICLE", 5L);
        verify(reassignmentTools, never()).confirmReassignment(71L);
    }

    @Test
    void naturalLanguageVehicleSelectionResolvesOnlyTheVisiblePlate() {
        prepareVehicleSelection();
        when(reassignmentTools.proposeReassignment(17L, "VEHICLE", 8L)).thenReturn(action(72L, com.wayrapass.model.AiActionStatus.PENDING));

        var response = service.chat(new ChatRequest("La unidad WP2-123"));

        assertTrue(response.message().contains("WP2-123"));
        assertTrue(response.message().contains("¿Confirmas"));
        verify(reassignmentTools).proposeReassignment(17L, "VEHICLE", 8L);
    }

    @Test
    void usesSelectionWordAsAResourceChoiceNotAsFinalConfirmation() {
        prepareVehicleSelection();
        when(reassignmentTools.proposeReassignment(17L, "VEHICLE", 5L)).thenReturn(action(71L, com.wayrapass.model.AiActionStatus.PENDING));

        var response = service.chat(new ChatRequest("Usa WP-111"));

        assertTrue(response.message().contains("¿Confirmas"));
        verify(reassignmentTools).proposeReassignment(17L, "VEHICLE", 5L);
        verify(reassignmentTools, never()).confirmReassignment(71L);
    }

    @Test
    void unavailableVehicleDoesNotCreateAProposalAndKeepsRealAlternativesVisible() {
        prepareVehicleSelection();

        var response = service.chat(new ChatRequest("WP-999"));

        assertTrue(response.message().contains("WP-999"));
        assertTrue(response.message().contains("WP-111"));
        assertTrue(response.message().contains("WP2-123"));
        verify(reassignmentTools, never()).proposeReassignment(org.mockito.ArgumentMatchers.anyLong(), eq("VEHICLE"), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void vehicleSelectionIsCaseInsensitive() {
        prepareVehicleSelection();
        when(reassignmentTools.proposeReassignment(17L, "VEHICLE", 5L)).thenReturn(action(71L, com.wayrapass.model.AiActionStatus.PENDING));

        service.chat(new ChatRequest("wp-111"));

        verify(reassignmentTools).proposeReassignment(17L, "VEHICLE", 5L);
    }

    @Test
    void finalConfirmationExecutesOnlyAfterAPendingProposalExists() {
        prepareVehicleSelection();
        when(reassignmentTools.proposeReassignment(17L, "VEHICLE", 5L)).thenReturn(action(71L, com.wayrapass.model.AiActionStatus.PENDING));
        service.chat(new ChatRequest("WP-111"));
        var pending = new com.wayrapass.model.AiActionRequest();
        pending.setId(71L);
        pending.setStatus(com.wayrapass.model.AiActionStatus.PENDING);
        when(aiActions.findFirstByRequestedByIdAndStatusOrderByCreatedAtDesc(77L, com.wayrapass.model.AiActionStatus.PENDING)).thenReturn(java.util.Optional.of(pending));
        when(reassignmentTools.confirmReassignment(71L)).thenReturn(action(71L, com.wayrapass.model.AiActionStatus.EXECUTED));

        var response = service.chat(new ChatRequest("Sí, confirma"));

        assertTrue(response.message().toLowerCase(java.util.Locale.ROOT).contains("reasignado correctamente"));
        verify(reassignmentTools).confirmReassignment(71L);
    }

    @Test
    void cancellationRejectsThePendingProposalWithoutExecutingIt() {
        prepareVehicleSelection();
        when(reassignmentTools.proposeReassignment(17L, "VEHICLE", 5L)).thenReturn(action(71L, com.wayrapass.model.AiActionStatus.PENDING));
        service.chat(new ChatRequest("WP-111"));
        var pending = new com.wayrapass.model.AiActionRequest();
        pending.setId(71L);
        pending.setStatus(com.wayrapass.model.AiActionStatus.PENDING);
        when(aiActions.findFirstByRequestedByIdAndStatusOrderByCreatedAtDesc(77L, com.wayrapass.model.AiActionStatus.PENDING)).thenReturn(java.util.Optional.of(pending));

        var response = service.chat(new ChatRequest("Cancela"));

        assertTrue(response.message().contains("cancelada"));
        assertEquals(com.wayrapass.model.AiActionStatus.REJECTED, pending.getStatus());
        verify(reassignmentTools, never()).confirmReassignment(71L);
    }

    @Test
    void driverIdentifierCreatesAPendingProposalAndStillRequiresConfirmation() {
        service.chat(new ChatRequest("Necesito reasignar un conductor disponible a la ruta UPC-MON-SMI-01"));
        when(reassignmentTools.findRelevantTrips("UPC-MON-SMI-01")).thenReturn(List.of(trip(17L, 15, 20)));
        when(reassignmentTools.findReassignmentOptions(17L, "UPC-MON-SMI-01")).thenReturn(driverOptions(17L, "Carlos Ruiz", "DRV-004"));
        service.chat(new ChatRequest("15:20"));
        when(reassignmentTools.proposeReassignment(17L, "DRIVER", 3L)).thenReturn(action(73L, com.wayrapass.model.AiActionStatus.PENDING));

        var response = service.chat(new ChatRequest("DRV-004"));

        assertTrue(response.message().contains("Carlos Ruiz"));
        assertTrue(response.message().contains("¿Confirmas"));
        verify(reassignmentTools).proposeReassignment(17L, "DRIVER", 3L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"cancelar", "cancelarla", "nono, cancelar reasignacion", "No hagas el cambio", "Olvídalo", "mejor no"})
    void pendingReassignmentRecognizesNaturalCancellationWithoutExecuting(String cancellation) {
        var pending = pendingAction(71L);
        when(aiActions.findFirstByRequestedByIdAndStatusOrderByCreatedAtDesc(77L, com.wayrapass.model.AiActionStatus.PENDING))
                .thenReturn(java.util.Optional.of(pending));

        var response = service.chat(new ChatRequest(cancellation));

        assertEquals("La reasignación fue cancelada. No se realizaron cambios.", response.message());
        assertEquals(com.wayrapass.model.AiActionStatus.REJECTED, pending.getStatus());
        verify(aiActions).save(pending);
        verify(reassignmentTools, never()).confirmReassignment(71L);
    }

    @Test
    void ambiguousOrContradictoryPendingMessagesNeverExecuteOrCancel() {
        var pending = pendingAction(71L);
        when(aiActions.findFirstByRequestedByIdAndStatusOrderByCreatedAtDesc(77L, com.wayrapass.model.AiActionStatus.PENDING))
                .thenReturn(java.util.Optional.of(pending));

        var ambiguous = service.chat(new ChatRequest("tal vez"));
        var contradictory = service.chat(new ChatRequest("no, confirma"));

        assertTrue(ambiguous.message().contains("pendiente"));
        assertTrue(contradictory.message().contains("pendiente"));
        assertEquals(com.wayrapass.model.AiActionStatus.PENDING, pending.getStatus());
        verify(aiActions, never()).save(pending);
        verify(reassignmentTools, never()).confirmReassignment(71L);
    }

    private void startVehicleReassignment() {
        service.chat(new ChatRequest("Necesito reasignar una unidad disponible a la ruta UPC-MON-SMI-01"));
    }

    private void prepareVehicleSelection() {
        startVehicleReassignment();
        when(reassignmentTools.findRelevantTrips("UPC-MON-SMI-01")).thenReturn(List.of(trip(17L, 15, 20)));
        when(reassignmentTools.findReassignmentOptions(17L, "UPC-MON-SMI-01")).thenReturn(vehicleOptions(17L, "WP-111", "WP2-123"));
        service.chat(new ChatRequest("15:20"));
    }

    private WayriaReassignmentTools.TripOption trip(Long id, int hour, int minute) {
        return new WayriaReassignmentTools.TripOption(id, "UPC-MON-SMI-01",
                LocalDate.of(2026, 11, 24).atTime(hour, minute).atZone(ZoneId.of("America/Lima")).toInstant(), TripStatus.SCHEDULED);
    }

    private WayriaReassignmentTools.Options vehicleOptions(Long tripId, String... plates) {
        return new WayriaReassignmentTools.Options(tripId, "UPC-MON-SMI-01", List.of(), java.util.stream.IntStream.range(0, plates.length)
                .mapToObj(index -> new com.wayrapass.dto.response.OperationalResponses.VehicleOption((long) (index == 0 ? 5 : 8 + index - 1), plates[index], "Marca", "Modelo", 30,
                        AuthorizationStatus.AUTHORIZED, VehicleOperationalStatus.AVAILABLE)).toList(), null);
    }

    private WayriaReassignmentTools.Options driverOptions(Long tripId, String name) {
        return driverOptions(tripId, name, "LIC-1");
    }

    private WayriaReassignmentTools.Options driverOptions(Long tripId, String name, String licenseNumber) {
        return new WayriaReassignmentTools.Options(tripId, "UPC-MON-SMI-01", List.of(new com.wayrapass.dto.response.OperationalResponses.DriverOption(3L, name,
                licenseNumber, "A-II", LocalDate.of(2027, 1, 1), AuthorizationStatus.AUTHORIZED, DriverOperationalStatus.AVAILABLE)), List.of(), null);
    }

    private com.wayrapass.dto.response.OperationalResponses.AiAction action(Long id, com.wayrapass.model.AiActionStatus status) {
        return new com.wayrapass.dto.response.OperationalResponses.AiAction(id, 17L,
                com.wayrapass.model.AiActionType.REASSIGN_VEHICLE, status, null, 5L);
    }

    private com.wayrapass.model.AiActionRequest pendingAction(Long id) {
        var pending = new com.wayrapass.model.AiActionRequest();
        pending.setId(id);
        pending.setStatus(com.wayrapass.model.AiActionStatus.PENDING);
        return pending;
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
