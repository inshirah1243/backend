package com.sheroute.service;

import com.sheroute.config.ExternalApiProperties;
import com.sheroute.dto.LocationDto;
import com.sheroute.exception.ExternalServiceException;
import com.sheroute.exception.LocationNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

@Service
public class NominatimGeocodingService implements GeocodingService {

    private static final Logger logger = LoggerFactory.getLogger(NominatimGeocodingService.class);

    private final RestTemplate restTemplate;
    private final ExternalApiProperties properties;

    public NominatimGeocodingService(RestTemplate restTemplate, ExternalApiProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    @Override
    public LocationDto geocode(String placeName) {
        URI uri = UriComponentsBuilder.fromHttpUrl(properties.nominatim().baseUrl())
                .path("/search")
                .queryParam("q", placeName)
                .queryParam("format", "jsonv2")
                .queryParam("limit", 1)
                .queryParam("addressdetails", 0)
                .queryParam("email", properties.nominatim().email())
                .build()
                .encode()
                .toUri();

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT, properties.nominatim().userAgent());

        try {
            ResponseEntity<List<NominatimResult>> response = restTemplate.exchange(
                    uri,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {
                    }
            );
            List<NominatimResult> results = response.getBody();
            if (results == null || results.isEmpty()) {
                throw new LocationNotFoundException("Location could not be found: " + placeName);
            }

            NominatimResult result = results.get(0);
            return new LocationDto(placeName, Double.parseDouble(result.lat()), Double.parseDouble(result.lon()));
        } catch (LocationNotFoundException ex) {
            throw ex;
        } catch (HttpStatusCodeException ex) {
            logger.warn(
                    "Nominatim geocoding request failed for place '{}' with HTTP status {} and response body: {}",
                    placeName,
                    ex.getStatusCode(),
                    ex.getResponseBodyAsString()
            );
            throw new ExternalServiceException("Nominatim geocoding service failed", ex);
        } catch (RestClientException | NumberFormatException ex) {
            logger.warn("Nominatim geocoding request failed for place '{}'", placeName, ex);
            throw new ExternalServiceException("Nominatim geocoding service failed", ex);
        }
    }

    private record NominatimResult(String lat, String lon) {
    }
}
