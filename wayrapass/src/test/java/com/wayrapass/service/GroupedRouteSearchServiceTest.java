package com.wayrapass.service;

import com.wayrapass.exception.ResourceNotFoundException;
import com.wayrapass.model.*;
import com.wayrapass.repository.*;
import com.wayrapass.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GroupedRouteSearchServiceTest {
    @Mock CurrentUser current;
    @Mock InstitutionRepository institutions;
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
    private Institution home;

    @BeforeEach
    void authenticate() {
        home = institution(1L, "UPC", "Monterrico");
        User user = new User();
        user.setId(20L);
        user.setRole(Role.STUDENT);
        student = new Student();
        student.setId(2L);
        student.setUser(user);
        student.setInstitution(home);
        when(current.require()).thenReturn(user);
        when(students.findByUserId(20L)).thenReturn(Optional.of(student));
    }

    @Test
    void searchWithoutFiltersGroupsOneRouteWithStopsSchedulesAndFavoriteState() {
        Route route = route(5L, home, "UPC-MON-SMI-01");
        List<RouteStop> relations = List.of(
                routeStop(route, stop(101L, "Plaza San Miguel", "San Miguel"), 1, 0),
                routeStop(route, stop(102L, "Av. La Marina", "San Miguel"), 2, 10),
                routeStop(route, stop(103L, "Óvalo Higuereta", "Surco"), 3, 30),
                routeStop(route, stop(104L, "Campus UPC Monterrico", "Surco"), 4, 45));
        List<RouteSchedule> routeSchedules = List.of(schedule(501L, route, LocalTime.of(6, 15), LocalTime.of(7, 0)),
                schedule(502L, route, LocalTime.of(8, 15), LocalTime.of(9, 0)));
        FavoriteRoute favorite = new FavoriteRoute();
        favorite.setStudent(student);
        favorite.setRoute(route);
        when(routes.searchAvailableRoutes(null, null, null, null)).thenReturn(List.of(route));
        when(routeStops.findAvailableByRouteIds(List.of(5L))).thenReturn(relations);
        when(schedules.findByRouteIdInAndActiveTrueOrderByRouteCodeAscDayOfWeekAscDepartureTimeAsc(List.of(5L)))
                .thenReturn(routeSchedules);
        when(favorites.findByStudentId(2L)).thenReturn(List.of(favorite));

        var result = service.searchGroupedRoutes(null, null, null, null, null);

        assertEquals(1, result.size());
        assertEquals("UPC-MON-SMI-01", result.getFirst().routeCode());
        assertEquals("UPC — Monterrico", result.getFirst().institution().displayName());
        assertEquals(4, result.getFirst().stops().size());
        assertEquals(2, result.getFirst().schedules().size());
        assertTrue(result.getFirst().favorite());
    }

    @Test
    void searchAllowsForeignCampusAndForwardsCombinedHumanFilters() {
        Institution pucp = institution(9L, "PUCP", "San Miguel");
        Route route = route(8L, pucp, "PUCP-NOR-01");
        Stop pickup = stop(101L, "Plaza San Miguel", "San Miguel");
        when(institutions.findById(9L)).thenReturn(Optional.of(pucp));
        when(routes.findById(8L)).thenReturn(Optional.of(route));
        when(stops.findById(101L)).thenReturn(Optional.of(pickup));
        when(routes.searchAvailableRoutes(9L, "San Miguel", 8L, 101L)).thenReturn(List.of(route));
        when(routeStops.findAvailableByRouteIds(List.of(8L))).thenReturn(List.of(routeStop(route, pickup, 1, 0)));
        when(schedules.findByRouteIdInAndActiveTrueOrderByRouteCodeAscDayOfWeekAscDepartureTimeAsc(List.of(8L)))
                .thenReturn(List.of(schedule(601L, route, LocalTime.of(8, 0), LocalTime.of(9, 0))));
        when(favorites.findByStudentId(2L)).thenReturn(List.of());

        var result = service.searchGroupedRoutes(9L, 8L, " San Miguel ", 101L, null);

        assertEquals("PUCP — San Miguel", result.getFirst().institution().displayName());
        verify(routes).searchAvailableRoutes(9L, "San Miguel", 8L, 101L);
    }

    @Test
    void timeFilterKeepsRouteWithNearestEstimatedPickupTime() {
        Route exact = route(5L, home, "EXACT");
        Route later = route(6L, home, "LATER");
        when(routes.searchAvailableRoutes(null, null, null, null)).thenReturn(List.of(exact, later));
        when(routeStops.findAvailableByRouteIds(List.of(5L, 6L))).thenReturn(List.of(
                routeStop(exact, stop(101L, "Exacto", "Lima"), 1, 15),
                routeStop(exact, stop(102L, "Campus exacto", "Lima"), 2, 45),
                routeStop(later, stop(103L, "Tarde", "Lima"), 1, 20),
                routeStop(later, stop(104L, "Campus tarde", "Lima"), 2, 50)));
        when(schedules.findByRouteIdInAndActiveTrueOrderByRouteCodeAscDayOfWeekAscDepartureTimeAsc(List.of(5L, 6L)))
                .thenReturn(List.of(schedule(501L, exact, LocalTime.of(8, 45), LocalTime.of(9, 30)),
                        schedule(502L, later, LocalTime.of(9, 20), LocalTime.of(10, 10))));
        when(favorites.findByStudentId(2L)).thenReturn(List.of());

        var result = service.searchGroupedRoutes(null, null, null, null, LocalTime.of(9, 0));

        assertEquals(1, result.size());
        assertEquals("EXACT", result.getFirst().routeCode());
    }

    @Test
    void nonexistentInstitutionRouteAndStopReturnNotFound() {
        when(institutions.findById(999L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> service.searchGroupedRoutes(999L, null, null, null, null));

        when(routes.findById(999L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> service.searchGroupedRoutes(null, 999L, null, null, null));

        when(stops.findById(999L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> service.searchGroupedRoutes(null, null, null, 999L, null));
    }

    private static Institution institution(Long id, String name, String campus) {
        Institution institution = new Institution();
        institution.setId(id);
        institution.setName(name);
        institution.setCampusName(campus);
        institution.setDistrict("Lima");
        institution.setActive(true);
        return institution;
    }

    private static Route route(Long id, Institution institution, String code) {
        Route route = new Route();
        route.setId(id);
        route.setInstitution(institution);
        route.setCode(code);
        route.setName("Ruta " + code);
        route.setOrigin("Origen");
        route.setDestination("Destino");
        route.setStatus(RouteStatus.ACTIVE);
        return route;
    }

    private static Stop stop(Long id, String name, String district) {
        Stop stop = new Stop();
        stop.setId(id);
        stop.setName(name);
        stop.setDistrict(district);
        stop.setAddress("Dirección pública");
        stop.setActive(true);
        return stop;
    }

    private static RouteStop routeStop(Route route, Stop stop, int order, int offset) {
        RouteStop relation = new RouteStop();
        relation.setRoute(route);
        relation.setStop(stop);
        relation.setStopOrder(order);
        relation.setEstimatedMinutesFromStart(offset);
        relation.setActive(true);
        return relation;
    }

    private static RouteSchedule schedule(Long id, Route route, LocalTime departure, LocalTime arrival) {
        RouteSchedule schedule = new RouteSchedule();
        schedule.setId(id);
        schedule.setRoute(route);
        schedule.setDayOfWeek(DayOfWeek.MONDAY);
        schedule.setDepartureTime(departure);
        schedule.setEstimatedArrivalTime(arrival);
        schedule.setActive(true);
        return schedule;
    }
}
