package com.wayrapass.agent;

import com.wayrapass.agent.dto.ChatRequest;
import com.wayrapass.agent.dto.ChatResponse;
import com.wayrapass.agent.dto.RouteDetailSummary;
import com.wayrapass.agent.dto.RouteSummary;
import com.wayrapass.agent.tools.WayriaTools;
import com.wayrapass.exception.WayriaUnavailableException;
import com.wayrapass.model.Role;
import com.wayrapass.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AgentService {
    private static final Pattern ROUTE_CODE = Pattern.compile("\\b[A-Z0-9]+(?:-[A-Z0-9]+){2,}\\b");
    private final ChatClient wayriaChatClient;
    private final CurrentUser currentUser;
    private final WayriaTools tools;

    public ChatResponse chat(ChatRequest request) {
        try {
            var user = currentUser.require();
            String message = request.message().trim();
            ChatResponse deterministic = criticalDataResponse(message, user.getRole());
            if (deterministic != null) return deterministic;

            String conversationId = "wayria:" + user.getId();
            ChatResponse response = wayriaChatClient.prompt()
                    .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, conversationId))
                    .user(message)
                    .call()
                    .entity(ChatResponse.class);
            if (response == null || response.message() == null || response.message().isBlank()) {
                throw new WayriaUnavailableException();
            }
            return new ChatResponse(response.message(),
                    response.routes() == null || response.routes().isEmpty() ? null : response.routes(),
                    hasActualServiceStatus(response.serviceStatus()) ? response.serviceStatus() : null);
        } catch (WayriaUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new WayriaUnavailableException(exception);
        }
    }

    /**
     * Schedules, stops, route codes and live status are critical values.  For these explicit
     * intents we render the Tool result directly instead of allowing the model to summarize it.
     */
    private ChatResponse criticalDataResponse(String message, Role role) {
        String normalized = message.toLowerCase(Locale.ROOT);
        if (role == Role.STUDENT && asksForMyAvailableRoutes(normalized)) {
            List<RouteSummary> routes = tools.getMyAvailableRoutes();
            String text = routes.isEmpty() ? "No tienes rutas disponibles para tu institución."
                    : "Tus rutas disponibles son: " + routes.stream().map(RouteSummary::code)
                    .reduce((left, right) -> left + ", " + right).orElse("") + ".";
            return new ChatResponse(text, routes.isEmpty() ? null : routes, null);
        }
        Optional<String> code = routeCode(message);
        if (code.isPresent() && (asksForSchedules(normalized) || asksForStops(normalized))) {
            List<RouteDetailSummary> details = tools.getRouteDetails(code.get());
            return detailResponse(details, asksForSchedules(normalized), asksForStops(normalized), code.get());
        }
        if (role == Role.STUDENT && asksForMyRouteSchedule(normalized)) {
            List<RouteDetailSummary> details = tools.getMyRouteDetails();
            return detailResponse(details, true, false, null);
        }
        return null;
    }

    private ChatResponse detailResponse(List<RouteDetailSummary> details, boolean schedules, boolean stops,
                                        String requestedCode) {
        if (details.isEmpty()) {
            String suffix = requestedCode == null ? "tu ruta inscrita" : "la ruta " + requestedCode;
            return new ChatResponse("No encontré información para " + suffix + ".", null, null);
        }
        if (details.size() > 1) {
            return new ChatResponse("Encontré varias rutas: " + details.stream().map(RouteDetailSummary::code)
                    .reduce((left, right) -> left + ", " + right).orElse("") + ". Indica el código exacto.", null, null);
        }
        RouteDetailSummary detail = details.getFirst();
        String values = schedules ? detail.schedules().stream().map(this::formatScheduleInSpanish)
                .reduce((left, right) -> left + ", " + right).orElse("") : String.join(", ", detail.stops());
        String label = schedules ? "Los horarios registrados para " : "Los paraderos registrados para ";
        return new ChatResponse(label + detail.code() + " son: " + values + ".",
                List.of(new RouteSummary(null, detail.code(), detail.name(), detail.origin(), detail.destination(),
                        detail.institution(), detail.campus())), null);
    }

    private boolean asksForMyAvailableRoutes(String message) {
        return message.contains("mis rutas") && (message.contains("disponible") || message.contains("puedo usar"));
    }

    private boolean asksForSchedules(String message) {
        return message.contains("horario") || message.contains("a que hora") || message.contains("a qué hora");
    }

    private boolean asksForStops(String message) {
        return message.contains("paradero");
    }

    private boolean asksForMyRouteSchedule(String message) {
        return message.contains("mi ruta") && asksForSchedules(message);
    }

    private Optional<String> routeCode(String message) {
        Matcher matcher = ROUTE_CODE.matcher(message.toUpperCase(Locale.ROOT));
        return matcher.find() ? Optional.of(matcher.group()) : Optional.empty();
    }

    /** Translates only the DayOfWeek token; the time part stays exactly as returned by the Tool. */
    private String formatScheduleInSpanish(String schedule) {
        int separator = schedule.indexOf(' ');
        if (separator < 0) return schedule;
        return dayToSpanish(schedule.substring(0, separator)) + schedule.substring(separator);
    }

    private String dayToSpanish(String day) {
        return switch (day) {
            case "MONDAY" -> "Lunes";
            case "TUESDAY" -> "Martes";
            case "WEDNESDAY" -> "Miércoles";
            case "THURSDAY" -> "Jueves";
            case "FRIDAY" -> "Viernes";
            case "SATURDAY" -> "Sábado";
            case "SUNDAY" -> "Domingo";
            default -> day;
        };
    }

    private boolean hasActualServiceStatus(com.wayrapass.agent.dto.ServiceStatusSummary status) {
        return status != null && (status.routeCode() != null || status.status() != null
                || status.scheduledStartAt() != null || status.estimatedArrivalAt() != null);
    }
}
