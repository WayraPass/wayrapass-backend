package com.wayrapass.agent.tools;

import com.wayrapass.agent.WayriaReadService;
import com.wayrapass.agent.dto.RouteDetailSummary;
import com.wayrapass.agent.dto.RouteSummary;
import com.wayrapass.agent.dto.ServiceStatusSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

/** Tools only delegate to the read-only application layer; they contain no SQL or write behaviour. */
@Component
@RequiredArgsConstructor
@Slf4j
public class WayriaTools {
    private final WayriaReadService readService;

    @Tool(description = "Busca rutas activas de WayraPass por código, nombre, origen, destino, universidad o campus, pero SOLO para identificar una ruta. Nunca la uses para responder horarios o paraderos: para eso usa getRouteDetails.")
    public List<RouteSummary> searchRoutes(String reference) {
        var result = readService.findRoutes(reference); log.info("WayrIA tool invoked: searchRoutes reference={} resultCount={}", reference, result.size()); return result;
    }

    @Tool(description = "Consulta los paraderos y horarios reales de una ruta de WayraPass. Úsala de inmediato si el usuario menciona un código de ruta explícito como UPC-MON-SMI-01 y pide horarios o paraderos. El parámetro reference es ese código. Si hay varias coincidencias, devuelve todas para pedir aclaración.")
    public List<RouteDetailSummary> getRouteDetails(String reference) {
        var result = readService.routeDetails(reference); log.info("WayrIA tool invoked: getRouteDetails reference={} resultCount={}", reference, result.size()); return result;
    }

    @Tool(description = "Consulta las rutas disponibles para el STUDENT autenticado usando su institución registrada. Úsala para preguntas como mis rutas disponibles. No recibe nombres ni IDs.")
    public List<RouteSummary> getMyAvailableRoutes() {
        var result = readService.myAvailableRoutes(); log.info("WayrIA tool invoked: getMyAvailableRoutes resultCount={}", result.size()); return result;
    }

    @Tool(description = "Consulta horarios y paraderos de la ruta inscrita del STUDENT autenticado. Úsala para preguntas como a qué hora sale mi ruta. No la uses para el estado de un viaje.")
    public List<RouteDetailSummary> getMyRouteDetails() {
        var result = readService.myRouteDetails(); log.info("WayrIA tool invoked: getMyRouteDetails resultCount={}", result.size()); return result;
    }

    @Tool(description = "Consulta el estado real del viaje o ruta actual del usuario autenticado. Para FAMILY, studentReference solo puede ser el nombre de un estudiante vinculado autorizado; déjalo vacío si tiene un único vínculo.")
    public ServiceStatusSummary getCurrentServiceStatus(String studentReference) {
        var result = readService.currentServiceStatus(studentReference); log.info("WayrIA tool invoked: getCurrentServiceStatus hasRoute={}", result.routeCode()!=null); return result;
    }
}
