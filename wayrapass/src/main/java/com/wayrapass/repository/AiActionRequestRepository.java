package com.wayrapass.repository;
import com.wayrapass.model.AiActionRequest; import org.springframework.data.jpa.repository.JpaRepository;
public interface AiActionRequestRepository extends JpaRepository<AiActionRequest,Long>{ java.util.Optional<AiActionRequest> findFirstByRequestedByIdAndStatusOrderByCreatedAtDesc(Long userId, com.wayrapass.model.AiActionStatus status); }
