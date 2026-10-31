package com.wayrapass.repository;
import com.wayrapass.model.RouteSchedule; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface RouteScheduleRepository extends JpaRepository<RouteSchedule,Long>{ List<RouteSchedule> findByRouteId(Long routeId); boolean existsByRouteIdAndActiveTrue(Long routeId); }
