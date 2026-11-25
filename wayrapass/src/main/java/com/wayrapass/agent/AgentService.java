package com.wayrapass.agent;

import com.wayrapass.agent.dto.ChatRequest;
import com.wayrapass.agent.dto.ChatResponse;
import com.wayrapass.agent.dto.RouteDetailSummary;
import com.wayrapass.agent.dto.RouteSummary;
import com.wayrapass.agent.dto.PunctualitySummary;
import com.wayrapass.agent.tools.WayriaTools;
import com.wayrapass.agent.tools.WayriaCoordinatorTools;
import com.wayrapass.exception.WayriaUnavailableException;
import com.wayrapass.model.Role;
import com.wayrapass.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.time.LocalDate;
import java.time.Month;
import java.math.BigDecimal;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AgentService {
    private static final Pattern ROUTE_CODE = Pattern.compile("\\b[A-Z0-9]+(?:-[A-Z0-9]+){2,}\\b");
    private static final Pattern DATE_RANGE = Pattern.compile("(?:del?\\s+)?(\\d{1,2})\\s+al\\s+(\\d{1,2})\\s+de\\s+([a-záéíóúñ]+)(?:\\s+de\\s+(\\d{4}))?");
    private static final Map<String, Month> SPANISH_MONTHS = Map.ofEntries(
            Map.entry("enero", Month.JANUARY), Map.entry("febrero", Month.FEBRUARY), Map.entry("marzo", Month.MARCH),
            Map.entry("abril", Month.APRIL), Map.entry("mayo", Month.MAY), Map.entry("junio", Month.JUNE),
            Map.entry("julio", Month.JULY), Map.entry("agosto", Month.AUGUST), Map.entry("septiembre", Month.SEPTEMBER),
            Map.entry("octubre", Month.OCTOBER), Map.entry("noviembre", Month.NOVEMBER), Map.entry("diciembre", Month.DECEMBER));
    @Qualifier("studentFamilyChatClient") private final ChatClient studentFamilyChatClient;
    @Qualifier("coordinatorChatClient") private final ChatClient coordinatorChatClient;
    private final CurrentUser currentUser;
    private final WayriaTools tools;
    private final WayriaCoordinatorTools coordinatorTools;
    private final com.wayrapass.service.PunctualityAnalyticsService analytics;
    private final ConcurrentHashMap<Long, Period> coordinatorPeriods = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, String> coordinatorRoutes = new ConcurrentHashMap<>();
    private final java.util.Set<Long> pendingCoordinatorRouteReports = ConcurrentHashMap.newKeySet();
    private final java.util.Set<Long> pendingCoordinatorRoutePunctuality = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<Long, String> routeContexts = new ConcurrentHashMap<>();

    public ChatResponse chat(ChatRequest request) {
        try {
            var user = currentUser.require();
            String message = request.message().trim();
            if (user.getRole() == Role.COORDINATOR) {
                ChatResponse coordinatorResponse = coordinatorResponse(message, user.getId());
                if (coordinatorResponse != null) return coordinatorResponse;
            }
            ChatResponse deterministic = criticalDataResponse(message, user.getRole(), user.getId());
            if (deterministic != null) return deterministic;

            String conversationId = "wayria:" + user.getId() + ":" + user.getRole();
            ChatClient chatClient = switch (user.getRole()) {
                case STUDENT, FAMILY -> studentFamilyChatClient;
                case COORDINATOR -> coordinatorChatClient;
                default -> throw new WayriaUnavailableException();
            };
            String content = chatClient.prompt()
                    .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, conversationId))
                    .user(message)
                    .call()
                    .content();
            if (content == null || content.isBlank()) {
                throw new WayriaUnavailableException();
            }
            return new ChatResponse(plainText(content), null, null, null);
        } catch (WayriaUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            org.slf4j.LoggerFactory.getLogger(AgentService.class).error("WayrIA ChatClient failure role={} exceptionType={} message={}",
                    currentUser.require().getRole(), exception.getClass().getSimpleName(), exception.getMessage(), exception);
            throw new WayriaUnavailableException(exception);
        }
    }

    private ChatResponse coordinatorResponse(String message, Long userId) {
        String normalized = message.toLowerCase(Locale.ROOT);
        Optional<String> code = routeCode(message);
        Optional<Period> explicitPeriod = explicitPeriod(normalized);
        boolean detail = code.isPresent() && (normalized.contains("más detalles") || normalized.contains("mas detalles")
                || (explicitPeriod.isPresent() && (normalized.contains("reporte") || normalized.contains("resúm") || normalized.contains("resum"))));
        boolean summaryIntent = asksForPunctualitySummary(normalized);
        boolean operationalReport = asksForOperationalReport(normalized);
        boolean routeAnalysis = normalized.contains("analizar") || normalized.contains("ruta") || normalized.contains("más detalles") || normalized.contains("mas detalles");
        boolean awaitingRouteReport = code.isPresent() && pendingCoordinatorRouteReports.remove(userId);
        boolean awaitingRoutePunctuality = code.isPresent() && pendingCoordinatorRoutePunctuality.remove(userId);
        if (awaitingRoutePunctuality) {
            coordinatorRoutes.put(userId, code.get());
            if (explicitPeriod.isEmpty()) {
                pendingCoordinatorRoutePunctuality.add(userId);
                logCoordinatorIntent("PUNCTUALITY_ROUTE missing=[period]");
                return new ChatResponse("¿De qué periodo deseas consultar la puntualidad de " + code.get() + "?", null, null, null);
            }
        }
        if (awaitingRouteReport) {
            coordinatorRoutes.put(userId, code.get());
            if (explicitPeriod.isEmpty()) {
                pendingCoordinatorRouteReports.add(userId);
                logCoordinatorIntent("ROUTE_REPORT missing=[period]");
                return new ChatResponse("¿De qué periodo deseas consultar " + code.get() + "?", null, null, null);
            }
        }
        if (routeAnalysis && code.isEmpty() && explicitPeriod.isEmpty()
                && (normalized.contains("quiero") || normalized.contains("reporte") || normalized.contains("analizar"))) {
            if (summaryIntent) {
                pendingCoordinatorRoutePunctuality.add(userId);
                logCoordinatorIntent("PUNCTUALITY_ROUTE missing=[route, period]");
                return new ChatResponse("¿Qué ruta deseas analizar?", null, null, null);
            }
            pendingCoordinatorRouteReports.add(userId);
            logCoordinatorIntent("ROUTE_REPORT missing=[route, period]");
            return new ChatResponse("Indícame la ruta y el periodo que deseas analizar.", null, null, null);
        }
        if (summaryIntent && code.isPresent() && explicitPeriod.isEmpty()) {
            coordinatorRoutes.put(userId, code.get());
            pendingCoordinatorRoutePunctuality.add(userId);
            logCoordinatorIntent("PUNCTUALITY_ROUTE missing=[period]");
            return new ChatResponse("¿De qué periodo deseas consultar la puntualidad de " + code.get() + "?", null, null, null);
        }
        if (code.isPresent() && routeAnalysis && explicitPeriod.isEmpty()) {
            coordinatorRoutes.put(userId, code.get());
            pendingCoordinatorRouteReports.add(userId);
            logCoordinatorIntent("ROUTE_REPORT missing=[period]");
            return new ChatResponse("¿De qué periodo deseas generar el reporte operativo de " + code.get() + "?", null, null, null);
        }
        if (operationalReport && !detail) {
            if (explicitPeriod.isEmpty()) {
                if (normalized.contains("puedes") || normalized.contains("puedo")) return null;
                logCoordinatorIntent("REPORT_GENERAL missing=[period]");
                return new ChatResponse("¿De qué periodo deseas generar el reporte operativo?", null, null, null);
            }
            Period period = effectiveOperationalPeriod(explicitPeriod.get()).orElse(null);
            if (period == null) return noOperationalData();
            logCoordinatorIntent("OPERATIONAL_REPORT");
            var report = coordinatorTools.getOperationalReportSummary(period.from(), period.to());
            if (!report.hasData()) return new ChatResponse(report.message(), null, null, null);
            coordinatorPeriods.put(userId, period);
            var p = report.punctuality(); var o = report.occupancy(); var i = report.incidents();
            String stops = report.stopDemand() == null || report.stopDemand().isEmpty() ? "No se registró demanda por paradero."
                    : "Demanda por paradero: " + report.stopDemand().stream().map(stop -> stop.stopName() + " " + stop.confirmedBoardings())
                    .reduce((left, right) -> left + ", " + right).orElse("") + ".";
            return new ChatResponse("Entre el " + period.from() + " y el " + period.to() + " se evaluaron "
                    + p.evaluatedTrips() + " viajes: " + p.onTimeTrips() + " puntuales y " + p.lateTrips()
                    + " retrasados (" + p.onTimePercentage() + "%). En ocupación hubo " + o.confirmedBoardings()
                    + " abordajes confirmados sobre " + o.offeredCapacity() + " plazas (" + o.occupancyPercentage()
                    + "%). Se registraron " + i.totalIncidents() + " incidencias. " + stops, null, null, null);
        }
        boolean pendingRouteReport = pendingCoordinatorRouteReports.contains(userId);
        boolean pendingRoutePunctuality = pendingCoordinatorRoutePunctuality.contains(userId);
        if (detail || (explicitPeriod.isPresent() && code.isEmpty() && coordinatorRoutes.containsKey(userId))
                || (code.isPresent() && explicitPeriod.isPresent() && (pendingRouteReport || awaitingRouteReport || pendingRoutePunctuality || awaitingRoutePunctuality))) {
            String routeCode = code.orElseGet(() -> coordinatorRoutes.get(userId));
            Period period = explicitPeriod.or(() -> Optional.ofNullable(coordinatorPeriods.get(userId))).orElse(null);
            if (period == null) {
                logCoordinatorIntent("PUNCTUALITY_DETAIL_MISSING_PERIOD");
                return new ChatResponse("¿De qué periodo deseas consultar la puntualidad de " + routeCode + "?", null, null, null);
            }
            if (pendingRoutePunctuality || awaitingRoutePunctuality) {
                period = effectiveOperationalPeriod(period).orElse(null);
                pendingCoordinatorRoutePunctuality.remove(userId);
                if (period == null) return insufficientData();
                logCoordinatorIntent("PUNCTUALITY_ROUTE route=" + routeCode + " period=" + period.from() + ".." + period.to());
                PunctualitySummary summary = coordinatorTools.getPunctualitySummary(period.from(), period.to());
                var route = summary.mostDelayedRoutes().stream().filter(item -> item.routeCode().equalsIgnoreCase(routeCode)).findFirst();
                if (route.isEmpty() || route.get().evaluatedTrips() == 0) return insufficientData();
                var item = route.get();
                return new ChatResponse("Puntualidad de " + item.routeCode() + " del " + period.from() + " al " + period.to()
                        + ": " + item.evaluatedTrips() + " viajes evaluados, " + (item.evaluatedTrips() - item.lateTrips())
                        + " puntuales, " + item.lateTrips() + " con retraso y "
                        + BigDecimal.valueOf(100).subtract(item.latePercentage()) + "% de puntualidad.", null, null, summary);
            }
            if (normalized.contains("reporte") || normalized.contains("operativo") || pendingRouteReport || awaitingRouteReport) {
                period = effectiveOperationalPeriod(period).orElse(null);
                if (period == null) {
                    pendingCoordinatorRouteReports.remove(userId);
                    return noOperationalData();
                }
                pendingCoordinatorRouteReports.remove(userId);
                var report = coordinatorTools.getRouteOperationalReport(routeCode, period.from(), period.to());
                if (!report.hasData()) return new ChatResponse(report.message(), null, null, null);
                var p = report.punctuality(); var o = report.occupancy(); var i = report.incidents();
                String stops = report.stopDemand().isEmpty() ? "No se registró demanda por paradero."
                        : "Demanda por paradero: " + report.stopDemand().stream().map(stop -> stop.stopName() + " " + stop.confirmedBoardings())
                        .reduce((left, right) -> left + ", " + right).orElse("") + ".";
                return new ChatResponse("Reporte operativo de " + routeCode + " del " + period.from() + " al " + period.to()
                        + ": " + p.evaluatedTrips() + " viajes evaluados, " + p.onTimeTrips() + " puntuales, "
                        + p.lateTrips() + " con retraso y " + p.onTimePercentage() + "% de puntualidad. Ocupación: "
                        + o.confirmedBoardings() + " abordajes confirmados sobre " + o.offeredCapacity() + " plazas ("
                        + o.occupancyPercentage() + "%). Incidencias: " + i.totalIncidents() + ". " + stops, null, null, null);
            }
            logCoordinatorIntent("PUNCTUALITY_DETAIL");
            PunctualitySummary summary = coordinatorTools.getPunctualitySummary(period.from(), period.to());
            if (summary.evaluatedTrips() == 0) return insufficientData();
            var route = summary.mostDelayedRoutes().stream().filter(item -> item.routeCode().equalsIgnoreCase(routeCode)).findFirst();
            if (route.isEmpty()) return new ChatResponse("No encontré retrasos evaluables para la ruta " + routeCode + " en este periodo.", null, null, summary);
            var item = route.get();
            return new ChatResponse("Detalle de " + item.routeCode() + ": " + item.evaluatedTrips()
                    + " viajes evaluados, " + item.lateTrips() + " con retraso (" + item.latePercentage()
                    + "%) y " + item.averageDelayMinutes() + " minutos de demora promedio.", null, null, summary);
        }
        if (summaryIntent) {
            if (explicitPeriod.isEmpty()) {
                if (normalized.contains("puedes") || normalized.contains("puedo")) return null;
                logCoordinatorIntent("PUNCTUALITY_SUMMARY missing=[period]");
                return new ChatResponse("¿De qué periodo deseas consultar la puntualidad?", null, null, null);
            }
            Period period = explicitPeriod.get();
            logCoordinatorIntent("PUNCTUALITY_SUMMARY");
            PunctualitySummary summary = coordinatorTools.getPunctualitySummary(period.from(), period.to());
            coordinatorPeriods.put(userId, period);
            if (summary.evaluatedTrips() == 0) return insufficientData();
            String routes = summary.lateTrips() == 0 ? "No se registraron viajes con retraso."
                    : summary.mostDelayedRoutes().isEmpty() ? "No se registraron rutas con retrasos."
                    : "Ruta con más retrasos: " + summary.mostDelayedRoutes().getFirst().routeCode() + ".";
            return new ChatResponse("Periodo " + summary.from() + " a " + summary.to() + ": "
                    + summary.evaluatedTrips() + " viajes evaluados, " + summary.onTimeTrips() + " puntuales, "
                    + summary.lateTrips() + " con retraso y " + summary.onTimePercentage() + "% de puntualidad. " + routes,
                    null, null, summary);
        }
        logCoordinatorIntent("GENERAL");
        return null;
    }

    private boolean asksForPunctualitySummary(String normalized) {
        return normalized.contains("puntualidad") || normalized.contains("más retrasos")
                || normalized.contains("mas retrasos") || normalized.contains("retrasos esta semana");
    }

    private boolean asksForOperationalReport(String normalized) {
        return normalized.contains("reporte") || normalized.contains("operación") || normalized.contains("operacion")
                || (normalized.contains("resumen") && normalized.contains("servicio"));
    }

    private Optional<Period> explicitPeriod(String normalized) {
        if (normalized.contains("semana pasada")) {
            LocalDate start = analytics.currentWeekStart().minusWeeks(1);
            return Optional.of(new Period(start, start.plusDays(6)));
        }
        if (normalized.contains("esta semana")) {
            LocalDate start = analytics.currentWeekStart();
            return Optional.of(new Period(start, start.plusDays(6)));
        }
        Matcher matcher = DATE_RANGE.matcher(normalized);
        if (matcher.find()) {
            Month month = SPANISH_MONTHS.get(matcher.group(3));
            if (month != null) {
                try {
                    int year = matcher.group(4) == null ? analytics.currentWeekStart().getYear() : Integer.parseInt(matcher.group(4));
                    Period period = new Period(LocalDate.of(year, month, Integer.parseInt(matcher.group(1))),
                            LocalDate.of(year, month, Integer.parseInt(matcher.group(2))));
                    if (!period.from().isAfter(period.to())) {
                        org.slf4j.LoggerFactory.getLogger(AgentService.class).info("WayrIA report period from={} to={}", period.from(), period.to());
                        return Optional.of(period);
                    }
                } catch (java.time.DateTimeException ignored) { }
            }
        }
        return Optional.empty();
    }

    private void logCoordinatorIntent(String intent) {
        org.slf4j.LoggerFactory.getLogger(AgentService.class).info("WayrIA coordinator intent={}", intent);
    }

    private Period currentWeek() {
        LocalDate start = analytics.currentWeekStart();
        return new Period(start, start.plusDays(6));
    }

    private ChatResponse insufficientData() {
        return new ChatResponse("No hay información suficiente para generar un resumen de puntualidad en este periodo", null, null, null);
    }

    private Optional<Period> effectiveOperationalPeriod(Period requested) {
        LocalDate today = analytics.today();
        if (requested.from().isAfter(today)) return Optional.empty();
        return Optional.of(new Period(requested.from(), requested.to().isAfter(today) ? today : requested.to()));
    }

    private ChatResponse noOperationalData() {
        return new ChatResponse("No existen datos disponibles para el periodo seleccionado", null, null, null);
    }

    /** Keeps conversational output readable by the plain-text frontend without touching deterministic Tool data. */
    private String plainText(String content) {
        return content.replaceAll("(?m)^#{1,6}\\s*", "")
                .replace("**", "")
                .replace("__", "")
                .replaceAll("(?m)^---+$", "")
                .replaceAll("[📊✅❌🔹✨]", "")
                .replaceAll("(?m)^[ \\t]+", "")
                .trim();
    }

    /**
     * Schedules, stops, route codes and live status are critical values.  For these explicit
     * intents we render the Tool result directly instead of allowing the model to summarize it.
     */
    private ChatResponse criticalDataResponse(String message, Role role, Long userId) {
        String normalized = message.toLowerCase(Locale.ROOT);
        if (role == Role.STUDENT && asksForMyAvailableRoutes(normalized)) {
            List<RouteSummary> routes = tools.getMyAvailableRoutes();
            String text = routes.isEmpty() ? "No tienes rutas disponibles para tu institución."
                    : "Tienes " + routes.size() + " rutas disponibles:\n" + routes.stream()
                    .map(route -> route.code() + " — " + route.name() + ".")
                    .reduce((left, right) -> left + "\n" + right).orElse("");
            return new ChatResponse(text, routes.isEmpty() ? null : routes, null);
        }
        Optional<String> code = routeCode(message);
        if (code.isEmpty() && message.toLowerCase(Locale.ROOT).contains("esa ruta")) {
            code = Optional.ofNullable(routeContexts.get(userId));
        }
        if (code.isPresent() && (asksForSchedules(normalized) || asksForStops(normalized))) {
            List<RouteDetailSummary> details = tools.getRouteDetails(code.get());
            routeContexts.put(userId, code.get());
            return detailResponse(details, asksForSchedules(normalized), asksForStops(normalized), code.get());
        }
        if (code.isPresent() && (normalized.contains("dame detalles") || normalized.contains("detalles de"))) {
            List<RouteDetailSummary> details = tools.getRouteDetails(code.get());
            routeContexts.put(userId, code.get());
            return fullRouteDetailResponse(details, code.get());
        }
        if (role == Role.STUDENT && asksForMyRouteSchedule(normalized)) {
            List<RouteDetailSummary> details = tools.getMyRouteDetails();
            return detailResponse(details, true, false, null);
        }
        return null;
    }

    private ChatResponse fullRouteDetailResponse(List<RouteDetailSummary> details, String code) {
        if (details.isEmpty()) return new ChatResponse("No encontré información para la ruta " + code + ".", null, null);
        RouteDetailSummary detail = details.getFirst();
        return new ChatResponse("La ruta " + detail.code() + " va de " + detail.origin() + " a " + detail.destination()
                + ". Sus paraderos son: " + String.join(", ", detail.stops()) + ". Sus horarios son: "
                + detail.schedules().stream().map(this::formatScheduleInSpanish).reduce((left, right) -> left + ", " + right).orElse("") + ".",
                List.of(new RouteSummary(null, detail.code(), detail.name(), detail.origin(), detail.destination(), detail.institution(), detail.campus())), null);
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

    private record Period(LocalDate from, LocalDate to) {}
}
