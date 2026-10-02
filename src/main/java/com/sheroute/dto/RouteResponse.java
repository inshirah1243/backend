package com.sheroute.dto;

import java.util.List;

public record RouteResponse(
        LocationDto source,
        LocationDto destination,
        List<RouteOptionDto> routes
) {
}
