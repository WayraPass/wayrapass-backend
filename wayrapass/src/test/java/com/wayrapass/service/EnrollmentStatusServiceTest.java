package com.wayrapass.service;

import com.wayrapass.dto.request.OperationalRequests;
import com.wayrapass.exception.BusinessRuleException;
import com.wayrapass.exception.ResourceNotFoundException;
import com.wayrapass.model.*;
import com.wayrapass.repository.StudentRouteEnrollmentRepository;
import com.wayrapass.repository.TripRepository;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.*;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EnrollmentStatusServiceTest {
    private static final ZoneId SERVICE_ZONE = ZoneId.of("America/Lima");

    @Mock StudentAccessService studentAccess;
    @Mock StudentRouteEnrollmentRepository enrollments;
    @Mock TripRepository trips;
    @Mock EnrollmentLifecycleService lifecycle;
    @InjectMocks EnrollmentStatusService service;

    private Student student;
    private StudentRouteEnrollment enrollment;

    @BeforeEach
    void setUp() {
        lenient().when(lifecycle.today()).thenReturn(LocalDate.now(SERVICE_ZONE));
        student = new Student();
        student.setId(2L);

        Route route = new Route();
        route.setId(5L);
        Stop stop = new Stop();
        stop.setId(8L);

        enrollment = new StudentRouteEnrollment();
        enrollment.setId(20L);
        enrollment.setStudent(student);
        enrollment.setRoute(route);
        enrollment.setStop(stop);
        enrollment.setStatus(EnrollmentStatus.ACTIVE);
    }

    @Test
    void activeEnrollmentCanBeSuspendedForValidPeriod() {
        LocalDate today = LocalDate.now(SERVICE_ZONE);
        when(studentAccess.requireCurrentStudent()).thenReturn(student);
        when(enrollments.findLatestForUpdate(2L)).thenReturn(Optional.of(enrollment));

        var response = service.suspend(new OperationalRequests.Suspension(today, today.plusDays(7)));

        assertEquals(EnrollmentStatus.SUSPENDED, enrollment.getStatus());
        assertEquals(today, enrollment.getSuspendedFrom());
        assertEquals(today.plusDays(7), enrollment.getSuspendedUntil());
        assertEquals(5L, response.routeId());
        assertEquals(8L, response.stopId());
    }

    @Test
    void suspendedEnrollmentCannotBeSuspendedAgain() {
        enrollment.setStatus(EnrollmentStatus.SUSPENDED);
        prepareLatest();
        LocalDate today = LocalDate.now(SERVICE_ZONE);

        assertThrows(BusinessRuleException.class,
                () -> service.suspend(new OperationalRequests.Suspension(today, today.plusDays(2))));
        verifyNoInteractions(trips);
    }

    @Test
    void endedEnrollmentCannotBeSuspended() {
        enrollment.setStatus(EnrollmentStatus.ENDED);
        prepareLatest();
        LocalDate today = LocalDate.now(SERVICE_ZONE);

        assertThrows(BusinessRuleException.class,
                () -> service.suspend(new OperationalRequests.Suspension(today, today.plusDays(2))));
    }

    @Test
    void studentWithoutEnrollmentCannotSuspend() {
        when(studentAccess.requireCurrentStudent()).thenReturn(student);
        when(enrollments.findLatestForUpdate(2L)).thenReturn(Optional.empty());
        LocalDate today = LocalDate.now(SERVICE_ZONE);

        assertThrows(ResourceNotFoundException.class,
                () -> service.suspend(new OperationalRequests.Suspension(today, today.plusDays(2))));
    }

    @Test
    void nonStudentRoleIsRejectedBeforeEnrollmentLookup() {
        when(studentAccess.requireCurrentStudent()).thenThrow(new AccessDeniedException("Solo STUDENT"));
        LocalDate today = LocalDate.now(SERVICE_ZONE);

        assertThrows(AccessDeniedException.class,
                () -> service.suspend(new OperationalRequests.Suspension(today, today.plusDays(2))));
        verifyNoInteractions(enrollments, trips);
    }

    @Test
    void suspensionEndMustBeAfterStart() {
        when(studentAccess.requireCurrentStudent()).thenReturn(student);
        LocalDate today = LocalDate.now(SERVICE_ZONE);

        assertThrows(BusinessRuleException.class,
                () -> service.suspend(new OperationalRequests.Suspension(today, today.minusDays(1))));
        verifyNoInteractions(enrollments, trips);
    }

    @Test
    void futureScheduledSuspensionIsRejectedWithoutSchedulerSupport() {
        when(studentAccess.requireCurrentStudent()).thenReturn(student);
        LocalDate tomorrow = LocalDate.now(SERVICE_ZONE).plusDays(1);

        assertThrows(BusinessRuleException.class,
                () -> service.suspend(new OperationalRequests.Suspension(tomorrow, tomorrow.plusDays(2))));
        verifyNoInteractions(enrollments, trips);
    }

    @Test
    void suspensionRequestRequiresBothDates() {
        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            var violations = validatorFactory.getValidator()
                    .validate(new OperationalRequests.Suspension(null, null));
            assertEquals(2, violations.size());
        }
    }

    @Test
    void tripOnSuspensionStartDateBlocksSuspension() {
        prepareLatest();
        LocalDate today = LocalDate.now(SERVICE_ZONE);
        when(trips.routeTripOverlaps(
                eq(5L), anyCollection(), any(), any())).thenReturn(true);

        assertThrows(BusinessRuleException.class,
                () -> service.suspend(new OperationalRequests.Suspension(today, today.plusDays(2))));
        assertEquals(EnrollmentStatus.ACTIVE, enrollment.getStatus());
    }

    @Test
    void suspendedEnrollmentCanBeReactivatedEarlyWithoutChangingRouteOrStop() {
        enrollment.setStatus(EnrollmentStatus.SUSPENDED);
        enrollment.setSuspendedFrom(LocalDate.now(SERVICE_ZONE));
        enrollment.setSuspendedUntil(LocalDate.now(SERVICE_ZONE).plusDays(10));
        prepareLatest();

        var response = service.reactivate();

        assertEquals(EnrollmentStatus.ACTIVE, enrollment.getStatus());
        assertEquals(LocalDate.now(SERVICE_ZONE), enrollment.getSuspendedUntil());
        assertEquals(5L, response.routeId());
        assertEquals(8L, response.stopId());
    }

    @Test
    void activeEnrollmentCannotBeReactivated() {
        prepareLatest();
        assertThrows(BusinessRuleException.class, service::reactivate);
    }

    @Test
    void endedEnrollmentCannotBeReactivated() {
        enrollment.setStatus(EnrollmentStatus.ENDED);
        prepareLatest();
        assertThrows(BusinessRuleException.class, service::reactivate);
    }

    @Test
    void studentWithoutEnrollmentCannotReactivate() {
        when(studentAccess.requireCurrentStudent()).thenReturn(student);
        when(enrollments.findLatestForUpdate(2L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, service::reactivate);
    }

    @Test
    void currentEnrollmentExposesSuspensionStateAndPeriod() {
        enrollment.setStatus(EnrollmentStatus.SUSPENDED);
        enrollment.setSuspendedFrom(LocalDate.now(SERVICE_ZONE));
        enrollment.setSuspendedUntil(LocalDate.now(SERVICE_ZONE).plusDays(4));
        when(studentAccess.requireCurrentStudent()).thenReturn(student);
        when(lifecycle.latestForUse(2L)).thenReturn(enrollment);

        var response = service.current();

        assertEquals(EnrollmentStatus.SUSPENDED, response.status());
        assertEquals(enrollment.getSuspendedUntil(), response.suspendedUntil());
    }

    private void prepareLatest() {
        when(studentAccess.requireCurrentStudent()).thenReturn(student);
        when(enrollments.findLatestForUpdate(2L)).thenReturn(Optional.of(enrollment));
    }
}
