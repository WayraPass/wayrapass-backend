package com.wayrapass.repository;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.math.BigDecimal;

public interface RouteSearchProjection {
    Long getRouteId();
    String getRouteCode();
    String getRouteName();
    String getOrigin();
    String getDestination();
    Long getInstitutionId();
    String getInstitutionName();
    String getCampusName();
    Long getStopId();
    String getStopName();
    String getStopAddress();
    String getStopDistrict();
    BigDecimal getStopLatitude();
    BigDecimal getStopLongitude();
    Integer getStopOrder();
    Integer getEstimatedMinutesFromStart();
    DayOfWeek getDayOfWeek();
    LocalTime getDepartureTime();
    LocalTime getEstimatedArrivalTime();
}
