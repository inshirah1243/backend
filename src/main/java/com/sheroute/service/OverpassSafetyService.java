package com.sheroute.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sheroute.config.ExternalApiProperties;
import com.sheroute.dto.CoordinateDto;

@Service
public class OverpassSafetyService {

    private static final Duration INFRASTRUCTURE_QUERY_TIMEOUT =
            Duration.ofSeconds(15);
    private static final Duration INFRASTRUCTURE_READ_TIMEOUT =
            Duration.ofSeconds(20);

    private static final Logger LOGGER =
            LoggerFactory.getLogger(OverpassSafetyService.class);

    private final RestTemplate restTemplate;
    private final RestTemplate infrastructureRestTemplate;
    private final ObjectMapper objectMapper;
    private final ExternalApiProperties properties;

    public OverpassSafetyService(
            RestTemplate restTemplate,
            RestTemplateBuilder restTemplateBuilder,
            ExternalApiProperties properties) {

        this.restTemplate = restTemplate;
        this.infrastructureRestTemplate = restTemplateBuilder
                .setConnectTimeout(properties.connectTimeout())
                .setReadTimeout(INFRASTRUCTURE_READ_TIMEOUT)
                .build();
        this.properties = properties;
        this.objectMapper = new ObjectMapper();
    }

    public SafetyData getSafetyData(
            List<CoordinateDto> routeCoordinates) {

        if (routeCoordinates == null
                || routeCoordinates.isEmpty()) {

            return new SafetyData();
        }

        List<CoordinateDto> samplePoints =
                sampleRoute(routeCoordinates);

        String query =
                buildOverpassQuery(samplePoints);

        try {

            String encodedQuery =
                    "data=" + URLEncoder.encode(
                            query,
                            StandardCharsets.UTF_8
                    );

            HttpHeaders headers =
                    new HttpHeaders();

            headers.setContentType(
                    MediaType.APPLICATION_FORM_URLENCODED
            );

            HttpEntity<String> request =
                    new HttpEntity<>(
                            encodedQuery,
                            headers
                    );

            String response =
                    restTemplate.postForObject(
                            properties.overpass().baseUrl(),
                            request,
                            String.class
                    );

            return parseResponse(response);

        } catch (Exception e) {

            // Do not block route generation
            System.out.println(
                    "Overpass unavailable. Continuing without safety data."
            );

            return new SafetyData();
        }
    }

    public List<RouteInfrastructureFeature> getRouteInfrastructureData(
            List<CoordinateDto> samplePoints) {

        if (samplePoints == null || samplePoints.isEmpty()) {
            return List.of();
        }

        String query = buildRouteInfrastructureQuery(samplePoints);
        String encodedQuery =
                "data=" + URLEncoder.encode(query, StandardCharsets.UTF_8);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        HttpEntity<String> request =
                new HttpEntity<>(encodedQuery, headers);

        try {
            String response = infrastructureRestTemplate.postForObject(
                    properties.overpass().baseUrl(),
                    request,
                    String.class
            );
            return parseRouteInfrastructureResponse(response);
        } catch (RestClientException e) {
            LOGGER.warn("Unable to retrieve route infrastructure from Overpass", e);
            return List.of();
        }
    }

    private String buildRouteInfrastructureQuery(
            List<CoordinateDto> points) {

        StringBuilder query =
                new StringBuilder()
                        .append("[out:json][timeout:")
                        .append(INFRASTRUCTURE_QUERY_TIMEOUT.toSeconds())
                        .append("];\n");
        List<String> resultSets = new ArrayList<>();

        int pointIndex = 0;
        for (CoordinateDto point : points) {
            if (point == null) {
                continue;
            }

            query.append("way[\"highway\"](around:50,")
                    .append(point.latitude()).append(',')
                    .append(point.longitude()).append(")->.route_roads_")
                    .append(pointIndex).append(";\n");
            query.append("is_in(")
                    .append(point.latitude()).append(',')
                    .append(point.longitude()).append(")->.route_areas_")
                    .append(pointIndex).append(";\n")
                    .append("area.route_areas_")
                    .append(pointIndex)
                    .append("[\"landuse\"]->.route_landuse_")
                    .append(pointIndex).append(";\n");
            resultSets.add(".route_roads_" + pointIndex);
            resultSets.add(".route_landuse_" + pointIndex);
            pointIndex++;
        }

        query.append("(\n");
        for (String resultSet : resultSets) {
            query.append(resultSet).append(";\n");
        }

        return query.append(");\nout center tags;\n").toString();
    }

    private List<RouteInfrastructureFeature> parseRouteInfrastructureResponse(
            String response) {

        if (response == null || response.isEmpty()) {
            return List.of();
        }

        try {
            JsonNode elements = objectMapper.readTree(response).path("elements");
            if (!elements.isArray()) {
                return List.of();
            }

            List<RouteInfrastructureFeature> features = new ArrayList<>();
            Set<String> processedElements = new HashSet<>();

            for (JsonNode element : elements) {
                String elementId = element.path("type").asText()
                        + "-" + element.path("id").asText();
                if (!processedElements.add(elementId)) {
                    continue;
                }

                JsonNode tags = element.path("tags");
                String roadType = tagValue(tags, "highway");
                String landUse = tagValue(tags, "landuse");
                if (roadType == null && landUse == null) {
                    continue;
                }

                JsonNode center = element.path("center");
                double latitude = center.isObject()
                        ? center.path("lat").asDouble(Double.NaN)
                        : element.path("lat").asDouble(Double.NaN);
                double longitude = center.isObject()
                        ? center.path("lon").asDouble(Double.NaN)
                        : element.path("lon").asDouble(Double.NaN);

                if (Double.isFinite(latitude) && Double.isFinite(longitude)) {
                    features.add(new RouteInfrastructureFeature(
                            latitude,
                            longitude,
                            roadType,
                            landUse
                    ));
                }
            }

            return features;
        } catch (JsonProcessingException e) {
            LOGGER.warn("Unable to parse route infrastructure from Overpass", e);
            return List.of();
        }
    }

