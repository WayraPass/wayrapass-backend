package com.wayrapass.agent;

import com.wayrapass.dto.response.TripQueryResponses;
import com.wayrapass.dto.response.OperationalResponses;
import com.wayrapass.model.Institution;
import com.wayrapass.model.Role;
import com.wayrapass.model.Route;
import com.wayrapass.model.RouteSchedule;
import com.wayrapass.model.RouteStop;
import com.wayrapass.model.RouteStatus;
import com.wayrapass.model.Stop;
import com.wayrapass.model.Student;
import com.wayrapass.model.User;
import com.wayrapass.repository.RouteRepository;
import com.wayrapass.repository.RouteScheduleRepository;
import com.wayrapass.repository.RouteStopRepository;
import com.wayrapass.security.CurrentUser;
import com.wayrapass.service.StudentAccessService;
import com.wayrapass.service.EnrollmentLifecycleService;
import com.wayrapass.service.TripQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WayriaReadServiceTest {
    @Mock RouteRepository routes;
    @Mock RouteStopRepository routeStops;
    @Mock RouteScheduleRepository schedules;
    @Mock CurrentUser currentUser;
    @Mock StudentAccessService studentAccess;
    @Mock EnrollmentLifecycleService enrollmentLifecycle;
    @Mock TripQueryService tripQueries;
    @InjectMocks WayriaReadService service;

    @Test
    void findsAnExistingActiveRouteByCode() {
        when(routes.findAll()).thenReturn(List.of(route("UPC-MON-SMI-01", "San Miguel a UPC")));
        assertThat(service.findRoutes("upc-mon-smi-01")).extracting(item -> item.code())
                .containsExactly("UPC-MON-SMI-01");
    }

    @Test
    void returnsEveryMatchingRouteSoTheAgentCanAskForClarification() {
        when(routes.findAll()).thenReturn(List.of(route("UPC-MON-SMI-01", "San Miguel"), route("UPC-MON-SMI-02", "San Miguel tarde")));
        assertThat(service.findRoutes("san miguel")).hasSize(2);
    }

    @Test
    void returnsNoRouteForAnUnknownReference() {
        when(routes.findAll()).thenReturn(List.of(route("UPC-MON-SMI-01", "San Miguel")));
        assertThat(service.findRoutes("ruta inexistente")).isEmpty();
    }

    @Test
    void returnsRealStopsAndSchedulesForAnExistingRouteCode() {
        Route route = route("UPC-MON-SMI-01", "San Miguel a UPC");
        Stop stop = new Stop(); stop.setName("Plaza San Miguel");
        RouteStop routeStop = new RouteStop(); routeStop.setStop(stop);
        RouteSchedule schedule = new RouteSchedule(); schedule.setDayOfWeek(DayOfWeek.MONDAY);
        schedule.setDepartureTime(LocalTime.of(6, 15)); schedule.setActive(true);
        when(routes.findAll()).thenReturn(List.of(route));
        when(routeStops.findAvailableByRouteIdOrderByStopOrder(1L)).thenReturn(List.of(routeStop));
        when(schedules.findByRouteId(1L)).thenReturn(List.of(schedule));

        var details = service.routeDetails("  upc-mon-smi-01 ");

        assertThat(details).singleElement().satisfies(item -> {
            assertThat(item.code()).isEqualTo("UPC-MON-SMI-01");
            assertThat(item.stops()).containsExactly("Plaza San Miguel");
            assertThat(item.schedules()).containsExactly("MONDAY 06:15");
        });
    }

    @Test
    void returnsAvailableRoutesForTheAuthenticatedStudentsInstitution() {
        var firstStop = new OperationalResponses.RouteSearchOption(1L, "UPC-MON-SMI-01", "San Miguel a UPC",
                "San Miguel", "Monterrico", 1L, "UPC", "Monterrico", 11L, "Plaza San Miguel", null,
                "San Miguel", null, null, null, 1, DayOfWeek.MONDAY, LocalTime.of(6, 15),
                LocalTime.of(6, 15), LocalTime.of(7, 0), false, 0, false);
        var sameRouteOtherSchedule = new OperationalResponses.RouteSearchOption(1L, "UPC-MON-SMI-01", "San Miguel a UPC",
                "San Miguel", "Monterrico", 1L, "UPC", "Monterrico", 11L, "Plaza San Miguel", null,
                "San Miguel", null, null, null, 1, DayOfWeek.MONDAY, LocalTime.of(8, 15),
                LocalTime.of(8, 15), LocalTime.of(9, 0), false, 0, false);
        when(studentAccess.searchRoutes(null, null, null)).thenReturn(List.of(firstStop, sameRouteOtherSchedule));

        assertThat(service.myAvailableRoutes()).extracting(item -> item.routeId())
                .containsExactly(1L);
    }

    @Test
    void returnsAnEmptyListForAStudentWithoutAnEnrollmentInsteadOfInventingARoute() {
        Student student = new Student(); student.setId(7L);
        when(studentAccess.requireCurrentStudent()).thenReturn(student);
        when(enrollmentLifecycle.currentForUse(7L)).thenReturn(Optional.empty());

        assertThat(service.myRouteDetails()).isEmpty();
    }

    @Test
    void returnsTheCurrentStudentServiceStatusFromTripQueryService() {
        User user = new User(); user.setId(9L); user.setRole(Role.STUDENT); user.setFullName("Rafael");
        when(currentUser.require()).thenReturn(user);
        when(tripQueries.myCurrentTrip()).thenReturn(new TripQueryResponses.CurrentTripResponse(false, "No hay un viaje activo o programado para la inscripción actual.", null));
        var status = service.currentServiceStatus(null);
        assertThat(status.studentName()).isEqualTo("Rafael");
        assertThat(status.message()).contains("No hay un viaje");
    }

    private Route route(String code, String name) {
        Institution institution = new Institution(); institution.setName("UPC"); institution.setCampusName("Monterrico"); institution.setActive(true);
        Route route = new Route(); route.setId(1L); route.setCode(code); route.setName(name); route.setOrigin("San Miguel"); route.setDestination("Monterrico"); route.setStatus(RouteStatus.ACTIVE); route.setInstitution(institution);
        return route;
    }
}
