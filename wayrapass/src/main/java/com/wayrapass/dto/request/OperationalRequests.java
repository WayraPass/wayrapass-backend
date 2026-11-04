package com.wayrapass.dto.request;
import com.wayrapass.model.*; import jakarta.validation.constraints.*; import java.math.BigDecimal; import java.time.*;
public final class OperationalRequests {private OperationalRequests(){}
 public record FamilyCode(@NotBlank @Size(max=100) String code,@Size(max=50) String relationship){}
 public record NotificationPreferences(
  @NotNull Boolean notifyBoarding,
  @NotNull Boolean notifyArrival,
  @NotNull Boolean notifyDelay,
  @NotNull Boolean notifyVehicleChange,
  @NotNull Boolean notifyIncident){}
 public record Enrollment(
  @NotNull Long routeId,
  @NotNull Long stopId,
  @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
  @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
  LocalTime time){
  public Enrollment(Long routeId,Long stopId){this(routeId,stopId,null,null,null);}
  @AssertTrue(message="latitude y longitude deben enviarse juntas")
  public boolean isLocationComplete(){return latitude==null&&longitude==null||latitude!=null&&longitude!=null;}
 }
 public record Suspension(@NotNull LocalDate startDate,@NotNull LocalDate endDate){}
 public record Trip(@NotNull Long routeId,Long routeScheduleId,Long driverId,Long vehicleId,@NotNull @FutureOrPresent Instant scheduledStartAt,@NotNull @Future Instant scheduledEndAt){}
 public record Assignment(@NotNull Long driverId,@NotNull Long vehicleId){}
 public record Reassignment(Long driverId,Long vehicleId){}
 public record TripStatusUpdate(@NotNull TripStatus status){}
 public record Location(@NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,@NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude){}
 public record Boarding(@NotNull Long tripId,@NotNull Long stopId,@NotBlank String vehicleQrToken){}
 public record Incident(@NotNull Long tripId,
  @NotNull(message="Selecciona el tipo de incidencia") IncidentType type,
  @Size(max=1000) String description,
  @PositiveOrZero int delayMinutes,
  @NotNull IncidentPriority priority){}
 public record AiProposal(@NotNull Long tripId,@NotNull AiActionType actionType,Long proposedDriverId,Long proposedVehicleId,@NotBlank @Size(max=1000) String requestText,@Size(max=1500) String aiExplanation){}
}