    private String tagValue(JsonNode tags, String tag) {
        String value = tags.path(tag).asText();
        return value.isBlank() ? null : value;
    }

    private List<CoordinateDto> sampleRoute(
            List<CoordinateDto> route) {

        List<CoordinateDto> samples =
                new ArrayList<>();

        samples.add(route.get(0));

        double distanceSinceLastSample = 0;

        for (int i = 1; i < route.size(); i++) {

            CoordinateDto previous =
                    route.get(i - 1);

            CoordinateDto current =
                    route.get(i);

            distanceSinceLastSample +=
                    distanceInMeters(
                            previous,
                            current
                    );

            // Sample only every 1 km
            if (distanceSinceLastSample >= 1000) {

                samples.add(current);

                distanceSinceLastSample = 0;
            }
        }

        CoordinateDto last =
                route.get(route.size() - 1);

        if (samples.get(samples.size() - 1) != last) {
            samples.add(last);
        }

        return samples;
    }

    private String buildOverpassQuery(
            List<CoordinateDto> points) {

        StringBuilder query =
                new StringBuilder();

        query.append(
                "[out:json][timeout:3];\n"
        );

        query.append("(\n");

        for (CoordinateDto point : points) {

            double latitude =
                    point.latitude();

            double longitude =
                    point.longitude();

            // Street lighting
            query.append(
                    "node[\"highway\"=\"street_lamp\"]"
                            + "(around:50,"
                            + latitude + ","
                            + longitude + ");\n"
            );

            // CCTV / surveillance
            query.append(
                    "node[\"surveillance\"]"
                            + "(around:50,"
                            + latitude + ","
                            + longitude + ");\n"
            );

            query.append(
                    "node[\"man_made\"=\"surveillance\"]"
                            + "(around:50,"
                            + latitude + ","
                            + longitude + ");\n"
            );
        }

        query.append(");\n");
        query.append("out tags;\n");

        return query.toString();
    }

    private SafetyData parseResponse(
            String response) {

        SafetyData data =
                new SafetyData();

        if (response == null
                || response.isEmpty()) {

            return data;
        }

        try {

            JsonNode root =
                    objectMapper.readTree(response);

            JsonNode elements =
                    root.get("elements");

            if (elements == null
                    || !elements.isArray()) {

                return data;
            }

            Set<String> processedElements =
                    new HashSet<>();

            for (JsonNode element : elements) {

                String elementId =
                        element.path("type").asText()
                                + "-"
                                + element.path("id").asText();

                if (!processedElements.add(
                        elementId)) {

                    continue;
                }

                JsonNode tags =
                        element.get("tags");

                if (tags == null) {
                    continue;
                }

                if ("street_lamp".equals(
                        tags.path("highway").asText())) {

                    data.streetLampCount++;
                }

                if (tags.has("surveillance")
                        || tags.has("camera:type")
                        || "surveillance".equals(
                        tags.path("man_made").asText())) {

                    data.surveillanceCount++;
                }
            }

        } catch (Exception e) {

            System.out.println(
                    "Error parsing Overpass response."
            );
        }

        return data;
    }

    private double distanceInMeters(
            CoordinateDto point1,
            CoordinateDto point2) {

        final double earthRadius = 6371000;

        double lat1 =
                Math.toRadians(point1.latitude());

        double lat2 =
                Math.toRadians(point2.latitude());

        double latDifference =
                Math.toRadians(
                        point2.latitude()
                                - point1.latitude()
                );

        double lonDifference =
                Math.toRadians(
                        point2.longitude()
                                - point1.longitude()
                );

        double a =
                Math.sin(latDifference / 2)
                        * Math.sin(latDifference / 2)
                        + Math.cos(lat1)
                        * Math.cos(lat2)
                        * Math.sin(lonDifference / 2)
                        * Math.sin(lonDifference / 2);

        double c =
                2 * Math.atan2(
                        Math.sqrt(a),
                        Math.sqrt(1 - a)
                );

        return earthRadius * c;
    }

    public static class SafetyData {

        private int streetLampCount;
        private int surveillanceCount;
        private int buildingCount;

        private List<String> roadTypes =
                new ArrayList<>();

        public int getStreetLampCount() {
            return streetLampCount;
        }

        public int getSurveillanceCount() {
            return surveillanceCount;
        }

        public int getBuildingCount() {
            return buildingCount;
        }

        public List<String> getRoadTypes() {
            return roadTypes;
        }
    }

    public record RouteInfrastructureFeature(
            double latitude,
            double longitude,
            String roadType,
            String landUse
    ) {
    }
}