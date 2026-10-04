package com.sheroute.service;

import org.springframework.stereotype.Service;

@Service
public class IsolationSafetyService {

    public double calculateIsolationIndex(String landUse) {
        if (landUse == null) {
            return 0.5;
        }

        return switch (landUse) {
            case "residential", "commercial" -> 1.0;
            case "institutional", "mixed" -> 0.7;
            case "industrial" -> 0.5;
            case "forest", "farmland", "grass", "meadow" -> 0.2;
            default -> 0.5;
        };
    }
}
