package com.wayrapass.repository;
import com.wayrapass.model.Incident; import org.springframework.data.jpa.repository.JpaRepository; import java.util.List;
public interface IncidentRepository extends JpaRepository<Incident,Long>{ List<Incident> findByTripIdOrderByCreatedAtAsc(Long tripId); }
