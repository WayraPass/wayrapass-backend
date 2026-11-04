package com.wayrapass.service;

import com.wayrapass.dto.request.OperationalRequests;
import com.wayrapass.exception.ConflictException;
import com.wayrapass.model.*;
import com.wayrapass.repository.*;
import com.wayrapass.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentAccessServiceD3Test {
    @Mock CurrentUser current;
    @Mock StudentRepository students;
    @Mock StudentFamilyLinkRepository links;
    @Mock RouteRepository routes;
    @Mock StopRepository stops;
    @Mock RouteStopRepository routeStops;
    @Mock RouteScheduleRepository schedules;
    @Mock StudentRouteEnrollmentRepository enrollments;
    @Mock FavoriteRouteRepository favorites;
    @Mock EnrollmentLifecycleService lifecycle;
    @InjectMocks StudentAccessService service;

    private Student student;
    private Route currentRoute;
    private Route targetRoute;
    private Stop currentStop;
    private Stop targetStop;
    private StudentRouteEnrollment currentEnrollment;

    @BeforeEach
    void setUp() {
        Institution institution = new Institution();
        institution.setId(1L);
        institution.setActive(true);
        User user = new User();
        user.setId(20L);
        user.setRole(Role.STUDENT);
        student = new Student();
        student.setId(2L);
        student.setUser(user);
        student.setInstitution(institution);
        currentRoute = route(5L, institution);
        targetRoute = route(6L, institution);
        currentStop = stop(8L);
        targetStop = stop(9L);
        currentEnrollment = new StudentRouteEnrollment();
        currentEnrollment.setId(30L);
        currentEnrollment.setStudent(student);
        currentEnrollment.setRoute(currentRoute);
        currentEnrollment.setStop(currentStop);
        currentEnrollment.setStatus(EnrollmentStatus.ACTIVE);
    }

    @Test
    void fullTargetRouteRejectsChangeWithoutEndingPreviousEnrollment() {
        prepareSelection();
        when(enrollments.findCurrentForUpdate(2L,
                List.of(EnrollmentStatus.ACTIVE, EnrollmentStatus.SUSPENDED)))
                .thenReturn(Optional.of(currentEnrollment));
        when(lifecycle.lockRouteAndRequireSeat(6L, false))
                .thenThrow(new ConflictException(EnrollmentLifecycleService.NO_SEATS_MESSAGE));

        assertThrows(ConflictException.class,
                () -> service.enroll(new OperationalRequests.Enrollment(6L, 9L)));

        assertEquals(EnrollmentStatus.ACTIVE, currentEnrollment.getStatus());
        assertNull(currentEnrollment.getValidUntil());
        verify(enrollments, never()).save(any());
    }

    @Test
    void suspendedEnrollmentMustAcquireSeatBeforeChangingStopOnSameRoute() {
        currentEnrollment.setStatus(EnrollmentStatus.SUSPENDED);
        targetRoute = currentRoute;
        prepareSelection();
        when(enrollments.findCurrentForUpdate(2L,
                List.of(EnrollmentStatus.ACTIVE, EnrollmentStatus.SUSPENDED)))
                .thenReturn(Optional.of(currentEnrollment));
        when(lifecycle.lockRouteAndRequireSeat(5L, false))
                .thenThrow(new ConflictException(EnrollmentLifecycleService.NO_SEATS_MESSAGE));

        assertThrows(ConflictException.class,
                () -> service.enroll(new OperationalRequests.Enrollment(5L, 9L)));

        assertEquals(EnrollmentStatus.SUSPENDED, currentEnrollment.getStatus());
    }

    private void prepareSelection() {
        when(current.require()).thenReturn(student.getUser());
        when(students.findByUserId(20L)).thenReturn(Optional.of(student));
        when(routes.findById(targetRoute.getId())).thenReturn(Optional.of(targetRoute));
        when(stops.findById(9L)).thenReturn(Optional.of(targetStop));
        when(routeStops.findByRouteIdAndStopId(targetRoute.getId(), 9L))
                .thenReturn(Optional.of(routeStop(targetRoute, targetStop)));
    }

    private Route route(Long id, Institution institution) {
        Route value = new Route();
        value.setId(id);
        value.setInstitution(institution);
        value.setStatus(RouteStatus.ACTIVE);
        value.setCapacity(1);
        return value;
    }

    private Stop stop(Long id) {
        Stop value = new Stop();
        value.setId(id);
        value.setActive(true);
        return value;
    }

    private RouteStop routeStop(Route route, Stop stop) {
        RouteStop value = new RouteStop();
        value.setRoute(route);
        value.setStop(stop);
        value.setActive(true);
        return value;
    }
}
