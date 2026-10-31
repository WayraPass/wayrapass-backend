package com.wayrapass.dto.response;
import com.wayrapass.model.*; import java.math.BigDecimal; import java.time.*;
import java.util.List;
public final class OperationalResponses {private OperationalResponses(){}
 public record FamilyCode(String code,Instant expiresAt){} public record Enrollment(Long id,Long studentId,Long routeId,Long stopId,EnrollmentStatus status,LocalDate validFrom,LocalDate validUntil,LocalTime estimatedPickupTime){}
 public record NotificationPreferences(Long linkId,Long studentId,boolean notifyBoarding,boolean notifyArrival,
  boolean notifyDelay,boolean notifyVehicleChange,boolean notifyIncident){}
 public record EnrollmentStatusDetails(Long enrollmentId,Long routeId,Long stopId,EnrollmentStatus status,LocalDate suspendedFrom,LocalDate suspendedUntil){}
 public record Trip(Long id,Long routeId,Long driverId,Long vehicleId,Instant scheduledStartAt,Instant scheduledEndAt,Instant estimatedArrivalAt,TripStatus status,boolean wasReassigned){}
 public record Location(Long id,Long tripId,BigDecimal latitude,BigDecimal longitude,Instant recordedAt){} public record Boarding(Long id,Long tripId,Long studentId,Long stopId,Instant boardedAt){}
 public record Incident(Long id,Long tripId,Long routeId,Long driverId,IncidentType type,String description,
  int delayMinutes,IncidentPriority priority,IncidentStatus status,Instant createdAt){}
 public record Notification(Long id,NotificationType type,String title,String message,Instant createdAt,Instant readAt){}
 public record DriverTrip(Long id,Long tripId,Long routeId,Long driverId,Long vehicleId,
  Instant scheduledStartAt,Instant scheduledEndAt,Instant estimatedArrivalAt,TripStatus status,boolean wasReassigned,
  String routeCode,String routeName,String origin,String destination,Long routeScheduleId,
  Instant actualStartAt,Instant actualEndAt,boolean assignmentChanged,String assignmentChangeMessage,DriverVehicle vehicle,
  List<DriverRouteStop> stops){}
 public record DriverVehicle(Long vehicleId,String plate,String brand,String model,int capacity){}
 public record DriverRouteStop(Long stopId,int stopOrder,String name,String address,String district,
  int estimatedMinutesFromStart,boolean active){}
 public record ReassignmentOptions(Long tripId,List<DriverOption> drivers,List<VehicleOption> vehicles,String message){}
 public record DriverOption(Long driverId,String fullName,String licenseNumber,String licenseCategory,
  LocalDate licenseExpiration,AuthorizationStatus authorizationStatus,DriverOperationalStatus operationalStatus){}
 public record VehicleOption(Long vehicleId,String plate,String brand,String model,int capacity,
  AuthorizationStatus authorizationStatus,VehicleOperationalStatus operationalStatus){}
 public record WeeklyOperation(Long tripId,LocalDate operationDate,Instant scheduledStartAt,Instant scheduledEndAt,
  TripStatus status,Long routeId,String routeCode,String routeName,String origin,String destination,
  DriverOption driver,VehicleOption vehicle,boolean modified,String modificationMessage){}
 public record AiAction(Long id,Long tripId,AiActionType actionType,AiActionStatus status,Long proposedDriverId,Long proposedVehicleId){}
 public record RouteSearchOption(Long routeId,String routeCode,String routeName,String origin,String destination,
  Long institutionId,String institutionName,String campusName,Long stopId,String stopName,String stopAddress,
  String stopDistrict,BigDecimal stopLatitude,BigDecimal stopLongitude,Long distanceMeters,
  int stopOrder,DayOfWeek dayOfWeek,LocalTime routeDepartureTime,
  LocalTime estimatedPickupTime,LocalTime estimatedArrivalTime,boolean exactTimeMatch,
  int differenceMinutes,boolean favorite){}
 public record FavoriteRoute(Long routeId,String routeCode,String routeName,RouteStatus status,boolean available){}
}
