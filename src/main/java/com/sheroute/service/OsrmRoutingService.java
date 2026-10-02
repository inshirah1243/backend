package com.sheroute.service;

import java.util.List;

import com.sheroute.dto.LocationDto;
import com.sheroute.dto.RouteOptionDto;

public interface OsrmRoutingService {

    List<RouteOptionDto> findRoutes(LocationDto source, LocationDto destination);
}
