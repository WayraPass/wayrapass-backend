package com.wayrapass.service;

import com.wayrapass.agent.dto.ProactiveMessageResponse;
import com.wayrapass.model.*;
import com.wayrapass.repository.*;
import com.wayrapass.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service @RequiredArgsConstructor
public class WayriaProactiveMessageService {
    private final WayriaProactiveMessageRepository messages; private final IncidentRepository incidents; private final WayriaDelayAlertComposer composer; private final CurrentUser currentUser;
    @Transactional public void upsert(User recipient, Trip trip, Incident incident) {
        List<String> descriptions = incidents.findByTripIdOrderByCreatedAtAsc(trip.getId()).stream().map(Incident::getDescription).filter(value -> value != null && !value.isBlank()).toList();
        String text = composer.compose(trip.getRoute().getCode(), incident.getDelayMinutes(), descriptions);
        WayriaProactiveMessage message = messages.findByTripIdAndRecipientIdAndType(trip.getId(), recipient.getId(), NotificationType.DELAY).orElseGet(WayriaProactiveMessage::new);
        message.setRecipient(recipient); message.setTrip(trip); message.setIncident(incident); message.setType(NotificationType.DELAY); message.setMessage(text); messages.save(message);
    }
    @Transactional(readOnly = true) public List<ProactiveMessageResponse> mine() { return messages.findVisibleByRecipientId(currentUser.id(), List.of(TripStatus.SCHEDULED, TripStatus.BOARDING, TripStatus.IN_PROGRESS)).stream().map(item -> new ProactiveMessageResponse(item.getId(), item.getTrip().getId(), item.getIncident() == null ? null : item.getIncident().getId(), item.getType(), item.getMessage(), item.getCreatedAt())).toList(); }
}
