package com.wayrapass.service;

import com.wayrapass.model.*;
import com.wayrapass.repository.NotificationRepository;
import com.wayrapass.repository.StudentFamilyLinkRepository;
import com.wayrapass.repository.StudentRouteEnrollmentRepository;
import com.wayrapass.service.WayriaProactiveMessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DelayAlertServiceTest {
    @Mock private NotificationRepository notifications;
    @Mock private StudentRouteEnrollmentRepository enrollments;
    @Mock private StudentFamilyLinkRepository links;
    @Mock private WayriaProactiveMessageService proactiveMessages;

    private DelayAlertService service;
    private Trip trip;
    private Incident incident;

    @BeforeEach
    void setUp() {
        service = new DelayAlertService(notifications, enrollments, links, proactiveMessages);
        ReflectionTestUtils.setField(service, "relevantDelayMinutes", 20);
        Route route = new Route(); route.setId(5L); route.setCode("R-12");
        trip = new Trip(); trip.setId(30L); trip.setRoute(route); trip.setStatus(TripStatus.IN_PROGRESS);
        incident = new Incident(); incident.setTrip(trip); incident.setDelayMinutes(25); incident.setDescription("Congestión intensa");
    }

    @Test
    void delayAlertsReachStudentAndEnabledFamilyOnlyAndReuseExistingNotificationRecords() {
        Student student = student(2L, 30L);
        User disabledFamily = user(40L, Role.FAMILY);
        User enabledFamily = user(41L, Role.FAMILY);
        when(enrollments.findByRouteIdAndStatus(5L, EnrollmentStatus.ACTIVE)).thenReturn(List.of(enrollment(student)));
        when(links.findByStudentIdAndAuthorizedTrue(2L)).thenReturn(List.of(link(student, disabledFamily, false), link(student, enabledFamily, true)));

        Map<Long, Notification> persistedByRecipient = new HashMap<>();
        when(notifications.findFirstByTripIdAndRecipientIdAndTypeOrderByCreatedAtDesc(eq(30L), anyLong(), eq(NotificationType.DELAY)))
                .thenAnswer(invocation -> Optional.ofNullable(persistedByRecipient.get(invocation.getArgument(1))));
        when(notifications.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            persistedByRecipient.put(notification.getRecipient().getId(), notification);
            return notification;
        });

        service.process(incident);
        service.process(incident);

        assertEquals(Set.of(30L, 41L), persistedByRecipient.keySet());
        assertTrue(persistedByRecipient.values().stream().allMatch(notification -> notification.getType() == NotificationType.DELAY));
        assertFalse(persistedByRecipient.containsKey(40L));
        verify(proactiveMessages, times(2)).upsert(eq(student.getUser()), eq(trip), eq(incident));
        verify(proactiveMessages, times(2)).upsert(eq(enabledFamily), eq(trip), eq(incident));
        verify(proactiveMessages, never()).upsert(eq(disabledFamily), eq(trip), eq(incident));
    }

    @Test
    void delayBelowThresholdCreatesNeitherNotificationNorProactiveMessage() {
        incident.setDelayMinutes(19);

        service.process(incident);

        verifyNoInteractions(enrollments, links, notifications, proactiveMessages);
    }

    private Student student(Long id, Long userId) {
        Student student = new Student(); student.setId(id); student.setUser(user(userId, Role.STUDENT)); return student;
    }
    private StudentRouteEnrollment enrollment(Student student) {
        StudentRouteEnrollment enrollment = new StudentRouteEnrollment(); enrollment.setStudent(student); enrollment.setRoute(trip.getRoute()); enrollment.setStatus(EnrollmentStatus.ACTIVE); return enrollment;
    }
    private StudentFamilyLink link(Student student, User family, boolean notifyDelay) {
        StudentFamilyLink link = new StudentFamilyLink(); link.setStudent(student); link.setFamilyUser(family); link.setAuthorized(true); link.setNotifyDelay(notifyDelay); return link;
    }
    private User user(Long id, Role role) {
        User user = new User(); user.setId(id); user.setRole(role); user.setEnabled(true); return user;
    }
}
