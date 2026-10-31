package com.wayrapass.service;

import com.wayrapass.dto.request.OperationalRequests;
import com.wayrapass.exception.BusinessRuleException;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OperationalServiceC3Test {
    @Mock CurrentUser current;
    @Mock TripRepository trips;
    @Mock RouteRepository routes;
    @Mock RouteScheduleRepository schedules;
    @Mock DriverRepository drivers;
    @Mock VehicleRepository vehicles;
    @Mock StudentRepository students;
    @Mock StopRepository stops;
    @Mock RouteStopRepository routeStops;
    @Mock StudentRouteEnrollmentRepository enrollments;
    @Mock BoardingRepository boardings;
    @Mock IncidentRepository incidents;
    @Mock TripLocationRepository locations;
    @Mock NotificationRepository notifications;
    @Mock StudentFamilyLinkRepository links;
    @Mock UserRepository users;
    @Mock AiActionRequestRepository aiRequests;
    @Mock StudentAccessService studentAccess;
    @InjectMocks OperationalService service;

    private Route route;
    private Driver driver;

    @BeforeEach
    void setUp() {
        route = new Route();
        route.setId(5L);
        route.setCode("R-12");
        route.setName("Ate - San Isidro");
        route.setOrigin("Ate");
        route.setDestination("UPC San Isidro");
        driver = new Driver();
        driver.setId(10L);
        driver.setUser(user(20L, Role.DRIVER));
    }

    @Test
    void driverReceivesOnlyOwnTripsForSelectedPeriodWithRouteStopsAndVehicle() {
        Trip included = trip(30L, TripStatus.SCHEDULED, Instant.parse("2026-11-10T11:45:00Z"));
        Vehicle vehicle = new Vehicle();
        vehicle.setId(12L);
        vehicle.setPlate("ABC-123");
        vehicle.setBrand("Mercedes");
        vehicle.setModel("Sprinter");
        vehicle.setCapacity(24);
        included.setVehicle(vehicle);
        Trip outside = trip(31L, TripStatus.COMPLETED, Instant.parse("2026-11-12T11:45:00Z"));
        when(current.id()).thenReturn(20L);
        when(trips.findByDriverUserId(20L)).thenReturn(List.of(included, outside));
        when(routeStops.findByRouteIdOrderByStopOrder(5L)).thenReturn(List.of(routeStop(8L, 1), routeStop(9L, 2)));

        var result = service.myDriverTrips(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 10));

        assertEquals(1, result.size());
        var assignment = result.getFirst();
        assertEquals("R-12", assignment.routeCode());
        assertEquals("ABC-123", assignment.vehicle().plate());
        assertEquals(2, assignment.stops().size());
        assertEquals(1, assignment.stops().getFirst().stopOrder());
        verify(trips).findByDriverUserId(20L);
    }

    @Test
    void driverTripWithoutVehicleReturnsNullInsteadOfFailing() {
        Trip trip = trip(30L, TripStatus.BOARDING, Instant.now().plusSeconds(3600));
        when(current.id()).thenReturn(20L);
        when(trips.findByDriverUserId(20L)).thenReturn(List.of(trip));
        when(routeStops.findByRouteIdOrderByStopOrder(5L)).thenReturn(List.of());

        var result = service.myDriverTrips(null, null);

        assertNull(result.getFirst().vehicle());
        assertEquals(TripStatus.BOARDING, result.getFirst().status());
    }

    @Test
    void invalidDriverPeriodIsRejected() {
        assertThrows(BusinessRuleException.class, () -> service.myDriverTrips(
                LocalDate.of(2026, 11, 11), LocalDate.of(2026, 11, 10)));
        verifyNoInteractions(trips);
    }

    @Test
    void delayIncidentUpdatesEtaNotifiesCoordinatorAndRespectsDisabledFamilyDelayPreference() {
        Trip trip = operationalTrip(TripStatus.IN_PROGRESS);
        Student student = enrolledStudent();
        StudentFamilyLink link = familyLink(student, false, true);
        User coordinator = user(50L, Role.COORDINATOR);
        when(current.id()).thenReturn(20L);
        when(trips.findByIdForUpdate(30L)).thenReturn(Optional.of(trip));
        when(incidents.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(enrollments.findByRouteIdAndStatus(5L, EnrollmentStatus.ACTIVE)).thenReturn(List.of(enrollment(student)));
        when(links.findByStudentIdAndAuthorizedTrue(2L)).thenReturn(List.of(link));
        when(users.findByRoleAndEnabledTrue(Role.COORDINATOR)).thenReturn(List.of(coordinator));

        var result = service.incident(new OperationalRequests.Incident(
                30L, IncidentType.TRAFFIC_DELAY, "Congestión intensa", 10, IncidentPriority.NORMAL));

        assertEquals(Instant.parse("2026-11-10T13:10:00Z"), trip.getEstimatedArrivalAt());
        assertEquals("Congestión intensa", result.description());
        assertEquals(5L, result.routeId());
        assertEquals(10L, result.driverId());
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notifications, times(2)).save(captor.capture());
        assertEquals(List.of(30L, 50L), captor.getAllValues().stream().map(n -> n.getRecipient().getId()).sorted().toList());
    }

    @Test
    void incidentPreferenceAllowsFamilyIncidentNotification() {
        Trip trip = operationalTrip(TripStatus.BOARDING);
        Student student = enrolledStudent();
        StudentFamilyLink link = familyLink(student, true, true);
        when(current.id()).thenReturn(20L);
        when(trips.findByIdForUpdate(30L)).thenReturn(Optional.of(trip));
        when(incidents.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(enrollments.findByRouteIdAndStatus(5L, EnrollmentStatus.ACTIVE)).thenReturn(List.of(enrollment(student)));
        when(links.findByStudentIdAndAuthorizedTrue(2L)).thenReturn(List.of(link));

        service.incident(new OperationalRequests.Incident(
                30L, IncidentType.DETOUR, "Desvío preventivo", 0, IncidentPriority.NORMAL));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notifications, times(2)).save(captor.capture());
        assertTrue(captor.getAllValues().stream().anyMatch(n -> n.getRecipient().getId().equals(40L)));
    }

    @Test
    void vehicleFailureIsAutomaticallyPrioritized() {
        Trip trip = operationalTrip(TripStatus.IN_PROGRESS);
        when(current.id()).thenReturn(20L);
        when(trips.findByIdForUpdate(30L)).thenReturn(Optional.of(trip));
        when(incidents.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.incident(new OperationalRequests.Incident(
                30L, IncidentType.VEHICLE_FAILURE, "Unidad no disponible", 0, IncidentPriority.NORMAL));

        assertEquals(IncidentPriority.HIGH, result.priority());
    }

    @Test
    void completedTripNotifiesArrivalOnlyToFamiliesThatEnabledIt() {
        Trip trip = operationalTrip(TripStatus.IN_PROGRESS);
        Student student = enrolledStudent();
        StudentFamilyLink link = familyLink(student, true, true);
        link.setNotifyArrival(false);
        User coordinator = user(1L, Role.COORDINATOR);
        when(current.require()).thenReturn(coordinator);
        when(trips.findByIdForUpdate(30L)).thenReturn(Optional.of(trip));
        when(enrollments.findByRouteIdAndStatus(5L, EnrollmentStatus.ACTIVE)).thenReturn(List.of(enrollment(student)));
        when(links.findByStudentIdAndAuthorizedTrue(2L)).thenReturn(List.of(link));

        service.status(30L, TripStatus.COMPLETED);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).save(captor.capture());
        assertEquals(30L, captor.getValue().getRecipient().getId());
        assertEquals(NotificationType.ARRIVAL, captor.getValue().getType());
    }

    @Test
    void vehicleReassignmentMarksAssignmentChangedAndRespectsFamilyPreference() {
        Trip trip = trip(30L, TripStatus.SCHEDULED, Instant.now().plusSeconds(3600));
        Vehicle previous = vehicle(11L, "OLD-111");
        Vehicle replacement = vehicle(12L, "NEW-222");
        trip.setVehicle(previous);
        Student student = enrolledStudent();
        StudentFamilyLink link = familyLink(student, true, true);
        link.setNotifyVehicleChange(false);
        when(trips.findByIdForUpdate(30L)).thenReturn(Optional.of(trip));
        when(vehicles.findByIdForUpdate(12L)).thenReturn(Optional.of(replacement));
        when(enrollments.findByRouteIdAndStatus(5L, EnrollmentStatus.ACTIVE)).thenReturn(List.of(enrollment(student)));
        when(links.findByStudentIdAndAuthorizedTrue(2L)).thenReturn(List.of(link));

        var result = service.reassign(30L, new OperationalRequests.Reassignment(null, 12L));

        assertTrue(result.wasReassigned());
        assertEquals(12L, result.vehicleId());
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notifications, times(2)).save(captor.capture());
        assertEquals(List.of(20L, 30L), captor.getAllValues().stream().map(n -> n.getRecipient().getId()).sorted().toList());
    }

    private Trip trip(Long id, TripStatus status, Instant start) {
        Trip trip = new Trip();
        trip.setId(id);
        trip.setRoute(route);
        trip.setDriver(driver);
        trip.setStatus(status);
        trip.setScheduledStartAt(start);
        trip.setScheduledEndAt(start.plusSeconds(3600));
        return trip;
    }

    private Trip operationalTrip(TripStatus status) {
        Trip trip = trip(30L, status, Instant.parse("2026-11-10T12:00:00Z"));
        trip.setEstimatedArrivalAt(Instant.parse("2026-11-10T13:00:00Z"));
        return trip;
    }

    private RouteStop routeStop(Long stopId, int order) {
        Stop stop = new Stop();
        stop.setId(stopId);
        stop.setName("Paradero " + order);
        stop.setActive(true);
        RouteStop routeStop = new RouteStop();
        routeStop.setRoute(route);
        routeStop.setStop(stop);
        routeStop.setStopOrder(order);
        routeStop.setEstimatedMinutesFromStart(order * 10);
        routeStop.setActive(true);
        return routeStop;
    }

    private Student enrolledStudent() {
        Student student = new Student();
        student.setId(2L);
        student.setUser(user(30L, Role.STUDENT));
        return student;
    }

    private StudentRouteEnrollment enrollment(Student student) {
        StudentRouteEnrollment enrollment = new StudentRouteEnrollment();
        enrollment.setStudent(student);
        enrollment.setRoute(route);
        enrollment.setStatus(EnrollmentStatus.ACTIVE);
        return enrollment;
    }

    private StudentFamilyLink familyLink(Student student, boolean notifyDelay, boolean notifyIncident) {
        StudentFamilyLink link = new StudentFamilyLink();
        link.setStudent(student);
        link.setFamilyUser(user(40L, Role.FAMILY));
        link.setAuthorized(true);
        link.setNotifyDelay(notifyDelay);
        link.setNotifyIncident(notifyIncident);
        return link;
    }

    private Vehicle vehicle(Long id, String plate) {
        Vehicle vehicle = new Vehicle();
        vehicle.setId(id);
        vehicle.setPlate(plate);
        vehicle.setAuthorizationStatus(AuthorizationStatus.AUTHORIZED);
        vehicle.setOperationalStatus(VehicleOperationalStatus.AVAILABLE);
        return vehicle;
    }

    private User user(Long id, Role role) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        user.setEnabled(true);
        return user;
    }
}
