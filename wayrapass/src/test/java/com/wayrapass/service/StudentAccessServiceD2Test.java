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

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentAccessServiceD2Test {
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

    @BeforeEach
    void setUp() {
        institution = new Institution();
        institution.setId(1L);
        institution.setActive(true);
        User user = new User();
        user.setId(20L);
        user.setRole(Role.STUDENT);
        student = new Student();
        student.setId(2L);
        student.setUser(user);
        student.setInstitution(institution);
        route = route(5L, "R-12", RouteStatus.ACTIVE);
    }

    @Test
    void locationOrdersNearestStopFirstAndUsesStableTieBreakers() {
        authenticate();
        when(routes.searchAvailableOptions(1L, null)).thenReturn(List.of(
                projection(5L, 9L, 2, 10, "-12.100000", "-77.030000", LocalTime.of(6, 50)),
                projection(5L, 8L, 1, 10, "-12.100000", "-77.030000", LocalTime.of(6, 50)),
                projection(5L, 7L, 3, 10, "-12.120000", "-77.030000", LocalTime.of(6, 50))));
        when(favorites.findByStudentId(2L)).thenReturn(List.of());

        var result = service.searchRoutes(null, null, null, null,
                new BigDecimal("-12.100000"), new BigDecimal("-77.030000"));

        assertEquals(List.of(8L, 9L, 7L), result.stream().map(option -> option.stopId()).toList());
        assertEquals(0L, result.getFirst().distanceMeters());
        assertTrue(result.getLast().distanceMeters() > 0);
    }

    @Test
    void searchWithoutLocationPreservesOperationalOrderAndNullDistance() {
        authenticate();
        when(routes.searchAvailableOptions(1L, null)).thenReturn(List.of(
                projection(5L, 8L, 1, 10, "-12.120000", "-77.030000", LocalTime.of(6, 50)),
                projection(5L, 9L, 2, 10, "-12.100000", "-77.030000", LocalTime.of(6, 50))));
        when(favorites.findByStudentId(2L)).thenReturn(List.of());

        var result = service.searchRoutes(null, null, null);

        assertEquals(List.of(8L, 9L), result.stream().map(option -> option.stopId()).toList());
        assertTrue(result.stream().allMatch(option -> option.distanceMeters() == null));
    }

    @Test
    void timeCompatibilityIsAppliedBeforeGeographicDistance() {
        authenticate();
        when(routes.searchAvailableOptions(1L, null)).thenReturn(List.of(
                projection(5L, 8L, 1, 0, "-12.100000", "-77.030000", LocalTime.of(7, 30)),
                projection(6L, 9L, 1, 0, "-12.200000", "-77.030000", LocalTime.of(7, 0))));
        when(favorites.findByStudentId(2L)).thenReturn(List.of());

        var result = service.searchRoutes(null, null, null, LocalTime.of(7, 0),
                new BigDecimal("-12.100000"), new BigDecimal("-77.030000"));

        assertEquals(1, result.size());
        assertEquals(9L, result.getFirst().stopId());
        assertTrue(result.getFirst().exactTimeMatch());
    }

    @Test
    void nearestAvailableTimeThenOrdersStopsByDistance() {
        authenticate();
        when(routes.searchAvailableOptions(1L, null)).thenReturn(List.of(
                projection(5L, 8L, 1, 0, "-12.101000", "-77.030000", LocalTime.of(7, 30)),
                projection(6L, 9L, 1, 0, "-12.110000", "-77.030000", LocalTime.of(7, 30)),
                projection(7L, 10L, 1, 0, "-12.100000", "-77.030000", LocalTime.of(8, 0))));
        when(favorites.findByStudentId(2L)).thenReturn(List.of());

        var result = service.searchRoutes(null, null, null, LocalTime.of(7, 0),
                new BigDecimal("-12.100000"), new BigDecimal("-77.030000"));

        assertEquals(List.of(8L, 9L), result.stream().map(option -> option.stopId()).toList());
        assertEquals(30, result.getFirst().differenceMinutes());
    }

    @Test
    void changeOnlyStopExcludesCurrentStopAndFiltersByCurrentRoute() {
        authenticate();
        Stop currentStop = stop(8L, "Próceres", "-12.100000", "-77.030000", true);
        StudentRouteEnrollment currentEnrollment = enrollment(30L, route, currentStop, EnrollmentStatus.ACTIVE);
        when(enrollments.findByStudentIdAndStatusIn(2L,
                List.of(EnrollmentStatus.ACTIVE, EnrollmentStatus.SUSPENDED)))
                .thenReturn(Optional.of(currentEnrollment));
        when(routes.searchAvailableOptions(1L, null, 5L)).thenReturn(List.of(
                projection(5L, 8L, 1, 0, "-12.100000", "-77.030000", LocalTime.of(7, 0)),
                projection(5L, 9L, 2, 5, "-12.101000", "-77.030000", LocalTime.of(6, 55))));
        when(favorites.findByStudentId(2L)).thenReturn(List.of());

        var result = service.searchRoutes(null, 5L, null, LocalTime.of(7, 0),
                new BigDecimal("-12.100000"), new BigDecimal("-77.030000"));

        assertEquals(1, result.size());
        assertEquals(9L, result.getFirst().stopId());
        verify(routes).searchAvailableOptions(1L, null, 5L);
    }

    @Test
    void locationSearchExcludesStopsWithoutCoordinates() {
        authenticate();
        when(routes.searchAvailableOptions(1L, null)).thenReturn(List.of(
                projection(5L, 8L, 1, 0, null, null, LocalTime.of(7, 0))));

        var result = service.searchRoutes(null, null, null, null,
                new BigDecimal("-12.100000"), new BigDecimal("-77.030000"));

        assertTrue(result.isEmpty());
        verifyNoInteractions(favorites);
    }

    @Test
    void locationMustBeCompleteAndInsideGeographicRanges() {
        authenticate();

        assertThrows(BusinessRuleException.class, () -> service.searchRoutes(null, null, null, null,
                BigDecimal.ZERO, null));
        assertThrows(BusinessRuleException.class, () -> service.searchRoutes(null, null, null, null,
                new BigDecimal("90.000001"), BigDecimal.ZERO));
        assertThrows(BusinessRuleException.class, () -> service.searchRoutes(null, null, null, null,
                BigDecimal.ZERO, new BigDecimal("-180.000001")));
        verifyNoInteractions(routes);
    }

    @Test
    void unavailableStopAlternativesAreOrderedByDistanceAndKeepPickupTime() {
        authenticate();
        Stop unavailable = stop(8L, "Fuera de servicio", "-12.100000", "-77.030000", false);
        Stop far = stop(9L, "Lejano", "-12.130000", "-77.030000", true);
        Stop near = stop(10L, "Cercano", "-12.101000", "-77.030000", true);
        when(routes.findById(5L)).thenReturn(Optional.of(route));
        when(stops.findById(8L)).thenReturn(Optional.of(unavailable));
        when(routeStops.findByRouteIdAndStopId(5L, 8L))
                .thenReturn(Optional.of(routeStop(route, unavailable, false, 1, 0)));
        when(routeStops.findAvailableByRouteIdOrderByStopOrder(5L)).thenReturn(List.of(
                routeStop(route, far, true, 2, 20), routeStop(route, near, true, 3, 10)));
        when(schedules.findByRouteId(5L)).thenReturn(List.of(schedule(route, LocalTime.of(6, 50))));

        BusinessRuleException error = assertThrows(BusinessRuleException.class,
                () -> service.enroll(new OperationalRequests.Enrollment(5L, 8L,
                        new BigDecimal("-12.100000"), new BigDecimal("-77.030000"), LocalTime.of(7, 0))));

        assertTrue(error.getMessage().indexOf("10 - Cercano") < error.getMessage().indexOf("9 - Lejano"));
        assertTrue(error.getMessage().contains("salida aproximada 07:00"));
        verifyNoInteractions(enrollments);
    }

    @Test
    void unavailableStopWithoutGeocodedAlternativesKeepsCurrentEnrollment() {
        authenticate();
        Stop unavailable = stop(8L, "Fuera de servicio", "-12.100000", "-77.030000", false);
        Stop withoutLocation = stop(9L, "Sin coordenadas", null, null, true);
        when(routes.findById(5L)).thenReturn(Optional.of(route));
        when(stops.findById(8L)).thenReturn(Optional.of(unavailable));
        when(routeStops.findByRouteIdAndStopId(5L, 8L))
                .thenReturn(Optional.of(routeStop(route, unavailable, false, 1, 0)));
        when(routeStops.findAvailableByRouteIdOrderByStopOrder(5L))
                .thenReturn(List.of(routeStop(route, withoutLocation, true, 2, 10)));

        BusinessRuleException error = assertThrows(BusinessRuleException.class,
                () -> service.enroll(new OperationalRequests.Enrollment(5L, 8L,
                        new BigDecimal("-12.100000"), new BigDecimal("-77.030000"), null)));

        assertEquals("No existen paraderos disponibles para la ubicación seleccionada.", error.getMessage());
        verifyNoInteractions(enrollments);
    }

    @Test
    void routeChangeEndsPreviousEnrollmentAndReturnsNearestPickupTime() {
        authenticate();
        Stop previousStop = stop(8L, "Próceres", "-12.100000", "-77.030000", true);
        StudentRouteEnrollment previous = enrollment(30L, route, previousStop, EnrollmentStatus.ACTIVE);
        Route newRoute = route(6L, "R-08", RouteStatus.ACTIVE);
        Stop newStop = stop(9L, "Canadá", "-12.101000", "-77.030000", true);
        RouteStop relation = routeStop(newRoute, newStop, true, 1, 10);
        when(routes.findById(6L)).thenReturn(Optional.of(newRoute));
        when(stops.findById(9L)).thenReturn(Optional.of(newStop));
        when(routeStops.findByRouteIdAndStopId(6L, 9L)).thenReturn(Optional.of(relation));
        when(enrollments.findCurrentForUpdate(2L,
                List.of(EnrollmentStatus.ACTIVE, EnrollmentStatus.SUSPENDED))).thenReturn(Optional.of(previous));
        when(schedules.findByRouteId(6L)).thenReturn(List.of(
                schedule(newRoute, LocalTime.of(6, 0)), schedule(newRoute, LocalTime.of(7, 20))));
        when(enrollments.save(any(StudentRouteEnrollment.class))).thenAnswer(invocation -> {
            StudentRouteEnrollment saved = invocation.getArgument(0);
            saved.setId(31L);
            saved.setValidFrom(LocalDate.now());
            return saved;
        });
        when(lifecycle.lockRouteAndRequireSeat(6L, false)).thenReturn(newRoute);
        when(lifecycle.today()).thenReturn(LocalDate.now());

        var result = service.enroll(new OperationalRequests.Enrollment(6L, 9L,
                new BigDecimal("-12.100000"), new BigDecimal("-77.030000"), LocalTime.of(7, 0)));

        assertEquals(EnrollmentStatus.ENDED, previous.getStatus());
        assertEquals(LocalDate.now(), previous.getValidUntil());
        assertEquals(EnrollmentStatus.ACTIVE, result.status());
        assertEquals(LocalTime.of(7, 30), result.estimatedPickupTime());
    }

    private void authenticate() {
        when(current.require()).thenReturn(student.getUser());
        when(students.findByUserId(20L)).thenReturn(Optional.of(student));
    }

    private Route route(Long id, String code, RouteStatus status) {
        Route value = new Route();
        value.setId(id);
        value.setInstitution(institution);
        value.setCode(code);
        value.setName("Ruta " + code);
        value.setStatus(status);
        return value;
    }

    private Stop stop(Long id, String name, String latitude, String longitude, boolean active) {
        Stop value = new Stop();
        value.setId(id);
        value.setName(name);
        value.setLatitude(latitude == null ? null : new BigDecimal(latitude));
        value.setLongitude(longitude == null ? null : new BigDecimal(longitude));
        value.setActive(active);
        return value;
    }

    private RouteStop routeStop(Route selectedRoute, Stop selectedStop, boolean active, int order, int offset) {
        RouteStop value = new RouteStop();
        value.setRoute(selectedRoute);
        value.setStop(selectedStop);
        value.setActive(active);
        value.setStopOrder(order);
        value.setEstimatedMinutesFromStart(offset);
        return value;
    }

    private RouteSchedule schedule(Route selectedRoute, LocalTime departure) {
        RouteSchedule value = new RouteSchedule();
        value.setRoute(selectedRoute);
        value.setDayOfWeek(DayOfWeek.MONDAY);
        value.setDepartureTime(departure);
        value.setActive(true);
        return value;
    }

    private StudentRouteEnrollment enrollment(Long id, Route selectedRoute, Stop selectedStop,
                                              EnrollmentStatus status) {
        StudentRouteEnrollment value = new StudentRouteEnrollment();
        value.setId(id);
        value.setStudent(student);
        value.setRoute(selectedRoute);
        value.setStop(selectedStop);
        value.setStatus(status);
        value.setValidFrom(LocalDate.now().minusDays(10));
        return value;
    }

    private Projection projection(Long routeId, Long stopId, int stopOrder, int offset,
                                  String latitude, String longitude, LocalTime departure) {
        return new Projection(routeId, "R-" + routeId, stopId, stopOrder, offset,
                latitude == null ? null : new BigDecimal(latitude),
                longitude == null ? null : new BigDecimal(longitude), departure);
    }

    private record Projection(Long routeId, String routeCode, Long stopId, Integer stopOrder,
                              Integer estimatedMinutesFromStart, BigDecimal stopLatitude,
                              BigDecimal stopLongitude, LocalTime departureTime)
            implements RouteSearchProjection {
        public Long getRouteId() { return routeId; }
        public String getRouteCode() { return routeCode; }
        public String getRouteName() { return "Ruta " + routeCode; }
        public String getOrigin() { return "Origen"; }
        public String getDestination() { return "Destino"; }
        public Long getInstitutionId() { return 1L; }
        public String getInstitutionName() { return "UPC"; }
        public String getCampusName() { return "San Isidro"; }
        public Long getStopId() { return stopId; }
        public String getStopName() { return "Paradero " + stopId; }
        public String getStopAddress() { return "Dirección"; }
        public String getStopDistrict() { return "San Borja"; }
        public BigDecimal getStopLatitude() { return stopLatitude; }
        public BigDecimal getStopLongitude() { return stopLongitude; }
        public Integer getStopOrder() { return stopOrder; }
        public Integer getEstimatedMinutesFromStart() { return estimatedMinutesFromStart; }
        public DayOfWeek getDayOfWeek() { return DayOfWeek.MONDAY; }
        public LocalTime getDepartureTime() { return departureTime; }
        public LocalTime getEstimatedArrivalTime() { return departureTime.plusHours(1); }
    }
}
