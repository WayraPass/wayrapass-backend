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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OperationalServiceHardeningTest {
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
    @Mock EnrollmentLifecycleService enrollmentLifecycle;
    @InjectMocks OperationalService service;

    private Route route;
    private User coordinator;

    @BeforeEach
    void setUp() {
        route = new Route();
        route.setId(5L);
        coordinator = user(1L, Role.COORDINATOR);
    }

    @Test
    void latestLocationRequiresLegitimateTripAccess() {
        Trip trip = trip(TripStatus.IN_PROGRESS);
        TripLocation location = new TripLocation();
        location.setId(9L);
        location.setTrip(trip);
        location.setLatitude(BigDecimal.valueOf(-12.1));
        location.setLongitude(BigDecimal.valueOf(-77.1));
        when(trips.findById(30L)).thenReturn(Optional.of(trip));
        when(locations.findFirstByTripIdOrderByRecordedAtDesc(30L)).thenReturn(Optional.of(location));

        assertEquals(30L, service.latestLocation(30L).tripId());
        verify(studentAccess).requireTripAccess(trip);
    }

    @Test
    void latestLocationRejectsUserWithoutTripRelationship() {
        Trip trip = trip(TripStatus.IN_PROGRESS);
        when(trips.findById(30L)).thenReturn(Optional.of(trip));
        doThrow(new AccessDeniedException("Sin acceso")).when(studentAccess).requireTripAccess(trip);

        assertThrows(AccessDeniedException.class, () -> service.latestLocation(30L));
        verifyNoInteractions(locations);
    }

    @Test
    void suspendedEnrollmentCannotBoard() {
        Student student = boardingStudent();
        Trip trip = boardingTrip();
        StudentRouteEnrollment enrollment = boardingEnrollment(student, EnrollmentStatus.SUSPENDED);
        when(current.require()).thenReturn(student.getUser());
        when(students.findByUserId(20L)).thenReturn(Optional.of(student));
        when(trips.findByIdForUpdate(30L)).thenReturn(Optional.of(trip));
        when(enrollmentLifecycle.currentForUse(2L)).thenReturn(Optional.of(enrollment));

        assertThrows(BusinessRuleException.class,
                () -> service.board(new OperationalRequests.Boarding(30L, 8L, "QR-10")));
        verifyNoInteractions(boardings);
    }

    @Test
    void activeEnrollmentCanStillBoard() {
        Student student = boardingStudent();
        Trip trip = boardingTrip();
        StudentRouteEnrollment enrollment = boardingEnrollment(student, EnrollmentStatus.ACTIVE);
        Stop stop = enrollment.getStop();
        when(current.require()).thenReturn(student.getUser());
        when(students.findByUserId(20L)).thenReturn(Optional.of(student));
        when(trips.findByIdForUpdate(30L)).thenReturn(Optional.of(trip));
        when(enrollmentLifecycle.currentForUse(2L)).thenReturn(Optional.of(enrollment));
        when(routeStops.existsByRouteIdAndStopId(5L, 8L)).thenReturn(true);
        when(stops.findById(8L)).thenReturn(Optional.of(stop));
        when(boardings.save(any(Boarding.class))).thenAnswer(invocation -> {
            Boarding saved = invocation.getArgument(0);
            saved.setId(70L);
            saved.setBoardedAt(Instant.now());
            return saved;
        });

        var result = service.board(new OperationalRequests.Boarding(30L, 8L, "QR-10"));

        assertEquals(30L, result.tripId());
        verify(boardings).save(any(Boarding.class));
    }

    @Test
    void enrollmentNormalizedAfterSuspensionExpiryCanBoard() {
        Student student = boardingStudent();
        Trip trip = boardingTrip();
        StudentRouteEnrollment enrollment = boardingEnrollment(student, EnrollmentStatus.ACTIVE);
        enrollment.setSuspendedFrom(LocalDate.now().minusDays(5));
        enrollment.setSuspendedUntil(LocalDate.now().minusDays(1));
        Stop stop = enrollment.getStop();
        when(current.require()).thenReturn(student.getUser());
        when(students.findByUserId(20L)).thenReturn(Optional.of(student));
        when(trips.findByIdForUpdate(30L)).thenReturn(Optional.of(trip));
        when(enrollmentLifecycle.currentForUse(2L)).thenReturn(Optional.of(enrollment));
        when(routeStops.existsByRouteIdAndStopId(5L, 8L)).thenReturn(true);
        when(stops.findById(8L)).thenReturn(Optional.of(stop));
        when(boardings.save(any(Boarding.class))).thenAnswer(invocation -> {
            Boarding saved = invocation.getArgument(0);
            saved.setId(71L);
            saved.setBoardedAt(Instant.now());
            return saved;
        });

        var result = service.board(new OperationalRequests.Boarding(30L, 8L, "QR-10"));

        assertEquals(30L, result.tripId());
        verify(enrollmentLifecycle).currentForUse(2L);
        verify(boardings).save(any(Boarding.class));
    }

    @Test
    void reassignmentRejectsBothResourcesNull() {
        assertThrows(BusinessRuleException.class,
                () -> service.reassign(30L, new OperationalRequests.Reassignment(null, null)));
        verifyNoInteractions(trips);
    }

    @Test
    void reassignmentAcceptsDriverOnly() {
        Trip trip = trip(TripStatus.SCHEDULED);
        Driver replacement = driver(11L, 21L);
        when(trips.findByIdForUpdate(30L)).thenReturn(Optional.of(trip));
        when(drivers.findByIdForUpdate(11L)).thenReturn(Optional.of(replacement));

        var result = service.reassign(30L, new OperationalRequests.Reassignment(11L, null));

        assertEquals(11L, result.driverId());
        assertTrue(result.wasReassigned());
    }

    @Test
    void reassignmentAcceptsVehicleOnly() {
        Trip trip = trip(TripStatus.SCHEDULED);
        Vehicle replacement = vehicle(12L, VehicleOperationalStatus.AVAILABLE);
        when(trips.findByIdForUpdate(30L)).thenReturn(Optional.of(trip));
        when(vehicles.findByIdForUpdate(12L)).thenReturn(Optional.of(replacement));

        var result = service.reassign(30L, new OperationalRequests.Reassignment(null, 12L));

        assertEquals(12L, result.vehicleId());
        assertTrue(result.wasReassigned());
    }

    @Test
    void reassignmentAcceptsDriverAndVehicle() {
        Trip trip = trip(TripStatus.SCHEDULED);
        Driver replacementDriver = driver(11L, 21L);
        Vehicle replacementVehicle = vehicle(12L, VehicleOperationalStatus.AVAILABLE);
        when(trips.findByIdForUpdate(30L)).thenReturn(Optional.of(trip));
        when(drivers.findByIdForUpdate(11L)).thenReturn(Optional.of(replacementDriver));
        when(vehicles.findByIdForUpdate(12L)).thenReturn(Optional.of(replacementVehicle));

        var result = service.reassign(30L, new OperationalRequests.Reassignment(11L, 12L));

        assertEquals(11L, result.driverId());
        assertEquals(12L, result.vehicleId());
    }

    @Test
    void reassignmentRejectsRequestThatDoesNotActuallyChangeResources() {
        Trip trip = trip(TripStatus.SCHEDULED);
        trip.setDriver(driver(11L, 21L));
        when(trips.findByIdForUpdate(30L)).thenReturn(Optional.of(trip));

        assertThrows(BusinessRuleException.class,
                () -> service.reassign(30L, new OperationalRequests.Reassignment(11L, null)));

        assertFalse(trip.isWasReassigned());
        verifyNoInteractions(notifications);
    }

    @Test
    void activeTripReassignmentReleasesOldVehicleAndActivatesNewVehicle() {
        Trip trip = trip(TripStatus.IN_PROGRESS);
        Vehicle previous = vehicle(10L, VehicleOperationalStatus.IN_SERVICE);
        Vehicle replacement = vehicle(12L, VehicleOperationalStatus.AVAILABLE);
        trip.setVehicle(previous);
        when(trips.findByIdForUpdate(30L)).thenReturn(Optional.of(trip));
        when(vehicles.findByIdForUpdate(12L)).thenReturn(Optional.of(replacement));

        service.reassign(30L, new OperationalRequests.Reassignment(null, 12L));

        assertEquals(VehicleOperationalStatus.AVAILABLE, previous.getOperationalStatus());
        assertEquals(VehicleOperationalStatus.IN_SERVICE, replacement.getOperationalStatus());
    }

    @Test
    void scheduledTripCanMoveToBoardingAndActivatesVehicle() {
        Trip trip = trip(TripStatus.SCHEDULED);
        trip.setDriver(driver(10L, 20L));
        Vehicle vehicle = vehicle(10L, VehicleOperationalStatus.AVAILABLE);
        trip.setVehicle(vehicle);
        when(trips.findByIdForUpdate(30L)).thenReturn(Optional.of(trip));
        when(current.require()).thenReturn(coordinator);

        service.status(30L, TripStatus.BOARDING);

        assertEquals(TripStatus.BOARDING, trip.getStatus());
        assertEquals(VehicleOperationalStatus.IN_SERVICE, vehicle.getOperationalStatus());
    }

    @Test
    void completedTripCannotRegressToInProgress() {
        Trip trip = trip(TripStatus.COMPLETED);
        when(trips.findByIdForUpdate(30L)).thenReturn(Optional.of(trip));
        when(current.require()).thenReturn(coordinator);

        assertThrows(BusinessRuleException.class, () -> service.status(30L, TripStatus.IN_PROGRESS));
        assertEquals(TripStatus.COMPLETED, trip.getStatus());
    }

    @Test
    void cancelledTripIsTerminal() {
        Trip trip = trip(TripStatus.CANCELLED);
        when(trips.findByIdForUpdate(30L)).thenReturn(Optional.of(trip));
        when(current.require()).thenReturn(coordinator);

        assertThrows(BusinessRuleException.class, () -> service.status(30L, TripStatus.BOARDING));
    }

    @Test
    void completingTripReleasesVehicle() {
        Trip trip = trip(TripStatus.IN_PROGRESS);
        Vehicle vehicle = vehicle(10L, VehicleOperationalStatus.IN_SERVICE);
        trip.setVehicle(vehicle);
        when(trips.findByIdForUpdate(30L)).thenReturn(Optional.of(trip));
        when(current.require()).thenReturn(coordinator);

        service.status(30L, TripStatus.COMPLETED);

        assertEquals(TripStatus.COMPLETED, trip.getStatus());
        assertEquals(VehicleOperationalStatus.AVAILABLE, vehicle.getOperationalStatus());
        assertNotNull(trip.getActualEndAt());
    }

    @Test
    void incidentIsAcceptedDuringOperationalTrip() {
        Trip trip = ownedTrip(TripStatus.BOARDING, 20L);
        when(trips.findByIdForUpdate(30L)).thenReturn(Optional.of(trip));
        when(current.id()).thenReturn(20L);
        when(incidents.save(any(Incident.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.incident(incidentRequest());

        assertEquals(30L, result.tripId());
        verify(incidents).save(any(Incident.class));
    }

    @Test
    void incidentIsRejectedForScheduledCompletedAndCancelledTrips() {
        Trip scheduled = ownedTrip(TripStatus.SCHEDULED, 20L);
        Trip completed = ownedTrip(TripStatus.COMPLETED, 20L);
        Trip cancelled = ownedTrip(TripStatus.CANCELLED, 20L);
        when(current.id()).thenReturn(20L);
        when(trips.findByIdForUpdate(30L)).thenReturn(Optional.of(scheduled), Optional.of(completed), Optional.of(cancelled));

        assertThrows(BusinessRuleException.class, () -> service.incident(incidentRequest()));
        assertThrows(BusinessRuleException.class, () -> service.incident(incidentRequest()));
        assertThrows(BusinessRuleException.class, () -> service.incident(incidentRequest()));
        verifyNoInteractions(incidents);
    }

    @Test
    void incidentIsRejectedWhenDriverDoesNotOwnTrip() {
        Trip trip = ownedTrip(TripStatus.IN_PROGRESS, 99L);
        when(trips.findByIdForUpdate(30L)).thenReturn(Optional.of(trip));
        when(current.id()).thenReturn(20L);

        assertThrows(AccessDeniedException.class, () -> service.incident(incidentRequest()));
        verifyNoInteractions(incidents);
    }

    private OperationalRequests.Incident incidentRequest() {
        return new OperationalRequests.Incident(30L, IncidentType.TRAFFIC_DELAY, "Tráfico intenso", 10, IncidentPriority.NORMAL);
    }

    private Student boardingStudent() {
        Student student = new Student();
        student.setId(2L);
        User user = user(20L, Role.STUDENT);
        user.setFullName("Estudiante Wayra");
        student.setUser(user);
        return student;
    }

    private Trip boardingTrip() {
        Trip trip = trip(TripStatus.BOARDING);
        Vehicle vehicle = vehicle(10L, VehicleOperationalStatus.IN_SERVICE);
        vehicle.setQrToken("QR-10");
        trip.setVehicle(vehicle);
        return trip;
    }

    private StudentRouteEnrollment boardingEnrollment(Student student, EnrollmentStatus status) {
        Stop stop = new Stop();
        stop.setId(8L);
        StudentRouteEnrollment enrollment = new StudentRouteEnrollment();
        enrollment.setStudent(student);
        enrollment.setRoute(route);
        enrollment.setStop(stop);
        enrollment.setStatus(status);
        return enrollment;
    }

    private Trip trip(TripStatus status) {
        Trip trip = new Trip();
        trip.setId(30L);
        trip.setRoute(route);
        trip.setStatus(status);
        trip.setScheduledStartAt(Instant.now().plusSeconds(3600));
        trip.setScheduledEndAt(Instant.now().plusSeconds(7200));
        return trip;
    }

    private Trip ownedTrip(TripStatus status, Long userId) {
        Trip trip = trip(status);
        trip.setDriver(driver(10L, userId));
        return trip;
    }

    private Driver driver(Long id, Long userId) {
        Driver driver = new Driver();
        driver.setId(id);
        driver.setUser(user(userId, Role.DRIVER));
        driver.setAuthorizationStatus(AuthorizationStatus.AUTHORIZED);
        driver.setOperationalStatus(DriverOperationalStatus.AVAILABLE);
        driver.setLicenseExpiration(LocalDate.now().plusYears(1));
        return driver;
    }

    private Vehicle vehicle(Long id, VehicleOperationalStatus status) {
        Vehicle vehicle = new Vehicle();
        vehicle.setId(id);
        vehicle.setAuthorizationStatus(AuthorizationStatus.AUTHORIZED);
        vehicle.setOperationalStatus(status);
        return vehicle;
    }

    private User user(Long id, Role role) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        return user;
    }
}
