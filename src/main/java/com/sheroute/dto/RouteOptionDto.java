package com.sheroute.dto;

import java.util.List;

public record RouteOptionDto(
        int routeId,
        double distanceMeters,
        double durationSeconds,
        List<CoordinateDto> geometry
) {
}