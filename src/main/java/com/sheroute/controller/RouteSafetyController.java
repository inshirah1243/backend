package com.sheroute.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sheroute.dto.RouteResponse;
import com.sheroute.service.RouteSafetyService;
import com.sheroute.service.RouteSafetyService.SegmentSafety;
import com.sheroute.service.RouteService;

@RestController
@RequestMapping("/api/route-safety")
public class RouteSafetyController {

    private final RouteService routeService;
    private final RouteSafetyService routeSafetyService;

    public RouteSafetyController(
            RouteService routeService,
            RouteSafetyService routeSafetyService) {

        this.routeService = routeService;
        this.routeSafetyService = routeSafetyService;
    }

    @GetMapping
    public Map<String, Object> calculateSafety(
            @RequestParam String source,
            @RequestParam String destination) {

        RouteResponse response =
                routeService.generateRoutes(source, destination);

        Map<String, Object> result = new LinkedHashMap<>();

        result.put("source", response.source());
        result.put("destination", response.destination());

        List<Map<String, Object>> routes = new ArrayList<>();

        double highestSafetyScore = -1.0;
        int safestRouteIndex = -1;

        // Calculate safety for every OSRM route
        for (int i = 0; i < response.routes().size(); i++) {

            var route = response.routes().get(i);

            Map<String, Object> routeData = new LinkedHashMap<>();

            routeData.put("routeId", route.routeId());
            routeData.put("distanceMeters", route.distanceMeters());
            routeData.put("durationSeconds", route.durationSeconds());
            routeData.put("geometry", route.geometry());

            List<SegmentSafety> segments =
                    routeSafetyService.calculateRouteSafety(route.geometry());

            double overallSafetyScore = segments.stream()
                    .mapToDouble(SegmentSafety::safetyScore)
                    .average()
                    .orElse(0.0);

            routeData.put("segments", segments);
            routeData.put("overallSafetyScore", overallSafetyScore);

            routes.add(routeData);

            // Find the route with the highest safety score
            if (overallSafetyScore > highestSafetyScore) {
                highestSafetyScore = overallSafetyScore;
                safestRouteIndex = i;
            }
        }

        // Mark only the safest route as recommended
        for (int i = 0; i < routes.size(); i++) {
            routes.get(i).put("recommended", i == safestRouteIndex);
        }

        result.put("routes", routes);

        return result;
    }
}