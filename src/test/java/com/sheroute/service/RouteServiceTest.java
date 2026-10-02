package com.sheroute.service;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

import com.sheroute.dto.CoordinateDto;
import com.sheroute.dto.LocationDto;
import com.sheroute.dto.RouteOptionDto;
import com.sheroute.dto.RouteResponse;
import com.sheroute.exception.ExternalServiceException;
import com.sheroute.exception.LocationNotFoundException;
import com.sheroute.exception.NoRouteFoundException;

class RouteServiceTest {

    @Test
    void generateRoutesReturnsGeocodedLocationsAndCandidateRoutes() {
        LocationDto source = new LocationDto("GNITS,Hyderabad", 17.412, 78.353);
        LocationDto destination = new LocationDto("Charminar,Hyderabad", 17.361, 78.474);
        RouteOptionDto route = new RouteOptionDto(
        1,
        12400,
        1920,
        List.of(
                new CoordinateDto(17.412, 78.353),
                new CoordinateDto(17.361, 78.474)
        )
        );
        RouteService service = new RouteService(
                place -> place.startsWith("GNITS") ? source : destination,
                (from, to) -> List.of(route)
        );

        RouteResponse response = service.generateRoutes("GNITS,Hyderabad", "Charminar,Hyderabad");

        assertThat(response.source()).isEqualTo(source);
        assertThat(response.destination()).isEqualTo(destination);
        assertThat(response.routes()).containsExactly(route);
    }

    @Test
    void generateRoutesPropagatesInvalidLocation() {
        RouteService service = new RouteService(
                place -> {
                    throw new LocationNotFoundException("Location could not be found: " + place);
                },
                (from, to) -> List.of()
        );

        assertThatThrownBy(() -> service.generateRoutes("Unknown place", "Charminar,Hyderabad"))
                .isInstanceOf(LocationNotFoundException.class)
                .hasMessageContaining("Unknown place");
    }

    @Test
    void generateRoutesPropagatesNoRouteFound() {
        RouteService service = new RouteService(
                place -> new LocationDto(place, 17.0, 78.0),
                (from, to) -> {
                    throw new NoRouteFoundException("No driving route found between source and destination");
                }
        );

        assertThatThrownBy(() -> service.generateRoutes("A", "B"))
                .isInstanceOf(NoRouteFoundException.class);
    }

    @Test
    void generateRoutesPropagatesExternalServiceFailure() {
        RouteService service = new RouteService(
                place -> new LocationDto(place, 17.0, 78.0),
                (from, to) -> {
                    throw new ExternalServiceException("OSRM routing service failed");
                }
        );

        assertThatThrownBy(() -> service.generateRoutes("A", "B"))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessage("OSRM routing service failed");
    }
}
