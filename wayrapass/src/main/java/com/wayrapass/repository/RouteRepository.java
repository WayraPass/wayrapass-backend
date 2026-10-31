package com.wayrapass.repository;
import com.wayrapass.model.Route; import jakarta.persistence.LockModeType; import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param; import java.util.*;
public interface RouteRepository extends JpaRepository<Route,Long>{
 boolean existsByCodeIgnoreCase(String code);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from Route r where r.id=:id")
 Optional<Route> findByIdForUpdate(@Param("id") Long id);
 @Query("""
  select distinct r.id as routeId, r.code as routeCode, r.name as routeName,
   r.origin as origin, r.destination as destination, i.id as institutionId,
   i.name as institutionName, i.campusName as campusName, s.id as stopId,
   s.name as stopName, s.address as stopAddress, s.district as stopDistrict,
   s.latitude as stopLatitude, s.longitude as stopLongitude,
   rs.stopOrder as stopOrder, rs.estimatedMinutesFromStart as estimatedMinutesFromStart,
   sc.dayOfWeek as dayOfWeek, sc.departureTime as departureTime,
   sc.estimatedArrivalTime as estimatedArrivalTime
  from RouteStop rs
  join rs.route r
  join r.institution i
  join rs.stop s
  , RouteSchedule sc
  where sc.route.id = r.id
   and r.status = com.wayrapass.model.RouteStatus.ACTIVE
   and i.active = true
   and rs.active = true
   and s.active = true
   and sc.active = true
   and i.id = :institutionId
   and (:district is null or lower(s.district) = lower(:district))
   and (:routeId is null or r.id = :routeId)
  order by r.code, rs.stopOrder, sc.dayOfWeek, sc.departureTime
  """)
 List<RouteSearchProjection> searchAvailableOptions(@Param("institutionId") Long institutionId,
                                                     @Param("district") String district,
                                                     @Param("routeId") Long routeId);

 default List<RouteSearchProjection> searchAvailableOptions(Long institutionId,String district){
  return searchAvailableOptions(institutionId,district,null);
 }
}
