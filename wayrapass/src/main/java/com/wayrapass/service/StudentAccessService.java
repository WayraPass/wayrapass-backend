package com.wayrapass.service;

import com.wayrapass.dto.request.OperationalRequests;
import com.wayrapass.dto.response.OperationalResponses;
import com.wayrapass.exception.BusinessRuleException;
import com.wayrapass.exception.ConflictException;
import com.wayrapass.exception.ResourceNotFoundException;
import com.wayrapass.model.*;
import com.wayrapass.repository.*;
import com.wayrapass.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StudentAccessService {
    private final CurrentUser current;
    private final StudentRepository students;
    private final StudentFamilyLinkRepository links;
    private final RouteRepository routes;
    private final StopRepository stops;
    private final RouteStopRepository routeStops;
    private final RouteScheduleRepository schedules;
    private final StudentRouteEnrollmentRepository enrollments;
    private final FavoriteRouteRepository favorites;
    private final EnrollmentLifecycleService lifecycle;

    @Transactional
    public OperationalResponses.FamilyCode generateCode() {
        Student student = requireCurrentStudent();
        String code = "%06d".formatted(new SecureRandom().nextInt(1_000_000));
        student.setFamilyLinkCodeHash(hash(code));
        student.setFamilyLinkCodeExpiresAt(Instant.now().plusSeconds(600));
        return new OperationalResponses.FamilyCode(code, student.getFamilyLinkCodeExpiresAt());
    }

    @Transactional
    public void linkFamily(Long studentId, OperationalRequests.FamilyCode request) {
        User family = current.require();
        if (family.getRole() != Role.FAMILY) throw new AccessDeniedException("Solo FAMILY");
        Student student = students.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Estudiante no encontrado."));
        if (student.getFamilyLinkCodeHash() == null || student.getFamilyLinkCodeExpiresAt() == null
                || !student.getFamilyLinkCodeExpiresAt().isAfter(Instant.now())
                || !MessageDigest.isEqual(student.getFamilyLinkCodeHash().getBytes(StandardCharsets.UTF_8),
                hash(request.code()).getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessRuleException("El código de vinculación no es válido. Solicita uno nuevo.");
        }
        StudentFamilyLink link = links.findByStudentIdAndFamilyUserId(studentId, family.getId())
                .orElseGet(StudentFamilyLink::new);
        link.setStudent(student);
        link.setFamilyUser(family);
        link.setRelationship(request.relationship());
        link.setAuthorized(true);
        link.setRevokedAt(null);
        links.save(link);
        student.setFamilyLinkCodeHash(null);
        student.setFamilyLinkCodeExpiresAt(null);
    }

    @Transactional
    public OperationalResponses.Enrollment enroll(OperationalRequests.Enrollment request) {
        Student student = requireCurrentStudent();
        validateLocation(request.latitude(), request.longitude());
        Route route = routes.findById(request.routeId())
                .orElseThrow(() -> new ResourceNotFoundException("Ruta no encontrada."));
        if (route.getStatus() != RouteStatus.ACTIVE || !route.getInstitution().isActive()
                || !route.getInstitution().getId().equals(student.getInstitution().getId())) {
            throw new BusinessRuleException("La ruta seleccionada no está disponible para tu institución.");
        }
        Stop stop = stops.findById(request.stopId())
                .orElseThrow(() -> new ResourceNotFoundException("Paradero no encontrado."));
        RouteStop relation = routeStops.findByRouteIdAndStopId(request.routeId(), request.stopId())
                .orElseThrow(() -> new BusinessRuleException("El paradero no pertenece a la ruta."));
        if (!relation.isActive() || !stop.isActive()) {
            throw new BusinessRuleException(unavailableStopMessage(route.getId(), request.latitude(),
                    request.longitude(), request.time()));
        }
        if (hasLocation(request.latitude(), request.longitude()) && !hasLocation(stop)) {
            throw new BusinessRuleException("No existen paraderos disponibles para la ubicación seleccionada.");
        }

        Optional<StudentRouteEnrollment> currentEnrollment = enrollments.findCurrentForUpdate(student.getId(),
                List.of(EnrollmentStatus.ACTIVE, EnrollmentStatus.SUSPENDED));
        if (currentEnrollment.filter(enrollment -> enrollment.getRoute().getId().equals(route.getId())
                && enrollment.getStop().getId().equals(stop.getId())).isPresent()) {
            throw new BusinessRuleException("La ruta y el paradero seleccionados ya corresponden a la inscripción actual.");
        }
        boolean alreadyOccupiesTargetRoute = currentEnrollment
                .filter(enrollment -> enrollment.getStatus() == EnrollmentStatus.ACTIVE)
                .map(enrollment -> enrollment.getRoute().getId().equals(route.getId()))
                .orElse(false);
        Route lockedRoute = lifecycle.lockRouteAndRequireSeat(route.getId(), alreadyOccupiesTargetRoute);
        if (lockedRoute.getStatus() != RouteStatus.ACTIVE || !lockedRoute.getInstitution().isActive()
                || !lockedRoute.getInstitution().getId().equals(student.getInstitution().getId())) {
            throw new BusinessRuleException("La ruta seleccionada no está disponible para tu institución.");
        }
        currentEnrollment.ifPresent(enrollment -> {
            enrollment.setStatus(EnrollmentStatus.ENDED);
            enrollment.setValidUntil(lifecycle.today());
        });
        StudentRouteEnrollment enrollment = new StudentRouteEnrollment();
        enrollment.setStudent(student);
        enrollment.setRoute(lockedRoute);
        enrollment.setStop(stop);
        StudentRouteEnrollment saved = enrollments.save(enrollment);
        return enrollmentOut(saved, estimatedPickupTime(route.getId(), relation, request.time()));
    }

    @Transactional
    public void favorite(Long routeId) {
        Student student = requireCurrentStudent();
        Route route = routes.findById(routeId)
                .orElseThrow(() -> new ResourceNotFoundException("Ruta no encontrada."));
        if (!route.getInstitution().getId().equals(student.getInstitution().getId()))
            throw new BusinessRuleException("La ruta pertenece a una institución distinta a la del estudiante.");
        if (route.getStatus() != RouteStatus.ACTIVE || !route.getInstitution().isActive())
            throw new BusinessRuleException("No se puede guardar una ruta que no está disponible.");
        FavoriteRouteId id = new FavoriteRouteId(student.getId(), routeId);
        if (favorites.existsById(id)) throw new ConflictException("La ruta ya está guardada como favorita.");
        FavoriteRoute favorite = new FavoriteRoute();
        favorite.setStudent(student);
        favorite.setRoute(route);
        favorites.save(favorite);
    }

    @Transactional
    public void unfavorite(Long routeId) {
        favorites.deleteById(new FavoriteRouteId(requireCurrentStudent().getId(), routeId));
    }

    @Transactional(readOnly = true)
    public List<OperationalResponses.FavoriteRoute> favorites() {
        return favorites.findByStudentId(requireCurrentStudent().getId()).stream().map(favorite -> {
            Route route = favorite.getRoute();
            boolean available = route.getStatus() == RouteStatus.ACTIVE && route.getInstitution().isActive()
                    && routeStops.existsByRouteIdAndActiveTrueAndStopActiveTrue(route.getId())
                    && schedules.existsByRouteIdAndActiveTrue(route.getId());
            return new OperationalResponses.FavoriteRoute(route.getId(), route.getCode(), route.getName(),
                    route.getStatus(), available);
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<OperationalResponses.RouteSearchOption> searchRoutes(Long institutionId, String district,
                                                                      LocalTime time) {
        return searchRoutes(institutionId, null, district, time, null, null);
    }

    @Transactional(readOnly = true)
    public List<OperationalResponses.RouteSearchOption> searchRoutes(Long institutionId, Long routeId,
                                                                      String district, LocalTime time,
                                                                      BigDecimal latitude,
                                                                      BigDecimal longitude) {
        Student student = requireCurrentStudent();
        validateLocation(latitude, longitude);
        if (institutionId != null && institutionId <= 0)
            throw new BusinessRuleException("institutionId debe ser positivo.");
        if (routeId != null && routeId <= 0)
            throw new BusinessRuleException("routeId debe ser positivo.");
        if (district != null && district.isBlank())
            throw new BusinessRuleException("district no puede estar vacío.");
        Long ownInstitutionId = student.getInstitution().getId();
        if (institutionId != null && !institutionId.equals(ownInstitutionId)) return List.of();

        String normalizedDistrict = district == null ? null : district.trim();
        List<RouteSearchProjection> projections = routeId == null
                ? routes.searchAvailableOptions(ownInstitutionId, normalizedDistrict)
                : routes.searchAvailableOptions(ownInstitutionId, normalizedDistrict, routeId);
        if (projections.isEmpty()) return List.of();

        boolean locationProvided = hasLocation(latitude, longitude);
        Long currentStopId = currentStopToExclude(student.getId(), routeId);
        List<SearchCandidate> candidates = projections.stream()
                .filter(projection -> currentStopId == null || !currentStopId.equals(projection.getStopId()))
                .filter(projection -> !locationProvided || hasLocation(projection))
                .map(projection -> searchCandidate(projection, time, latitude, longitude))
                .toList();
        if (candidates.isEmpty()) return List.of();

        if (time != null) {
            int bestDifference = candidates.stream().mapToInt(SearchCandidate::differenceMinutes).min().orElse(0);
            candidates = candidates.stream()
                    .filter(candidate -> candidate.differenceMinutes() == bestDifference).toList();
        }
        if (locationProvided) {
            candidates = candidates.stream().sorted(Comparator
                    .comparing(SearchCandidate::distanceMeters)
                    .thenComparingInt(candidate -> candidate.projection().getStopOrder())
                    .thenComparing(candidate -> candidate.projection().getStopId())
                    .thenComparing(candidate -> candidate.projection().getRouteId())
                    .thenComparing(candidate -> candidate.projection().getDayOfWeek())
                    .thenComparing(candidate -> candidate.projection().getDepartureTime())).toList();
        }

        Set<Long> favoriteIds = favorites.findByStudentId(student.getId()).stream()
                .map(favorite -> favorite.getRoute().getId()).collect(Collectors.toSet());
        return candidates.stream().map(candidate -> searchOut(candidate,
                favoriteIds.contains(candidate.projection().getRouteId()), time)).toList();
    }

    @Transactional(readOnly = true)
    public List<Long> familyStudents() {
        User user = current.require();
        if (user.getRole() != Role.FAMILY) throw new AccessDeniedException("Solo FAMILY");
        return links.findByFamilyUserIdAndAuthorizedTrue(user.getId()).stream()
                .map(link -> link.getStudent().getId()).toList();
    }

    @Transactional(readOnly = true)
    public OperationalResponses.NotificationPreferences notificationPreferences(Long studentId) {
        return preferencesOut(requireFamilyLink(studentId));
    }

    @Transactional
    public OperationalResponses.NotificationPreferences updateNotificationPreferences(
            Long studentId, OperationalRequests.NotificationPreferences request) {
        StudentFamilyLink link = requireFamilyLink(studentId);
        link.setNotifyBoarding(request.notifyBoarding());
        link.setNotifyArrival(request.notifyArrival());
        link.setNotifyDelay(request.notifyDelay());
        link.setNotifyVehicleChange(request.notifyVehicleChange());
        link.setNotifyIncident(request.notifyIncident());
        return preferencesOut(links.save(link));
    }

    public void requireStudentAccess(Long studentId) {
        User user = current.require();
        if (user.getRole() == Role.COORDINATOR) return;
        if (user.getRole() == Role.STUDENT && students.findByUserId(user.getId())
                .map(Student::getId).filter(studentId::equals).isPresent()) return;
        if (user.getRole() == Role.FAMILY
                && links.existsByStudentIdAndFamilyUserIdAndAuthorizedTrue(studentId, user.getId())) return;
        throw new AccessDeniedException("Sin acceso al estudiante.");
    }

    @Transactional(readOnly = true)
    public void requireTripAccess(Trip trip) {
        User user = current.require();
        if (user.getRole() == Role.COORDINATOR) return;
        if (user.getRole() == Role.DRIVER && trip.getDriver() != null
                && trip.getDriver().getUser().getId().equals(user.getId())) return;
        List<EnrollmentStatus> visibleStatuses = List.of(EnrollmentStatus.ACTIVE, EnrollmentStatus.SUSPENDED);
        if (user.getRole() == Role.STUDENT) {
            Student student = students.findByUserId(user.getId())
                    .orElseThrow(() -> new AccessDeniedException("Sin acceso al viaje."));
            if (enrollments.existsByStudentIdAndRouteIdAndStatusIn(student.getId(), trip.getRoute().getId(),
                    visibleStatuses)) return;
        }
        if (user.getRole() == Role.FAMILY) {
            boolean allowed = links.findByFamilyUserIdAndAuthorizedTrue(user.getId()).stream()
                    .anyMatch(link -> enrollments.existsByStudentIdAndRouteIdAndStatusIn(
                            link.getStudent().getId(), trip.getRoute().getId(), visibleStatuses));
            if (allowed) return;
        }
        throw new AccessDeniedException("Sin acceso al viaje.");
    }

    @Transactional(readOnly = true)
    public Student requireCurrentStudent() {
        User user = current.require();
        if (user.getRole() != Role.STUDENT) throw new AccessDeniedException("Solo STUDENT");
        return students.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Perfil estudiantil no encontrado."));
    }

    @Transactional(readOnly = true)
    public Student requireFamilyStudent(Long studentId) {
        User user = current.require();
        if (user.getRole() != Role.FAMILY) throw new AccessDeniedException("Solo FAMILY");
        Student student = students.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Estudiante no encontrado."));
        if (!links.existsByStudentIdAndFamilyUserIdAndAuthorizedTrue(studentId, user.getId()))
            throw new AccessDeniedException("Sin acceso al estudiante.");
        return student;
    }

    private StudentFamilyLink requireFamilyLink(Long studentId) {
        User user = current.require();
        if (user.getRole() != Role.FAMILY) throw new AccessDeniedException("Solo FAMILY");
        StudentFamilyLink link = links.findByStudentIdAndFamilyUserId(studentId, user.getId())
                .orElseThrow(() -> new AccessDeniedException("Sin acceso al estudiante."));
        if (!link.isAuthorized() || link.getRevokedAt() != null)
            throw new AccessDeniedException("Sin acceso al estudiante.");
        return link;
    }

    private OperationalResponses.NotificationPreferences preferencesOut(StudentFamilyLink link) {
        return new OperationalResponses.NotificationPreferences(link.getId(), link.getStudent().getId(),
                link.isNotifyBoarding(), link.isNotifyArrival(), link.isNotifyDelay(),
                link.isNotifyVehicleChange(), link.isNotifyIncident());
    }

    private String unavailableStopMessage(Long routeId, BigDecimal latitude, BigDecimal longitude,
                                          LocalTime requestedTime) {
        List<RouteStop> available = routeStops.findAvailableByRouteIdOrderByStopOrder(routeId);
        boolean locationProvided = hasLocation(latitude, longitude);
        if (locationProvided) {
            available = available.stream().filter(routeStop -> hasLocation(routeStop.getStop()))
                    .sorted(Comparator
                            .comparingLong((RouteStop routeStop) -> distanceMeters(latitude, longitude,
                                    routeStop.getStop()))
                            .thenComparingInt(RouteStop::getStopOrder)
                            .thenComparing(routeStop -> routeStop.getStop().getId())).toList();
        }
        if (available.isEmpty()) {
            return locationProvided
                    ? "No existen paraderos disponibles para la ubicación seleccionada."
                    : "El paradero seleccionado está temporalmente fuera de servicio y la ruta no tiene paraderos habilitados.";
        }
        String alternatives = available.stream().map(routeStop -> {
            String distance = locationProvided
                    ? ", " + distanceMeters(latitude, longitude, routeStop.getStop()) + " m" : "";
            LocalTime pickupTime = estimatedPickupTime(routeId, routeStop, requestedTime);
            String pickup = pickupTime == null ? "" : ", salida aproximada " + pickupTime;
            return routeStop.getStop().getId() + " - " + routeStop.getStop().getName()
                    + " (" + (distance + pickup).replaceFirst("^, ", "") + ")";
        }).collect(Collectors.joining(", "));
        return "El paradero seleccionado está temporalmente fuera de servicio. Paraderos habilitados: "
                + alternatives + ".";
    }

    private SearchCandidate searchCandidate(RouteSearchProjection projection, LocalTime requestedTime,
                                            BigDecimal latitude, BigDecimal longitude) {
        LocalTime pickup = projection.getDepartureTime().plusMinutes(projection.getEstimatedMinutesFromStart());
        int difference = requestedTime == null ? 0 : minuteDifference(pickup, requestedTime);
        Long distance = hasLocation(latitude, longitude)
                ? GeographicDistanceCalculator.distanceMeters(latitude, longitude,
                projection.getStopLatitude(), projection.getStopLongitude()) : null;
        return new SearchCandidate(projection, pickup, difference, distance);
    }

    private OperationalResponses.RouteSearchOption searchOut(SearchCandidate candidate, boolean favorite,
                                                               LocalTime requestedTime) {
        RouteSearchProjection projection = candidate.projection();
        return new OperationalResponses.RouteSearchOption(projection.getRouteId(), projection.getRouteCode(),
                projection.getRouteName(), projection.getOrigin(), projection.getDestination(),
                projection.getInstitutionId(), projection.getInstitutionName(), projection.getCampusName(),
                projection.getStopId(), projection.getStopName(), projection.getStopAddress(),
                projection.getStopDistrict(), projection.getStopLatitude(), projection.getStopLongitude(),
                candidate.distanceMeters(), projection.getStopOrder(), projection.getDayOfWeek(),
                projection.getDepartureTime(), candidate.estimatedPickupTime(),
                projection.getEstimatedArrivalTime(), requestedTime != null && candidate.differenceMinutes() == 0,
                candidate.differenceMinutes(), favorite);
    }

    private Long currentStopToExclude(Long studentId, Long routeId) {
        if (routeId == null) return null;
        return enrollments.findByStudentIdAndStatusIn(studentId,
                        List.of(EnrollmentStatus.ACTIVE, EnrollmentStatus.SUSPENDED))
                .filter(enrollment -> routeId.equals(enrollment.getRoute().getId()))
                .map(enrollment -> enrollment.getStop().getId()).orElse(null);
    }

    private LocalTime estimatedPickupTime(Long routeId, RouteStop routeStop, LocalTime requestedTime) {
        return schedules.findByRouteId(routeId).stream().filter(RouteSchedule::isActive)
                .map(schedule -> new PickupOption(schedule.getDayOfWeek(), schedule.getDepartureTime(),
                        schedule.getDepartureTime().plusMinutes(routeStop.getEstimatedMinutesFromStart())))
                .min(Comparator
                        .comparingInt((PickupOption option) -> requestedTime == null ? 0
                                : minuteDifference(option.pickupTime(), requestedTime))
                        .thenComparing(PickupOption::dayOfWeek)
                        .thenComparing(PickupOption::departureTime))
                .map(PickupOption::pickupTime).orElse(null);
    }

    private OperationalResponses.Enrollment enrollmentOut(StudentRouteEnrollment enrollment,
                                                            LocalTime estimatedPickupTime) {
        return new OperationalResponses.Enrollment(enrollment.getId(), enrollment.getStudent().getId(),
                enrollment.getRoute().getId(), enrollment.getStop().getId(), enrollment.getStatus(),
                enrollment.getValidFrom(), enrollment.getValidUntil(), estimatedPickupTime);
    }

    private static void validateLocation(BigDecimal latitude, BigDecimal longitude) {
        if ((latitude == null) != (longitude == null))
            throw new BusinessRuleException("latitude y longitude deben enviarse juntas.");
        if (latitude != null && (latitude.compareTo(BigDecimal.valueOf(-90)) < 0
                || latitude.compareTo(BigDecimal.valueOf(90)) > 0
                || longitude.compareTo(BigDecimal.valueOf(-180)) < 0
                || longitude.compareTo(BigDecimal.valueOf(180)) > 0))
            throw new BusinessRuleException("Las coordenadas están fuera del rango permitido.");
    }

    private static boolean hasLocation(BigDecimal latitude, BigDecimal longitude) {
        return latitude != null && longitude != null;
    }

    private static boolean hasLocation(Stop stop) {
        return hasLocation(stop.getLatitude(), stop.getLongitude());
    }

    private static boolean hasLocation(RouteSearchProjection projection) {
        return hasLocation(projection.getStopLatitude(), projection.getStopLongitude());
    }

    private static long distanceMeters(BigDecimal latitude, BigDecimal longitude, Stop stop) {
        return GeographicDistanceCalculator.distanceMeters(latitude, longitude,
                stop.getLatitude(), stop.getLongitude());
    }

    private static int minuteDifference(LocalTime first, LocalTime second) {
        return Math.abs(first.toSecondOfDay() - second.toSecondOfDay()) / 60;
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record SearchCandidate(RouteSearchProjection projection, LocalTime estimatedPickupTime,
                                   int differenceMinutes, Long distanceMeters) {}
    private record PickupOption(java.time.DayOfWeek dayOfWeek, LocalTime departureTime,
                                LocalTime pickupTime) {}
}
