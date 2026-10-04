package com.sheroute.controller;

import java.util.List;

import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import com.sheroute.dto.CoordinateDto;
import com.sheroute.dto.LocationDto;
import com.sheroute.dto.RouteOptionDto;
import com.sheroute.dto.RouteResponse;
import com.sheroute.service.RouteSafetyService;
import com.sheroute.service.RouteSafetyService.SegmentSafety;
import com.sheroute.service.RouteService;

class RouteSafetyControllerTest {

    private final RouteService routeService = mock(RouteService.class);
    private final RouteSafetyService routeSafetyService =
            mock(RouteSafetyService.class);
    private final MockMvc mockMvc = standaloneSetup(
            new RouteSafetyController(routeService, routeSafetyService)
    ).build();

    @Test
    void returnsRouteIdAndSegmentSafetyParameters() throws Exception {
        List<CoordinateDto> geometry =
                List.of(new CoordinateDto(17.412, 78.353));
        RouteOptionDto route = new RouteOptionDto(1, 1200, 300, geometry);
        when(routeService.generateRoutes("Start", "End")).thenReturn(
                new RouteResponse(
                        new LocationDto("Start", 17.412, 78.353),
                        new LocationDto("End", 17.361, 78.474),
                        List.of(route)
                )
        );
        when(routeSafetyService.calculateRouteSafety(geometry)).thenReturn(
                List.of(new SegmentSafety(
                        1,
                        geometry.get(0),
                        "primary",
                        0.6,
                        "forest",
                        0.2,
                        0.6,
                        null,
                        false,
                        0.5,
                        (0.6 + 0.2 + 0.6) / 3.0
                ))
        );

        mockMvc.perform(get("/api/route-safety")
                        .param("source", "Start")
                        .param("destination", "End"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.routes[0].routeId").value(1))
                .andExpect(jsonPath("$.routes[0].segments[0].roadType").value("primary"))
                .andExpect(jsonPath("$.routes[0].segments[0].roadSafetyIndex").value(0.6))
                .andExpect(jsonPath("$.routes[0].segments[0].landUse").value("forest"))
                .andExpect(jsonPath("$.routes[0].segments[0].isolationIndex").value(0.2))
                .andExpect(jsonPath("$.routes[0].segments[0].timeIndex").value(0.6))
                .andExpect(jsonPath("$.routes[0].segments[0].safetyScore")
                        .value((0.6 + 0.2 + 0.6) / 3.0));
    }
}
