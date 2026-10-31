package com.wayrapass.service;

import com.wayrapass.exception.ConflictException;
import com.wayrapass.model.EnrollmentStatus;
import com.wayrapass.model.Route;
import com.wayrapass.model.StudentRouteEnrollment;
import com.wayrapass.repository.RouteRepository;
import com.wayrapass.repository.StudentRouteEnrollmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EnrollmentLifecycleServiceTest {
    @Mock StudentRouteEnrollmentRepository enrollments;
    @Mock RouteRepository routes;

    private EnrollmentLifecycleService service;
    private Route route;
    private StudentRouteEnrollment enrollment;

    @BeforeEach
    void setUp() {
        service = new EnrollmentLifecycleService(enrollments, routes,
                Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"), ZoneOffset.UTC));
        route = new Route();
        route.setId(5L);
        route.setCapacity(2);
        enrollment = new StudentRouteEnrollment();
        enrollment.setId(20L);
        enrollment.setRoute(route);
        enrollment.setStatus(EnrollmentStatus.SUSPENDED);
        enrollment.setSuspendedFrom(LocalDate.of(2026, 11, 1));
    }

    @Test
    void activeSuspensionPeriodRemainsSuspended() {
        enrollment.setSuspendedUntil(LocalDate.of(2026, 11, 11));
        prepareCurrent();

        assertEquals(EnrollmentStatus.SUSPENDED, service.currentForUse(2L).orElseThrow().getStatus());
        verifyNoInteractions(routes);
    }

    @Test
    void suspendedUntilIsTheLastSuspendedDay() {
        enrollment.setSuspendedUntil(LocalDate.of(2026, 11, 10));
        prepareCurrent();

        assertEquals(EnrollmentStatus.SUSPENDED, service.currentForUse(2L).orElseThrow().getStatus());
        verifyNoInteractions(routes);
    }

    @Test
    void dayAfterSuspensionEndReactivatesWhenSeatIsAvailable() {
        enrollment.setSuspendedUntil(LocalDate.of(2026, 11, 9));
        prepareCurrent();
        when(routes.findByIdForUpdate(5L)).thenReturn(Optional.of(route));
        when(enrollments.countByRouteIdAndStatus(5L, EnrollmentStatus.ACTIVE)).thenReturn(1L);

        var result = service.currentForUse(2L).orElseThrow();

        assertEquals(EnrollmentStatus.ACTIVE, result.getStatus());
        assertEquals(LocalDate.of(2026, 11, 1), result.getSuspendedFrom());
        assertEquals(LocalDate.of(2026, 11, 9), result.getSuspendedUntil());
    }

    @Test
    void normalizationIsIdempotent() {
        enrollment.setSuspendedUntil(LocalDate.of(2026, 11, 9));
        prepareCurrent();
        when(routes.findByIdForUpdate(5L)).thenReturn(Optional.of(route));
        when(enrollments.countByRouteIdAndStatus(5L, EnrollmentStatus.ACTIVE)).thenReturn(0L);

        service.currentForUse(2L);
        service.currentForUse(2L);

        assertEquals(EnrollmentStatus.ACTIVE, enrollment.getStatus());
        verify(routes, times(1)).findByIdForUpdate(5L);
        verify(enrollments, times(1)).countByRouteIdAndStatus(5L, EnrollmentStatus.ACTIVE);
    }

    @Test
    void fullRouteRejectsReactivationAndKeepsSuspendedState() {
        enrollment.setSuspendedUntil(LocalDate.of(2026, 11, 9));
        prepareCurrent();
        when(routes.findByIdForUpdate(5L)).thenReturn(Optional.of(route));
        when(enrollments.countByRouteIdAndStatus(5L, EnrollmentStatus.ACTIVE)).thenReturn(2L);

        ConflictException error = assertThrows(ConflictException.class, () -> service.currentForUse(2L));

        assertEquals(EnrollmentLifecycleService.NO_SEATS_MESSAGE, error.getMessage());
        assertEquals(EnrollmentStatus.SUSPENDED, enrollment.getStatus());
    }

    @Test
    void lastAvailableSeatCanBeTaken() {
        when(routes.findByIdForUpdate(5L)).thenReturn(Optional.of(route));
        when(enrollments.countByRouteIdAndStatus(5L, EnrollmentStatus.ACTIVE)).thenReturn(1L);

        assertSame(route, service.lockRouteAndRequireSeat(5L, false));
    }

    @Test
    void existingActiveStudentDoesNotConsumeAnotherSeatWhenChangingStop() {
        when(routes.findByIdForUpdate(5L)).thenReturn(Optional.of(route));

        assertSame(route, service.lockRouteAndRequireSeat(5L, true));
        verify(enrollments, never()).countByRouteIdAndStatus(anyLong(), any());
    }

    private void prepareCurrent() {
        when(enrollments.findCurrentForUpdate(2L,
                List.of(EnrollmentStatus.ACTIVE, EnrollmentStatus.SUSPENDED)))
                .thenReturn(Optional.of(enrollment));
    }
}
