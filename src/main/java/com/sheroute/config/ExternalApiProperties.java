package com.sheroute.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "external-apis")
public record ExternalApiProperties(
        Nominatim nominatim,
        Osrm osrm,
        Overpass overpass,
        Duration connectTimeout,
        Duration readTimeout
) {
    public ExternalApiProperties {
        if (nominatim == null) {
            nominatim = new Nominatim(
                    "https://nominatim.openstreetmap.org",
                    "SheRouteMajorProject/1.0",
                    "sheroute@example.com"
            );
        }

        if (osrm == null) {
            osrm = new Osrm(
                    "https://router.project-osrm.org"
            );
        }

        if (overpass == null) {
            overpass = new Overpass(
                    "https://overpass-api.de/api/interpreter"
            );
        }

        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(5);
        }

        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(10);
        }
    }

    public record Nominatim(
            String baseUrl,
            String userAgent,
            String email
    ) {
    }

    public record Osrm(
            String baseUrl
    ) {
    }

    public record Overpass(
            String baseUrl
    ) {
    }
}