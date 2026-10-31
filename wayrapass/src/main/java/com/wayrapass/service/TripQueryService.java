package com.wayrapass.service;

import com.wayrapass.config.LocationTrackingProperties;
import com.wayrapass.dto.response.TripQueryResponses;
import com.wayrapass.exception.BusinessRuleException;
import com.wayrapass.exception.ResourceNotFoundException;
import com.wayrapass.model.*;
import com.wayrapass.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TripQueryService {
    private static final ZoneId SERVICE_ZONE = ZoneId.of("America/Lima");

    private final StudentAccessService studentAccess;
    private final StudentRouteEnrollmentRepository enrollments;
    private final BoardingRepository boardings;
    private final TripRepository trips;
    private final IncidentRepository incidents;
    private final TripLocationRepository locations;
    private final RouteStopRepository routeStops;
    private final LocationTrackingProperties locationProperties;
    private final EnrollmentLifecycleService enrollmentLifecycle;
    private final Clock clock;

    @Transactional
    public TripQueryResponses.CurrentTripResponse myCurrentTrip() {
        Student student = studentAccess.requireCurrentStudent();
        CurrentTripSelection selection = resolveCurrentTrip(student);
        if (selection.trip() == null) {
            return new TripQueryResponses.CurrentTripResponse(
                    false,
                    "No hay un viaje activo o programado para la inscripción actual.",
                    null
            );
        }
        return new TripQueryResponses.CurrentTripResponse(
                true,
                "Viaje actual disponible.",
                currentTripDetails(selection, clock.instant())
        );
    }

    @Transactional
    public TripQueryResponses.DriverCredential familyCurrentTripDriver(Long studentId) {
        Student student = studentAccess.requireFamilyStudent(studentId);
        CurrentTripSelection selection = resolveCurrentTrip(student);
        if (selection.trip() == null) {
            return unassignedDriver("No hay un viaje activo o programado para el estudiante vinculado.");
        }
        return driverCredential(selection.trip().getDriver());
    }

    @Transactional(readOnly = true)
    public List<TripQueryResponses.HistoryItem> myHistory(LocalDate from, LocalDate to) {
        return history(studentAccess.requireCurrentStudent().getId(), from, to);
    }

    @Transactional(readOnly = true)
    public TripQueryResponses.StudentHistoryDetail myHistoryDetail(Long tripId) {
        Student student = studentAccess.requireCurrentStudent();
        Boarding boarding = historyBoarding(student.getId(), tripId);
        Trip trip = boarding.getTrip();
        Driver driver = trip.getDriver();
        Vehicle vehicle = trip.getVehicle();
        return new TripQueryResponses.StudentHistoryDetail(
                historyItem(boarding),
                driver == null ? null : driver.getId(),
                driver == null ? null : driver.getUser().getFullName(),
                vehicle == null ? null : vehicle.getId(),
                vehicle == null ? null : vehicle.getPlate(),
                vehicle == null ? null : vehicle.getBrand(),
                vehicle == null ? null : vehicle.getModel(),
                incidentItems(trip.getId())
        );
    }

    @Transactional(readOnly = true)
    public List<TripQueryResponses.HistoryItem> familyHistory(Long studentId, LocalDate from, LocalDate to) {
        Student student = studentAccess.requireFamilyStudent(studentId);
        return history(student.getId(), from, to);
    }

    @Transactional(readOnly = true)
    public TripQueryResponses.FamilyHistoryDetail familyHistoryDetail(Long studentId, Long tripId) {
        Student student = studentAccess.requireFamilyStudent(studentId);
        Boarding boarding = historyBoarding(student.getId(), tripId);
        return new TripQueryResponses.FamilyHistoryDetail(
                historyItem(boarding),
                incidentItems(boarding.getTrip().getId())
        );
    }

    @Transactional
    public List<TripQueryResponses.UpcomingTrip> familyUpcomingTrips(Long studentId) {
        Student student = studentAccess.requireFamilyStudent(studentId);
        Instant now = clock.instant();
        return currentEnrollment(student.getId())
                .filter(enrollment -> enrollment.getStatus() == EnrollmentStatus.ACTIVE)
                .map(enrollment -> {
                    boolean routeOrStopModified = lastEnrollmentChangedRouteOrStop(enrollment);
                    return trips.findUpcomingByRouteAndStatus(enrollment.getRoute().getId(), TripStatus.SCHEDULED, now).stream()
                        .filter(trip -> trip.getStatus() == TripStatus.SCHEDULED && trip.getScheduledStartAt().isAfter(now))
                        .map(trip -> upcomingTrip(trip, enrollment.getStop(), routeOrStopModified))
                        .sorted(Comparator.comparing(TripQueryResponses.UpcomingTrip::scheduledStartAt))
                        .toList();
                })
                .orElseGet(List::of);
    }

    private CurrentTripSelection resolveCurrentTrip(Student student) {
        return currentEnrollment(student.getId())
                .filter(enrollment -> enrollment.getStatus() == EnrollmentStatus.ACTIVE)
                .map(enrollment -> {
                    List<Trip> candidates = trips.findCurrentCandidates(
                            enrollment.getRoute().getId(),
                            List.of(TripStatus.BOARDING, TripStatus.IN_PROGRESS),
                            TripStatus.SCHEDULED,
                            clock.instant()
                    );
                    Trip selected = candidates.stream()
                            .filter(trip -> trip.getStatus() == TripStatus.IN_PROGRESS
                                    || trip.getStatus() == TripStatus.BOARDING
                                    || trip.getStatus() == TripStatus.SCHEDULED)
                            .min(Comparator.comparingInt((Trip trip) -> currentTripPriority(trip.getStatus()))
                                    .thenComparing(Trip::getScheduledStartAt))
                            .orElse(null);
                    return new CurrentTripSelection(enrollment, selected);
                })
                .orElseGet(() -> new CurrentTripSelection(null, null));
    }

    private java.util.Optional<StudentRouteEnrollment> currentEnrollment(Long studentId) {
        return enrollmentLifecycle.currentForUse(studentId);
    }

    private TripQueryResponses.CurrentTripDetails currentTripDetails(CurrentTripSelection selection, Instant now) {
        Trip trip = selection.trip();
        Route route = trip.getRoute();
        Stop stop = selection.enrollment().getStop();
        Vehicle vehicle = trip.getVehicle();
        return new TripQueryResponses.CurrentTripDetails(
                trip.getId(),
                route.getId(),
                route.getCode(),
                route.getName(),
                trip.getStatus(),
                trip.getScheduledStartAt(),
                trip.getScheduledEndAt(),
                trip.getActualStartAt(),
                trip.getActualEndAt(),
                vehicle == null ? null : new TripQueryResponses.VehicleSummary(
                        vehicle.getId(), vehicle.getPlate(), vehicle.getBrand(), vehicle.getModel()),
                new TripQueryResponses.AssignedStop(
                        stop.getId(), stop.getName(), stop.getAddress(), stop.getDistrict()),
                locationStatus(trip.getId(), now),
                stopArrivalEstimate(trip, stop, now),
                universityArrivalEstimate(trip, now),
                driverCredential(trip.getDriver())
        );
    }

    private TripQueryResponses.LocationStatus locationStatus(Long tripId, Instant now) {
        return locations.findFirstByTripIdOrderByRecordedAtDesc(tripId)
                .map(location -> {
                    boolean stale = location.getRecordedAt()
                            .isBefore(now.minusSeconds(locationProperties.getStaleAfterSeconds()));
                    return new TripQueryResponses.LocationStatus(
                            true,
                            stale,
                            stale ? TripQueryResponses.LocationFreshness.STALE
                                    : TripQueryResponses.LocationFreshness.RECENT,
                            location.getLatitude(),
                            location.getLongitude(),
                            location.getRecordedAt(),
                            stale ? "La última ubicación conocida está desactualizada."
                                    : "Última ubicación registrada."
                    );
                })
                .orElseGet(() -> new TripQueryResponses.LocationStatus(
                        false,
                        false,
                        TripQueryResponses.LocationFreshness.UNAVAILABLE,
                        null,
                        null,
                        null,
                        "Ubicación temporalmente no disponible."
                ));
    }

    private TripQueryResponses.StopArrivalEstimate stopArrivalEstimate(Trip trip, Stop stop, Instant now) {
        return routeStops.findByRouteIdAndStopId(trip.getRoute().getId(), stop.getId())
                .map(routeStop -> {
                    Instant scheduled = trip.getScheduledStartAt()
                            .plusSeconds(routeStop.getEstimatedMinutesFromStart() * 60L);
                    long delaySeconds = trip.getEstimatedArrivalAt() != null && trip.getScheduledEndAt() != null
                            ? Duration.between(trip.getScheduledEndAt(), trip.getEstimatedArrivalAt()).getSeconds()
                            : 0L;
                    Instant estimated = scheduled.plusSeconds(delaySeconds);
                    String message = delaySeconds == 0L
                            ? "Estimación basada en el horario programado."
                            : "Estimación actualizada con el retraso registrado.";
                    return new TripQueryResponses.StopArrivalEstimate(
                            true,
                            scheduled,
                            estimated,
                            remainingMinutes(now, estimated),
                            trip.getUpdatedAt(),
                            message
                    );
                })
                .orElseGet(() -> new TripQueryResponses.StopArrivalEstimate(
                        false,
                        null,
                        null,
                        null,
                        null,
                        "Tiempo estimado de llegada al paradero no disponible temporalmente."
                ));
    }

    private TripQueryResponses.UniversityArrivalEstimate universityArrivalEstimate(Trip trip, Instant now) {
        Instant scheduled = trip.getScheduledEndAt();
        Instant estimated = trip.getEstimatedArrivalAt();
        if (trip.getStatus() == TripStatus.COMPLETED || trip.getStatus() == TripStatus.CANCELLED) {
            return new TripQueryResponses.UniversityArrivalEstimate(
                    false,
                    scheduled,
                    null,
                    null,
                    false,
                    null,
                    null,
                    TripQueryResponses.EtaSource.UNAVAILABLE,
                    "El viaje ya no tiene una ETA activa."
            );
        }
        if (estimated == null
                && (trip.getStatus() == TripStatus.BOARDING || trip.getStatus() == TripStatus.IN_PROGRESS)
                && scheduled != null) {
            return new TripQueryResponses.UniversityArrivalEstimate(
                    true,
                    scheduled,
                    scheduled,
                    remainingMinutes(now, scheduled),
                    false,
                    0L,
                    trip.getUpdatedAt(),
                    TripQueryResponses.EtaSource.PLANNED,
                    "ETA base derivada del horario programado."
            );
        }
        if (estimated == null) {
            return new TripQueryResponses.UniversityArrivalEstimate(
                    false,
                    scheduled,
                    null,
                    null,
                    false,
                    null,
                    null,
                    scheduled == null ? TripQueryResponses.EtaSource.UNAVAILABLE
                            : TripQueryResponses.EtaSource.PLANNED,
                    scheduled == null
                            ? "Tiempo estimado de llegada no disponible temporalmente."
                            : "Llegada programada disponible; ETA operativa aún no iniciada."
            );
        }
        boolean delayed = scheduled != null && estimated.isAfter(scheduled);
        Long delayMinutes = scheduled == null ? null
                : Math.max(0L, Duration.between(scheduled, estimated).toMinutes());
        return new TripQueryResponses.UniversityArrivalEstimate(
                true,
                scheduled,
                estimated,
                remainingMinutes(now, estimated),
                delayed,
                delayMinutes,
                trip.getUpdatedAt(),
                TripQueryResponses.EtaSource.OPERATIONAL,
                delayed ? "Viaje retrasado; ETA actualizada." : "ETA disponible."
        );
    }

    private TripQueryResponses.DriverCredential driverCredential(Driver driver) {
        if (driver == null) {
            return unassignedDriver("Aún no se ha asignado un conductor a este viaje.");
        }
        boolean imageAvailable = driver.getCredentialImageUrl() != null
                && !driver.getCredentialImageUrl().isBlank();
        boolean expired = driver.getLicenseExpiration() != null
                && driver.getLicenseExpiration().isBefore(LocalDate.ofInstant(clock.instant(), SERVICE_ZONE));
        return new TripQueryResponses.DriverCredential(
                true,
                imageAvailable ? "Credencial del conductor disponible."
                        : "No se pudo cargar la credencial del conductor.",
                driver.getId(),
                driver.getUser().getFullName(),
                driver.getCredentialImageUrl(),
                imageAvailable,
                driver.getLicenseNumber(),
                driver.getLicenseCategory(),
                driver.getLicenseExpiration(),
                expired,
                driver.getAuthorizationStatus(),
                driver.getOperationalStatus(),
                driver.getAuthorizationStatus() == AuthorizationStatus.AUTHORIZED
        );
    }

    private TripQueryResponses.DriverCredential unassignedDriver(String message) {
        return new TripQueryResponses.DriverCredential(
                false, message, null, null, null, false, null, null,
                null, false, null, null, false
        );
    }

    private long remainingMinutes(Instant now, Instant arrival) {
        long seconds = Duration.between(now, arrival).getSeconds();
        return seconds <= 0 ? 0L : (seconds + 59L) / 60L;
    }

    private int currentTripPriority(TripStatus status) {
        return switch (status) {
            case IN_PROGRESS -> 0;
            case BOARDING -> 1;
            case SCHEDULED -> 2;
            default -> Integer.MAX_VALUE;
        };
    }

    private record CurrentTripSelection(StudentRouteEnrollment enrollment, Trip trip) {
    }

    private boolean lastEnrollmentChangedRouteOrStop(StudentRouteEnrollment current) {
        return enrollments.findFirstByStudentIdAndStatusOrderByIdDesc(
                        current.getStudent().getId(), EnrollmentStatus.ENDED)
                .map(previous -> !previous.getRoute().getId().equals(current.getRoute().getId())
                        || !previous.getStop().getId().equals(current.getStop().getId()))
                .orElse(false);
    }

    private List<TripQueryResponses.HistoryItem> history(Long studentId, LocalDate from, LocalDate to) {
        validateRange(from, to);
        Instant fromInstant = from.atStartOfDay(SERVICE_ZONE).toInstant();
        Instant toExclusive = to.plusDays(1).atStartOfDay(SERVICE_ZONE).toInstant();
        Instant now = clock.instant();
        return boardings.findStudentHistory(studentId, fromInstant, toExclusive, now).stream()
                .filter(boarding -> !boarding.getTrip().getScheduledStartAt().isAfter(now))
                .map(this::historyItem)
                .sorted(Comparator.comparing(TripQueryResponses.HistoryItem::scheduledStartAt).reversed())
                .toList();
    }

    private Boarding historyBoarding(Long studentId, Long tripId) {
        return boardings.findStudentHistoryDetail(studentId, tripId, clock.instant())
                .orElseThrow(() -> {
                    if (!trips.existsById(tripId)) {
                        return new ResourceNotFoundException("Viaje no encontrado.");
                    }
                    return new AccessDeniedException("El viaje no pertenece al historial del estudiante.");
                });
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new BusinessRuleException("La fecha inicial no puede ser posterior a la fecha final.");
        }
    }

    private TripQueryResponses.HistoryItem historyItem(Boarding boarding) {
        Trip trip = boarding.getTrip();
        Route route = trip.getRoute();
        Institution institution = route.getInstitution();
        Stop stop = boarding.getStop();
        return new TripQueryResponses.HistoryItem(
                trip.getId(),
                route.getId(),
                route.getCode(),
                route.getName(),
                route.getOrigin(),
                route.getDestination(),
                institution.getName(),
                institution.getCampusName(),
                trip.getScheduledStartAt(),
                trip.getScheduledEndAt(),
                trip.getActualStartAt(),
                trip.getActualEndAt(),
                trip.getStatus(),
                boarding.getStatus(),
                boarding.getBoardedAt(),
                stop.getId(),
                stop.getName(),
                isDelayed(trip)
        );
    }

    private TripQueryResponses.UpcomingTrip upcomingTrip(Trip trip, Stop stop, boolean routeOrStopModified) {
        Route route = trip.getRoute();
        Institution institution = route.getInstitution();
        return new TripQueryResponses.UpcomingTrip(
                trip.getId(),
                route.getId(),
                route.getCode(),
                route.getName(),
                route.getOrigin(),
                route.getDestination(),
                institution.getName(),
                institution.getCampusName(),
                stop.getId(),
                stop.getName(),
                trip.getScheduledStartAt(),
                trip.getScheduledEndAt(),
                trip.getStatus(),
                trip.isWasReassigned(),
                routeOrStopModified,
                routeOrStopModified ? "Este viaje fue modificado" : null
        );
    }

    private List<TripQueryResponses.IncidentItem> incidentItems(Long tripId) {
        return incidents.findByTripIdOrderByCreatedAtAsc(tripId).stream()
                .map(incident -> new TripQueryResponses.IncidentItem(
                        incident.getId(),
                        incident.getType(),
                        incident.getDescription(),
                        incident.getDelayMinutes(),
                        incident.getPriority(),
                        incident.getStatus(),
                        incident.getCreatedAt()
                ))
                .toList();
    }

    private boolean isDelayed(Trip trip) {
        if (trip.getScheduledEndAt() == null) {
            return false;
        }
        return trip.getEstimatedArrivalAt() != null && trip.getEstimatedArrivalAt().isAfter(trip.getScheduledEndAt())
                || trip.getActualEndAt() != null && trip.getActualEndAt().isAfter(trip.getScheduledEndAt());
    }
}
