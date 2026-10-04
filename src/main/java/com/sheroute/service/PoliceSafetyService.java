package com.sheroute.service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sheroute.dto.CoordinateDto;

@Service
public class PoliceSafetyService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Get all police stations around the whole route in ONE request
    public List<PolicePoint> findPoliceStations(
            List<CoordinateDto> routePoints) {

        List<PolicePoint> policeStations = new ArrayList<>();

        if (routePoints == null || routePoints.isEmpty()) {
            return policeStations;
        }

        double minLat = Double.MAX_VALUE;
        double maxLat = -Double.MAX_VALUE;
        double minLon = Double.MAX_VALUE;
        double maxLon = -Double.MAX_VALUE;

        for (CoordinateDto point : routePoints) {

            minLat = Math.min(minLat, point.latitude());
            maxLat = Math.max(maxLat, point.latitude());

            minLon = Math.min(minLon, point.longitude());
            maxLon = Math.max(maxLon, point.longitude());
        }

        // Small buffer around the route
        double buffer = 0.01;

        minLat -= buffer;
        maxLat += buffer;
        minLon -= buffer;
        maxLon += buffer;

        try {

            String query =
                    "[out:json];" +
                    "nwr[\"amenity\"=\"police\"]" +
                    "(" + minLat + "," + minLon + "," +
                    maxLat + "," + maxLon + ");" +
                    "out center;";
                    String url = "https://overpass.kumi.systems/api/interpreter";

            HttpClient client = HttpClient.newHttpClient();

            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .header(
                                    "User-Agent",
                                    "SheRoute-Android"
                            )
                            .header(
                                    "Content-Type",
                                    "application/x-www-form-urlencoded"
                            )
                            .timeout(
                                    java.time.Duration.ofSeconds(60)
                            )
                            .POST(
                                    HttpRequest.BodyPublishers.ofString(
                                            "data=" + URLEncoder.encode(
                                                    query,
                                                    StandardCharsets.UTF_8
                                            )
                                    )
                            )
                            .build();

            System.out.println(
                    "Calling Overpass for police stations..."
            );

            HttpResponse<String> response =
                    client.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );

            System.out.println(
                    "Overpass response: "
                            + response.statusCode()
            );

            if (response.statusCode() != 200) {
                System.out.println(
                        "Overpass error: HTTP "
                                + response.statusCode()
                );
                return policeStations;
            }

            JsonNode root =
                    objectMapper.readTree(response.body());

            JsonNode elements = root.get("elements");

            if (elements == null || !elements.isArray()) {
                return policeStations;
            }

            for (JsonNode element : elements) {

                double lat;
                double lon;

                if (element.has("lat")) {

                    lat = element.get("lat").asDouble();
                    lon = element.get("lon").asDouble();

                } else if (element.has("center")) {

                    lat = element
                            .get("center")
                            .get("lat")
                            .asDouble();

                    lon = element
                            .get("center")
                            .get("lon")
                            .asDouble();

                } else {
                    continue;
                }

                policeStations.add(
                        new PolicePoint(lat, lon)
                );
            }

            System.out.println(
                    "Police stations found: "
                            + policeStations.size()
            );

        } catch (Exception e) {

            System.out.println("Police lookup failed!");
            System.out.println(
                    "Exception: " + e.getMessage()
            );
            e.printStackTrace();
        }

        return policeStations;
    }

    // Find nearest police station locally
    public double findNearestDistance(
            double latitude,
            double longitude,
            List<PolicePoint> policeStations) {

        if (policeStations == null ||
                policeStations.isEmpty()) {

            return -1;
        }

        double nearestDistance = Double.MAX_VALUE;

        for (PolicePoint police : policeStations) {

            double distance = calculateDistance(
                    latitude,
                    longitude,
                    police.latitude(),
                    police.longitude()
            );

            if (distance < nearestDistance) {
                nearestDistance = distance;
            }
        }

        return nearestDistance;
    }

    // Convert police distance into safety index
    public double calculatePoliceIndex(
            double distanceMeters) {

        double maxDistance = 3000.0;

        if (distanceMeters < 0 ||
                distanceMeters >= maxDistance) {

            return 0.0;
        }

        return 1.0 -
                (distanceMeters / maxDistance);
    }

    // Haversine distance calculation
    private double calculateDistance(
            double lat1,
            double lon1,
            double lat2,
            double lon2) {

        final double earthRadius = 6371000;

        double latDifference =
                Math.toRadians(lat2 - lat1);

        double lonDifference =
                Math.toRadians(lon2 - lon1);

        double a =
                Math.sin(latDifference / 2)
                        * Math.sin(latDifference / 2)
                +
                Math.cos(Math.toRadians(lat1))
                        * Math.cos(Math.toRadians(lat2))
                        * Math.sin(lonDifference / 2)
                        * Math.sin(lonDifference / 2);

        double c =
                2 * Math.atan2(
                        Math.sqrt(a),
                        Math.sqrt(1 - a)
                );

        return earthRadius * c;
    }

    // Used by SafetyController for a single location
    public double findNearestPoliceDistance(
            double latitude,
            double longitude) {

        List<CoordinateDto> points =
                new ArrayList<>();

        points.add(
                new CoordinateDto(
                        latitude,
                        longitude
                )
        );

        List<PolicePoint> policeStations =
                findPoliceStations(points);

        return findNearestDistance(
                latitude,
                longitude,
                policeStations
        );
    }

    public record PolicePoint(
            double latitude,
            double longitude
    ) {
    }
}