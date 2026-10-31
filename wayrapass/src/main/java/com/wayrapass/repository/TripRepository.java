package com.wayrapass.repository;
import com.wayrapass.model.*; import org.springframework.data.jpa.repository.*; import jakarta.persistence.LockModeType; import java.time.Instant; import java.util.*;
public interface TripRepository extends JpaRepository<Trip,Long>{
 List<Trip> findByDriverUserId(Long userId);
 boolean existsByVehicleIdAndStatusInAndIdNot(Long vehicleId,Collection<TripStatus> statuses,Long id);
 boolean existsByVehicleIdAndStatusIn(Long vehicleId,Collection<TripStatus> statuses);
 boolean existsByDriverIdAndStatusIn(Long driverId,Collection<TripStatus> statuses);
 @Query("select t from Trip t join fetch t.route r join fetch r.institution where r.id=:routeId and t.status=:status and t.scheduledStartAt>:now order by t.scheduledStartAt asc") List<Trip> findUpcomingByRouteAndStatus(Long routeId,TripStatus status,Instant now);
 @Query("""
  select distinct t from Trip t
  join fetch t.route r
  join fetch r.institution
  left join fetch t.routeSchedule
  left join fetch t.driver d
  left join fetch d.user
  left join fetch t.vehicle
  where r.id=:routeId
   and (t.status in :operationalStatuses
    or (t.status=:scheduledStatus and t.scheduledStartAt>=:now))
  """)
 List<Trip> findCurrentCandidates(Long routeId,Collection<TripStatus> operationalStatuses,
                                  TripStatus scheduledStatus,Instant now);
 @Query("""
  select distinct t from Trip t
  join fetch t.route r
  left join fetch t.routeSchedule
  left join fetch t.driver d
  left join fetch d.user
  left join fetch t.vehicle
  where t.scheduledStartAt>=:from and t.scheduledStartAt<:toExclusive
  order by t.scheduledStartAt asc,t.id asc
  """)
 List<Trip> findCalendarOperations(Instant from,Instant toExclusive);
 @Query("select count(t)>0 from Trip t where t.route.id=:routeId and t.status in :statuses and t.scheduledStartAt<:end and coalesce(t.scheduledEndAt,t.scheduledStartAt)>=:start") boolean routeTripOverlaps(Long routeId,Collection<TripStatus> statuses,Instant start,Instant end);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select t from Trip t where t.id=:id") Optional<Trip> findByIdForUpdate(Long id);
 @Query("select count(t)>0 from Trip t where t.driver.id=:driverId and t.status not in ('COMPLETED','CANCELLED') and t.scheduledStartAt < :end and coalesce(t.scheduledEndAt,t.scheduledStartAt) > :start and t.id<>:excludeId") boolean driverOverlaps(Long driverId,Instant start,Instant end,Long excludeId);
 @Query("select count(t)>0 from Trip t where t.vehicle.id=:vehicleId and t.status not in ('COMPLETED','CANCELLED') and t.scheduledStartAt < :end and coalesce(t.scheduledEndAt,t.scheduledStartAt) > :start and t.id<>:excludeId") boolean vehicleOverlaps(Long vehicleId,Instant start,Instant end,Long excludeId);
}
