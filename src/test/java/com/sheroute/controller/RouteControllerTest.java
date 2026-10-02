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
import com.sheroute.exception.ExternalServiceException;
import com.sheroute.exception.GlobalExceptionHandler;
import com.sheroute.exception.LocationNotFoundException;
import com.sheroute.exception.NoRouteFoundException;
import com.sheroute.service.RouteService;

class RouteControllerTest {

    private final RouteService routeService = mock(RouteService.class);
    private final MockMvc mockMvc = standaloneSetup(new RouteController(routeService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void getRoutesReturnsSuccessfulResponse() throws Exception {
        RouteResponse response = new RouteResponse(
                new LocationDto("GNITS,Hyderabad", 17.412, 78.353),
                new LocationDto("Charminar,Hyderabad", 17.361, 78.474),        
                List.of(new RouteOptionDto(
                        1,
                        12400,
                        1920,
                        List.of(
                                new CoordinateDto(17.412, 78.353),
                                new CoordinateDto(17.361, 78.474)
                        )
                ))
        );
        when(routeService.generateRoutes("GNITS,Hyderabad", "Charminar,Hyderabad")).thenReturn(response);

        mockMvc.perform(get("/api/routes")
                        .param("source", "GNITS,Hyderabad")
                        .param("destination", "Charminar,Hyderabad"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source.name").value("GNITS,Hyderabad"))
                .andExpect(jsonPath("$.routes[0].routeId").value(1))
                .andExpect(jsonPath("$.routes[0].geometry[0].latitude").value(17.412));
    }

    @Test
    void getRoutesReturnsBadRequestForMissingDestination() throws Exception {
        mockMvc.perform(get("/api/routes").param("source", "GNITS,Hyderabad"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("destination parameter is required"));
    }

    @Test
    void getRoutesReturnsBadRequestForBlankSource() throws Exception {
        mockMvc.perform(get("/api/routes")
                        .param("source", "   ")
                        .param("destination", "Charminar,Hyderabad"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("source parameter is required"));
    }

    @Test
    void getRoutesReturnsNotFoundForInvalidLocation() throws Exception {
        when(routeService.generateRoutes("Unknown", "Charminar,Hyderabad"))
                .thenThrow(new LocationNotFoundException("Location could not be found: Unknown"));

        mockMvc.perform(get("/api/routes")
                        .param("source", "Unknown")
                        .param("destination", "Charminar,Hyderabad"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Location could not be found: Unknown"));
    }

    @Test
    void getRoutesReturnsNotFoundWhenNoRouteExists() throws Exception {
        when(routeService.generateRoutes("GNITS,Hyderabad", "Remote island"))
                .thenThrow(new NoRouteFoundException("No driving route found between source and destination"));

        mockMvc.perform(get("/api/routes")
                        .param("source", "GNITS,Hyderabad")
                        .param("destination", "Remote island"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No driving route found between source and destination"));
    }

    @Test
    void getRoutesReturnsBadGatewayForExternalFailure() throws Exception {
        when(routeService.generateRoutes("GNITS,Hyderabad", "Charminar,Hyderabad"))
                .thenThrow(new ExternalServiceException("OSRM routing service failed"));

        mockMvc.perform(get("/api/routes")
                        .param("source", "GNITS,Hyderabad")
                        .param("destination", "Charminar,Hyderabad"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("OSRM routing service failed"));
    }
}
