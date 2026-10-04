package com.sheroute.controller;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import com.sheroute.service.RoadSafetyService;

class RoadSafetyControllerTest {

    private final MockMvc mockMvc = standaloneSetup(
            new RoadSafetyController(new RoadSafetyService())
    ).build();

    @Test
    void getRoadSafetyReturnsIndexForEverySupportedRoadType() throws Exception {
        Map<String, Double> expectedIndexes = Map.ofEntries(
                Map.entry("residential", 1.0),
                Map.entry("living_street", 1.0),
                Map.entry("pedestrian", 0.9),
                Map.entry("tertiary", 0.8),
                Map.entry("secondary", 0.7),
                Map.entry("primary", 0.6),
                Map.entry("trunk", 0.5),
                Map.entry("motorway", 0.4),
                Map.entry("service", 0.5),
                Map.entry("unclassified", 0.5),
                Map.entry("unknown", 0.5)
        );

        for (Map.Entry<String, Double> entry : expectedIndexes.entrySet()) {
            mockMvc.perform(get("/api/safety/road")
                            .param("roadType", entry.getKey()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.roadType").value(entry.getKey()))
                    .andExpect(jsonPath("$.roadSafetyIndex").value(entry.getValue()));
        }
    }
}
