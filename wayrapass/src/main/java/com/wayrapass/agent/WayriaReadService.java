package com.wayrapass.agent;

import com.wayrapass.agent.dto.RouteDetailSummary;
import com.wayrapass.agent.dto.RouteSummary;
import com.wayrapass.agent.dto.ServiceStatusSummary;
import com.wayrapass.agent.dto.NextTripSummary;
import com.wayrapass.agent.dto.CurrentTripSummary;
import com.wayrapass.dto.response.TripQueryResponses;
import com.wayrapass.model.Role;
import com.wayrapass.model.Route;
import com.wayrapass.model.RouteStatus;
import com.wayrapass.repository.RouteRepository;
import com.wayrapass.repository.RouteScheduleRepository;
import com.wayrapass.repository.RouteStopRepository;
import com.wayrapass.repository.TripRepository;
import com.wayrapass.security.CurrentUser;
import com.wayrapass.service.StudentAccessService;
import com.wayrapass.service.EnrollmentLifecycleService;
import com.wayrapass.service.TripQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.time.Clock;
import java.time.Instant;

/** Read-only projection layer used by WayrIA tools. It never exposes JPA entities to the model. */
@Service
@RequiredArgsConstructor
public class WayriaReadService {
    private static final int MAX_ROUTE_RESULTS = 5;

