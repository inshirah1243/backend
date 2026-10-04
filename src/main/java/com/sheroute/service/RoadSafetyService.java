package com.sheroute.service;

import org.springframework.stereotype.Service;

@Service
public class RoadSafetyService {

    public double calculateRoadSafetyIndex(String roadType) {
        if (roadType == null) {
            return 0.5;
        }

        return switch (roadType) {
            case "residential", "living_street" -> 1.0;
            case "pedestrian" -> 0.9;
            case "tertiary" -> 0.8;
            case "secondary" -> 0.7;
            case "primary" -> 0.6;
            case "trunk" -> 0.5;
            case "motorway" -> 0.4;
            case "service", "unclassified" -> 0.5;
            default -> 0.5;
        };
    }
}
