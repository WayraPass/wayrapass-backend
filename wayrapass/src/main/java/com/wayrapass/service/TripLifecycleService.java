package com.wayrapass.service;

import com.wayrapass.exception.BusinessRuleException;
import com.wayrapass.exception.ResourceNotFoundException;
import com.wayrapass.model.*;
import com.wayrapass.repository.NotificationRepository;
import com.wayrapass.repository.StudentFamilyLinkRepository;
import com.wayrapass.repository.StudentRouteEnrollmentRepository;
import com.wayrapass.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TripLifecycleService {
    private static final ZoneId SERVICE_ZONE = ZoneId.of("America/Lima");
    private static final List<TripStatus> OPERATIONAL_STATUSES =
            List.of(TripStatus.BOARDING, TripStatus.IN_PROGRESS);
    private static final Map<TripStatus, Set<TripStatus>> VALID_TRANSITIONS = Map.of(
            TripStatus.SCHEDULED, EnumSet.of(TripStatus.BOARDING, TripStatus.CANCELLED),
            TripStatus.BOARDING, EnumSet.of(TripStatus.IN_PROGRESS, TripStatus.CANCELLED),
            TripStatus.IN_PROGRESS, EnumSet.of(TripStatus.COMPLETED, TripStatus.CANCELLED),
            TripStatus.COMPLETED, EnumSet.noneOf(TripStatus.class),
            TripStatus.CANCELLED, EnumSet.noneOf(TripStatus.class));

    private final TripRepository trips;
    private final StudentRouteEnrollmentRepository enrollments;
    private final StudentFamilyLinkRepository links;
    private final NotificationRepository notifications;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<Long> dueTripIds(Instant now) {
        return trips.findDueLifecycleIds(now);
    }

    @Transactional
    public boolean reconcileTrip(Long tripId, Instant now) {
        Trip trip = trips.findByIdForUpdate(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Viaje no encontrado."));
        return reconcile(trip, now);
    }

    public boolean reconcile(Trip trip, Instant now) {
        if (trip.getStatus() == TripStatus.COMPLETED || trip.getStatus() == TripStatus.CANCELLED) {
            return false;
        }
        boolean changed = false;
        Instant start = trip.getScheduledStartAt();
        Instant end = trip.getScheduledEndAt();
        if (trip.getStatus() == TripStatus.SCHEDULED && !now.isBefore(start)) {
            transitionAt(trip, TripStatus.BOARDING, start);
            transitionAt(trip, TripStatus.IN_PROGRESS, start);
            changed = true;
        } else if (trip.getStatus() == TripStatus.BOARDING && !now.isBefore(start)) {
            transitionAt(trip, TripStatus.IN_PROGRESS, start);
            changed = true;
        }
        if (trip.getStatus() == TripStatus.IN_PROGRESS && end != null && !now.isBefore(end)) {
            transitionAt(trip, TripStatus.COMPLETED, end);
            changed = true;
        }
        return changed;
    }

    public void transition(Trip trip, TripStatus target) {
        transitionAt(trip, target, clock.instant());
    }

    void transitionAt(Trip trip, TripStatus target, Instant effectiveAt) {
        if (!VALID_TRANSITIONS.getOrDefault(trip.getStatus(), Set.of()).contains(target)) {
            throw new BusinessRuleException("Transición de estado no permitida: "
                    + trip.getStatus() + " -> " + target + ".");
        }
        if (target == TripStatus.BOARDING) activateResources(trip, effectiveAt);
        if (target == TripStatus.IN_PROGRESS && trip.getActualStartAt() == null) {
            trip.setActualStartAt(effectiveAt);
        }
        if (target == TripStatus.COMPLETED) {
            trip.setActualEndAt(effectiveAt);
            releaseVehicle(trip.getVehicle(), trip.getId());
        }
        if (target == TripStatus.CANCELLED) releaseVehicle(trip.getVehicle(), trip.getId());
        trip.setStatus(target);
        if (target == TripStatus.COMPLETED) {
            notifyRoute(trip, NotificationType.ARRIVAL, "Llegada confirmada",
                    "El viaje llegó a su destino.");
        }
    }

    private void activateResources(Trip trip, Instant effectiveAt) {
        Driver driver = trip.getDriver();
        if (driver != null && (driver.getAuthorizationStatus() != AuthorizationStatus.AUTHORIZED
                || driver.getOperationalStatus() != DriverOperationalStatus.AVAILABLE
                || !driver.getUser().isEnabled()
                || driver.getLicenseExpiration() == null
                || driver.getLicenseExpiration().isBefore(LocalDate.ofInstant(effectiveAt, SERVICE_ZONE)))) {
            throw new BusinessRuleException("El conductor no está autorizado, habilitado y con licencia vigente.");
        }
        Vehicle vehicle = trip.getVehicle();
        if (vehicle != null) {
            if (vehicle.getAuthorizationStatus() != AuthorizationStatus.AUTHORIZED
                    || vehicle.getOperationalStatus() != VehicleOperationalStatus.AVAILABLE) {
                throw new BusinessRuleException("El vehículo no está autorizado y disponible.");
            }
            vehicle.setOperationalStatus(VehicleOperationalStatus.IN_SERVICE);
        }
    }

    private void releaseVehicle(Vehicle vehicle, Long tripId) {
        if (vehicle != null && vehicle.getOperationalStatus() == VehicleOperationalStatus.IN_SERVICE
                && !trips.existsByVehicleIdAndStatusInAndIdNot(
                vehicle.getId(), OPERATIONAL_STATUSES, tripId)) {
            vehicle.setOperationalStatus(VehicleOperationalStatus.AVAILABLE);
        }
    }

    private void notifyRoute(Trip trip, NotificationType type, String title, String message) {
        for (StudentRouteEnrollment enrollment : enrollments.findByRouteIdAndStatus(
                trip.getRoute().getId(), EnrollmentStatus.ACTIVE)) {
            notify(enrollment.getStudent().getUser(), trip, type, title, message);
            for (StudentFamilyLink link : links.findByStudentIdAndAuthorizedTrue(
                    enrollment.getStudent().getId())) {
                if (type != NotificationType.ARRIVAL || link.isNotifyArrival()) {
                    notify(link.getFamilyUser(), trip, type, title, message);
                }
            }
        }
    }

    private void notify(User recipient, Trip trip, NotificationType type, String title, String message) {
        Notification notification = new Notification();
        notification.setRecipient(recipient);
        notification.setTrip(trip);
        notification.setType(type);
        notification.setTitle(title);
        notification.setMessage(message);
        notifications.save(notification);
    }
}
