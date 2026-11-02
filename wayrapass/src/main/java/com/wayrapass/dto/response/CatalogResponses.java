package com.wayrapass.dto.response;
import com.wayrapass.model.*; import java.math.BigDecimal; import java.time.*;
import java.util.List;
public final class CatalogResponses { private CatalogResponses(){}
 public record Institution(Long id,String name,String campusName,String address,String district,boolean active,Instant createdAt){}
 public record Route(Long id,Long institutionId,String code,String name,String origin,String destination,String description,int capacity,RouteStatus status){}
 public record Stop(Long id,String name,String address,String district,BigDecimal latitude,BigDecimal longitude,boolean active){}
 public record Schedule(Long id,Long routeId,DayOfWeek dayOfWeek,LocalTime departureTime,LocalTime estimatedArrivalTime,boolean active){}
 public record RouteStop(Long id,Long routeId,Long stopId,int stopOrder,int estimatedMinutesFromStart,boolean active){}
 public record Vehicle(Long id,String plate,String brand,String model,int capacity,String qrToken,AuthorizationStatus authorizationStatus,VehicleOperationalStatus operationalStatus){}
 public record PublicInstitution(Long id,String name,String campusName,String displayName,String district){}
 public record PickupStop(Long id,String name,String district,String address){}
 public record RouteSearchInstitution(Long id,String name,String campusName,String displayName,String district){}
 public record RouteSearchStop(Long id,String name,String address,String district,int sequence,
  int estimatedOffsetMinutes){}
 public record RouteSearchSchedule(Long id,DayOfWeek dayOfWeek,LocalTime departureTime,
  LocalTime estimatedArrivalTime){}
 public record GroupedRouteSearch(Long routeId,String routeCode,String routeName,String origin,String destination,
  RouteSearchInstitution institution,List<RouteSearchStop> stops,List<RouteSearchSchedule> schedules,
  boolean favorite){}
}
