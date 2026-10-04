package com.sheroute.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sheroute.service.RoadSafetyService;

@RestController
@RequestMapping("/api/safety/road")
public class RoadSafetyController {

    private final RoadSafetyService roadSafetyService;

    public RoadSafetyController(RoadSafetyService roadSafetyService) {
        this.roadSafetyService = roadSafetyService;
    }

    @GetMapping
    public Map<String, Object> getRoadSafety(
            @RequestParam String roadType) {

        double roadSafetyIndex =
                roadSafetyService.calculateRoadSafetyIndex(roadType);

        return Map.of(
                "roadType", roadType,
                "roadSafetyIndex", roadSafetyIndex
        );
    }
}
