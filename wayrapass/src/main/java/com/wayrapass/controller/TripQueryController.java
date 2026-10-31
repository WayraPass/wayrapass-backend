package com.wayrapass.controller;

import com.wayrapass.dto.response.TripQueryResponses;
import com.wayrapass.service.TripQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class TripQueryController {
    private final TripQueryService service;

    @GetMapping("/api/trips/me/history")
    @PreAuthorize("hasRole('STUDENT')")
    public List<TripQueryResponses.HistoryItem> myHistory(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return service.myHistory(from, to);
    }

    @GetMapping("/api/trips/me/history/{tripId}")
    @PreAuthorize("hasRole('STUDENT')")
    public TripQueryResponses.StudentHistoryDetail myHistoryDetail(@PathVariable Long tripId) {
        return service.myHistoryDetail(tripId);
    }

    @GetMapping("/api/family-links/me/students/{studentId}/trip-history")
    @PreAuthorize("hasRole('FAMILY')")
    public List<TripQueryResponses.HistoryItem> familyHistory(
            @PathVariable Long studentId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return service.familyHistory(studentId, from, to);
    }

    @GetMapping("/api/family-links/me/students/{studentId}/trip-history/{tripId}")
    @PreAuthorize("hasRole('FAMILY')")
    public TripQueryResponses.FamilyHistoryDetail familyHistoryDetail(
            @PathVariable Long studentId,
            @PathVariable Long tripId
    ) {
        return service.familyHistoryDetail(studentId, tripId);
    }

    @GetMapping("/api/family-links/me/students/{studentId}/upcoming-trips")
    @PreAuthorize("hasRole('FAMILY')")
    public List<TripQueryResponses.UpcomingTrip> familyUpcomingTrips(@PathVariable Long studentId) {
        return service.familyUpcomingTrips(studentId);
    }

    @GetMapping("/api/trips/me/current")
    @PreAuthorize("hasRole('STUDENT')")
    public TripQueryResponses.CurrentTripResponse myCurrentTrip() {
        return service.myCurrentTrip();
    }

    @GetMapping("/api/family-links/me/students/{studentId}/current-trip/driver")
    @PreAuthorize("hasRole('FAMILY')")
    public TripQueryResponses.DriverCredential familyCurrentTripDriver(@PathVariable Long studentId) {
        return service.familyCurrentTripDriver(studentId);
    }
}
