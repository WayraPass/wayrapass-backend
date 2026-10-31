package com.wayrapass.service;

import com.wayrapass.dto.request.OperationalRequests;
import com.wayrapass.exception.BusinessRuleException;
import com.wayrapass.model.*;
import com.wayrapass.repository.*;
import com.wayrapass.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentAccessServiceC3Test {
    @Mock CurrentUser current;
    @Mock StudentRepository students;
    @Mock StudentFamilyLinkRepository links;
    @Mock RouteRepository routes;
    @Mock StopRepository stops;
    @Mock RouteStopRepository routeStops;
    @Mock RouteScheduleRepository schedules;
    @Mock StudentRouteEnrollmentRepository enrollments;
    @Mock FavoriteRouteRepository favorites;
    @InjectMocks StudentAccessService service;

    @Test
    void linkedFamilyReadsItsOwnPreferences() {
        User family = user(20L, Role.FAMILY);
        StudentFamilyLink link = link(7L, 2L, family);
        link.setNotifyDelay(false);
        when(current.require()).thenReturn(family);
        when(links.findByStudentIdAndFamilyUserId(2L, 20L)).thenReturn(Optional.of(link));

        var result = service.notificationPreferences(2L);

        assertEquals(7L, result.linkId());
        assertEquals(2L, result.studentId());
        assertFalse(result.notifyDelay());
        assertTrue(result.notifyBoarding());
    }

    @Test
    void familyStudentListUsesOnlyAuthorizedLinksForAuthenticatedFamily() {
        User family = user(20L, Role.FAMILY);
        StudentFamilyLink first = link(7L, 2L, family);
        StudentFamilyLink second = link(8L, 3L, family);
        when(current.require()).thenReturn(family);
        when(links.findByFamilyUserIdAndAuthorizedTrue(20L)).thenReturn(List.of(first, second));

        assertEquals(List.of(2L, 3L), service.familyStudents());
        verify(links).findByFamilyUserIdAndAuthorizedTrue(20L);
    }

    @Test
    void linkedFamilyUpdatesOnlyItsPreferencesWithoutLosingLink() {
        User family = user(20L, Role.FAMILY);
        StudentFamilyLink link = link(7L, 2L, family);
        Instant linkedAt = Instant.parse("2026-10-01T10:00:00Z");
        link.setLinkedAt(linkedAt);
        when(current.require()).thenReturn(family);
        when(links.findByStudentIdAndFamilyUserId(2L, 20L)).thenReturn(Optional.of(link));
        when(links.save(link)).thenReturn(link);

        var result = service.updateNotificationPreferences(2L,
                new OperationalRequests.NotificationPreferences(false, true, false, true, false));

        assertFalse(result.notifyBoarding());
        assertTrue(result.notifyArrival());
        assertFalse(result.notifyDelay());
        assertTrue(link.isAuthorized());
        assertEquals(linkedAt, link.getLinkedAt());
        verify(links).save(link);
    }

    @Test
    void familyCannotReadOrUpdateAnotherFamiliesLink() {
        User family = user(20L, Role.FAMILY);
        when(current.require()).thenReturn(family);
        when(links.findByStudentIdAndFamilyUserId(2L, 20L)).thenReturn(Optional.empty());

        assertThrows(AccessDeniedException.class, () -> service.notificationPreferences(2L));
        assertThrows(AccessDeniedException.class, () -> service.updateNotificationPreferences(2L,
                new OperationalRequests.NotificationPreferences(true, true, true, true, true)));
        verify(links, never()).save(any());
    }

    @Test
    void revokedLinkCannotManagePreferences() {
        User family = user(20L, Role.FAMILY);
        StudentFamilyLink link = link(7L, 2L, family);
        link.setAuthorized(false);
        link.setRevokedAt(Instant.now());
        when(current.require()).thenReturn(family);
        when(links.findByStudentIdAndFamilyUserId(2L, 20L)).thenReturn(Optional.of(link));

        assertThrows(AccessDeniedException.class, () -> service.notificationPreferences(2L));
    }

    @Test
    void invalidFamilyCodeUsesOfficialMessageAndCreatesNoLink() {
        User family = user(20L, Role.FAMILY);
        Student student = new Student();
        student.setId(2L);
        student.setFamilyLinkCodeHash("invalid-hash");
        student.setFamilyLinkCodeExpiresAt(Instant.now().plusSeconds(300));
        when(current.require()).thenReturn(family);
        when(students.findById(2L)).thenReturn(Optional.of(student));

        BusinessRuleException error = assertThrows(BusinessRuleException.class,
                () -> service.linkFamily(2L, new OperationalRequests.FamilyCode("123456", "MADRE")));

        assertEquals("El código de vinculación no es válido. Solicita uno nuevo.", error.getMessage());
        verify(links, never()).save(any());
    }

    private StudentFamilyLink link(Long id, Long studentId, User family) {
        Student student = new Student();
        student.setId(studentId);
        StudentFamilyLink link = new StudentFamilyLink();
        link.setId(id);
        link.setStudent(student);
        link.setFamilyUser(family);
        link.setAuthorized(true);
        link.setNotifyBoarding(true);
        link.setNotifyArrival(true);
        link.setNotifyDelay(true);
        link.setNotifyVehicleChange(true);
        link.setNotifyIncident(true);
        return link;
    }

    private User user(Long id, Role role) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        return user;
    }
}
