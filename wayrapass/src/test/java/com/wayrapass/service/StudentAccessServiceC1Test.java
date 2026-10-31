package com.wayrapass.service;

import com.wayrapass.dto.request.OperationalRequests;
import com.wayrapass.exception.BusinessRuleException;
import com.wayrapass.exception.ConflictException;
import com.wayrapass.exception.ResourceNotFoundException;
import com.wayrapass.model.*;
import com.wayrapass.repository.*;
import com.wayrapass.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentAccessServiceC1Test {
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
    private Stop stop;

    @BeforeEach
    void setUp() {
        institution = institution(1L, true);
        student = student(2L, 20L, institution);
        route = route(5L, institution, RouteStatus.ACTIVE, "R-12");
        stop = stop(8L, "Próceres", "San Borja", true);
    }

    @Test
    void searchWithoutFiltersReturnsOnlyDatabaseCandidatesAndEstimatedPickup() {
        authenticateStudent();
        when(routes.searchAvailableOptions(1L, null)).thenReturn(List.of(
                projection(5L, 8L, "San Borja", 15, DayOfWeek.MONDAY, LocalTime.of(6, 45))));
        when(favorites.findByStudentId(2L)).thenReturn(List.of());

        var result = service.searchRoutes(null, null, null);

        assertEquals(1, result.size());
        assertEquals(LocalTime.of(7, 0), result.getFirst().estimatedPickupTime());
        assertEquals(0, result.getFirst().differenceMinutes());
        assertFalse(result.getFirst().exactTimeMatch());
        verify(routes).searchAvailableOptions(1L, null);
    }

    @Test
    void searchCombinesDistrictAndTimeAndKeepsOnlyExactPickupOptions() {
        authenticateStudent();
        when(routes.searchAvailableOptions(1L, "San Borja")).thenReturn(List.of(
                projection(5L, 8L, "San Borja", 15, DayOfWeek.MONDAY, LocalTime.of(6, 45)),
                projection(6L, 9L, "San Borja", 10, DayOfWeek.MONDAY, LocalTime.of(7, 20))));
        when(favorites.findByStudentId(2L)).thenReturn(List.of());

        var result = service.searchRoutes(1L, " San Borja ", LocalTime.of(7, 0));

        assertEquals(1, result.size());
        assertEquals(5L, result.getFirst().routeId());
        assertTrue(result.getFirst().exactTimeMatch());
    }

    @Test
    void searchSuggestsNearestAvailablePickupWhenExactTimeDoesNotExist() {
        authenticateStudent();
        when(routes.searchAvailableOptions(1L, null)).thenReturn(List.of(
                projection(5L, 8L, "San Borja", 0, DayOfWeek.MONDAY, LocalTime.of(18, 30)),
                projection(6L, 9L, "Surco", 0, DayOfWeek.MONDAY, LocalTime.of(19, 30))));
        when(favorites.findByStudentId(2L)).thenReturn(List.of());

        var result = service.searchRoutes(null, null, LocalTime.of(18, 0));

        assertEquals(1, result.size());
        assertEquals(LocalTime.of(18, 30), result.getFirst().estimatedPickupTime());
        assertEquals(30, result.getFirst().differenceMinutes());
        assertFalse(result.getFirst().exactTimeMatch());
    }

    @Test
    void searchWithoutMatchesReturnsEmptyList() {
        authenticateStudent();
        when(routes.searchAvailableOptions(1L, "Ancón")).thenReturn(List.of());

        assertEquals(List.of(), service.searchRoutes(null, "Ancón", null));
        verifyNoInteractions(favorites);
    }

    @Test
    void searchForAnotherInstitutionReturnsEmptyWithoutExposingRoutes() {
        authenticateStudent();

        assertEquals(List.of(), service.searchRoutes(99L, null, null));
        verifyNoInteractions(routes, favorites);
    }

    @Test
    void searchRejectsBlankDistrict() {
        authenticateStudent();
        assertThrows(BusinessRuleException.class, () -> service.searchRoutes(null, " ", null));
    }

    @Test
    void duplicateFavoriteIsRejectedAsConflict() {
        authenticateStudent();
        when(routes.findById(5L)).thenReturn(Optional.of(route));
        when(favorites.existsById(new FavoriteRouteId(2L, 5L))).thenReturn(true);

        assertThrows(ConflictException.class, () -> service.favorite(5L));
        verify(favorites, never()).save(any());
    }

    @Test
    void activeRouteFromStudentInstitutionCanBeSavedAndRemovedAsFavorite() {
        authenticateStudent();
        when(routes.findById(5L)).thenReturn(Optional.of(route));
        when(favorites.existsById(new FavoriteRouteId(2L, 5L))).thenReturn(false);

        service.favorite(5L);
        service.unfavorite(5L);

        ArgumentCaptor<FavoriteRoute> saved = ArgumentCaptor.forClass(FavoriteRoute.class);
        verify(favorites).save(saved.capture());
        assertSame(student, saved.getValue().getStudent());
        assertSame(route, saved.getValue().getRoute());
        verify(favorites).deleteById(new FavoriteRouteId(2L, 5L));
    }

    @Test
    void favoriteListMarksRouteUnavailableAfterItBecomesInactive() {
        authenticateStudent();
        route.setStatus(RouteStatus.INACTIVE);
        FavoriteRoute favorite = new FavoriteRoute();
        favorite.setStudent(student);
        favorite.setRoute(route);
        when(favorites.findByStudentId(2L)).thenReturn(List.of(favorite));

        var result = service.favorites();

        assertEquals(1, result.size());
        assertFalse(result.getFirst().available());
        assertEquals(RouteStatus.INACTIVE, result.getFirst().status());
        verifyNoInteractions(routeStops, schedules);
    }

    @Test
    void enrollmentRejectsMissingStopAsNotFound() {
        authenticateStudent();
        when(routes.findById(5L)).thenReturn(Optional.of(route));
        when(stops.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.enroll(new OperationalRequests.Enrollment(5L, 999L)));
        verifyNoInteractions(enrollments);
    }

    @Test
    void enrollmentRejectsInactiveStopAndListsEnabledAlternatives() {
        authenticateStudent();
        stop.setActive(false);
        RouteStop selected = routeStop(route, stop, false, 1);
        Stop alternativeStop = stop(9L, "Av. Canadá", "San Borja", true);
        RouteStop alternative = routeStop(route, alternativeStop, true, 2);
        when(routes.findById(5L)).thenReturn(Optional.of(route));
        when(stops.findById(8L)).thenReturn(Optional.of(stop));
        when(routeStops.findByRouteIdAndStopId(5L, 8L)).thenReturn(Optional.of(selected));
        when(routeStops.findAvailableByRouteIdOrderByStopOrder(5L)).thenReturn(List.of(alternative));

        BusinessRuleException error = assertThrows(BusinessRuleException.class,
                () -> service.enroll(new OperationalRequests.Enrollment(5L, 8L)));

        assertTrue(error.getMessage().contains("9 - Av. Canadá"));
        verifyNoInteractions(enrollments);
    }

    @Test
    void enrollmentRejectsInactiveRouteStopRelationEvenWhenStopIsActive() {
        authenticateStudent();
        RouteStop selected = routeStop(route, stop, false, 1);
        when(routes.findById(5L)).thenReturn(Optional.of(route));
        when(stops.findById(8L)).thenReturn(Optional.of(stop));
        when(routeStops.findByRouteIdAndStopId(5L, 8L)).thenReturn(Optional.of(selected));
        when(routeStops.findAvailableByRouteIdOrderByStopOrder(5L)).thenReturn(List.of());

        assertThrows(BusinessRuleException.class,
                () -> service.enroll(new OperationalRequests.Enrollment(5L, 8L)));
        verifyNoInteractions(enrollments);
    }

    @Test
    void sameRouteAndStopIsRejectedWithoutEndingCurrentEnrollment() {
        authenticateStudent();
        StudentRouteEnrollment currentEnrollment = enrollment(30L, route, stop, EnrollmentStatus.ACTIVE);
        prepareValidSelection(route, stop);
        when(enrollments.findCurrentForUpdate(2L, List.of(EnrollmentStatus.ACTIVE, EnrollmentStatus.SUSPENDED)))
                .thenReturn(Optional.of(currentEnrollment));

        assertThrows(BusinessRuleException.class,
                () -> service.enroll(new OperationalRequests.Enrollment(5L, 8L)));

        assertEquals(EnrollmentStatus.ACTIVE, currentEnrollment.getStatus());
        assertNull(currentEnrollment.getValidUntil());
        verify(enrollments, never()).save(any());
    }

    @Test
    void changingOnlyStopEndsPreviousEnrollmentAndCreatesActiveHistoryEntry() {
        authenticateStudent();
        Stop newStop = stop(9L, "Av. Canadá", "San Borja", true);
        StudentRouteEnrollment previous = enrollment(30L, route, stop, EnrollmentStatus.ACTIVE);
        prepareValidSelection(route, newStop);
        when(enrollments.findCurrentForUpdate(2L, List.of(EnrollmentStatus.ACTIVE, EnrollmentStatus.SUSPENDED)))
                .thenReturn(Optional.of(previous));
        mockEnrollmentSave();
        when(lifecycle.lockRouteAndRequireSeat(5L, true)).thenReturn(route);
        when(lifecycle.today()).thenReturn(LocalDate.now());

        var result = service.enroll(new OperationalRequests.Enrollment(5L, 9L));

        assertEquals(EnrollmentStatus.ENDED, previous.getStatus());
        assertEquals(LocalDate.now(), previous.getValidUntil());
        assertEquals(5L, result.routeId());
        assertEquals(9L, result.stopId());
        assertEquals(EnrollmentStatus.ACTIVE, result.status());
    }

    @Test
    void changingRouteAndStopPreservesPreviousEnrollment() {
        authenticateStudent();
        Route newRoute = route(6L, institution, RouteStatus.ACTIVE, "R-08");
        Stop newStop = stop(9L, "Av. Canadá", "Lince", true);
        StudentRouteEnrollment previous = enrollment(30L, route, stop, EnrollmentStatus.SUSPENDED);
        prepareValidSelection(newRoute, newStop);
        when(enrollments.findCurrentForUpdate(2L, List.of(EnrollmentStatus.ACTIVE, EnrollmentStatus.SUSPENDED)))
                .thenReturn(Optional.of(previous));
        mockEnrollmentSave();
        when(lifecycle.lockRouteAndRequireSeat(6L, false)).thenReturn(newRoute);
        when(lifecycle.today()).thenReturn(LocalDate.now());

        var result = service.enroll(new OperationalRequests.Enrollment(6L, 9L));

        assertEquals(EnrollmentStatus.ENDED, previous.getStatus());
        assertEquals(6L, result.routeId());
        assertEquals(9L, result.stopId());
    }

    @Test
    void invalidNewRouteLeavesPreviousEnrollmentUntouched() {
        authenticateStudent();
        StudentRouteEnrollment previous = enrollment(30L, route, stop, EnrollmentStatus.ACTIVE);
        Route inactive = route(6L, institution, RouteStatus.INACTIVE, "R-08");
        when(routes.findById(6L)).thenReturn(Optional.of(inactive));

        assertThrows(BusinessRuleException.class,
                () -> service.enroll(new OperationalRequests.Enrollment(6L, 9L)));

        assertEquals(EnrollmentStatus.ACTIVE, previous.getStatus());
        verify(enrollments, never()).findCurrentForUpdate(anyLong(), any());
        verify(enrollments, never()).save(any());
    }

    @Test
    void studentWithoutProfileCannotSearchOrEnroll() {
        User user = user(20L, Role.STUDENT);
        when(current.require()).thenReturn(user);
        when(students.findByUserId(20L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.searchRoutes(null, null, null));
    }

    private void authenticateStudent() {
        when(current.require()).thenReturn(student.getUser());
        when(students.findByUserId(20L)).thenReturn(Optional.of(student));
    }

    private void prepareValidSelection(Route selectedRoute, Stop selectedStop) {
        when(routes.findById(selectedRoute.getId())).thenReturn(Optional.of(selectedRoute));
        when(stops.findById(selectedStop.getId())).thenReturn(Optional.of(selectedStop));
        when(routeStops.findByRouteIdAndStopId(selectedRoute.getId(), selectedStop.getId()))
                .thenReturn(Optional.of(routeStop(selectedRoute, selectedStop, true, 1)));
    }

    private void mockEnrollmentSave() {
        when(enrollments.save(any(StudentRouteEnrollment.class))).thenAnswer(invocation -> {
            StudentRouteEnrollment saved = invocation.getArgument(0);
            saved.setId(50L);
            saved.setValidFrom(LocalDate.now());
            return saved;
        });
    }

    private Projection projection(Long routeId, Long stopId, String district, int offset,
                                  DayOfWeek day, LocalTime departure) {
        return new Projection(routeId, "R-" + routeId, "Ruta " + routeId, "Origen", "Destino",
                1L, "UPC", "San Isidro", stopId, "Paradero " + stopId, "Dirección", district,
                1, offset, day, departure, departure.plusHours(1));
    }

    private Institution institution(Long id, boolean active) {
        Institution value = new Institution();
        value.setId(id);
        value.setActive(active);
        return value;
    }

    private Student student(Long id, Long userId, Institution value) {
        Student result = new Student();
        result.setId(id);
        result.setUser(user(userId, Role.STUDENT));
        result.setInstitution(value);
        return result;
    }

    private User user(Long id, Role role) {
        User value = new User();
        value.setId(id);
        value.setRole(role);
        return value;
    }

    private Route route(Long id, Institution value, RouteStatus status, String code) {
        Route result = new Route();
        result.setId(id);
        result.setInstitution(value);
        result.setStatus(status);
        result.setCode(code);
        result.setName("Ruta " + code);
        return result;
    }

    private Stop stop(Long id, String name, String district, boolean active) {
        Stop value = new Stop();
        value.setId(id);
        value.setName(name);
        value.setDistrict(district);
        value.setActive(active);
        return value;
    }

    private RouteStop routeStop(Route value, Stop selectedStop, boolean active, int order) {
        RouteStop relation = new RouteStop();
        relation.setRoute(value);
        relation.setStop(selectedStop);
        relation.setActive(active);
        relation.setStopOrder(order);
        return relation;
    }

    private StudentRouteEnrollment enrollment(Long id, Route selectedRoute, Stop selectedStop,
                                              EnrollmentStatus status) {
        StudentRouteEnrollment value = new StudentRouteEnrollment();
        value.setId(id);
        value.setStudent(student);
        value.setRoute(selectedRoute);
        value.setStop(selectedStop);
        value.setStatus(status);
        value.setValidFrom(LocalDate.now().minusMonths(1));
        return value;
    }

    private record Projection(Long routeId, String routeCode, String routeName, String origin,
                              String destination, Long institutionId, String institutionName,
                              String campusName, Long stopId, String stopName, String stopAddress,
                              String stopDistrict, Integer stopOrder, Integer estimatedMinutesFromStart,
                              DayOfWeek dayOfWeek, LocalTime departureTime,
                              LocalTime estimatedArrivalTime) implements RouteSearchProjection {
        public Long getRouteId() { return routeId; }
        public String getRouteCode() { return routeCode; }
        public String getRouteName() { return routeName; }
        public String getOrigin() { return origin; }
        public String getDestination() { return destination; }
        public Long getInstitutionId() { return institutionId; }
        public String getInstitutionName() { return institutionName; }
        public String getCampusName() { return campusName; }
        public Long getStopId() { return stopId; }
        public String getStopName() { return stopName; }
        public String getStopAddress() { return stopAddress; }
        public String getStopDistrict() { return stopDistrict; }
        public java.math.BigDecimal getStopLatitude() { return null; }
        public java.math.BigDecimal getStopLongitude() { return null; }
        public Integer getStopOrder() { return stopOrder; }
        public Integer getEstimatedMinutesFromStart() { return estimatedMinutesFromStart; }
        public DayOfWeek getDayOfWeek() { return dayOfWeek; }
        public LocalTime getDepartureTime() { return departureTime; }
        public LocalTime getEstimatedArrivalTime() { return estimatedArrivalTime; }
    }
}
