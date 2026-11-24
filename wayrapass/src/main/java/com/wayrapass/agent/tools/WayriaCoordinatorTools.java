package com.wayrapass.agent.tools;

import com.wayrapass.agent.dto.PunctualitySummary;
import com.wayrapass.exception.ResourceNotFoundException;
import com.wayrapass.service.PunctualityAnalyticsService;
import com.wayrapass.service.ReportService;
import com.wayrapass.dto.response.ReportResponses;
import com.wayrapass.model.ReportIndicator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/** Coordinator-only, read-only analytics tools. They expose no student/family capabilities. */
@Component
@RequiredArgsConstructor
@Slf4j
public class WayriaCoordinatorTools {
    private final PunctualityAnalyticsService analytics;
    private final ReportService reports;

    @Tool(description = "Obtiene el reporte operativo completo y real para un periodo inclusivo.")
    public ReportResponses.OperationalReport getOperationalReportSummary(LocalDate from, LocalDate to) {
        var result = reports.generate(from, to, ReportIndicator.ALL);
        log.info("WayrIA coordinator tool invoked: getOperationalReportSummary from={} to={} hasData={}", from, to, result.hasData());
        return result;
    }

    @Tool(description = "Obtiene el reporte operativo real de una sola ruta para un periodo inclusivo.")
    public ReportResponses.OperationalReport getRouteOperationalReport(String routeCode, LocalDate from, LocalDate to) {
        var result = reports.generateForRoute(from, to, routeCode);
        log.info("WayrIA coordinator tool invoked: getRouteOperationalReport routeCode={} from={} to={} evaluatedTrips={}",
                routeCode, from, to, result.punctuality().evaluatedTrips());
        return result;
    }

    @Tool(description = "Obtiene métricas reales y deterministas de puntualidad para el periodo inclusivo indicado.")
    public PunctualitySummary getPunctualitySummary(LocalDate from, LocalDate to) {
        PunctualitySummary result = analytics.summarize(from, to);
        log.info("WayrIA coordinator tool invoked: getPunctualitySummary from={} to={} evaluatedTrips={}",
                from, to, result.evaluatedTrips());
        return result;
    }

    @Tool(description = "Obtiene el detalle de puntualidad de una ruta real dentro de un periodo inclusivo.")
    public PunctualitySummary.DelayedRoute getRoutePunctualityDetails(String routeCode, LocalDate from, LocalDate to) {
        PunctualitySummary summary = analytics.summarize(from, to);
        var route = summary.mostDelayedRoutes().stream().filter(item -> item.routeCode().equalsIgnoreCase(routeCode.trim()))
                .findFirst().orElseThrow(() -> new ResourceNotFoundException("No hay retrasos evaluables para la ruta " + routeCode + " en este periodo."));
        log.info("WayrIA coordinator tool invoked: getRoutePunctualityDetails routeCode={} lateTrips={}",
                route.routeCode(), route.lateTrips());
        return route;
    }
}
