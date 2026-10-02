package com.sheroute.controller;

import com.sheroute.dto.RouteResponse;
import com.sheroute.exception.InvalidRouteRequestException;
import com.sheroute.service.RouteService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/routes")
public class RouteController {

    private final RouteService routeService;

    public RouteController(RouteService routeService) {
        this.routeService = routeService;
    }

    @GetMapping
    public RouteResponse getRoutes(
        @RequestParam(required = false) String source,
        @RequestParam(required = false) Double sourceLatitude,
        @RequestParam(required = false) Double sourceLongitude,
        @RequestParam @NotBlank(message = "destination is required") String destination) {

        String cleanedDestination = requireText(destination, "destination");

        // Use exact GPS coordinates when available
        if (sourceLatitude != null && sourceLongitude != null) {
            return routeService.generateRoutes(
                    sourceLatitude,
                    sourceLongitude,
                    cleanedDestination
            );
        }

        // Otherwise use the old text-based source
        String cleanedSource = requireText(source, "source");

        return routeService.generateRoutes(
                cleanedSource,
                cleanedDestination
        );
    }

    private String requireText(String value, String parameterName) {
        if (value == null || value.trim().isEmpty()) {
            throw new InvalidRouteRequestException(parameterName + " parameter is required");
        }
        return value.trim();
    }
}
