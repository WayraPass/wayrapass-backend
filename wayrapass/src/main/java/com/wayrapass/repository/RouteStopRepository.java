package com.wayrapass.repository;
import com.wayrapass.model.RouteStop; import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param; import java.util.*;
public interface RouteStopRepository extends JpaRepository<RouteStop,Long>{
 boolean existsByRouteIdAndStopId(Long routeId,Long stopId);
 Optional<RouteStop> findByRouteIdAndStopId(Long routeId,Long stopId);
 boolean existsByRouteIdAndActiveTrueAndStopActiveTrue(Long routeId);
 List<RouteStop> findByRouteIdOrderByStopOrder(Long routeId);
 @Query("select rs from RouteStop rs join fetch rs.stop s where rs.route.id=:routeId and rs.active=true and s.active=true order by rs.stopOrder")
 List<RouteStop> findAvailableByRouteIdOrderByStopOrder(@Param("routeId") Long routeId);
 @Query("select rs from RouteStop rs join fetch rs.stop s where rs.route.id in :routeIds and rs.active=true and s.active=true order by rs.route.code,rs.stopOrder")
 List<RouteStop> findAvailableByRouteIds(@Param("routeIds") Collection<Long> routeIds);
}
