package com.wayrapass.service;

import com.wayrapass.config.LocationTrackingProperties;
import com.wayrapass.model.*;
import com.wayrapass.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CurrentTripQueryServiceTest {
    @Mock StudentAccessService studentAccess;
    @Mock StudentRouteEnrollmentRepository enrollments;
    @Mock BoardingRepository boardings;
    @Mock TripRepository trips;
    @Mock IncidentRepository incidents;
    @Mock TripLocationRepository locations;
    @Mock RouteStopRepository routeStops;
    @Mock EnrollmentLifecycleService enrollmentLifecycle;
    private TripQueryService service;
    private final Instant now = Instant.parse("2026-11-10T12:00:00Z");
    private final LocationTrackingProperties locationProperties = new LocationTrackingProperties();

    private Student student;
    private StudentRouteEnrollment enrollment;
    private Route route;
    private Stop stop;

    @BeforeEach
    void setUp() {
        locationProperties.setStaleAfterSeconds(120);
        service = new TripQueryService(
                studentAccess, enrollments, boardings, trips, incidents, locations, routeStops,
                locationProperties, enrollmentLifecycle, Clock.fixed(now, ZoneOffset.UTC));
        Institution institution = new Institution();
        institution.setId(1L);
        institution.setName("UPC");
        institution.setCampusName("San Isidro");

        route = new Route();
        route.setId(5L);
        route.setCode("R-12");
        route.setName("San Borja - UPC");
        route.setInstitution(institution);

        stop = new Stop();
        stop.setId(8L);
        stop.setName("Próceres");
        stop.setAddress("Av. Próceres 1250");
        stop.setDistrict("San Borja");

        User user = user(20L, Role.STUDENT, "Andy Pérez");
        student = new Student();
        student.setId(2L);
        student.setUser(user);
        student.setInstitution(institution);

        enrollment = new StudentRouteEnrollment();
        enrollment.setId(30L);
        enrollment.setStudent(student);
        enrollment.setRoute(route);
        enrollment.setStop(stop);
        enrollment.setStatus(EnrollmentStatus.ACTIVE);
    }

    @Test
    void currentTripReturnsStateLocationVehicleStopEtaAndAuthorizedDriver() {
        Trip trip = trip(40L, TripStatus.IN_PROGRESS, now.minusSeconds(600));
        trip.setEstimatedArrivalAt(trip.getScheduledEndAt().plusSeconds(15 * 60L));
        trip.setVehicle(vehicle());
        trip.setDriver(driver(AuthorizationStatus.AUTHORIZED, "https://cdn.test/credential.png"));
        TripLocation location = location(trip);
        RouteStop relation = routeStop(10);
        prepareStudentCurrent(List.of(trip));
        when(locations.findFirstByTripIdOrderByRecordedAtDesc(40L)).thenReturn(Optional.of(location));
        when(routeStops.findByRouteIdAndStopId(5L, 8L)).thenReturn(Optional.of(relation));

        var response = service.myCurrentTrip();

        assertTrue(response.available());
        assertEquals(TripStatus.IN_PROGRESS, response.trip().tripStatus());
        assertEquals("BUS-123", response.trip().vehicle().plate());
        assertEquals("Próceres", response.trip().assignedStop().name());
        assertTrue(response.trip().location().available());
        assertFalse(response.trip().location().stale());
        assertEquals(com.wayrapass.dto.response.TripQueryResponses.LocationFreshness.RECENT,
                response.trip().location().freshness());
        assertEquals(new BigDecimal("-12.097100"), response.trip().location().latitude());
        assertTrue(response.trip().stopArrival().available());
        assertEquals(response.trip().stopArrival().scheduledArrivalAt().plusSeconds(15 * 60L),
                response.trip().stopArrival().estimatedArrivalAt());
        assertTrue(response.trip().universityArrival().available());
        assertTrue(response.trip().universityArrival().delayed());
        assertEquals(15L, response.trip().universityArrival().delayMinutes());
        assertTrue(response.trip().driver().assigned());
        assertTrue(response.trip().driver().authorized());
        assertEquals("LIC-123", response.trip().driver().licenseNumber());
    }

    @Test
    void inProgressHasPriorityOverBoardingAndScheduled() {
        Trip scheduled = trip(41L, TripStatus.SCHEDULED, now.plusSeconds(3600));
        Trip boarding = trip(42L, TripStatus.BOARDING, now.minusSeconds(300));
        Trip inProgress = trip(43L, TripStatus.IN_PROGRESS, now.minusSeconds(600));
        prepareStudentCurrent(List.of(scheduled, boarding, inProgress));

        var response = service.myCurrentTrip();

        assertEquals(43L, response.trip().tripId());
    }

    @Test
    void scheduledTripIsReturnedWhenThereIsNoOperationalTrip() {
        Trip scheduled = trip(41L, TripStatus.SCHEDULED, now.plusSeconds(3600));
        prepareStudentCurrent(List.of(scheduled));

        var response = service.myCurrentTrip();

        assertTrue(response.available());
        assertEquals(TripStatus.SCHEDULED, response.trip().tripStatus());
    }

    @Test
    void studentWithoutActiveEnrollmentReceivesAvailableFalse() {
        when(studentAccess.requireCurrentStudent()).thenReturn(student);
        when(enrollmentLifecycle.currentForUse(2L))
                .thenReturn(Optional.empty());

        var response = service.myCurrentTrip();

        assertFalse(response.available());
        assertNull(response.trip());
        verifyNoInteractions(trips, locations, routeStops);
    }

    @Test
    void studentWithoutRelevantTripReceivesAvailableFalse() {
        prepareStudentCurrent(List.of());

        var response = service.myCurrentTrip();

        assertFalse(response.available());
        assertNull(response.trip());
    }

    @Test
    void completedAndCancelledTripsAreDefensivelyExcluded() {
        Trip completed = trip(44L, TripStatus.COMPLETED, now.minusSeconds(7200));
        Trip cancelled = trip(45L, TripStatus.CANCELLED, now.minusSeconds(3600));
        prepareStudentCurrent(List.of(completed, cancelled));

        assertFalse(service.myCurrentTrip().available());
    }

    @Test
    void missingLocationIsExplicitAndDoesNotInventCoordinates() {
        Trip trip = trip(40L, TripStatus.IN_PROGRESS, now.minusSeconds(600));
        prepareStudentCurrent(List.of(trip));
        when(locations.findFirstByTripIdOrderByRecordedAtDesc(40L)).thenReturn(Optional.empty());

        var location = service.myCurrentTrip().trip().location();

        assertFalse(location.available());
        assertFalse(location.stale());
        assertEquals(com.wayrapass.dto.response.TripQueryResponses.LocationFreshness.UNAVAILABLE,
                location.freshness());
        assertNull(location.latitude());
        assertNull(location.longitude());
        assertNull(location.recordedAt());
    }

    @Test
    void oldLocationIsMarkedStaleButKeepsCoordinatesAndTimestamp() {
        Trip trip = trip(40L, TripStatus.IN_PROGRESS, now.minusSeconds(600));
        TripLocation lastKnown = location(trip);
        lastKnown.setRecordedAt(now.minusSeconds(121));
        prepareStudentCurrent(List.of(trip));
        when(locations.findFirstByTripIdOrderByRecordedAtDesc(40L)).thenReturn(Optional.of(lastKnown));

        var location = service.myCurrentTrip().trip().location();

        assertTrue(location.available());
        assertTrue(location.stale());
        assertEquals(com.wayrapass.dto.response.TripQueryResponses.LocationFreshness.STALE,
                location.freshness());
        assertEquals(new BigDecimal("-12.097100"), location.latitude());
        assertEquals(new BigDecimal("-77.036500"), location.longitude());
        assertEquals(now.minusSeconds(121), location.recordedAt());
        assertTrue(location.message().contains("desactualizada"));
    }

    @Test
    void locationAtExactFreshnessBoundaryIsStillRecent() {
        Trip trip = trip(40L, TripStatus.IN_PROGRESS, now.minusSeconds(600));
        TripLocation boundary = location(trip);
        boundary.setRecordedAt(now.minusSeconds(120));
        prepareStudentCurrent(List.of(trip));
        when(locations.findFirstByTripIdOrderByRecordedAtDesc(40L)).thenReturn(Optional.of(boundary));

        var location = service.myCurrentTrip().trip().location();

        assertFalse(location.stale());
        assertEquals(com.wayrapass.dto.response.TripQueryResponses.LocationFreshness.RECENT,
                location.freshness());
    }

    @Test
    void customFreshnessThresholdIsApplied() {
        locationProperties.setStaleAfterSeconds(30);
        Trip trip = trip(40L, TripStatus.IN_PROGRESS, now.minusSeconds(600));
        TripLocation oldForCustomThreshold = location(trip);
        oldForCustomThreshold.setRecordedAt(now.minusSeconds(31));
        prepareStudentCurrent(List.of(trip));
        when(locations.findFirstByTripIdOrderByRecordedAtDesc(40L))
                .thenReturn(Optional.of(oldForCustomThreshold));

        assertTrue(service.myCurrentTrip().trip().location().stale());
    }

    @Test
    void inProgressWithoutIncidentUsesScheduledEndAsBaseEta() {
        Trip trip = trip(40L, TripStatus.IN_PROGRESS, now.minusSeconds(600));
        trip.setEstimatedArrivalAt(null);
        prepareStudentCurrent(List.of(trip));

        var eta = service.myCurrentTrip().trip().universityArrival();

        assertTrue(eta.available());
        assertEquals(trip.getScheduledEndAt(), eta.scheduledArrivalAt());
        assertEquals(trip.getScheduledEndAt(), eta.estimatedArrivalAt());
        assertEquals(50L, eta.remainingMinutes());
        assertEquals(0L, eta.delayMinutes());
        assertEquals(com.wayrapass.dto.response.TripQueryResponses.EtaSource.PLANNED, eta.source());
    }

    @Test
    void boardingWithoutIncidentUsesScheduledEndAsBaseEta() {
        Trip trip = trip(40L, TripStatus.BOARDING, now.minusSeconds(60));
        prepareStudentCurrent(List.of(trip));

        var eta = service.myCurrentTrip().trip().universityArrival();

        assertTrue(eta.available());
        assertEquals(trip.getScheduledEndAt(), eta.estimatedArrivalAt());
        assertEquals(com.wayrapass.dto.response.TripQueryResponses.EtaSource.PLANNED, eta.source());
    }

    @Test
    void scheduledTripShowsPlanWithoutClaimingOperationalEta() {
        Trip trip = trip(40L, TripStatus.SCHEDULED, now.plusSeconds(600));
        prepareStudentCurrent(List.of(trip));

        var eta = service.myCurrentTrip().trip().universityArrival();

        assertFalse(eta.available());
        assertEquals(trip.getScheduledEndAt(), eta.scheduledArrivalAt());
        assertNull(eta.estimatedArrivalAt());
        assertEquals(com.wayrapass.dto.response.TripQueryResponses.EtaSource.PLANNED, eta.source());
    }

    @Test
    void missingPlanningKeepsEtaUnavailable() {
        Trip trip = trip(40L, TripStatus.IN_PROGRESS, now.minusSeconds(600));
        trip.setScheduledEndAt(null);
        prepareStudentCurrent(List.of(trip));

        var eta = service.myCurrentTrip().trip().universityArrival();

        assertFalse(eta.available());
        assertNull(eta.estimatedArrivalAt());
        assertEquals(com.wayrapass.dto.response.TripQueryResponses.EtaSource.UNAVAILABLE, eta.source());
    }

    @Test
    void baseEtaNeverProducesNegativeMinutesAndIsNotPersistedByReads() {
        Trip trip = trip(40L, TripStatus.IN_PROGRESS, now.minusSeconds(7200));
        trip.setScheduledEndAt(now.minusSeconds(3600));
        prepareStudentCurrent(List.of(trip));

        var first = service.myCurrentTrip().trip().universityArrival();
        var second = service.myCurrentTrip().trip().universityArrival();

        assertEquals(0L, first.remainingMinutes());
        assertEquals(first.estimatedArrivalAt(), second.estimatedArrivalAt());
        assertNull(trip.getEstimatedArrivalAt());
    }

    @Test
    void incidentAdjustedEtaIsReflectedWithoutRecalculationFromGps() {
        Trip trip = trip(40L, TripStatus.IN_PROGRESS, now.minusSeconds(600));
        trip.setEstimatedArrivalAt(trip.getScheduledEndAt().plusSeconds(20 * 60L));
        prepareStudentCurrent(List.of(trip));

        var eta = service.myCurrentTrip().trip().universityArrival();

        assertEquals(trip.getEstimatedArrivalAt(), eta.estimatedArrivalAt());
        assertEquals(20L, eta.delayMinutes());
        assertTrue(eta.delayed());
        assertEquals(com.wayrapass.dto.response.TripQueryResponses.EtaSource.OPERATIONAL, eta.source());
    }

    @Test
    void missingRouteStopRelationMakesStopEtaUnavailable() {
        Trip trip = trip(40L, TripStatus.BOARDING, now.minusSeconds(60));
        prepareStudentCurrent(List.of(trip));
        when(routeStops.findByRouteIdAndStopId(5L, 8L)).thenReturn(Optional.empty());

        var estimate = service.myCurrentTrip().trip().stopArrival();

        assertFalse(estimate.available());
        assertNull(estimate.estimatedArrivalAt());
        assertNull(estimate.remainingMinutes());
    }

    @Test
    void familyCanReadPendingDriverCredentialForLinkedStudent() {
        Trip trip = trip(40L, TripStatus.SCHEDULED, now.plusSeconds(3600));
        trip.setDriver(driver(AuthorizationStatus.PENDING, null));
        prepareFamilyCurrent(List.of(trip));

        var credential = service.familyCurrentTripDriver(2L);

        assertTrue(credential.assigned());
        assertEquals(AuthorizationStatus.PENDING, credential.authorizationStatus());
        assertFalse(credential.authorized());
        assertFalse(credential.credentialImageAvailable());
    }

    @Test
    void familyCanReadRejectedDriverStatus() {
        Trip trip = trip(40L, TripStatus.SCHEDULED, now.plusSeconds(3600));
        trip.setDriver(driver(AuthorizationStatus.REJECTED, "https://cdn.test/credential.png"));
        prepareFamilyCurrent(List.of(trip));

        var credential = service.familyCurrentTripDriver(2L);

        assertEquals(AuthorizationStatus.REJECTED, credential.authorizationStatus());
        assertFalse(credential.authorized());
    }

    @Test
    void tripWithoutDriverReturnsUnassignedCredentialInsteadOfFailing() {
        Trip trip = trip(40L, TripStatus.SCHEDULED, now.plusSeconds(3600));
        prepareFamilyCurrent(List.of(trip));

        var credential = service.familyCurrentTripDriver(2L);

        assertFalse(credential.assigned());
        assertNull(credential.driverId());
        assertNull(credential.licenseNumber());
    }

    @Test
    void familyWithoutCurrentTripReceivesUnassignedCredential() {
        prepareFamilyCurrent(List.of());

        var credential = service.familyCurrentTripDriver(2L);

        assertFalse(credential.assigned());
        assertTrue(credential.message().contains("No hay un viaje"));
    }

    @Test
    void familyOwnershipFailureIsPropagatedBeforeTripLookup() {
        when(studentAccess.requireFamilyStudent(2L)).thenThrow(new AccessDeniedException("Sin acceso"));

        assertThrows(AccessDeniedException.class, () -> service.familyCurrentTripDriver(2L));
        verifyNoInteractions(enrollments, trips);
    }

    @Test
    void currentTripLookupUsesOnlyStudentsEnrollmentRoute() {
        Trip trip = trip(40L, TripStatus.SCHEDULED, now.plusSeconds(3600));
        prepareStudentCurrent(List.of(trip));

        service.myCurrentTrip();

        verify(trips).findCurrentCandidates(eq(5L),
                eq(List.of(TripStatus.BOARDING, TripStatus.IN_PROGRESS)),
                eq(TripStatus.SCHEDULED), any(Instant.class));
    }

    private void prepareStudentCurrent(List<Trip> candidates) {
        when(studentAccess.requireCurrentStudent()).thenReturn(student);
        prepareEnrollmentAndTrips(candidates);
    }

    private void prepareFamilyCurrent(List<Trip> candidates) {
        when(studentAccess.requireFamilyStudent(2L)).thenReturn(student);
        prepareEnrollmentAndTrips(candidates);
    }

    private void prepareEnrollmentAndTrips(List<Trip> candidates) {
        when(enrollmentLifecycle.currentForUse(2L))
                .thenReturn(Optional.of(enrollment));
        when(trips.findCurrentCandidates(eq(5L),
                eq(List.of(TripStatus.BOARDING, TripStatus.IN_PROGRESS)),
                eq(TripStatus.SCHEDULED), any(Instant.class))).thenReturn(candidates);
    }

    private Trip trip(Long id, TripStatus status, Instant start) {
        Trip trip = new Trip();
        trip.setId(id);
        trip.setRoute(route);
        trip.setStatus(status);
        trip.setScheduledStartAt(start);
        trip.setScheduledEndAt(start.plusSeconds(3600));
        trip.setUpdatedAt(now.minusSeconds(5));
        return trip;
    }

    private TripLocation location(Trip trip) {
        TripLocation location = new TripLocation();
        location.setId(60L);
        location.setTrip(trip);
        location.setLatitude(new BigDecimal("-12.097100"));
        location.setLongitude(new BigDecimal("-77.036500"));
        location.setRecordedAt(now.minusSeconds(30));
        return location;
    }

    private RouteStop routeStop(int estimatedMinutes) {
        RouteStop relation = new RouteStop();
        relation.setRoute(route);
        relation.setStop(stop);
        relation.setEstimatedMinutesFromStart(estimatedMinutes);
        relation.setActive(true);
        return relation;
    }

    private Vehicle vehicle() {
        Vehicle vehicle = new Vehicle();
        vehicle.setId(70L);
        vehicle.setPlate("BUS-123");
        vehicle.setBrand("Mercedes");
        vehicle.setModel("Sprinter");
        return vehicle;
    }

    private Driver driver(AuthorizationStatus authorizationStatus, String imageUrl) {
        Driver driver = new Driver();
        driver.setId(80L);
        driver.setUser(user(81L, Role.DRIVER, "Carlos Chofer"));
        driver.setLicenseNumber("LIC-123");
        driver.setLicenseCategory("AIII-C");
        driver.setLicenseExpiration(LocalDate.ofInstant(now, ZoneOffset.UTC).plusYears(1));
        driver.setCredentialImageUrl(imageUrl);
        driver.setAuthorizationStatus(authorizationStatus);
        driver.setOperationalStatus(DriverOperationalStatus.AVAILABLE);
        return driver;
    }

    private User user(Long id, Role role, String name) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        user.setFullName(name);
        user.setEmail(name.replace(" ", ".").toLowerCase() + "@secret.test");
        user.setPasswordHash("sensitive-password-hash");
        user.setPhone("999999999");
        return user;
    }
}
