package com.wayrapass.service;

import com.wayrapass.dto.request.OperationalRequests;
import com.wayrapass.dto.response.OperationalResponses;
import com.wayrapass.exception.BusinessRuleException;
import com.wayrapass.exception.ConflictException;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OperationalServiceC4Test {
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
    private Vehicle vehicle;
    private Trip trip;

    @BeforeEach
    void setUp() {
        route = new Route();
        route.setId(5L);
        route.setCode("R-12");
        route.setName("Ate - San Isidro");
        route.setOrigin("Ate");
        route.setDestination("UPC San Isidro");
        route.setStatus(RouteStatus.ACTIVE);
        driver = driver(10L, DriverOperationalStatus.AVAILABLE, AuthorizationStatus.AUTHORIZED);
        vehicle = vehicle(12L, VehicleOperationalStatus.AVAILABLE, AuthorizationStatus.AUTHORIZED);
        trip = trip(30L, TripStatus.SCHEDULED, Instant.parse("2026-11-10T11:45:00Z"));
    }

    @Test
    void assignsDriverAndVehicleToScheduledTrip() {
        when(trips.findByIdForUpdate(30L)).thenReturn(Optional.of(trip));
        when(drivers.findByIdForUpdate(10L)).thenReturn(Optional.of(driver));
        when(vehicles.findByIdForUpdate(12L)).thenReturn(Optional.of(vehicle));

        var result = service.assignResources(30L, new OperationalRequests.Assignment(10L, 12L));

        assertEquals(10L, result.driverId());
        assertEquals(12L, result.vehicleId());
        assertSame(driver, trip.getDriver());
        assertSame(vehicle, trip.getVehicle());
    }

    @Test
    void assignmentRejectsMissingDriverAndVehicle() {
        when(trips.findByIdForUpdate(30L)).thenReturn(Optional.of(trip));
        when(drivers.findByIdForUpdate(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> service.assignResources(30L, new OperationalRequests.Assignment(99L, 12L)));

        when(drivers.findByIdForUpdate(10L)).thenReturn(Optional.of(driver));
        when(vehicles.findByIdForUpdate(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> service.assignResources(30L, new OperationalRequests.Assignment(10L, 99L)));
    }

    @Test
    void assignmentRejectsUnauthorizedOrExpiredDriver() {
        driver.setAuthorizationStatus(AuthorizationStatus.PENDING);
        when(trips.findByIdForUpdate(30L)).thenReturn(Optional.of(trip));
        when(drivers.findByIdForUpdate(10L)).thenReturn(Optional.of(driver));
        assertThrows(BusinessRuleException.class,
                () -> service.assignResources(30L, new OperationalRequests.Assignment(10L, 12L)));

        driver.setAuthorizationStatus(AuthorizationStatus.AUTHORIZED);
        driver.setLicenseExpiration(LocalDate.now().minusDays(1));
        assertThrows(BusinessRuleException.class,
                () -> service.assignResources(30L, new OperationalRequests.Assignment(10L, 12L)));
    }

    @Test
    void assignmentRejectsDriverOverlapWithOfficialMessage() {
        when(trips.findByIdForUpdate(30L)).thenReturn(Optional.of(trip));
        when(drivers.findByIdForUpdate(10L)).thenReturn(Optional.of(driver));
        when(trips.driverOverlaps(eq(10L), any(), any(), eq(30L))).thenReturn(true);

        ConflictException error = assertThrows(ConflictException.class,
                () -> service.assignResources(30L, new OperationalRequests.Assignment(10L, 12L)));

        assertEquals("El recurso seleccionado no se encuentra disponible", error.getMessage());
    }

    @Test
    void assignmentRejectsUnavailableOrOverlappingVehicle() {
        when(trips.findByIdForUpdate(30L)).thenReturn(Optional.of(trip));
        when(drivers.findByIdForUpdate(10L)).thenReturn(Optional.of(driver));
        vehicle.setOperationalStatus(VehicleOperationalStatus.MAINTENANCE);
        when(vehicles.findByIdForUpdate(12L)).thenReturn(Optional.of(vehicle));
        assertThrows(BusinessRuleException.class,
                () -> service.assignResources(30L, new OperationalRequests.Assignment(10L, 12L)));

        trip.setDriver(null); // Simula el rollback transaccional de la asignación fallida anterior.
        vehicle.setOperationalStatus(VehicleOperationalStatus.AVAILABLE);
        when(trips.vehicleOverlaps(eq(12L), any(), any(), eq(30L))).thenReturn(true);
        assertThrows(ConflictException.class,
                () -> service.assignResources(30L, new OperationalRequests.Assignment(10L, 12L)));
    }

    @Test
    void assignmentRequiresActiveRouteAndScheduledTrip() {
        route.setStatus(RouteStatus.INACTIVE);
        when(trips.findByIdForUpdate(30L)).thenReturn(Optional.of(trip));
        assertThrows(BusinessRuleException.class,
                () -> service.assignResources(30L, new OperationalRequests.Assignment(10L, 12L)));

        route.setStatus(RouteStatus.ACTIVE);
        trip.setStatus(TripStatus.BOARDING);
        assertThrows(BusinessRuleException.class,
                () -> service.assignResources(30L, new OperationalRequests.Assignment(10L, 12L)));
    }

    @Test
    void assignmentRejectsTripThatAlreadyHasResources() {
        trip.setDriver(driver);
        when(trips.findByIdForUpdate(30L)).thenReturn(Optional.of(trip));

        assertThrows(ConflictException.class,
                () -> service.assignResources(30L, new OperationalRequests.Assignment(10L, 12L)));
    }

    @Test
    void terminalTripsCannotBeReassigned() {
        trip.setStatus(TripStatus.COMPLETED);
        when(trips.findByIdForUpdate(30L)).thenReturn(Optional.of(trip));
        assertThrows(BusinessRuleException.class,
                () -> service.reassign(30L, new OperationalRequests.Reassignment(10L, null)));

        trip.setStatus(TripStatus.CANCELLED);
        assertThrows(BusinessRuleException.class,
                () -> service.reassign(30L, new OperationalRequests.Reassignment(null, 12L)));
    }

    @Test
    void reassignmentOptionsContainOnlyEligibleNonOverlappingAlternatives() {
        Driver unavailable = driver(11L, DriverOperationalStatus.UNAVAILABLE, AuthorizationStatus.AUTHORIZED);
        Driver overlapping = driver(13L, DriverOperationalStatus.AVAILABLE, AuthorizationStatus.AUTHORIZED);
        Vehicle maintenance = vehicle(14L, VehicleOperationalStatus.MAINTENANCE, AuthorizationStatus.AUTHORIZED);
        Vehicle overlappingVehicle = vehicle(15L, VehicleOperationalStatus.AVAILABLE, AuthorizationStatus.AUTHORIZED);
        trip.setDriver(driver);
        trip.setVehicle(vehicle);
        Driver alternative = driver(16L, DriverOperationalStatus.AVAILABLE, AuthorizationStatus.AUTHORIZED);
        Vehicle alternativeVehicle = vehicle(17L, VehicleOperationalStatus.AVAILABLE, AuthorizationStatus.AUTHORIZED);
        when(trips.findById(30L)).thenReturn(Optional.of(trip));
        when(drivers.findAllByOrderByIdAsc()).thenReturn(List.of(driver, unavailable, overlapping, alternative));
        when(vehicles.findAllByOrderByIdAsc()).thenReturn(List.of(vehicle, maintenance, overlappingVehicle, alternativeVehicle));
        when(trips.driverOverlaps(eq(13L), any(), any(), eq(30L))).thenReturn(true);
        when(trips.vehicleOverlaps(eq(15L), any(), any(), eq(30L))).thenReturn(true);

        var result = service.reassignmentOptions(30L);

        assertEquals(List.of(16L), result.drivers().stream().map(OperationalResponses.DriverOption::driverId).toList());
        assertEquals(List.of(17L), result.vehicles().stream().map(OperationalResponses.VehicleOption::vehicleId).toList());
        assertNull(result.message());
    }

    @Test
    void reassignmentOptionsReturnOfficialMessageWhenNothingIsAvailable() {
        trip.setDriver(driver);
        trip.setVehicle(vehicle);
        when(trips.findById(30L)).thenReturn(Optional.of(trip));
        when(drivers.findAllByOrderByIdAsc()).thenReturn(List.of(driver));
        when(vehicles.findAllByOrderByIdAsc()).thenReturn(List.of(vehicle));

        var result = service.reassignmentOptions(30L);

        assertTrue(result.drivers().isEmpty());
        assertTrue(result.vehicles().isEmpty());
        assertEquals("No existen recursos disponibles para realizar la reasignación", result.message());
    }

    @Test
    void weeklyCalendarReturnsCurrentResourcesAndModificationMarker() {
        trip.setDriver(driver);
        trip.setVehicle(vehicle);
        trip.setWasReassigned(true);
        when(trips.findCalendarOperations(any(), any())).thenReturn(List.of(trip));

        var result = service.weeklyCalendar(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 15));

        assertEquals(1, result.size());
        assertEquals(LocalDate.of(2026, 11, 10), result.getFirst().operationDate());
        assertEquals("R-12", result.getFirst().routeCode());
        assertEquals(10L, result.getFirst().driver().driverId());
        assertEquals(12L, result.getFirst().vehicle().vehicleId());
        assertTrue(result.getFirst().modified());
    }

    @Test
    void weeklyCalendarWithoutTripsReturnsEmptyList() {
        when(trips.findCalendarOperations(any(), any())).thenReturn(List.of());

        assertTrue(service.weeklyCalendar(
                LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 15)).isEmpty());
    }

    @Test
    void weeklyCalendarRejectsNonWeeklyRange() {
        assertThrows(BusinessRuleException.class, () -> service.weeklyCalendar(
                LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 15)));
        assertThrows(BusinessRuleException.class, () -> service.weeklyCalendar(
                LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 16)));
        verifyNoInteractions(trips);
    }

    private Trip trip(Long id, TripStatus status, Instant start) {
        Trip trip = new Trip();
        trip.setId(id);
        trip.setRoute(route);
        trip.setStatus(status);
        trip.setScheduledStartAt(start);
        trip.setScheduledEndAt(start.plusSeconds(3600));
        return trip;
    }

    private Driver driver(Long id, DriverOperationalStatus status, AuthorizationStatus authorization) {
        User user = new User();
        user.setId(100L + id);
        user.setFullName("Conductor " + id);
        user.setEnabled(true);
        Driver driver = new Driver();
        driver.setId(id);
        driver.setUser(user);
        driver.setLicenseNumber("LIC-" + id);
        driver.setLicenseCategory("AIII-C");
        driver.setLicenseExpiration(LocalDate.now().plusYears(1));
        driver.setAuthorizationStatus(authorization);
        driver.setOperationalStatus(status);
        return driver;
    }

    private Vehicle vehicle(Long id, VehicleOperationalStatus status, AuthorizationStatus authorization) {
        Vehicle vehicle = new Vehicle();
        vehicle.setId(id);
        vehicle.setPlate("ABC-" + id);
        vehicle.setBrand("Mercedes");
        vehicle.setModel("Sprinter");
        vehicle.setCapacity(24);
        vehicle.setAuthorizationStatus(authorization);
        vehicle.setOperationalStatus(status);
        return vehicle;
    }
}
