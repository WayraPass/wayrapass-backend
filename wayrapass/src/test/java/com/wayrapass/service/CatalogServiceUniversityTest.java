package com.wayrapass.service;

import com.wayrapass.exception.BusinessRuleException;
import com.wayrapass.model.Institution;
import com.wayrapass.model.Stop;
import com.wayrapass.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CatalogServiceUniversityTest {
    @Mock InstitutionRepository institutions;
    @Mock RouteRepository routes;
    @Mock StopRepository stops;
    @Mock RouteScheduleRepository schedules;
    @Mock VehicleRepository vehicles;
    @Mock RouteStopRepository routeStops;
    @Mock TripRepository trips;
    @InjectMocks CatalogService service;

    @Test
    void publicInstitutionsContainOnlyActiveRepositorySelectionAndHumanDisplayName() {
        Institution institution = new Institution();
        institution.setId(25L);
        institution.setName("UPC");
        institution.setCampusName("Monterrico");
        institution.setDistrict("Santiago de Surco");
        institution.setActive(true);
        when(institutions.findByActiveTrueOrderByNameAscCampusNameAscIdAsc()).thenReturn(List.of(institution));

        var result = service.publicInstitutions();

        assertEquals(1, result.size());
        assertEquals("UPC — Monterrico", result.getFirst().displayName());
    }

    @Test
    void pickupCatalogUsesStableDistinctDatabaseResultsAndNoCoordinates() {
        when(stops.findAvailablePickupDistricts()).thenReturn(List.of("Barranco", "San Miguel"));
        Stop stop = new Stop();
        stop.setId(101L);
        stop.setName("Plaza San Miguel");
        stop.setDistrict("San Miguel");
        stop.setAddress("Av. La Marina");
        when(stops.findAvailablePickupsByDistrict("San Miguel")).thenReturn(List.of(stop));

        assertEquals(List.of("Barranco", "San Miguel"), service.pickupDistricts());
        var result = service.pickupStops(" San Miguel ");
        assertEquals(101L, result.getFirst().id());
        assertEquals("Av. La Marina", result.getFirst().address());
        verify(stops).findAvailablePickupsByDistrict("San Miguel");
    }

    @Test
    void missingDistrictIsRejectedAndUnknownDistrictReturnsEmptyList() {
        assertThrows(BusinessRuleException.class, () -> service.pickupStops(" "));
        when(stops.findAvailablePickupsByDistrict("Distrito inexistente")).thenReturn(List.of());
        assertTrue(service.pickupStops("Distrito inexistente").isEmpty());
    }
}
