package com.wayrapass.repository;

import com.wayrapass.model.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
@ActiveProfiles("test")
class ReportRepositoryTest {
    @Autowired ReportRepository reports;
    @Autowired EntityManager entityManager;

    private Route route;
    private Vehicle vehicle;
    private Student student;
    private Stop stop;
    private Driver driver;

    @BeforeEach
    void setUp() {
        Institution institution = new Institution();
        institution.setName("Universidad Wayra");
        institution.setCampusName("Campus Centro");
        entityManager.persist(institution);

        route = new Route();
        route.setInstitution(institution);
        route.setCode("R-REPORT");
        route.setName("Ruta Reportes");
        route.setOrigin("Origen");
        route.setDestination("Destino");
        entityManager.persist(route);

        vehicle = new Vehicle();
        vehicle.setPlate("RPT-001");
        vehicle.setQrToken("qr-report");
        vehicle.setCapacity(10);
        vehicle.setAuthorizationStatus(AuthorizationStatus.AUTHORIZED);
        vehicle.setOperationalStatus(VehicleOperationalStatus.AVAILABLE);
        entityManager.persist(vehicle);

        stop = new Stop();
        stop.setName("Paradero Central");
        entityManager.persist(stop);

        User studentUser = user("student.report@wayrapass.test", Role.STUDENT);
        entityManager.persist(studentUser);
        student = new Student();
        student.setUser(studentUser);
        student.setInstitution(institution);
        entityManager.persist(student);

        User driverUser = user("driver.report@wayrapass.test", Role.DRIVER);
        entityManager.persist(driverUser);
        driver = new Driver();
        driver.setUser(driverUser);
        driver.setLicenseNumber("LIC-REPORT");
        driver.setLicenseCategory("AIII-C");
        driver.setLicenseExpiration(LocalDate.of(2030, 1, 1));
        driver.setAuthorizationStatus(AuthorizationStatus.AUTHORIZED);
        driver.setOperationalStatus(DriverOperationalStatus.AVAILABLE);
        entityManager.persist(driver);
    }

    @Test
    void aggregatesUseRealOperationsAndExcludeCancelledAndFutureDataCorrectly() {
        Trip onTime = trip("2026-09-10T12:00:00Z", TripStatus.COMPLETED, "2026-09-10T13:00:00Z");
        Trip late = trip("2026-09-11T12:00:00Z", TripStatus.COMPLETED, "2026-09-11T13:20:00Z");
        Trip cancelled = trip("2026-09-12T12:00:00Z", TripStatus.CANCELLED, "2026-09-12T13:00:00Z");
        Trip future = trip("2027-09-12T12:00:00Z", TripStatus.SCHEDULED, null);

        boarding(onTime, BoardingStatus.CONFIRMED);
        boarding(late, BoardingStatus.CONFIRMED);
        boarding(cancelled, BoardingStatus.CONFIRMED);
        boarding(future, BoardingStatus.CONFIRMED);
        incident(onTime);
        incident(cancelled);
        incident(future);
        entityManager.flush();

        Instant from = Instant.parse("2026-09-01T05:00:00Z");
        Instant toExclusive = Instant.parse("2027-10-01T05:00:00Z");
        Instant now = Instant.parse("2026-10-31T12:00:00Z");

        var punctuality = reports.punctuality(from, toExclusive, now, TripStatus.COMPLETED);
        var occupancy = reports.occupancy(from, toExclusive, now, TripStatus.COMPLETED, BoardingStatus.CONFIRMED);
        long incidents = reports.incidentCount(from, toExclusive, now,
                List.of(TripStatus.BOARDING, TripStatus.IN_PROGRESS, TripStatus.COMPLETED, TripStatus.CANCELLED));
        var demand = reports.stopDemand(from, toExclusive, now,
                List.of(TripStatus.BOARDING, TripStatus.IN_PROGRESS, TripStatus.COMPLETED), BoardingStatus.CONFIRMED);

        assertEquals(2, punctuality.getEvaluatedTrips());
        assertEquals(1, punctuality.getOnTimeTrips());
        assertEquals(2, occupancy.size());
        assertEquals(2, occupancy.stream().mapToLong(ReportRepository.OccupancyProjection::getConfirmedBoardings).sum());
        assertEquals(20, occupancy.stream().mapToInt(ReportRepository.OccupancyProjection::getVehicleCapacity).sum());
        assertEquals(2, incidents);
        assertEquals(1, demand.size());
        assertEquals(2, demand.getFirst().getConfirmedBoardings());
    }

    private Trip trip(String start, TripStatus status, String actualEnd) {
        Trip trip = new Trip();
        trip.setRoute(route);
        trip.setVehicle(vehicle);
        trip.setDriver(driver);
        trip.setScheduledStartAt(Instant.parse(start));
        trip.setScheduledEndAt(Instant.parse(start).plusSeconds(3600));
        trip.setActualEndAt(actualEnd == null ? null : Instant.parse(actualEnd));
        trip.setStatus(status);
        entityManager.persist(trip);
        return trip;
    }

    private void boarding(Trip trip, BoardingStatus status) {
        Boarding boarding = new Boarding();
        boarding.setTrip(trip);
        boarding.setStudent(student);
        boarding.setStop(stop);
        boarding.setStatus(status);
        boarding.setBoardedAt(trip.getScheduledStartAt().plusSeconds(300));
        entityManager.persist(boarding);
    }

    private void incident(Trip trip) {
        Incident incident = new Incident();
        incident.setTrip(trip);
        incident.setDriver(driver);
        incident.setType(IncidentType.TRAFFIC_DELAY);
        incident.setDescription("Tráfico");
        incident.setDelayMinutes(10);
        entityManager.persist(incident);
    }

    private User user(String email, Role role) {
        User user = new User();
        user.setFullName("Usuario Reporte");
        user.setEmail(email);
        user.setPasswordHash("hash");
        user.setRole(role);
        return user;
    }
}
