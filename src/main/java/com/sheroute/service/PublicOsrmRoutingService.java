package com.sheroute.service;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.sheroute.config.ExternalApiProperties;
import com.sheroute.dto.CoordinateDto;
import com.sheroute.dto.LocationDto;
import com.sheroute.dto.RouteOptionDto;
import com.sheroute.exception.ExternalServiceException;
import com.sheroute.exception.NoRouteFoundException;

@Service
public class PublicOsrmRoutingService implements OsrmRoutingService {

    private final RestTemplate restTemplate;
    private final ExternalApiProperties properties;

    public PublicOsrmRoutingService(
            RestTemplate restTemplate,
            ExternalApiProperties properties) {

        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    @Override
    public List<RouteOptionDto> findRoutes(
            LocationDto source,
            LocationDto destination) {

        String coordinates = String.format(
                Locale.ROOT,
                "%f,%f;%f,%f",
                source.longitude(),
                source.latitude(),
                destination.longitude(),
                destination.latitude()
        );

        URI uri = UriComponentsBuilder
                .fromHttpUrl(properties.osrm().baseUrl())
                .path("/route/v1/driving/" + coordinates)
                .queryParam("alternatives", "true")
                .queryParam("overview", "full")
                .queryParam("geometries", "geojson")
                .build()
                .toUri();

        try {

            OsrmResponse response =
                    restTemplate.getForObject(
                            uri,
                            OsrmResponse.class
                    );

            if (response == null
                    || response.routes() == null
                    || response.routes().isEmpty()) {

                throw new NoRouteFoundException(
                        "No driving route found between source and destination"
                );
            }
            System.out.println("OSRM returned routes: " + response.routes().size());

            List<RouteOptionDto> routes =
                    new ArrayList<>();

            for (int index = 0;
                 index < response.routes().size();
                 index++) {

                OsrmRoute route =
                        response.routes().get(index);

                routes.add(
                        new RouteOptionDto(
                                index + 1,
                                route.distance(),
                                route.duration(),
                                toCoordinates(
                                        route.geometry()
                                )
                        )
                );
            }

            return routes;

        } catch (NoRouteFoundException ex) {

            throw ex;

        } catch (RestClientException ex) {

            throw new ExternalServiceException(
                    "OSRM routing service failed",
                    ex
            );
        }
    }

    private List<CoordinateDto> toCoordinates(
            OsrmGeometry geometry) {

        if (geometry == null
                || geometry.coordinates() == null
                || geometry.coordinates().isEmpty()) {

            return List.of();
        }

        return geometry.coordinates()
                .stream()
                .filter(point ->
                        point != null
                                && point.size() >= 2
                )
                .map(point ->
                        new CoordinateDto(
                                point.get(1),
                                point.get(0)
                        )
                )
                .toList();
    }

    private record OsrmResponse(
            List<OsrmRoute> routes) {
    }

    private record OsrmRoute(
            double distance,
            double duration,
            OsrmGeometry geometry) {
    }

    private record OsrmGeometry(
            List<List<Double>> coordinates) {
    }
}