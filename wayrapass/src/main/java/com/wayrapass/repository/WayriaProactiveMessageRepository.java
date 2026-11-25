package com.wayrapass.repository;

import com.wayrapass.model.NotificationType;
import com.wayrapass.model.WayriaProactiveMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface WayriaProactiveMessageRepository extends JpaRepository<WayriaProactiveMessage, Long> {
    List<WayriaProactiveMessage> findByRecipientIdOrderByCreatedAtAsc(Long recipientId);
    Optional<WayriaProactiveMessage> findByTripIdAndRecipientIdAndType(Long tripId, Long recipientId, NotificationType type);
}
