package com.wayrapass.dto.request;
import com.wayrapass.model.*; import jakarta.validation.constraints.*; import java.math.BigDecimal; import java.time.*;
public final class CatalogRequests { private CatalogRequests(){}
 public record Institution(@NotBlank @Size(max=150) String name,@Size(max=150) String campusName,@Size(max=250) String address,@Size(max=100) String district,boolean active){}
 public record Route(@NotNull Long institutionId,@NotBlank @Size(max=30) String code,@NotBlank @Size(max=150) String name,@NotBlank @Size(max=200) String origin,@NotBlank @Size(max=200) String destination,@Size(max=500) String description,@Positive int capacity,@NotNull RouteStatus status){}
 public record Stop(@NotBlank @Size(max=150) String name,@Size(max=250) String address,@Size(max=100) String district,@DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,@DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,boolean active){
  @AssertTrue(message="latitude y longitude deben enviarse juntas")
  public boolean isLocationComplete(){return latitude==null&&longitude==null||latitude!=null&&longitude!=null;}
 }
 public record Schedule(@NotNull Long routeId,@NotNull DayOfWeek dayOfWeek,@NotNull LocalTime departureTime,LocalTime estimatedArrivalTime,boolean active){}
 public record RouteStop(@NotNull Long routeId,@NotNull Long stopId,@Positive int stopOrder,@PositiveOrZero int estimatedMinutesFromStart,boolean active){}
 public record Vehicle(@NotBlank @Size(max=20) String plate,@Size(max=60) String brand,@Size(max=60) String model,@Positive int capacity,@Size(max=120) String qrToken,@NotNull AuthorizationStatus authorizationStatus,@NotNull VehicleOperationalStatus operationalStatus){}
}
