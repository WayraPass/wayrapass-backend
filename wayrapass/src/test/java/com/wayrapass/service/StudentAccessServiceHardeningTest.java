package com.wayrapass.service;

import com.wayrapass.dto.request.OperationalRequests;
import com.wayrapass.exception.BusinessRuleException;
import com.wayrapass.model.*;
import com.wayrapass.repository.*;
import com.wayrapass.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentAccessServiceHardeningTest {
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

    private Institution institution;
    private Student student;
    private Route route;
    private Trip trip;

    @BeforeEach
    void setUp() {
        institution = institution(1L);
        student = student(2L, 20L, institution);
        route = route(5L, institution, RouteStatus.ACTIVE);
        trip = new Trip();
        trip.setId(30L);
        trip.setRoute(route);
    }

    @Test
    void coordinatorCanAccessAnyTripLocation() {
        when(current.require()).thenReturn(user(1L, Role.COORDINATOR));
        assertDoesNotThrow(() -> service.requireTripAccess(trip));
    }

    @Test
    void driverCanAccessOnlyOwnedTripLocation() {
        Driver assigned = new Driver();
        assigned.setUser(user(21L, Role.DRIVER));
        trip.setDriver(assigned);
        when(current.require()).thenReturn(user(21L, Role.DRIVER), user(22L, Role.DRIVER));

        assertDoesNotThrow(() -> service.requireTripAccess(trip));
        assertThrows(AccessDeniedException.class, () -> service.requireTripAccess(trip));
    }

    @Test
    void studentNeedsActiveEnrollmentOnTripRoute() {
        when(current.require()).thenReturn(user(20L, Role.STUDENT));
        when(students.findByUserId(20L)).thenReturn(Optional.of(student));
        when(enrollments.existsByStudentIdAndRouteIdAndStatusIn(eq(2L), eq(5L), argThat(this::includesReadableEnrollmentStatuses))).thenReturn(true);

        assertDoesNotThrow(() -> service.requireTripAccess(trip));

        when(enrollments.existsByStudentIdAndRouteIdAndStatusIn(eq(2L), eq(5L), argThat(this::includesReadableEnrollmentStatuses))).thenReturn(false);
        assertThrows(AccessDeniedException.class, () -> service.requireTripAccess(trip));
    }

    @Test
    void familyNeedsAuthorizedLinkedStudentEnrolledOnTripRoute() {
        User family = user(40L, Role.FAMILY);
        StudentFamilyLink link = new StudentFamilyLink();
        link.setStudent(student);
        link.setFamilyUser(family);
        link.setAuthorized(true);
        when(current.require()).thenReturn(family);
        when(links.findByFamilyUserIdAndAuthorizedTrue(40L)).thenReturn(List.of(link));
        when(enrollments.existsByStudentIdAndRouteIdAndStatusIn(eq(2L), eq(5L), argThat(this::includesReadableEnrollmentStatuses))).thenReturn(true, false);

        assertDoesNotThrow(() -> service.requireTripAccess(trip));
        assertThrows(AccessDeniedException.class, () -> service.requireTripAccess(trip));
    }

    @Test
    void authorizedFamilyCanResolveLinkedStudent() {
        User family = user(40L, Role.FAMILY);
        when(current.require()).thenReturn(family);
        when(students.findById(2L)).thenReturn(Optional.of(student));
        when(links.existsByStudentIdAndFamilyUserIdAndAuthorizedTrue(2L, 40L)).thenReturn(true);

        assertSame(student, service.requireFamilyStudent(2L));
    }

    @Test
    void familyStudentResolutionReturnsNotFoundBeforeOwnershipCheck() {
        when(current.require()).thenReturn(user(40L, Role.FAMILY));
        when(students.findById(999L)).thenReturn(Optional.empty());

        assertThrows(com.wayrapass.exception.ResourceNotFoundException.class,
                () -> service.requireFamilyStudent(999L));
        verifyNoInteractions(links);
    }

    @Test
    void familyStudentResolutionRejectsMissingAuthorizedLink() {
        when(current.require()).thenReturn(user(40L, Role.FAMILY));
        when(students.findById(2L)).thenReturn(Optional.of(student));
        when(links.existsByStudentIdAndFamilyUserIdAndAuthorizedTrue(2L, 40L)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> service.requireFamilyStudent(2L));
    }

    @Test
    void enrollmentAcceptsActiveRouteFromSameInstitutionAndValidStop() {
        prepareStudentEnrollment();
        Stop stop = new Stop();
        stop.setId(8L);
        when(routes.findById(5L)).thenReturn(Optional.of(route));
        when(stops.findById(8L)).thenReturn(Optional.of(stop));
        when(routeStops.findByRouteIdAndStopId(5L, 8L)).thenReturn(Optional.of(routeStop(route, stop, true)));
        when(enrollments.findCurrentForUpdate(any(), any())).thenReturn(Optional.empty());
        when(enrollments.save(any(StudentRouteEnrollment.class))).thenAnswer(invocation -> {
            StudentRouteEnrollment saved = invocation.getArgument(0);
            saved.setId(50L);
            saved.setValidFrom(java.time.LocalDate.now());
            return saved;
        });
        when(lifecycle.lockRouteAndRequireSeat(5L, false)).thenReturn(route);

        var result = service.enroll(new OperationalRequests.Enrollment(5L, 8L));

        assertEquals(5L, result.routeId());
        assertEquals(8L, result.stopId());
    }

    @Test
    void enrollmentRejectsRouteFromDifferentInstitution() {
        prepareStudentEnrollment();
        Route foreignRoute = route(5L, institution(99L), RouteStatus.ACTIVE);
        when(routes.findById(5L)).thenReturn(Optional.of(foreignRoute));

        assertThrows(BusinessRuleException.class,
                () -> service.enroll(new OperationalRequests.Enrollment(5L, 8L)));
        verifyNoInteractions(routeStops, stops);
    }

    @Test
    void enrollmentRejectsInactiveRoute() {
        prepareStudentEnrollment();
        route.setStatus(RouteStatus.INACTIVE);
        when(routes.findById(5L)).thenReturn(Optional.of(route));

        assertThrows(BusinessRuleException.class,
                () -> service.enroll(new OperationalRequests.Enrollment(5L, 8L)));
        verifyNoInteractions(routeStops, stops);
    }

    @Test
    void enrollmentRejectsStopOutsideRoute() {
        prepareStudentEnrollment();
        when(routes.findById(5L)).thenReturn(Optional.of(route));
        Stop stop = new Stop();
        stop.setId(8L);
        when(stops.findById(8L)).thenReturn(Optional.of(stop));
        when(routeStops.findByRouteIdAndStopId(5L, 8L)).thenReturn(Optional.empty());

        assertThrows(BusinessRuleException.class,
                () -> service.enroll(new OperationalRequests.Enrollment(5L, 8L)));
        verify(stops).findById(8L);
    }

    private void prepareStudentEnrollment() {
        when(current.require()).thenReturn(student.getUser());
        when(students.findByUserId(20L)).thenReturn(Optional.of(student));
    }

    private Route route(Long id, Institution institution, RouteStatus status) {
        Route route = new Route();
        route.setId(id);
        route.setInstitution(institution);
        route.setStatus(status);
        return route;
    }

    private Institution institution(Long id) {
        Institution institution = new Institution();
        institution.setId(id);
        return institution;
    }

    private RouteStop routeStop(Route route, Stop stop, boolean active) {
        RouteStop routeStop = new RouteStop();
        routeStop.setRoute(route);
        routeStop.setStop(stop);
        routeStop.setActive(active);
        return routeStop;
    }

    private Student student(Long id, Long userId, Institution institution) {
        Student student = new Student();
        student.setId(id);
        student.setUser(user(userId, Role.STUDENT));
        student.setInstitution(institution);
        return student;
    }

    private User user(Long id, Role role) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        return user;
    }

    private boolean includesReadableEnrollmentStatuses(java.util.Collection<EnrollmentStatus> statuses) {
        return statuses.contains(EnrollmentStatus.ACTIVE) && statuses.contains(EnrollmentStatus.SUSPENDED);
    }
}
