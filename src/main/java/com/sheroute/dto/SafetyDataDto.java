package com.sheroute.dto;

import java.util.List;

public record SafetyDataDto(
        int streetLampCount,
        int surveillanceCount,
        int buildingCount,
        List<String> roadTypes
) {
}