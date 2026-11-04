package com.wayrapass.service;

import com.wayrapass.dto.request.OperationalRequests;
import com.wayrapass.dto.response.OperationalResponses;
import com.wayrapass.exception.BusinessRuleException;
import com.wayrapass.exception.ResourceNotFoundException;
import com.wayrapass.model.*;
import com.wayrapass.repository.StudentRouteEnrollmentRepository;
import com.wayrapass.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EnrollmentStatusService {
    private static final ZoneId SERVICE_ZONE = ZoneId.of("America/Lima");
    private static final List<TripStatus> BLOCKING_TRIP_STATUSES = List.of(
            TripStatus.SCHEDULED,
            TripStatus.BOARDING,
            TripStatus.IN_PROGRESS
    );

    private final StudentAccessService studentAccess;
    private final StudentRouteEnrollmentRepository enrollments;
    private final TripRepository trips;
    private final EnrollmentLifecycleService lifecycle;

    @Transactional
    public OperationalResponses.EnrollmentStatusDetails current() {
        Student student = studentAccess.requireCurrentStudent();
        return response(lifecycle.latestForUse(student.getId()));
    }

    @Transactional
    public OperationalResponses.EnrollmentStatusDetails suspend(OperationalRequests.Suspension request) {
        Student student = studentAccess.requireCurrentStudent();
        LocalDate today = lifecycle.today();
        validatePeriod(request, today);
        StudentRouteEnrollment enrollment = latestForUpdate(student.getId());
        requireStatus(enrollment, EnrollmentStatus.ACTIVE, "Solo una inscripción activa puede suspenderse.");
        Instant dayStart = request.startDate().atStartOfDay(SERVICE_ZONE).toInstant();
        Instant nextDayStart = request.startDate().plusDays(1).atStartOfDay(SERVICE_ZONE).toInstant();
        if (trips.routeTripOverlaps(
                enrollment.getRoute().getId(), BLOCKING_TRIP_STATUSES, dayStart, nextDayStart)) {
            throw new BusinessRuleException("No puede suspender el servicio porque existe un viaje activo o programado para la fecha de inicio.");
        }
        enrollment.setStatus(EnrollmentStatus.SUSPENDED);
        enrollment.setSuspendedFrom(request.startDate());
        enrollment.setSuspendedUntil(request.endDate());
        return response(enrollment);
    }

    @Transactional
    public OperationalResponses.EnrollmentStatusDetails reactivate() {
        Student student = studentAccess.requireCurrentStudent();
        StudentRouteEnrollment enrollment = latestForUpdate(student.getId());
        requireStatus(enrollment, EnrollmentStatus.SUSPENDED, "Solo una inscripción suspendida puede reactivarse.");
        LocalDate today = lifecycle.today();
        lifecycle.reactivate(enrollment);
        enrollment.setStatus(EnrollmentStatus.ACTIVE);
        if (enrollment.getSuspendedUntil() == null || today.isBefore(enrollment.getSuspendedUntil())) {
            enrollment.setSuspendedUntil(today);
        }
        return response(enrollment);
    }

    private StudentRouteEnrollment latestForUpdate(Long studentId) {
        return enrollments.findLatestForUpdate(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("El estudiante no tiene una inscripción registrada."));
    }

    private void validatePeriod(OperationalRequests.Suspension request, LocalDate today) {
        if (!request.startDate().equals(today)) {
            throw new BusinessRuleException("La suspensión debe iniciar en la fecha actual.");
        }
        if (!request.endDate().isAfter(request.startDate())) {
            throw new BusinessRuleException("La fecha final debe ser posterior a la fecha inicial.");
        }
    }

    private void requireStatus(StudentRouteEnrollment enrollment, EnrollmentStatus expected, String message) {
        if (enrollment.getStatus() != expected) {
            throw new BusinessRuleException(message);
        }
    }

    private OperationalResponses.EnrollmentStatusDetails response(StudentRouteEnrollment enrollment) {
        return new OperationalResponses.EnrollmentStatusDetails(
                enrollment.getId(),
                enrollment.getRoute().getId(),
                enrollment.getStop().getId(),
                enrollment.getStatus(),
                enrollment.getSuspendedFrom(),
                enrollment.getSuspendedUntil()
        );
    }
}
