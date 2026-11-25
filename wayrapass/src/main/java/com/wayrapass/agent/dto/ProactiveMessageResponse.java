package com.wayrapass.agent.dto;

import com.wayrapass.model.NotificationType;
import java.time.Instant;

public record ProactiveMessageResponse(Long id, Long tripId, Long incidentId, NotificationType type, String message, Instant createdAt) { }
