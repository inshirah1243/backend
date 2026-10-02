package com.sheroute.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.sheroute.dto.CoordinateDto;
import com.sheroute.dto.SafetyDataDto;

@Service
public class SafetyDataService {

    public SafetyDataDto getSafetyData(
            List<CoordinateDto> routeCoordinates) {

        if (routeCoordinates == null
                || routeCoordinates.isEmpty()) {

            return emptySafetyData();
        }

        // Street lighting removed.
        // Other safety parameters can be added here later.

        return emptySafetyData();
    }

    private SafetyDataDto emptySafetyData() {

        return new SafetyDataDto(
                0,
                0,
                0,
                new ArrayList<>()
        );
    }
}