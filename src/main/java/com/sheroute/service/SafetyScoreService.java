package com.sheroute.service;

import java.time.LocalTime;

import org.springframework.stereotype.Service;

import com.sheroute.dto.SafetyDataDto;

@Service
public class SafetyScoreService {

    public double calculateScore(
            SafetyDataDto safetyData,
            double distanceMeters) {

        if (safetyData == null) {
            return 0.0;
        }

        double score = 0.0;
        
        // CCTV / surveillance
        score += Math.min(
                safetyData.surveillanceCount() * 3.0,
                25.0
        );

        // Building density as an isolation indicator
        score += Math.min(
                safetyData.buildingCount() * 0.5,
                20.0
        );

        // Road type
        for (String roadType : safetyData.roadTypes()) {

            if (isMajorRoad(roadType)) {
                score += 5.0;
            }
        }

        // Time of day
        score += getTimeOfDayScore();

        return Math.round(
                Math.min(score, 100.0) * 100.0
        ) / 100.0;
    }

    private double getTimeOfDayScore() {

        int hour = LocalTime.now().getHour();

        // Daytime
        if (hour >= 6 && hour < 18) {
            return 10.0;
        }

        // Evening
        if (hour >= 18 && hour < 22) {
            return 5.0;
        }

        // Night
        return 0.0;
    }

    private boolean isMajorRoad(String roadType) {

        return "primary".equals(roadType)
                || "secondary".equals(roadType)
                || "tertiary".equals(roadType)
                || "trunk".equals(roadType);
    }
}