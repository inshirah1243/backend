package com.sheroute.dto;

public record SafetyResponse(
        double timeIndex,
        double policeDistance,
        double policeIndex
) {
}