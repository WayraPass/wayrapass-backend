package com.wayrapass.service;

import com.wayrapass.exception.ConflictException;
import com.wayrapass.exception.ResourceNotFoundException;
import com.wayrapass.model.EnrollmentStatus;
import com.wayrapass.model.Route;
import com.wayrapass.model.StudentRouteEnrollment;
import com.wayrapass.repository.RouteRepository;
import com.wayrapass.repository.StudentRouteEnrollmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EnrollmentLifecycleService {
    static final ZoneId SERVICE_ZONE = ZoneId.of("America/Lima");
    static final String NO_SEATS_MESSAGE =
            "La ruta ya no dispone de cupos. Debe seleccionar una nueva asignación de plaza.";

    private final StudentRouteEnrollmentRepository enrollments;
    private final RouteRepository routes;
    private final Clock clock;

    @Transactional
    public Optional<StudentRouteEnrollment> currentForUse(Long studentId) {
        Optional<StudentRouteEnrollment> enrollment = enrollments.findCurrentForUpdate(studentId,
                List.of(EnrollmentStatus.ACTIVE, EnrollmentStatus.SUSPENDED));
        enrollment.ifPresent(this::normalizeExpiredSuspension);
        return enrollment;
    }

    @Transactional
    public StudentRouteEnrollment latestForUse(Long studentId) {
        StudentRouteEnrollment enrollment = enrollments.findLatestForUpdate(studentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "El estudiante no tiene una inscripción registrada."));
        normalizeExpiredSuspension(enrollment);
        return enrollment;
    }

    @Transactional
    public Route lockRouteAndRequireSeat(Long routeId, boolean studentAlreadyOccupiesSeat) {
        Route route = routes.findByIdForUpdate(routeId)
                .orElseThrow(() -> new ResourceNotFoundException("Ruta no encontrada."));
        if (!studentAlreadyOccupiesSeat
                && enrollments.countByRouteIdAndStatus(routeId, EnrollmentStatus.ACTIVE) >= route.getCapacity()) {
            throw new ConflictException(NO_SEATS_MESSAGE);
        }
        return route;
    }

    @Transactional
    public void reactivate(StudentRouteEnrollment enrollment) {
        lockRouteAndRequireSeat(enrollment.getRoute().getId(), false);
        enrollment.setStatus(EnrollmentStatus.ACTIVE);
    }

    LocalDate today() {
        return LocalDate.now(clock.withZone(SERVICE_ZONE));
    }

    private void normalizeExpiredSuspension(StudentRouteEnrollment enrollment) {
        if (enrollment.getStatus() == EnrollmentStatus.SUSPENDED
                && enrollment.getSuspendedUntil() != null
                && today().isAfter(enrollment.getSuspendedUntil())) {
            reactivate(enrollment);
        }
    }
}