    private final RouteRepository routes;
    private final RouteStopRepository routeStops;
    private final RouteScheduleRepository schedules;
    private final CurrentUser currentUser;
    private final StudentAccessService studentAccess;
    private final EnrollmentLifecycleService enrollmentLifecycle;
    private final TripQueryService tripQueries;
    private final TripRepository trips;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<RouteSummary> findRoutes(String reference) {
        String query = normalize(reference);
        if (query.isBlank()) return List.of();
        return routes.findAll().stream()
                .filter(route -> route.getStatus() == RouteStatus.ACTIVE && route.getInstitution().isActive())
                .filter(route -> matches(route, query))
                .sorted(Comparator.comparing(Route::getCode, String.CASE_INSENSITIVE_ORDER))
                .limit(MAX_ROUTE_RESULTS)
                .map(this::summary)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RouteDetailSummary> routeDetails(String reference) {
        String query = normalize(reference);
        if (query.isBlank()) return List.of();
        return routes.findAll().stream()
                .filter(route -> route.getStatus() == RouteStatus.ACTIVE && route.getInstitution().isActive())
                .filter(route -> matches(route, query))
                .sorted(Comparator.comparing(Route::getCode, String.CASE_INSENSITIVE_ORDER))
                .limit(MAX_ROUTE_RESULTS)
                .map(route -> new RouteDetailSummary(
                        route.getCode(), route.getName(), route.getOrigin(), route.getDestination(),
                        route.getInstitution().getName(), route.getInstitution().getCampusName(),
                        routeStops.findAvailableByRouteIdOrderByStopOrder(route.getId()).stream()
                                .map(item -> item.getStop().getName()).toList(),
                        schedules.findByRouteId(route.getId()).stream().filter(schedule -> schedule.isActive())
                                .sorted(Comparator.comparing((com.wayrapass.model.RouteSchedule schedule) -> schedule.getDayOfWeek())
                                        .thenComparing(com.wayrapass.model.RouteSchedule::getDepartureTime))
                                .map(schedule -> schedule.getDayOfWeek() + " " + schedule.getDepartureTime()).toList()
                )).toList();
    }

    @Transactional(readOnly = true)
    public List<RouteSummary> myAvailableRoutes() {
        return studentAccess.searchRoutes(null, null, null).stream()
                .collect(java.util.stream.Collectors.toMap(
                        route -> route.routeId(),
                        route -> new RouteSummary(route.routeId(), route.routeCode(), route.routeName(),
                                route.origin(), route.destination(), route.institutionName(), route.campusName()),
                        (first, ignored) -> first,
                        LinkedHashMap::new))
                .values().stream().limit(MAX_ROUTE_RESULTS).toList();
    }

    @Transactional(readOnly = true)
    public List<RouteDetailSummary> myRouteDetails() {
        var student = studentAccess.requireCurrentStudent();
        return enrollmentLifecycle.currentForUse(student.getId()).map(enrollment -> routeDetails(enrollment.getRoute().getCode()))
                .orElseGet(List::of);
    }

    /** Uses the same StudentAccessService search as "Buscar rutas" before reading a future trip. */
    @Transactional(readOnly = true)
    public NextTripSummary nextTripForMyAvailableRoute(String routeCode) {
        if (routeCode == null || routeCode.isBlank()) return null;
        String requestedCode = routeCode.trim().toUpperCase(Locale.ROOT);
        var route = studentAccess.searchRoutes(null, null, null).stream()
                .filter(option -> requestedCode.equalsIgnoreCase(option.routeCode()))
                .findFirst();
        if (route.isEmpty()) return null;
        var option = route.get();
        Instant now = clock.instant();
        return trips.findUpcomingByRouteAndStatus(option.routeId(), com.wayrapass.model.TripStatus.SCHEDULED, now).stream()
                .filter(trip -> trip.getScheduledStartAt().isAfter(now))
                .findFirst()
                .map(trip -> new NextTripSummary(option.routeCode(), option.routeName(), option.origin(),
                        option.destination(), trip.getScheduledStartAt()))
                .orElse(null);
    }

    /** Projects only the authenticated student's active trip from TripQueryService, never a caller-provided ID. */
    public CurrentTripSummary myActiveTrip() {
        TripQueryResponses.CurrentTripResponse response = tripQueries.myCurrentTrip();
        if (!response.available() || response.trip() == null
                || (response.trip().tripStatus() != com.wayrapass.model.TripStatus.BOARDING
                && response.trip().tripStatus() != com.wayrapass.model.TripStatus.IN_PROGRESS)) {
            return new CurrentTripSummary(false, "Actualmente no tienes un viaje en curso.", null, null,
                    null, null, null, null, null, false, null, null, null, null, List.of());
        }
        var trip = response.trip();
        return new CurrentTripSummary(true, response.message(), trip.routeCode(), trip.routeName(),
                trip.routeOrigin(), trip.routeDestination(), trip.tripStatus(), trip.scheduledStartAt(),
                trip.actualStartAt(), trip.boarding().confirmed(), trip.boarding().boardedAt(),
                trip.assignedStop() == null ? null : trip.assignedStop().name(),
                trip.stopArrival() == null ? null : trip.stopArrival().estimatedArrivalAt(),
                trip.universityArrival() == null ? null : trip.universityArrival().estimatedArrivalAt(),
                trip.incidents().stream().map(incident -> new CurrentTripSummary.Incident(
                        incident.description(), incident.delayMinutes())).toList());
    }

    @Transactional(readOnly = true)
    public ServiceStatusSummary currentServiceStatus(String studentReference) {
        Role role = currentUser.require().getRole();
        TripQueryResponses.CurrentTripResponse response;
        String studentName;
        if (role == Role.STUDENT) {
            studentName = currentUser.require().getFullName();
            response = tripQueries.myCurrentTrip();
        } else if (role == Role.FAMILY) {
            var allowed = studentAccess.familyStudentSummaries();
            var matches = allowed.stream().filter(student -> studentReference == null || studentReference.isBlank()
                    ? allowed.size() == 1 : normalize(student.fullName()).contains(normalize(studentReference)))
                    .toList();
            if (matches.size() != 1) {
                String names = allowed.stream().map(student -> student.fullName()).limit(MAX_ROUTE_RESULTS)
                        .reduce((left, right) -> left + " o " + right).orElse("ningún estudiante vinculado");
                return new ServiceStatusSummary(null, null, null, null, null, null, null, null, null,
                        allowed.isEmpty() ? "No tienes estudiantes vinculados autorizados."
                                : "Necesito que aclares el estudiante: " + names + ".");
            }
            var student = matches.getFirst();
            studentName = student.fullName();
            response = tripQueries.familyCurrentTrip(student.studentId());
        } else {
            throw new AccessDeniedException("Solo STUDENT o FAMILY pueden consultar WayrIA.");
        }
        if (!response.available() || response.trip() == null) {
            return new ServiceStatusSummary(studentName, null, null, null, null, null, null, null, null,
                    response.message());
        }
        var trip = response.trip();
        var eta = trip.universityArrival();
        return new ServiceStatusSummary(studentName, trip.routeCode(), trip.routeName(), trip.tripStatus().name(),
                trip.scheduledStartAt(), trip.scheduledEndAt(),
                eta == null ? null : eta.estimatedArrivalAt(), eta == null ? null : eta.delayMinutes(),
                trip.assignedStop() == null ? null : trip.assignedStop().name(), response.message());
    }

    private RouteSummary summary(Route route) {
        return new RouteSummary(route.getId(), route.getCode(), route.getName(), route.getOrigin(), route.getDestination(),
                route.getInstitution().getName(), route.getInstitution().getCampusName());
    }

    private boolean matches(Route route, String query) {
        return normalize(route.getCode()).contains(query) || normalize(route.getName()).contains(query)
                || normalize(route.getOrigin()).contains(query) || normalize(route.getDestination()).contains(query)
                || normalize(route.getInstitution().getName()).contains(query)
                || normalize(route.getInstitution().getCampusName()).contains(query);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
