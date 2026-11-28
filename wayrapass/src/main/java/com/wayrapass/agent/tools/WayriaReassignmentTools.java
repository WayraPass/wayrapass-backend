package com.wayrapass.agent.tools;

import com.wayrapass.dto.request.OperationalRequests;
import com.wayrapass.dto.response.OperationalResponses;
import com.wayrapass.exception.BusinessRuleException;
import com.wayrapass.exception.ResourceNotFoundException;
import com.wayrapass.model.*;
import com.wayrapass.repository.RouteRepository;
import com.wayrapass.repository.TripRepository;
import com.wayrapass.service.OperationalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.List;

/** Coordinator-only bridge to the existing transactional reassignment service. */
@Component @RequiredArgsConstructor @Slf4j
public class WayriaReassignmentTools {
    private final RouteRepository routes; private final TripRepository trips; private final OperationalService operations;

    public record Options(Long tripId, String routeCode, List<OperationalResponses.DriverOption> drivers, List<OperationalResponses.VehicleOption> vehicles, String message) { }
    public record TripOption(Long tripId, String routeCode, java.time.Instant scheduledStartAt, TripStatus status) { }

    @Tool(description = "Lista viajes reales actuales o próximos de una ruta para que el coordinador elija cuál reasignar. No modifica datos.")
    public List<TripOption> findRelevantTrips(String routeCode) {
        Route route = routes.findByCodeIgnoreCase(routeCode.trim()).orElseThrow(() -> new ResourceNotFoundException("Ruta no encontrada."));
        return trips.findCurrentCandidates(route.getId(), List.of(TripStatus.BOARDING, TripStatus.IN_PROGRESS), TripStatus.SCHEDULED, Instant.now())
                .stream().map(t -> new TripOption(t.getId(), route.getCode(), t.getScheduledStartAt(), t.getStatus())).toList();
    }

    @Tool(description = "Consulta alternativas reales y disponibles para reasignar una ruta. No modifica datos ni crea solicitudes.")
    public Options findReassignmentOptions(String routeCode) {
        Route route = routes.findByCodeIgnoreCase(routeCode.trim()).orElseThrow(() -> new ResourceNotFoundException("Ruta no encontrada."));
        List<Trip> candidates = trips.findCurrentCandidates(route.getId(), List.of(TripStatus.BOARDING, TripStatus.IN_PROGRESS), TripStatus.SCHEDULED, Instant.now());
        if (candidates.isEmpty()) throw new BusinessRuleException("No existe un viaje actual o próximo para esa ruta.");
        if (candidates.size() > 1) throw new BusinessRuleException("La ruta tiene varios viajes relevantes; indica el identificador del viaje.");
        OperationalResponses.ReassignmentOptions result = operations.reassignmentOptions(candidates.getFirst().getId());
        log.info("WayrIA reassignment options route={} tripId={} vehicles={} drivers={}", route.getCode(), result.tripId(), result.vehicles().size(), result.drivers().size());
        return new Options(result.tripId(), route.getCode(), result.drivers(), result.vehicles(), result.message());
    }

    /**
     * Resolves alternatives for the trip already selected by the coordinator. This is deliberately
     * not exposed as an LLM tool: AgentService obtains the trip id deterministically from the
     * coordinator's selected departure time.
     */
    public Options findReassignmentOptions(Long tripId, String routeCode) {
        OperationalResponses.ReassignmentOptions result = operations.reassignmentOptions(tripId);
        log.info("WayrIA reassignment options route={} tripId={} vehicles={} drivers={}", routeCode, result.tripId(),
                result.vehicles().size(), result.drivers().size());
        return new Options(result.tripId(), routeCode, result.drivers(), result.vehicles(), result.message());
    }

    /**
     * Creates a PENDING proposal only after AgentService has deterministically resolved the
     * selected trip and eligible resource. It is intentionally not an LLM Tool.
     */
    public OperationalResponses.AiAction proposeReassignment(Long tripId, String resourceType, Long resourceId) {
        AiActionType type = "VEHICLE".equalsIgnoreCase(resourceType) ? AiActionType.REASSIGN_VEHICLE : AiActionType.REASSIGN_DRIVER;
        OperationalResponses.AiAction action = operations.propose(new OperationalRequests.AiProposal(tripId, type,
                type == AiActionType.REASSIGN_DRIVER ? resourceId : null,
                type == AiActionType.REASSIGN_VEHICLE ? resourceId : null,
                "Propuesta WayrIA", "Recurso seleccionado desde alternativas disponibles."));
        log.info("WayrIA reassignment proposed actionId={} tripId={} type={}", action.id(), tripId, type);
        return action;
    }

    public OperationalResponses.AiAction confirmReassignment(Long actionId) {
        OperationalResponses.AiAction action = operations.confirm(actionId);
        log.info("WayrIA reassignment executed actionId={}", action.id());
        return action;
    }
}
