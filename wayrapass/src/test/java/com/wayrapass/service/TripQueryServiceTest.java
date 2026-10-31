package com.wayrapass.service;

import com.wayrapass.config.LocationTrackingProperties;
import com.wayrapass.exception.BusinessRuleException;
import com.wayrapass.exception.ResourceNotFoundException;
import com.wayrapass.model.*;
import com.wayrapass.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.*;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TripQueryServiceTest {
    @Mock StudentAccessService studentAccess;
    @Mock StudentRouteEnrollmentRepository enrollments;
    @Mock BoardingRepository boardings;
    @Mock TripRepository trips;
    @Mock IncidentRepository incidents;
    @Mock TripLocationRepository locations;
    @Mock RouteStopRepository routeStops;
    @Mock EnrollmentLifecycleService enrollmentLifecycle;
    @Mock LocationTrackingProperties locationProperties;
    @Mock Clock clock;
    @InjectMocks TripQueryService service;

    private Student student;
    private Route route;
    private Stop stop;

    @BeforeEach
    void setUp() {
        lenient().when(clock.instant()).thenReturn(Instant.now());
        Institution institution = new Institution();
        institution.setId(1L);
        institution.setName("Universidad Wayra");
        institution.setCampusName("Campus Centro");

        route = new Route();
        route.setId(5L);
        route.setCode("R-12");
        route.setName("Ruta Centro");
        route.setOrigin("Próceres");
        route.setDestination("Campus Centro");
        route.setInstitution(institution);

        stop = new Stop();
        stop.setId(8L);
        stop.setName("Paradero Próceres");

        student = new Student();
        student.setId(2L);
    }

    @Test
    void studentObtainsOwnHistoryOrderedNewestFirst() {
        when(studentAccess.requireCurrentStudent()).thenReturn(student);
        Boarding older = boarding(31L, Instant.now().minus(Duration.ofDays(4)), TripStatus.COMPLETED, BoardingStatus.CONFIRMED);
        Boarding newer = boarding(32L, Instant.now().minus(Duration.ofDays(1)), TripStatus.COMPLETED, BoardingStatus.CONFIRMED);
        when(boardings.findStudentHistory(eq(2L), any(), any(), any())).thenReturn(List.of(older, newer));

        var result = service.myHistory(LocalDate.now().minusDays(10), LocalDate.now());

        assertEquals(List.of(32L, 31L), result.stream().map(item -> item.tripId()).toList());
    }

    @Test
    void studentHistoryEmptyReturnsEmptyList() {
        when(studentAccess.requireCurrentStudent()).thenReturn(student);
        when(boardings.findStudentHistory(eq(2L), any(), any(), any())).thenReturn(List.of());

        assertEquals(List.of(), service.myHistory(LocalDate.now().minusDays(10), LocalDate.now()));
    }

    @Test
    void studentHistoryDoesNotIncludeFutureTripsEvenIfRepositoryReturnsOne() {
        when(studentAccess.requireCurrentStudent()).thenReturn(student);
        Boarding past = boarding(31L, Instant.now().minus(Duration.ofDays(1)), TripStatus.COMPLETED, BoardingStatus.CONFIRMED);
        Boarding future = boarding(32L, Instant.now().plus(Duration.ofDays(1)), TripStatus.SCHEDULED, BoardingStatus.CONFIRMED);
        when(boardings.findStudentHistory(eq(2L), any(), any(), any())).thenReturn(List.of(past, future));

        var result = service.myHistory(LocalDate.now().minusDays(2), LocalDate.now().plusDays(2));

        assertEquals(List.of(31L), result.stream().map(item -> item.tripId()).toList());
    }

    @Test
    void studentHistoryPreservesBoardingStatusAndDelayClassification() {
        when(studentAccess.requireCurrentStudent()).thenReturn(student);
        Boarding boarding = boarding(31L, Instant.now().minus(Duration.ofDays(1)), TripStatus.COMPLETED, BoardingStatus.CANCELLED);
        boarding.getTrip().setScheduledEndAt(Instant.now().minus(Duration.ofHours(2)));
        boarding.getTrip().setActualEndAt(Instant.now().minus(Duration.ofHours(1)));
        when(boardings.findStudentHistory(eq(2L), any(), any(), any())).thenReturn(List.of(boarding));

        var result = service.myHistory(LocalDate.now().minusDays(2), LocalDate.now());

        assertEquals(BoardingStatus.CANCELLED, result.getFirst().boardingStatus());
        assertTrue(result.getFirst().delayed());
    }

    @Test
    void studentHistoryRejectsInvertedDateRange() {
        when(studentAccess.requireCurrentStudent()).thenReturn(student);
        assertThrows(BusinessRuleException.class,
                () -> service.myHistory(LocalDate.now(), LocalDate.now().minusDays(1)));
        verifyNoInteractions(boardings);
    }

    @Test
    void studentCanViewOwnHistoryDetailWithDriverVehicleAndIncidents() {
        when(studentAccess.requireCurrentStudent()).thenReturn(student);
        Boarding boarding = boarding(31L, Instant.now().minus(Duration.ofDays(1)), TripStatus.COMPLETED, BoardingStatus.CONFIRMED);
        Driver driver = new Driver();
        driver.setId(11L);
        User driverUser = new User();
        driverUser.setFullName("Ana Conductora");
        driver.setUser(driverUser);
        Vehicle vehicle = new Vehicle();
        vehicle.setId(12L);
        vehicle.setPlate("ABC-123");
        vehicle.setBrand("Volvo");
        vehicle.setModel("B8R");
        boarding.getTrip().setDriver(driver);
        boarding.getTrip().setVehicle(vehicle);
        when(boardings.findStudentHistoryDetail(eq(2L), eq(31L), any())).thenReturn(Optional.of(boarding));
        when(incidents.findByTripIdOrderByCreatedAtAsc(31L)).thenReturn(List.of(incident(90L, boarding.getTrip())));

        var detail = service.myHistoryDetail(31L);

        assertEquals("Ana Conductora", detail.driverName());
        assertEquals("ABC-123", detail.vehiclePlate());
        assertEquals(1, detail.incidents().size());
    }

    @Test
    void studentCannotViewTripOutsideOwnHistory() {
        when(studentAccess.requireCurrentStudent()).thenReturn(student);
        when(boardings.findStudentHistoryDetail(eq(2L), eq(99L), any())).thenReturn(Optional.empty());
        when(trips.existsById(99L)).thenReturn(true);

        assertThrows(AccessDeniedException.class, () -> service.myHistoryDetail(99L));
    }

    @Test
    void linkedFamilyObtainsStudentHistoryWithMultipleTrips() {
        when(studentAccess.requireFamilyStudent(2L)).thenReturn(student);
        Boarding older = boarding(31L, Instant.now().minus(Duration.ofDays(3)), TripStatus.COMPLETED, BoardingStatus.CONFIRMED);
        Boarding newer = boarding(32L, Instant.now().minus(Duration.ofDays(1)), TripStatus.CANCELLED, BoardingStatus.CONFIRMED);
        when(boardings.findStudentHistory(eq(2L), any(), any(), any())).thenReturn(List.of(older, newer));

        var result = service.familyHistory(2L, LocalDate.now().minusDays(5), LocalDate.now());

        assertEquals(List.of(32L, 31L), result.stream().map(item -> item.tripId()).toList());
    }

    @Test
    void unlinkedFamilyIsRejected() {
        when(studentAccess.requireFamilyStudent(2L)).thenThrow(new AccessDeniedException("Sin acceso"));
        assertThrows(AccessDeniedException.class,
                () -> service.familyHistory(2L, LocalDate.now().minusDays(5), LocalDate.now()));
        verifyNoInteractions(boardings);
    }

    @Test
    void nonexistentStudentReturnsNotFoundForFamily() {
        when(studentAccess.requireFamilyStudent(999L)).thenThrow(new ResourceNotFoundException("Estudiante no encontrado."));
        assertThrows(ResourceNotFoundException.class,
                () -> service.familyHistory(999L, LocalDate.now().minusDays(5), LocalDate.now()));
    }

    @Test
    void linkedFamilyHistoryEmptyReturnsEmptyList() {
        when(studentAccess.requireFamilyStudent(2L)).thenReturn(student);
        when(boardings.findStudentHistory(eq(2L), any(), any(), any())).thenReturn(List.of());

        assertTrue(service.familyHistory(2L, LocalDate.now().minusDays(5), LocalDate.now()).isEmpty());
    }

    @Test
    void familyDetailReturnsAuthorizedDataWithoutDriverOrVehicleContract() {
        when(studentAccess.requireFamilyStudent(2L)).thenReturn(student);
        Boarding boarding = boarding(31L, Instant.now().minus(Duration.ofDays(1)), TripStatus.COMPLETED, BoardingStatus.CONFIRMED);
        when(boardings.findStudentHistoryDetail(eq(2L), eq(31L), any())).thenReturn(Optional.of(boarding));
        when(incidents.findByTripIdOrderByCreatedAtAsc(31L)).thenReturn(List.of());

        var detail = service.familyHistoryDetail(2L, 31L);

        assertEquals(31L, detail.trip().tripId());
        assertTrue(detail.incidents().isEmpty());
        assertEquals(2, detail.getClass().getRecordComponents().length);
    }

    @Test
    void linkedFamilyGetsOnlyFutureScheduledTripsOrderedChronologically() {
        when(studentAccess.requireFamilyStudent(2L)).thenReturn(student);
        StudentRouteEnrollment enrollment = enrollment();
        when(enrollmentLifecycle.currentForUse(2L)).thenReturn(Optional.of(enrollment));
        Trip later = trip(41L, Instant.now().plus(Duration.ofDays(3)), TripStatus.SCHEDULED);
        Trip sooner = trip(42L, Instant.now().plus(Duration.ofHours(3)), TripStatus.SCHEDULED);
        Trip past = trip(43L, Instant.now().minus(Duration.ofHours(1)), TripStatus.SCHEDULED);
        Trip completed = trip(44L, Instant.now().plus(Duration.ofDays(1)), TripStatus.COMPLETED);
        Trip cancelled = trip(45L, Instant.now().plus(Duration.ofDays(2)), TripStatus.CANCELLED);
        when(trips.findUpcomingByRouteAndStatus(eq(5L), eq(TripStatus.SCHEDULED), any())).thenReturn(List.of(later, past, completed, cancelled, sooner));

        var result = service.familyUpcomingTrips(2L);

        assertEquals(List.of(42L, 41L), result.stream().map(item -> item.tripId()).toList());
        assertTrue(result.stream().allMatch(item -> item.status() == TripStatus.SCHEDULED));
    }

    @Test
    void upcomingTripsExposeCurrentStopAndReassignmentIndicator() {
        when(studentAccess.requireFamilyStudent(2L)).thenReturn(student);
        when(enrollmentLifecycle.currentForUse(2L)).thenReturn(Optional.of(enrollment()));
        Trip trip = trip(41L, Instant.now().plus(Duration.ofDays(1)), TripStatus.SCHEDULED);
        trip.setWasReassigned(true);
        when(trips.findUpcomingByRouteAndStatus(eq(5L), eq(TripStatus.SCHEDULED), any())).thenReturn(List.of(trip));

        var result = service.familyUpcomingTrips(2L);

        assertEquals("Paradero Próceres", result.getFirst().stopName());
        assertTrue(result.getFirst().resourcesReassigned());
        assertFalse(result.getFirst().studentRouteOrStopModified());
        assertNull(result.getFirst().modificationMessage());
    }

    @Test
    void upcomingTripsIndicateWhenStudentChangedRouteOrStop() {
        when(studentAccess.requireFamilyStudent(2L)).thenReturn(student);
        StudentRouteEnrollment current = enrollment();
        when(enrollmentLifecycle.currentForUse(2L))
                .thenReturn(Optional.of(current));

        Route previousRoute = new Route();
        previousRoute.setId(4L);
        Stop previousStop = new Stop();
        previousStop.setId(7L);
        StudentRouteEnrollment previous = new StudentRouteEnrollment();
        previous.setStudent(student);
        previous.setRoute(previousRoute);
        previous.setStop(previousStop);
        previous.setStatus(EnrollmentStatus.ENDED);
        when(enrollments.findFirstByStudentIdAndStatusOrderByIdDesc(2L, EnrollmentStatus.ENDED))
                .thenReturn(Optional.of(previous));
        when(trips.findUpcomingByRouteAndStatus(eq(5L), eq(TripStatus.SCHEDULED), any()))
                .thenReturn(List.of(trip(41L, Instant.now().plus(Duration.ofDays(1)), TripStatus.SCHEDULED)));

        var result = service.familyUpcomingTrips(2L);

        assertTrue(result.getFirst().studentRouteOrStopModified());
        assertEquals("Este viaje fue modificado", result.getFirst().modificationMessage());
        assertEquals(5L, result.getFirst().routeId());
        assertEquals(8L, result.getFirst().stopId());
    }

    @Test
    void upcomingTripsDoNotClaimModificationWhenPreviousAssignmentWasEquivalent() {
        when(studentAccess.requireFamilyStudent(2L)).thenReturn(student);
        StudentRouteEnrollment current = enrollment();
        StudentRouteEnrollment previous = enrollment();
        previous.setStatus(EnrollmentStatus.ENDED);
        when(enrollmentLifecycle.currentForUse(2L))
                .thenReturn(Optional.of(current));
        when(enrollments.findFirstByStudentIdAndStatusOrderByIdDesc(2L, EnrollmentStatus.ENDED))
                .thenReturn(Optional.of(previous));
        when(trips.findUpcomingByRouteAndStatus(eq(5L), eq(TripStatus.SCHEDULED), any()))
                .thenReturn(List.of(trip(41L, Instant.now().plus(Duration.ofDays(1)), TripStatus.SCHEDULED)));

        var result = service.familyUpcomingTrips(2L);

        assertFalse(result.getFirst().studentRouteOrStopModified());
        assertNull(result.getFirst().modificationMessage());
    }

    @Test
    void unlinkedFamilyCannotViewUpcomingTrips() {
        when(studentAccess.requireFamilyStudent(2L)).thenThrow(new AccessDeniedException("Sin acceso"));
        assertThrows(AccessDeniedException.class, () -> service.familyUpcomingTrips(2L));
        verifyNoInteractions(enrollments, trips);
    }

    @Test
    void noActiveEnrollmentProducesEmptyUpcomingTrips() {
        when(studentAccess.requireFamilyStudent(2L)).thenReturn(student);
        when(enrollmentLifecycle.currentForUse(2L)).thenReturn(Optional.empty());

        assertTrue(service.familyUpcomingTrips(2L).isEmpty());
        verifyNoInteractions(trips);
    }

    @Test
    void suspendedEnrollmentProducesEmptyUpcomingTrips() {
        when(studentAccess.requireFamilyStudent(2L)).thenReturn(student);
        when(enrollmentLifecycle.currentForUse(2L))
                .thenReturn(Optional.empty());

        assertEquals(List.of(), service.familyUpcomingTrips(2L));
        verifyNoInteractions(trips);
    }

    @Test
    void activeEnrollmentWithoutScheduledTripsProducesEmptyList() {
        when(studentAccess.requireFamilyStudent(2L)).thenReturn(student);
        when(enrollmentLifecycle.currentForUse(2L))
                .thenReturn(Optional.of(enrollment()));
        when(trips.findUpcomingByRouteAndStatus(eq(5L), eq(TripStatus.SCHEDULED), any()))
                .thenReturn(List.of());

        assertEquals(List.of(), service.familyUpcomingTrips(2L));
    }

    @Test
    void us15UpcomingTripsUsesEnrollmentNormalizedAfterSuspensionExpiry() {
        when(studentAccess.requireFamilyStudent(2L)).thenReturn(student);
        StudentRouteEnrollment normalized = enrollment();
        normalized.setSuspendedFrom(LocalDate.now().minusDays(5));
        normalized.setSuspendedUntil(LocalDate.now().minusDays(1));
        when(enrollmentLifecycle.currentForUse(2L)).thenReturn(Optional.of(normalized));
        Trip upcoming = trip(46L, Instant.now().plus(Duration.ofHours(2)), TripStatus.SCHEDULED);
        when(trips.findUpcomingByRouteAndStatus(eq(5L), eq(TripStatus.SCHEDULED), any()))
                .thenReturn(List.of(upcoming));

        var result = service.familyUpcomingTrips(2L);

        assertEquals(List.of(46L), result.stream().map(item -> item.tripId()).toList());
        verify(enrollmentLifecycle).currentForUse(2L);
    }

    private Boarding boarding(Long tripId, Instant start, TripStatus status, BoardingStatus boardingStatus) {
        Boarding boarding = new Boarding();
        boarding.setId(tripId + 100);
        boarding.setStudent(student);
        boarding.setTrip(trip(tripId, start, status));
        boarding.setStop(stop);
        boarding.setBoardedAt(start.plus(Duration.ofMinutes(5)));
        boarding.setStatus(boardingStatus);
        return boarding;
    }

    private Trip trip(Long id, Instant start, TripStatus status) {
        Trip trip = new Trip();
        trip.setId(id);
        trip.setRoute(route);
        trip.setScheduledStartAt(start);
        trip.setScheduledEndAt(start.plus(Duration.ofHours(1)));
        trip.setStatus(status);
        return trip;
    }

    private StudentRouteEnrollment enrollment() {
        StudentRouteEnrollment enrollment = new StudentRouteEnrollment();
        enrollment.setStudent(student);
        enrollment.setRoute(route);
        enrollment.setStop(stop);
        enrollment.setStatus(EnrollmentStatus.ACTIVE);
        return enrollment;
    }

    private Incident incident(Long id, Trip trip) {
        Incident incident = new Incident();
        incident.setId(id);
        incident.setTrip(trip);
        incident.setType(IncidentType.TRAFFIC_DELAY);
        incident.setDescription("Tráfico intenso");
        incident.setDelayMinutes(15);
        incident.setPriority(IncidentPriority.NORMAL);
        incident.setStatus(IncidentStatus.OPEN);
        incident.setCreatedAt(Instant.now().minus(Duration.ofHours(1)));
        return incident;
    }
}
