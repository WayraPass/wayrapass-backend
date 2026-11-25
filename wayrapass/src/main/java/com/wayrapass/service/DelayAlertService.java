package com.wayrapass.service;

import com.wayrapass.model.*;
import com.wayrapass.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.*;

@Service @RequiredArgsConstructor
public class DelayAlertService {
 private final NotificationRepository notifications; private final StudentRouteEnrollmentRepository enrollments; private final StudentFamilyLinkRepository links; private final WayriaProactiveMessageService proactiveMessages;
 @Value("${wayria.alerts.relevant-delay-minutes:20}") private int relevantDelayMinutes;
 public void process(Incident incident){if(incident.getDelayMinutes()<relevantDelayMinutes)return; Trip trip=incident.getTrip(); String route=trip.getRoute().getCode(); String message="La ruta "+route+" presenta un retraso aproximado de "+incident.getDelayMinutes()+" minutos debido a una incidencia reportada."; for(StudentRouteEnrollment e:enrollments.findByRouteIdAndStatus(trip.getRoute().getId(),EnrollmentStatus.ACTIVE)){save(e.getStudent().getUser(),trip,incident,message);for(StudentFamilyLink l:links.findByStudentIdAndAuthorizedTrue(e.getStudent().getId()))if(l.isNotifyDelay())save(l.getFamilyUser(),trip,incident,message);}}
 private void save(User user,Trip trip,Incident incident,String message){Notification n=notifications.findFirstByTripIdAndRecipientIdAndTypeOrderByCreatedAtDesc(trip.getId(),user.getId(),NotificationType.DELAY).orElseGet(Notification::new);n.setRecipient(user);n.setTrip(trip);n.setIncident(incident);n.setType(NotificationType.DELAY);n.setTitle("Retraso en tu ruta");n.setMessage(message);notifications.save(n);proactiveMessages.upsert(user,trip,incident);}
}
