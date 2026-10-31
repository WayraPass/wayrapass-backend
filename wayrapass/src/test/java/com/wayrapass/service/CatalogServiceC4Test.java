package com.wayrapass.service;

import com.wayrapass.dto.request.CatalogRequests;
import com.wayrapass.exception.BusinessRuleException;
import com.wayrapass.exception.ConflictException;
import com.wayrapass.model.*;
import com.wayrapass.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CatalogServiceC4Test {
    @Mock InstitutionRepository institutions;
    @Mock RouteRepository routes;
    @Mock StopRepository stops;
    @Mock RouteScheduleRepository schedules;
    @Mock VehicleRepository vehicles;
    @Mock RouteStopRepository routeStops;
    @Mock TripRepository trips;
    @InjectMocks CatalogService service;

    @Test
    void createsAuthorizedAvailableVehicle() {
        when(vehicles.save(any())).thenAnswer(invocation -> {
            Vehicle vehicle = invocation.getArgument(0);
            vehicle.setId(12L);
            return vehicle;
        });

        var result = service.saveVehicle(null, request(AuthorizationStatus.AUTHORIZED,
                VehicleOperationalStatus.AVAILABLE, "QR-12"));

        assertEquals(12L, result.id());
        assertEquals("ABC-123", result.plate());
        assertEquals(24, result.capacity());
    }

    @Test
    void listsVehiclesWithStateAndCapacity() {
        Vehicle vehicle = vehicle(VehicleOperationalStatus.MAINTENANCE);
        when(vehicles.findAllByOrderByIdAsc()).thenReturn(List.of(vehicle));

        var result = service.vehicles();

        assertEquals(1, result.size());
        assertEquals(20, result.getFirst().capacity());
        assertEquals(VehicleOperationalStatus.MAINTENANCE, result.getFirst().operationalStatus());
    }

    @Test
    void availableVehicleMustBeAuthorized() {
        assertThrows(BusinessRuleException.class, () -> service.saveVehicle(null,
                request(AuthorizationStatus.PENDING, VehicleOperationalStatus.AVAILABLE, "QR-12")));
        verify(vehicles, never()).save(any());
    }

    @Test
    void updateRejectsDuplicatePlateAndQr() {
        Vehicle vehicle = vehicle(VehicleOperationalStatus.UNAVAILABLE);
        when(vehicles.findByIdForUpdate(12L)).thenReturn(Optional.of(vehicle));
        when(vehicles.existsByPlateIgnoreCaseAndIdNot("ABC-123", 12L)).thenReturn(true);
        assertThrows(ConflictException.class, () -> service.saveVehicle(12L,
                request(AuthorizationStatus.AUTHORIZED, VehicleOperationalStatus.UNAVAILABLE, "QR-12")));

        reset(vehicles);
        when(vehicles.findByIdForUpdate(12L)).thenReturn(Optional.of(vehicle));
        when(vehicles.existsByQrTokenAndIdNot("QR-12", 12L)).thenReturn(true);
        assertThrows(ConflictException.class, () -> service.saveVehicle(12L,
                request(AuthorizationStatus.AUTHORIZED, VehicleOperationalStatus.UNAVAILABLE, "QR-12")));
    }

    @Test
    void maintenanceStatePreservesVehicle() {
        Vehicle vehicle = vehicle(VehicleOperationalStatus.AVAILABLE);
        when(vehicles.findByIdForUpdate(12L)).thenReturn(Optional.of(vehicle));
        when(vehicles.save(vehicle)).thenReturn(vehicle);

        var result = service.saveVehicle(12L,
                request(AuthorizationStatus.AUTHORIZED, VehicleOperationalStatus.MAINTENANCE, "QR-12"));

        assertEquals(VehicleOperationalStatus.MAINTENANCE, result.operationalStatus());
        verify(vehicles).save(vehicle);
    }

    @Test
    void deleteSoftDisablesVehicle() {
        Vehicle vehicle = vehicle(VehicleOperationalStatus.AVAILABLE);
        when(vehicles.findByIdForUpdate(12L)).thenReturn(Optional.of(vehicle));

        service.deleteVehicle(12L);

        assertEquals(VehicleOperationalStatus.INACTIVE, vehicle.getOperationalStatus());
        verify(vehicles, never()).delete(any());
    }

    @Test
    void vehicleInOperationalTripCannotBeDisabled() {
        Vehicle vehicle = vehicle(VehicleOperationalStatus.IN_SERVICE);
        when(vehicles.findByIdForUpdate(12L)).thenReturn(Optional.of(vehicle));
        when(trips.existsByVehicleIdAndStatusIn(eq(12L), anyCollection())).thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> service.deleteVehicle(12L));
        assertEquals(VehicleOperationalStatus.IN_SERVICE, vehicle.getOperationalStatus());
    }

    @Test
    void inServiceVehicleCannotBeMovedToMaintenanceWhileTripIsRunning() {
        Vehicle vehicle = vehicle(VehicleOperationalStatus.IN_SERVICE);
        when(vehicles.findByIdForUpdate(12L)).thenReturn(Optional.of(vehicle));
        when(trips.existsByVehicleIdAndStatusIn(eq(12L), anyCollection())).thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> service.saveVehicle(12L,
                request(AuthorizationStatus.AUTHORIZED, VehicleOperationalStatus.MAINTENANCE, "QR-12")));
    }

    private CatalogRequests.Vehicle request(AuthorizationStatus authorization,
                                            VehicleOperationalStatus status, String qr) {
        return new CatalogRequests.Vehicle("ABC-123", "Mercedes", "Sprinter", 24, qr,
                authorization, status);
    }

    private Vehicle vehicle(VehicleOperationalStatus status) {
        Vehicle vehicle = new Vehicle();
        vehicle.setId(12L);
        vehicle.setPlate("OLD-111");
        vehicle.setCapacity(20);
        vehicle.setQrToken("QR-OLD");
        vehicle.setAuthorizationStatus(AuthorizationStatus.AUTHORIZED);
        vehicle.setOperationalStatus(status);
        return vehicle;
    }
}
