package com.wayrapass.repository;

import com.wayrapass.model.NotificationType;
import com.wayrapass.model.WayriaProactiveMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface WayriaProactiveMessageRepository extends JpaRepository<WayriaProactiveMessage, Long> {
    List<WayriaProactiveMessage> findByRecipientIdOrderByCreatedAtAsc(Long recipientId);
    Optional<WayriaProactiveMessage> findByTripIdAndRecipientIdAndType(Long tripId, Long recipientId, NotificationType type);
    @Query("select m from WayriaProactiveMessage m where m.recipient.id=:recipientId and (m.type<>com.wayrapass.model.NotificationType.DELAY or m.trip.status in :activeStatuses) order by m.createdAt asc")
    List<WayriaProactiveMessage> findVisibleByRecipientId(@Param("recipientId") Long recipientId, @Param("activeStatuses") Collection<com.wayrapass.model.TripStatus> activeStatuses);
}
