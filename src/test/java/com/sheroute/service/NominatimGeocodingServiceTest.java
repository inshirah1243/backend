package com.sheroute.service;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.sheroute.config.ExternalApiProperties;
import com.sheroute.dto.LocationDto;
import com.sheroute.exception.ExternalServiceException;

class NominatimGeocodingServiceTest {

    private final RestTemplate restTemplate = new RestTemplateBuilder().build();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    private final ExternalApiProperties properties = new ExternalApiProperties(
            new ExternalApiProperties.Nominatim(
                    "https://nominatim.openstreetmap.org",
                    "SheRouteMajorProject/1.0",
                    "sheroute@example.com"
            ),
            new ExternalApiProperties.Osrm("https://router.project-osrm.org"),
            new ExternalApiProperties.Overpass("https://overpass-api.de/api/interpreter"),
            Duration.ofSeconds(5),
            Duration.ofSeconds(10)
    );
    private final NominatimGeocodingService service = new NominatimGeocodingService(restTemplate, properties);

    @Test
    void geocodeSendsExpectedNominatimRequestAndMapsCoordinates() {
        server.expect(request -> {
                    URI uri = request.getURI();
                    assertThat(uri.getScheme()).isEqualTo("https");
                    assertThat(uri.getHost()).isEqualTo("nominatim.openstreetmap.org");
                    assertThat(uri.getPath()).isEqualTo("/search");
                    assertThat(UriComponentsBuilder.fromUri(uri).build().getQueryParams())
                            .containsEntry("q", java.util.List.of("GNITS,Hyderabad"))
                            .containsEntry("format", java.util.List.of("jsonv2"))
                            .containsEntry("limit", java.util.List.of("1"))
                            .containsEntry("addressdetails", java.util.List.of("0"))
                            .containsEntry("email", java.util.List.of("sheroute@example.com"));
                })
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.USER_AGENT, "SheRouteMajorProject/1.0"))
                .andRespond(withSuccess("""
                        [{
                          "place_id": 250243628,
                          "lat": "17.4118839",
                          "lon": "78.3984928",
                          "display_name": "G. Narayanamma Institute of Technology and Science (GNITS), Hyderabad"
                        }]
                        """, MediaType.APPLICATION_JSON));

        LocationDto location = service.geocode("GNITS,Hyderabad");

        assertThat(location.name()).isEqualTo("GNITS,Hyderabad");
        assertThat(location.latitude()).isEqualTo(17.4118839);
        assertThat(location.longitude()).isEqualTo(78.3984928);
        server.verify();
    }

    @Test
    void geocodeWrapsNominatimHttpErrors() {
        server.expect(request -> assertThat(request.getURI().getPath()).isEqualTo("/search"))
                .andExpect(header(HttpHeaders.USER_AGENT, "SheRouteMajorProject/1.0"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.TEXT_PLAIN)
                        .body("Access denied. See https://operations.osmfoundation.org/policies/nominatim/"));

        assertThatThrownBy(() -> service.geocode("GNITS,Hyderabad"))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessage("Nominatim geocoding service failed");
        server.verify();
    }
}
