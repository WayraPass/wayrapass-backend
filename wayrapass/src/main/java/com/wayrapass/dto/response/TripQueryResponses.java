package com.wayrapass.dto.response;

import com.wayrapass.model.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;

public final class TripQueryResponses {
    private TripQueryResponses() {
    }

    public record HistoryItem(
            Long tripId,
            Long routeId,
            String routeCode,
            String routeName,
            String routeOrigin,
            String routeDestination,
            String institutionName,
            String campusName,
            Instant scheduledStartAt,
            Instant scheduledEndAt,
            Instant actualStartAt,
            Instant actualEndAt,
            TripStatus tripStatus,
            BoardingStatus boardingStatus,
            Instant boardedAt,
            Long stopId,
            String stopName,
            boolean delayed
    ) {
    }

    public record IncidentItem(
            Long id,
            IncidentType type,
            String description,
            int delayMinutes,
            IncidentPriority priority,
            IncidentStatus status,
            Instant createdAt
    ) {
    }

    public record StudentHistoryDetail(
            HistoryItem trip,
            Long driverId,
            String driverName,
            Long vehicleId,
            String vehiclePlate,
            String vehicleBrand,
            String vehicleModel,
            List<IncidentItem> incidents
    ) {
    }

    public record FamilyHistoryDetail(
            HistoryItem trip,
            List<IncidentItem> incidents
    ) {
    }

    public record UpcomingTrip(
            Long tripId,
            Long routeId,
            String routeCode,
            String routeName,
            String routeOrigin,
            String routeDestination,
            String institutionName,
            String campusName,
            Long stopId,
            String stopName,
            Instant scheduledStartAt,
            Instant scheduledEndAt,
            TripStatus status,
            boolean resourcesReassigned,
            boolean studentRouteOrStopModified,
            String modificationMessage
    ) {
    }

    public record CurrentTripResponse(
            boolean available,
            String message,
            CurrentTripDetails trip
    ) {
    }

    public record CurrentTripDetails(
            Long tripId,
            Long routeId,
            String routeCode,
            String routeName,
            TripStatus tripStatus,
            Instant scheduledStartAt,
            Instant scheduledEndAt,
            Instant actualStartAt,
            Instant actualEndAt,
            VehicleSummary vehicle,
            AssignedStop assignedStop,
            LocationStatus location,
            StopArrivalEstimate stopArrival,
            UniversityArrivalEstimate universityArrival,
            DriverCredential driver
    ) {
    }

    public record VehicleSummary(Long vehicleId,String plate,String brand,String model) {
    }

    public record AssignedStop(Long stopId,String name,String address,String district) {
    }

    public enum LocationFreshness {
        RECENT,
        STALE,
        UNAVAILABLE
    }

    public enum EtaSource {
        PLANNED,
        OPERATIONAL,
        UNAVAILABLE
    }

    public record LocationStatus(
            boolean available,
            boolean stale,
            LocationFreshness freshness,
            BigDecimal latitude,
            BigDecimal longitude,
            Instant recordedAt,
            String message
    ) {
    }

    public record StopArrivalEstimate(
            boolean available,
            Instant scheduledArrivalAt,
            Instant estimatedArrivalAt,
            Long remainingMinutes,
            Instant updatedAt,
            String message
    ) {
    }

    public record UniversityArrivalEstimate(
            boolean available,
            Instant scheduledArrivalAt,
            Instant estimatedArrivalAt,
            Long remainingMinutes,
            boolean delayed,
            Long delayMinutes,
            Instant updatedAt,
            EtaSource source,
            String message
    ) {
    }

    public record DriverCredential(
            boolean assigned,
            String message,
            Long driverId,
            String fullName,
            String credentialImageUrl,
            boolean credentialImageAvailable,
            String licenseNumber,
            String licenseCategory,
            LocalDate licenseExpiration,
            boolean licenseExpired,
            AuthorizationStatus authorizationStatus,
            DriverOperationalStatus operationalStatus,
            boolean authorized
    ) {
    }
}
