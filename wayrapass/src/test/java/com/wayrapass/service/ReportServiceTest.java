package com.wayrapass.service;

import com.wayrapass.exception.BusinessRuleException;
import com.wayrapass.model.ReportIndicator;
import com.wayrapass.repository.ReportRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {
    @Mock ReportRepository reports;
    @InjectMocks ReportService service;

    @Test
    void completeReportCalculatesAllRequiredIndicators() {
        ReportRepository.PunctualityProjection punctuality = mock(ReportRepository.PunctualityProjection.class);
        when(punctuality.getEvaluatedTrips()).thenReturn(4L);
        when(punctuality.getOnTimeTrips()).thenReturn(3L);
        when(reports.punctuality(any(), any(), any(), any())).thenReturn(punctuality);
        List<ReportRepository.OccupancyProjection> occupancyRows = List.of(
                occupancy(6, 10),
                occupancy(8, 20)
        );
        List<ReportRepository.StopDemandProjection> demandRows = List.of(
                demand(8L, "Paradero Próceres", 9),
                demand(9L, "Av. Canadá", 5)
        );
        when(reports.occupancy(any(), any(), any(), any(), any())).thenReturn(occupancyRows);
        when(reports.incidentCount(any(), any(), any(), anyCollection())).thenReturn(2L);
        when(reports.stopDemand(any(), any(), any(), anyCollection(), any())).thenReturn(demandRows);

        var result = service.generate(
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 30),
                ReportIndicator.ALL
        );

        assertTrue(result.hasData());
        assertNull(result.message());
        assertEquals(4, result.punctuality().evaluatedTrips());
        assertEquals(1, result.punctuality().lateTrips());
        assertEquals(new BigDecimal("75.00"), result.punctuality().onTimePercentage());
        assertEquals(14, result.occupancy().confirmedBoardings());
        assertEquals(30, result.occupancy().offeredCapacity());
        assertEquals(new BigDecimal("46.67"), result.occupancy().occupancyPercentage());
        assertEquals(2, result.incidents().totalIncidents());
        assertEquals("Paradero Próceres", result.stopDemand().getFirst().stopName());
        assertEquals("America/Lima", result.period().zoneId());
    }

    @Test
    void indicatorFilterCalculatesOnlySelectedSection() {
        ReportRepository.PunctualityProjection punctuality = mock(ReportRepository.PunctualityProjection.class);
        when(punctuality.getEvaluatedTrips()).thenReturn(2L);
        when(punctuality.getOnTimeTrips()).thenReturn(2L);
        when(reports.punctuality(any(), any(), any(), any())).thenReturn(punctuality);

        var result = service.generate(
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 30),
                ReportIndicator.PUNCTUALITY
        );

        assertNotNull(result.punctuality());
        assertNull(result.occupancy());
        assertNull(result.incidents());
        assertNull(result.stopDemand());
        verify(reports, never()).occupancy(any(), any(), any(), any(), any());
        verify(reports, never()).incidentCount(any(), any(), any(), anyCollection());
    }

    @Test
    void validPeriodWithoutDataReturnsNormalEmptyReport() {
        ReportRepository.PunctualityProjection punctuality = mock(ReportRepository.PunctualityProjection.class);
        when(reports.punctuality(any(), any(), any(), any())).thenReturn(punctuality);
        when(reports.occupancy(any(), any(), any(), any(), any())).thenReturn(List.of());
        when(reports.stopDemand(any(), any(), any(), anyCollection(), any())).thenReturn(List.of());

        var result = service.generate(
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 1, 31),
                ReportIndicator.ALL
        );

        assertFalse(result.hasData());
        assertEquals(ReportService.NO_DATA_MESSAGE, result.message());
        assertEquals(BigDecimal.ZERO.setScale(2), result.punctuality().onTimePercentage());
    }

    @Test
    void invertedPeriodIsRejectedBeforeQueryingRepositories() {
        assertThrows(BusinessRuleException.class, () -> service.generate(
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 9, 30),
                ReportIndicator.ALL
        ));
        verifyNoInteractions(reports);
    }

    private ReportRepository.OccupancyProjection occupancy(long boardings, int capacity) {
        ReportRepository.OccupancyProjection projection = mock(ReportRepository.OccupancyProjection.class);
        when(projection.getConfirmedBoardings()).thenReturn(boardings);
        when(projection.getVehicleCapacity()).thenReturn(capacity);
        return projection;
    }

    private ReportRepository.StopDemandProjection demand(Long stopId, String stopName, long boardings) {
        ReportRepository.StopDemandProjection projection = mock(ReportRepository.StopDemandProjection.class);
        when(projection.getStopId()).thenReturn(stopId);
        when(projection.getStopName()).thenReturn(stopName);
        when(projection.getConfirmedBoardings()).thenReturn(boardings);
        return projection;
    }
}
