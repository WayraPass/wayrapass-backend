package com.wayrapass.service;

import com.wayrapass.exception.ConflictException;
import com.wayrapass.model.*;
import com.wayrapass.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:wayrapass_capacity_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RouteCapacityConcurrencyTest {

    @Autowired EnrollmentLifecycleService lifecycle;
    @Autowired InstitutionRepository institutions;
    @Autowired RouteRepository routes;
    @Autowired StopRepository stops;
    @Autowired UserRepository users;
    @Autowired StudentRepository students;
    @Autowired StudentRouteEnrollmentRepository enrollments;
    @Autowired TransactionTemplate transactions;

    @Test
    void twoConcurrentAttemptsCannotTakeTheOnlySeat() throws Exception {
        Fixture fixture = transactions.execute(status -> fixture());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Attempt> first = executor.submit(() -> attempt(fixture.studentIds().get(0), fixture, ready, start));
            Future<Attempt> second = executor.submit(() -> attempt(fixture.studentIds().get(1), fixture, ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            List<Attempt> results = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));

            assertEquals(1, results.stream().filter(result -> result == Attempt.ENROLLED).count());
            assertEquals(1, results.stream().filter(result -> result == Attempt.FULL).count());
            assertEquals(1L, enrollments.countByRouteIdAndStatus(fixture.routeId(), EnrollmentStatus.ACTIVE));
        }
    }

    private Attempt attempt(Long studentId, Fixture fixture, CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        assertTrue(start.await(5, TimeUnit.SECONDS));
        try {
            transactions.executeWithoutResult(status -> {
                Route lockedRoute = lifecycle.lockRouteAndRequireSeat(fixture.routeId(), false);
                StudentRouteEnrollment enrollment = new StudentRouteEnrollment();
                enrollment.setStudent(students.findById(studentId).orElseThrow());
                enrollment.setRoute(lockedRoute);
                enrollment.setStop(stops.findById(fixture.stopId()).orElseThrow());
                enrollment.setStatus(EnrollmentStatus.ACTIVE);
                enrollments.saveAndFlush(enrollment);
            });
            return Attempt.ENROLLED;
        } catch (ConflictException expected) {
            return Attempt.FULL;
        }
    }

    private Fixture fixture() {
        Institution institution = new Institution();
        institution.setName("Institución concurrencia D3");
        institution = institutions.save(institution);

        Route route = new Route();
        route.setInstitution(institution);
        route.setCode("D3-CONCURRENT");
        route.setName("Ruta de un cupo");
        route.setOrigin("Origen");
        route.setDestination("Destino");
        route.setCapacity(1);
        route = routes.save(route);

        Stop stop = new Stop();
        stop.setName("Paradero D3");
        stop = stops.save(stop);

        Student first = student("d3-first@wayrapass.test", institution);
        Student second = student("d3-second@wayrapass.test", institution);
        return new Fixture(route.getId(), stop.getId(), List.of(first.getId(), second.getId()));
    }

    private Student student(String email, Institution institution) {
        User user = new User();
        user.setFullName("Estudiante concurrencia");
        user.setEmail(email);
        user.setPasswordHash("not-used-in-test");
        user.setRole(Role.STUDENT);
        user = users.save(user);

        Student student = new Student();
        student.setUser(user);
        student.setInstitution(institution);
        return students.save(student);
    }

    private enum Attempt { ENROLLED, FULL }

    private record Fixture(Long routeId, Long stopId, List<Long> studentIds) {}
}
