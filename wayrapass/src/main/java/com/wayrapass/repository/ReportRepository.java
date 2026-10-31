package com.wayrapass.repository;

import com.wayrapass.model.BoardingStatus;
import com.wayrapass.model.Trip;
import com.wayrapass.model.TripStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface ReportRepository extends Repository<Trip, Long> {

    interface PunctualityProjection {
        long getEvaluatedTrips();

        long getOnTimeTrips();
    }

    interface OccupancyProjection {
        Long getTripId();

        long getConfirmedBoardings();

        int getVehicleCapacity();
    }

    interface StopDemandProjection {
        Long getStopId();

        String getStopName();

        long getConfirmedBoardings();
    }

    @Query("""
            select count(t) as evaluatedTrips,
                   coalesce(sum(case when t.actualEndAt <= t.scheduledEndAt then 1 else 0 end), 0) as onTimeTrips
            from Trip t
            where t.scheduledStartAt >= :from
              and t.scheduledStartAt < :toExclusive
              and t.scheduledStartAt < :now
              and t.status = :completed
              and t.scheduledEndAt is not null
              and t.actualEndAt is not null
            """)
    PunctualityProjection punctuality(
            @Param("from") Instant from,
            @Param("toExclusive") Instant toExclusive,
            @Param("now") Instant now,
            @Param("completed") TripStatus completed
    );

    @Query("""
            select t.id as tripId,
                   count(b.id) as confirmedBoardings,
                   v.capacity as vehicleCapacity
            from Trip t
            join t.vehicle v
            left join Boarding b on b.trip = t and b.status = :confirmed
            where t.scheduledStartAt >= :from
              and t.scheduledStartAt < :toExclusive
              and t.scheduledStartAt < :now
              and t.status = :completed
              and v.capacity > 0
            group by t.id, v.capacity
            """)
    List<OccupancyProjection> occupancy(
            @Param("from") Instant from,
            @Param("toExclusive") Instant toExclusive,
            @Param("now") Instant now,
            @Param("completed") TripStatus completed,
            @Param("confirmed") BoardingStatus confirmed
    );

    @Query("""
            select count(i.id)
            from Incident i
            where i.trip.scheduledStartAt >= :from
              and i.trip.scheduledStartAt < :toExclusive
              and i.trip.scheduledStartAt < :now
              and i.trip.status in :statuses
            """)
    long incidentCount(
            @Param("from") Instant from,
            @Param("toExclusive") Instant toExclusive,
            @Param("now") Instant now,
            @Param("statuses") Collection<TripStatus> statuses
    );

    @Query("""
            select b.stop.id as stopId,
                   b.stop.name as stopName,
                   count(b.id) as confirmedBoardings
            from Boarding b
            where b.trip.scheduledStartAt >= :from
              and b.trip.scheduledStartAt < :toExclusive
              and b.trip.scheduledStartAt < :now
              and b.trip.status in :statuses
              and b.status = :confirmed
            group by b.stop.id, b.stop.name
            order by count(b.id) desc, b.stop.name asc
            """)
    List<StopDemandProjection> stopDemand(
            @Param("from") Instant from,
            @Param("toExclusive") Instant toExclusive,
            @Param("now") Instant now,
            @Param("statuses") Collection<TripStatus> statuses,
            @Param("confirmed") BoardingStatus confirmed
    );
}
