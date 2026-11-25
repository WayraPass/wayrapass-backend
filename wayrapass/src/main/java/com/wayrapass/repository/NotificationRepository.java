package com.wayrapass.repository;
import com.wayrapass.model.Notification; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface NotificationRepository extends JpaRepository<Notification,Long>{ List<Notification> findByRecipientIdOrderByCreatedAtDesc(Long id); Optional<Notification> findFirstByTripIdAndRecipientIdAndTypeOrderByCreatedAtDesc(Long tripId,Long recipientId,com.wayrapass.model.NotificationType type); }
